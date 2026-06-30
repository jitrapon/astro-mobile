plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // ktfmt + Detekt versions come from the root version catalog (gradle/libs.versions.toml) so the
    // Gradle plugin, the pre-commit hook's CLI jars, and verifyKtfmtAlignment share one source.
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

// ktfmt — Kotlin source formatter. Registers `ktfmtCheck` (verify) and `ktfmtFormat` (rewrite)
// lifecycle tasks plus per-source-set variants. `kotlinLangStyle()` selects ktfmt's
// Kotlin-official-style-guide preset, matching `kotlin.code.style=official` in gradle.properties —
// not the default Meta style or `googleStyle()`.
ktfmt { kotlinLangStyle() }

// Verify the standalone `ktfmt-cli` jar the pre-commit hook invokes matches the formatter the
// ncorti Gradle plugin bundles, so local Gradle, the hook, and CI can never format differently. The
// plugin registers a `ktfmt` configuration whose resolved `com.facebook:ktfmt` artifact is the real
// formatter; compare its version against `ktfmt-cli` from the version catalog. Wired into this
// module's `check` below so the gate fails fast on drift.
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

tasks.named("check") { dependsOn(verifyKtfmtAlignment) }

// Detekt — static analysis for Kotlin code smells. Runs Detekt's bundled defaults plus the narrow
// Compose-aware overrides in config/detekt/detekt.yml (buildUponDefaultConfig layers them on top).
// No baseline file and no custom complexity thresholds — findings are fixed by refactoring, never
// suppressed. Formatting is owned by ktfmt, so the `formatting` ruleset stays off. `source` is set
// explicitly: this module's KMP source sets live under src/<sourceSet>/kotlin, which Detekt's
// default of src/main/kotlin would otherwise miss entirely.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
    source.setFrom(files("src"))
}

kotlin {
    android {
        compileSdk { version = release(36) }
        namespace = "io.jitrapon.astro.shared"
        // Opt into Android/JVM host (unit) tests. The com.android.kotlin.multiplatform.library
        // plugin creates no host-test compilation by default, so without this commonTest would run
        // only on iOS — `withHostTest` adds `testAndroidHostTest` so the same shared tests run on
        // the JVM host too.
        withHostTest {}
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "shared" }
    }

    sourceSets {
        // The `iosMain` / `iosTest` intermediate source sets — and their dependsOn edges across
        // iosX64/iosArm64/iosSimulatorArm64 — are created automatically by Kotlin's default
        // hierarchy template once the iOS targets above are declared. Declaring them by hand would
        // disable the template (and emit a "Default Kotlin Hierarchy Template was not applied"
        // warning). Configure a source set explicitly only when it needs its own deps or sources.
        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
        // Android host (JVM) unit tests created by `withHostTest {}` live in `androidHostTest`.
        // AndroidGreetingTest asserts on the androidMain `Greeting` actual via JUnit directly, so
        // the host-test source set needs JUnit on its classpath (commonTest's kotlin("test") does
        // not supply org.junit on its own).
        val androidHostTest by getting { dependencies { implementation("junit:junit:4.13.2") } }
    }
}

// Detect Apple Silicon *hardware*. `System.getProperty("os.arch")` is unreliable here: a Rosetta-
// translated Gradle daemon reports `x86_64` even on an arm64 Mac, so it would misclassify the host.
// `sysctl -n hw.optional.arm64` queries the hardware (not the process), returning "1" on every
// arm64 Mac regardless of translation; on Intel Macs / non-macOS it errors or returns 0, which we
// treat as "not Apple Silicon".
val isAppleSiliconHost =
    System.getProperty("os.name").startsWith("Mac") &&
        providers
            .exec {
                commandLine("sysctl", "-n", "hw.optional.arm64")
                isIgnoreExitValue = true
            }
            .standardOutput
            .asText
            .map { it.trim() == "1" }
            .getOrElse(false)

// `iosX64Test` runs an x86_64 iOS-simulator test binary, which cannot be exec'd on Apple Silicon
// hardware — the launcher aborts with "Bad CPU type in executable". Since `check` aggregates every
// target's test task, leaving it enabled would fail the gate on every arm64 dev machine and arm64
// CI runner. Disable the task on Apple Silicon; `iosSimulatorArm64Test` covers the simulator-test
// surface there, and on a genuine Intel Mac iosX64Test stays enabled and runs. This is decided at
// configuration time — a `Task.onlyIf` predicate does not work because the Kotlin Native test task
// resets onlyIf during execution.
if (isAppleSiliconHost) {
    tasks.named("iosX64Test") { enabled = false }
}
