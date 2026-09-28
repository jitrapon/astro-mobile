// Black-box instrumented tests against `:androidApp`'s `releaseLoopback` build — the shipping
// shrink
// with no keep rule added on a test's behalf.
//
// A separate module, not `:androidApp`'s androidTest source set, because that source set is tied to
// `minifiedTest`, whose keep rules are generated from what its tests reference and so pin every
// contract model they name. Here nothing references an app class: the test APK is
// SELF-INSTRUMENTING — it instruments its own package and runs in its own process — so it cannot
// reach the app's classes at all, and nothing about it changes how R8 shrinks the app. It drives
// the
// app as a user would, through UiAutomator, and serves the vendored contract fixture from an
// on-device backend the `releaseLoopback` build is pointed at.
plugins {
    id("com.android.test")
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn(tasks.named("ktfmtCheck"), tasks.named("detekt")) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
    source.setFrom(files("src/main/java"))
}

val releaseLoopbackBackendPort =
    providers.gradleProperty("astro.releaseLoopback.backendPort").get().toInt()

android {
    namespace = "io.jitrapon.astro.releasetest"
    compileSdk = 37
    targetProjectPath = ":androidApp"

    defaultConfig {
        minSdk = 30
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Where the test serves the fixture — the port `releaseLoopback`'s base URL names.
        testInstrumentationRunnerArguments["backendPort"] = "$releaseLoopbackBackendPort"
    }

    buildTypes {
        // A test module's variant runs against the app variant of the same build-type name, so
        // this is what selects `releaseLoopback`. The test APK signs with the debug key: running in
        // its own process, it need not share the app's signature, and the release key belongs to
        // the app alone.
        create("releaseLoopback") { signingConfig = signingConfigs.getByName("debug") }
    }

    // The vendored fixture, read from where `:shared`'s contract tests and `:androidApp`'s
    // decoding test read it — the one copy verifyVendoredContractParity holds to the contract.
    sourceSets
        .getByName("main")
        .assets
        .srcDir(rootProject.file("shared/src/commonTest/resources/contract"))

    testOptions {
        managedDevices {
            localDevices {
                // Mirrors `:androidApp`'s device, so both release runs share one system image.
                // The DSL name is load-bearing: the CI job names the task AGP derives from it.
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

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    // Only the release run exists here. The `debug` build type every module carries would test the
    // debug app, which `:androidApp`'s own suite already covers unshrunk.
    beforeVariants { it.enable = it.buildType == "releaseLoopback" }
}

dependencies {
    detektPlugins(libs.structured.coroutines.detekt.rules)

    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.mockwebserver3)
}
