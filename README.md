# astro-mobile

The mobile client for Astro — a Kotlin Multiplatform Mobile (KMP) smart planner app for Android and iOS. Business logic lives once in a shared Kotlin module; the UI is platform-native (Jetpack Compose on Android, SwiftUI on iOS). It is a calendar / events / reminders / tasks / budgeting app with AI assistance.

> **Status:** M-1 scaffold. The KMP module skeleton, the lint/format/build gate (ktfmt + Detekt for Kotlin, swift-format + SwiftLint for Swift), the git hooks, CI, and the `.claude` development harness are in place. Product screens and feature work land on later (M-2+) branches scaffolded from this foundation.

## Stack

Kotlin 2.3.10 · Gradle 9.x · Android Gradle Plugin 9.x · Jetpack Compose (Android UI) · SwiftUI (iOS UI). Android targets compileSdk 36 / minSdk 23 / targetSdk 36 on Java 17; iOS targets `iosX64`, `iosArm64`, `iosSimulatorArm64`. Lint/format: ktfmt + Detekt (Kotlin), swift-format + SwiftLint (Swift).

## Getting started

Requires JDK 17 and (for the iOS app) Xcode. Build everything and run the gate:

```bash
./gradlew build      # build all modules
./gradlew check      # full CI gate (compile + tests + Detekt + ktfmt, and Swift lint where the toolchain is present)
```

Run the Android app on a connected device or emulator:

```bash
./gradlew :androidApp:installDebug
```

The iOS app is built and run from Xcode (`iosApp/iosApp.xcodeproj`); it consumes the `shared` framework produced by the `:shared` module.

Install the local pre-commit / pre-push git hooks once after cloning (see [`CONTRIBUTING.md`](CONTRIBUTING.md) for what they enforce):

```bash
./gradlew installGitHooks
```

## Commands

| Task                          | Command                                    |
| ----------------------------- | ------------------------------------------ |
| Build everything              | `./gradlew build`                          |
| Android debug APK             | `./gradlew :androidApp:assembleDebug`      |
| Install Android app           | `./gradlew :androidApp:installDebug`       |
| Build shared module only      | `./gradlew :shared:build`                  |
| Unit tests (JVM/Android)      | `./gradlew test`                           |
| iOS shared tests              | `./gradlew :shared:iosSimulatorArm64Test`  |
| Format (Kotlin)               | `./gradlew ktfmtFormat`                    |
| Lint (Kotlin)                 | `./gradlew detekt`                         |
| Format (Swift)                | `./gradlew swiftFormatApply`               |
| Lint (Swift, format)          | `./gradlew swiftFormatCheck`               |
| Lint (Swift, static analysis) | `./gradlew swiftLintCheck`                 |
| Unused iOS code (on-demand)   | `./gradlew peripheryScan`                  |
| Full CI gate                  | `./gradlew check`                          |

`./gradlew check` is the authoritative gate — it aggregates compile, unit tests, Detekt, and ktfmt verification (plus swift-format and SwiftLint wherever the Swift toolchain resolves on PATH). Run it before pushing.

## Architecture

Three modules, with all platform-agnostic logic shared and the UI kept platform-native:

```
shared/      # :shared — KMP business logic, data layer, platform abstractions; produces the iOS `shared` framework
  src/commonMain/   # platform-agnostic code (models, data layer, validation)
  src/androidMain/  # Android expect/actual implementations
  src/iosMain/      # iOS expect/actual implementations
androidApp/  # :androidApp — Android app (Jetpack Compose); depends on :shared
iosApp/      # SwiftUI iOS app (Xcode project); consumes the `shared` framework
```

Key patterns: platform abstractions use `expect`/`actual` (never runtime `if (isAndroid)` branching); error handling uses the sealed `Result<T>` (`Success<T>` / `Error`); data access follows the repository-over-data-source pattern; the base package is `io.jitrapon.astro`, organized by layer. **UI stays out of `:shared`** — Compose lives in `androidApp`, SwiftUI in `iosApp`, and the shared module exposes only platform-agnostic models and logic.

For the full architecture, conventions, lint/build details, and the spec-driven development workflow, see [`.claude/CLAUDE.md`](.claude/CLAUDE.md). Before contributing, read [`CONTRIBUTING.md`](CONTRIBUTING.md).
