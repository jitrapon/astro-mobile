import com.android.build.api.dsl.Packaging
import com.android.build.gradle.internal.tasks.AndroidTestTask
import com.ncorti.ktfmt.gradle.FormattingOptionsBean
import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // ktfmt + Detekt versions come from the root version catalog (gradle/libs.versions.toml) so the
    // Gradle plugin, the pre-commit hook's CLI jars, and verifyKtfmtAlignment share one source.
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    // Compose Stability Analyzer — emits a compose-compiler stability report (skippability /
    // restartability / parameter stability per composable) via the on-demand `stabilityDump` task.
    alias(libs.plugins.compose.stability.analyzer)
}

// ktfmt — Kotlin source formatter. Registers `ktfmtCheck` (verify) and `ktfmtFormat` (rewrite)
// lifecycle tasks. `kotlinLangStyle()` selects ktfmt's Kotlin-official-style-guide preset,
// matching `kotlin.code.style=official` — not the default Meta style or `googleStyle()`.
ktfmt { kotlinLangStyle() }

// The ncorti ktfmt plugin discovers an Android application module's Kotlin source sets through
// AGP's legacy `com.android.build.gradle.api.AndroidSourceSet` API, which AGP 9 removed. Its
// discovery is wrapped in a swallowed `runCatching`, so on AGP 9 it silently creates no
// source-set tasks for this module — the aggregate `ktfmtCheck`/`ktfmtFormat` end up covering only
// build scripts, leaving the app's Kotlin under src/main/java unformatted and unchecked. Re-create
// the per-source check/format tasks explicitly over src/main/java (and the instrumented tests under
// src/androidTest/java) using the plugin's public task
// API, deriving the formatting options from the configured `ktfmt { kotlinLangStyle() }` extension
// so the app module formats identically to `:shared`. (The KMP `:shared` module is unaffected — the
// plugin's multiplatform path does not use the removed AGP API.)
run {
    val ktfmtExtension = the<KtfmtExtension>()
    val ktfmtClasspathConfig = configurations.named("ktfmt")
    val androidKotlinSources =
        files("src/main/java", "src/androidTest/java").asFileTree.matching { include("**/*.kt") }
    val formattingOptions = provider {
        FormattingOptionsBean(
            ktfmtExtension.maxWidth.get(),
            ktfmtExtension.blockIndent.get(),
            ktfmtExtension.continuationIndent.get(),
            ktfmtExtension.trailingCommaManagementStrategy.get(),
            ktfmtExtension.removeUnusedImports.get(),
            ktfmtExtension.debuggingPrintOpsAfterFormatting.get(),
        )
    }
    val androidKtfmtCheck =
        tasks.register<KtfmtCheckTask>("ktfmtCheckAndroidMain") {
            source(androidKotlinSources)
            ktfmtClasspath.from(ktfmtClasspathConfig)
            formattingOptionsBean.set(formattingOptions)
            useClassloaderIsolation.set(ktfmtExtension.useClassloaderIsolation)
        }
    val androidKtfmtFormat =
        tasks.register<KtfmtFormatTask>("ktfmtFormatAndroidMain") {
            source(androidKotlinSources)
            ktfmtClasspath.from(ktfmtClasspathConfig)
            formattingOptionsBean.set(formattingOptions)
            useClassloaderIsolation.set(ktfmtExtension.useClassloaderIsolation)
        }
    tasks.named("ktfmtCheck") { dependsOn(androidKtfmtCheck) }
    tasks.named("ktfmtFormat") { dependsOn(androidKtfmtFormat) }
}

// Verify the standalone `ktfmt-cli` jar the pre-commit hook invokes matches the formatter the
// ncorti Gradle plugin bundles, so local Gradle, the hook, and CI can never format differently. The
// plugin registers a `ktfmt` configuration whose resolved `com.facebook:ktfmt` artifact is the real
// formatter; compare its version against `ktfmt-cli` from the version catalog.
val verifyKtfmtAlignment =
    tasks.register("verifyKtfmtAlignment") {
        group = "verification"
        description = "Fail if ktfmt-cli drifts from the ktfmt version the Gradle plugin bundles."
        // Capture the comparison as configuration-cache-safe locals: a plain String for the catalog
        // version, and a `Provider<String>` for the plugin's bundled version resolved lazily at
        // execution. Capturing the `ktfmt` configuration object directly in `doLast` instead breaks
        // under the configuration cache (the serialized task gets a null receiver).
        val expectedKtfmtCli = libs.versions.ktfmt.cli.get()
        val pluginKtfmtVersion =
            configurations.named("ktfmt").map { ktfmtConfiguration ->
                ktfmtConfiguration.incoming.resolutionResult.allComponents
                    .mapNotNull { it.moduleVersion }
                    .firstOrNull { it.group == "com.facebook" && it.name == "ktfmt" }
                    ?.version
                    ?: error(
                        "com.facebook:ktfmt not found in the plugin's `ktfmt` configuration — " +
                            "cannot verify alignment."
                    )
            }
        doLast {
            val bundledVersion = pluginKtfmtVersion.get()
            check(bundledVersion == expectedKtfmtCli) {
                "ktfmt version drift: the ncorti plugin bundles com.facebook:ktfmt:" +
                    "$bundledVersion but gradle/libs.versions.toml pins ktfmt-cli=" +
                    "$expectedKtfmtCli. Upgrade both in lockstep so the hook and the Gradle " +
                    "plugin format identically."
            }
            logger.lifecycle(
                "ktfmt alignment OK: plugin bundles $bundledVersion == ktfmt-cli $expectedKtfmtCli"
            )
        }
    }

// Make `./gradlew check` a real format + static-analysis gate for this module. The ncorti plugin
// only auto-wires `ktfmtCheckScripts` into `check` on AGP 9 (its broken Android source-set path —
// see above), so the aggregate `ktfmtCheck` that now covers src/main/java is not reached by `check`
// unless wired explicitly. Detekt's plugin already wires `detekt` into `check`; it is named here
// too so the gate's composition is self-documenting and survives a change in that default.
// `verifyKtfmtAlignment` joins the gate so toolchain drift fails the build alongside formatting.
tasks.named("check") {
    dependsOn(tasks.named("ktfmtCheck"), tasks.named("detekt"), verifyKtfmtAlignment)
}

// Detekt — static analysis for Kotlin code smells. Runs Detekt's bundled defaults plus the narrow
// Compose-aware overrides in config/detekt/detekt.yml (buildUponDefaultConfig layers them on top).
// No baseline file and no custom complexity thresholds — findings are fixed by refactoring, never
// suppressed. Formatting is owned by ktfmt, so the `formatting` ruleset stays off. This module
// keeps its Kotlin sources under src/main/java and its instrumented tests under
// src/androidTest/java, so point `source` at both explicitly.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
    source.setFrom(files("src/main/java", "src/androidTest/java"))
}

// Compose Stability Analyzer — deliberately inert. Its on-demand `stabilityDump` task (variant form
// `debugStabilityDump`) writes a human-readable compose-compiler stability report; until composable
// product UI lands there is nothing meaningful to regression-gate on. The report goes under the
// build directory (gitignored) rather than a committed baseline, so the scaffold ships no stale
// snapshot. When real composables arrive, adopt the validation gate by pointing outputDir at a
// committed `stability/` dir, flipping failOnStabilityChange to true, and re-attaching the
// stabilityCheck tasks to `check` (see the detach below).
composeStabilityAnalyzer {
    stabilityValidation {
        enabled.set(true)
        outputDir.set(layout.buildDirectory.dir("compose-stability"))
        // Belt-and-suspenders for inertness: never fail a build on stability drift, and tolerate
        // the
        // missing baseline, so an ad-hoc `stabilityDump`/`stabilityCheck` can't break on the
        // empty/evolving composable set even though the check tasks are detached from `check`
        // below.
        failOnStabilityChange.set(false)
        allowMissingBaseline.set(true)
    }
}

// The analyzer auto-wires its per-variant `stabilityCheck` tasks (debug/release) into the `check`
// lifecycle. Detach them so the analyzer stays inert until M-2 — the report is produced on demand
// via
// `stabilityDump`, and there is no committed baseline to validate against yet. Detaching also
// avoids
// a Gradle 9 strict-validation failure: the compiler plugin declares `build/stability` as an output
// of every Kotlin compile task (unit-test compiles included), but each stabilityCheck task reads
// that directory while depending only on its main-variant compile, which Gradle rejects as an
// undeclared implicit dependency. Done in afterEvaluate so the AGP variant callbacks that add these
// dependencies have already run. Re-attach when composable UI and a committed baseline land in M-2.
afterEvaluate {
    tasks.named("check").configure {
        setDependsOn(
            dependsOn.filterNot { it is TaskProvider<*> && it.name.endsWith("StabilityCheck") }
        )
    }
}

// Release signing credentials. Read from a gitignored `keystore.properties` at the repo root, or
// from the matching environment variables when that file is absent (CI, a fresh clone). The
// keystore itself lives outside the repo — `storeFile` is an absolute path into it — so no secret
// and no key material is ever tracked by git.
//
// Resolution is deliberately all-or-nothing: unless every one of the four values is present the
// release build stays unsigned, exactly as it was before signing existed. That is what keeps
// CI green — the `verify-android-common` job runs `:androidApp:assemble`, which builds the release
// variant on a runner that has no keystore, and a half-configured signingConfig would fail it at
// configuration time rather than skip.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use(::load)
        }
    }

fun resolveSigningCredential(propertyKey: String, environmentKey: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(environmentKey)

val releaseStoreFile = resolveSigningCredential("storeFile", "ASTRO_KEYSTORE_FILE")
val releaseStorePassword = resolveSigningCredential("storePassword", "ASTRO_KEYSTORE_PASSWORD")
val releaseKeyAlias = resolveSigningCredential("keyAlias", "ASTRO_KEY_ALIAS")
val releaseKeyPassword = resolveSigningCredential("keyPassword", "ASTRO_KEY_PASSWORD")
val hasReleaseSigningCredentials =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).none {
        it.isNullOrBlank()
    }

android {
    compileSdk = 37

    defaultConfig {
        applicationId = "io.jitrapon.astro"
        minSdk = 23
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.2"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningCredentials) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            // Declared explicitly rather than inherited. AGP already applies
            // `proguard-android-optimize.txt` when a minified build type names no `proguardFiles`,
            // so naming it here adds no rule that was missing — it pins which default applies
            // (the plain `proguard-android.txt` turns R8's optimization passes off wholesale) and
            // gives the app's own rules a home. See proguard-rules.pro's header.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null when no credentials resolved, which leaves the variant unsigned rather than
            // failing the build — see the all-or-nothing note above `keystorePropertiesFile`.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    // Instrumented tests run against the *release* variant, not AGP's default `debug`. The shrinker
    // is what they exist to cover: R8 strips a reflectively reached type only when minification is
    // on, so a debug run installs an unshrunk APK that cannot reproduce the failure however well it
    // is written. Flipping this makes every `*AndroidTest` task build, sign and install the
    // minified release APK — which is also why `verifyReleaseSigningCredentials` below attaches to
    // them, and why anything the tests need must come from a configuration the release variant
    // resolves (`androidTestImplementation`), never `debugImplementation`.
    testBuildType = "release"

    testOptions {
        managedDevices {
            localDevices {
                // A Gradle-provisioned emulator, so a release-variant instrumented run needs no
                // attached device and no hand-managed AVD — CI invokes one task and AGP does the
                // rest. The DSL name is load-bearing: AGP derives the run task's name from it
                // (`aospAtd34ReleaseAndroidTest`), and the CI job names that task.
                //
                // `aosp-atd` is the Automated Test Device image — headless, no Play Services, no
                // preinstalled apps — the cheapest image that still boots a real framework. AOSP
                // rather than `google-atd` because nothing here touches Play Services. API 34
                // publishes both `x86_64` and `arm64-v8a`, so the same device provisions on a
                // Linux CI runner and an Apple Silicon dev machine without pinning an ABI.
                //
                // `apiLevel` rather than AGP 9's `sdkVersion`: the latter is still incubating and
                // carries the same value.
                create("aospAtd34") {
                    device = "Pixel 2"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    namespace = "io.jitrapon.astro"

    fun Packaging.() {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Instrumented tests install the app-under-test APK on a device, and the platform refuses an
// unsigned one. Signing stays all-or-nothing (see the note above `keystorePropertiesFile`), so with
// the four credentials absent the release variant is packaged unsigned and a run against it dies at
// install time with INSTALL_PARSE_FAILED_NO_CERTIFICATES — an error that names neither which
// credential is missing nor where to put it. This task turns that into a failure that says both.
//
// It is deliberately NOT wired into `check`: it fails on every machine without release credentials,
// which includes the CI runner that only assembles, and `check` must stay green there. Its one
// consumer is the instrumented-test wiring below. Run it directly to confirm credentials resolve.
val verifyReleaseSigningCredentials =
    tasks.register("verifyReleaseSigningCredentials") {
        group = "verification"
        description = "Fail naming the absent ASTRO_KEYSTORE_* credentials, not at install time."
        // Resolved at configuration time and captured as a plain list so the task body stays
        // configuration-cache safe.
        val missingCredentials =
            listOf(
                    "ASTRO_KEYSTORE_FILE" to releaseStoreFile,
                    "ASTRO_KEYSTORE_PASSWORD" to releaseStorePassword,
                    "ASTRO_KEY_ALIAS" to releaseKeyAlias,
                    "ASTRO_KEY_PASSWORD" to releaseKeyPassword,
                )
                .filter { (_, value) -> value.isNullOrBlank() }
                .map { (name, _) -> name }
        doLast {
            check(missingCredentials.isEmpty()) {
                """
                |Release signing credentials are missing, so the release APK an instrumented test
                |run installs would be unsigned and the install would fail with
                |INSTALL_PARSE_FAILED_NO_CERTIFICATES.
                |
                |Missing: ${missingCredentials.joinToString()}
                |
                |Supply all four, as environment variables or as keys in a gitignored
                |keystore.properties at the repo root:
                |
                |  ASTRO_KEYSTORE_FILE      (storeFile)      absolute path to the keystore
                |  ASTRO_KEYSTORE_PASSWORD  (storePassword)
                |  ASTRO_KEY_ALIAS          (keyAlias)
                |  ASTRO_KEY_PASSWORD       (keyPassword)
                """
                    .trimMargin()
            }
            logger.lifecycle("Release signing credentials OK: all four resolved.")
        }
    }

// AGP creates one instrumented-test run task per variant and per Gradle Managed Device
// (`connectedReleaseAndroidTest`, `<device><Variant>AndroidTest`, …), so there is no single task
// name to hook. `AndroidTestTask` is the interface every one of them implements. Matching on that
// type rather than on a name pattern is what keeps the guard honest: an AGP release that moves or
// removes the interface fails this build script to compile, where a name pattern would quietly stop
// matching and leave the runs unguarded — the one failure mode a guard must not have.
//
// The guard attaches only when the build type under test resolves no signing config, which is
// exactly when the install cannot succeed. It keys on whatever `testBuildType` names rather than on
// the literal "release", so a tested variant that is signed — `debug` under AGP's default, or
// `release` itself once the four credentials resolve — attaches nothing and runs untouched.
if (android.buildTypes.getByName(android.testBuildType).signingConfig == null) {
    tasks.configureEach {
        if (this is AndroidTestTask) {
            dependsOn(verifyReleaseSigningCredentials)
        }
    }
}

dependencies {
    // The `structured-coroutines` ruleset on Detekt's rule classpath (40 syntactic coroutine
    // rules; tiers live under `structured-coroutines:` in config/detekt/detekt.yml). Applied here
    // too so the app's Compose ViewModels / coroutine call sites get the same gate as :shared.
    detektPlugins(libs.structured.coroutines.detekt.rules)

    implementation(project(":shared"))
    // The app resolves the shared data layer from the Koin graph `:shared` starts; the version is
    // the catalog's, so the app and `:shared` can never run two Koin runtimes.
    implementation(libs.koin.core)

    // Navigation 3 — catalog-declared; see gradle/libs.versions.toml.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // The shell's tab icons — catalog-declared; see gradle/libs.versions.toml.
    implementation(libs.androidx.compose.material.icons.core)

    // Jetpack Compose — catalog-declared; see gradle/libs.versions.toml.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    // `ui-tooling-preview` carries only the `@Preview` annotation AppShellPreviews.kt needs at
    // compile time, so it stays on `implementation`. Its sibling `ui-tooling` — the runtime
    // inspector — is debug-only below.
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Test-only artifacts stay off `implementation`: there, either would ship in the release APK.
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // `ui-tooling` is the runtime preview inspector, and it is debug-only for a reason beyond the
    // obvious one. Its AAR manifest declares an `androidx.compose.ui.tooling.PreviewActivity`;
    // manifest merger folds that activity into the app's merged manifest, and AAPT2 generates a
    // keep rule for every manifest-declared component, since the framework instantiates them
    // reflectively by name. On `implementation` that ships a dev-only Activity in the release APK
    // and pins it against the shrinker. Debug-only removes both.
    debugImplementation(libs.androidx.compose.ui.tooling)
}
