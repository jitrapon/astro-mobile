# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Astro is a Kotlin Multiplatform Mobile (KMP) smart planner app targeting Android and iOS. It uses a shared Kotlin module for business logic with platform-specific UI layers (Jetpack Compose for Android, SwiftUI for iOS).

## Build Commands

```bash
./gradlew build                        # Build all modules
./gradlew :androidApp:assembleDebug    # Build Android debug APK
./gradlew :shared:build                # Build shared module only

./gradlew test                         # Run all tests
./gradlew :shared:testDebugUnitTest    # Run shared module Android unit tests
./gradlew :shared:iosSimulatorArm64Test # Run shared module iOS tests
```

iOS app is built via Xcode from `iosApp/iosApp.xcodeproj`.

## Architecture

### Module Structure

- **`:shared`** — KMP module with business logic, data layer, and platform abstractions. Produces a `shared` framework for iOS.
- **`:androidApp`** — Android app using Jetpack Compose. Depends on `:shared`.
- **`iosApp/`** — SwiftUI iOS app (Xcode project). Consumes the `shared` framework.

### Shared Module Source Sets

- `commonMain` — Platform-agnostic code (data layer, models, validation)
- `androidMain` / `iosMain` — Platform-specific implementations via `expect`/`actual`
- `commonTest` / `androidTest` / `iosTest` — Corresponding test source sets

### Key Patterns

- **expect/actual** for platform abstractions (`Platform.kt`, `Utils.kt`)
- **Sealed `Result<T>`** class (`Success<T>`, `Error`) for type-safe error handling in `shared/src/commonMain/kotlin/io/jitrapon/astro/data/Result.kt`
- **Repository pattern** — `LoginRepository` wraps `LoginDataSource`, manages cached state
- Package structure: `io.jitrapon.astro` base, organized by layer (`data/`, `ui/`)

## Tech Stack & Versions

- Kotlin: 2.3.10, Gradle: 9.1.0, AGP: 9.0.1
- Android: compileSdk 36, minSdk 23, targetSdk 36, Java 17
- Jetpack Compose (Material 2): 1.10.4
- iOS targets: iosX64, iosArm64, iosSimulatorArm64
- Kotlin code style: official (set in `gradle.properties`)
