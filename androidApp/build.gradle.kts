import com.android.build.api.dsl.Packaging
import com.ncorti.ktfmt.gradle.FormattingOptionsBean
import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // ktfmt + Detekt versions come from the root version catalog (gradle/libs.versions.toml) so the
    // Gradle plugin, the pre-commit hook's CLI jars, and verifyKtfmtAlignment share one source.
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
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
// the per-source check/format tasks explicitly over src/main/java using the plugin's public task
// API, deriving the formatting options from the configured `ktfmt { kotlinLangStyle() }` extension
// so the app module formats identically to `:shared`. (The KMP `:shared` module is unaffected — the
// plugin's multiplatform path does not use the removed AGP API.)
run {
    val ktfmtExtension = the<KtfmtExtension>()
    val ktfmtClasspathConfig = configurations.named("ktfmt")
    val androidKotlinSources = fileTree("src/main/java") { include("**/*.kt") }
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
// keeps its Kotlin sources under src/main/java, so point `source` there explicitly.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
    source.setFrom(files("src/main/java"))
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "io.jitrapon.astro"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes { getByName("release") { isMinifyEnabled = true } }
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

dependencies {
    implementation(project(":shared"))

    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material:material:1.10.4")
    implementation("androidx.compose.animation:animation:1.10.4")
    implementation("androidx.compose.ui:ui-tooling:1.10.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.10.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.4")
}
