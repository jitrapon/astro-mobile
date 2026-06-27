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
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "shared" }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
        val androidMain by getting
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
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
