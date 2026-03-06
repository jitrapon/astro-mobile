import com.android.build.api.dsl.Packaging

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "io.jitrapon.astro"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    namespace = "io.jitrapon.astro"

    fun Packaging.() {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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