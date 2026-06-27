plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.ncorti.ktfmt.gradle") version "0.26.0"
}

// ktfmt — Kotlin source formatter. Registers `ktfmtCheck` (verify) and `ktfmtFormat` (rewrite)
// lifecycle tasks plus per-source-set variants. `kotlinLangStyle()` selects ktfmt's
// Kotlin-official-style-guide preset, matching `kotlin.code.style=official` in gradle.properties —
// not the default Meta style or `googleStyle()`.
ktfmt { kotlinLangStyle() }

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
