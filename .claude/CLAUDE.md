# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository. It is the canonical project doc; the root `CLAUDE.md` is a pointer to this file.

## Project Overview

Astro is a Kotlin Multiplatform Mobile (KMP) smart planner app targeting Android and iOS — a calendar/events/reminders/tasks/budgeting app with AI assistance. Business logic lives in a shared Kotlin module; the UI is platform-specific (Jetpack Compose on Android, SwiftUI on iOS).

> **Status:** repository scaffolding in progress. The KMP module structure, build, and `.claude` development harness are being established; CI gates, the Kotlin lint/format toolchain (ktfmt + Detekt), and the iOS lint toolchain are planned but not all wired up yet. Commands below that reference unconfigured tooling note that explicitly.

## Build & Run Commands

```bash
./gradlew build                          # Build all modules
./gradlew :androidApp:assembleDebug      # Build Android debug APK
./gradlew :androidApp:installDebug       # Install the Android app on a connected device/emulator
./gradlew :shared:build                  # Build shared module only

./gradlew test                           # Run JVM/Android unit tests across modules
./gradlew :shared:testDebugUnitTest      # Shared module Android unit tests
./gradlew :shared:iosSimulatorArm64Test  # Shared module iOS tests (simulator)

./gradlew check                          # Aggregate gate: compile + tests + Detekt + ktfmt verification
```

The iOS app is built and run via Xcode from `iosApp/iosApp.xcodeproj` (it consumes the `shared` framework produced by the shared module).

| Task                         | Command                                      |
| ---------------------------- | -------------------------------------------- |
| Build everything             | `./gradlew build`                            |
| Android debug APK            | `./gradlew :androidApp:assembleDebug`        |
| Unit tests (JVM/Android)     | `./gradlew test`                             |
| iOS shared tests             | `./gradlew :shared:iosSimulatorArm64Test`    |
| Format (Kotlin)              | `./gradlew ktfmtFormat`                      |
| Lint (Kotlin)                | `./gradlew detekt`                           |
| Format (Swift)               | `./gradlew swiftFormatApply`                 |
| Lint (Swift, in `check`)     | `./gradlew swiftFormatCheck`                 |
| Full CI gate                 | `./gradlew check`                            |

## Architecture

### Module structure

- **`:shared`** — KMP module with business logic, data layer, and platform abstractions. Produces a `shared` framework for iOS.
- **`:androidApp`** — Android app using Jetpack Compose. Depends on `:shared`.
- **`iosApp/`** — SwiftUI iOS app (Xcode project). Consumes the `shared` framework.

### Shared module source sets

- `commonMain` — platform-agnostic code (data layer, models, validation).
- `androidMain` / `iosMain` — platform-specific implementations via `expect`/`actual`.
- `commonTest` / `androidTest` / `iosTest` — corresponding test source sets.

### Key patterns (architectural seams)

- **expect/actual** for platform abstractions (e.g. `Platform.kt`, `Utils.kt`) — never `if (isAndroid)` branching in shared code.
- **Sealed `Result<T>`** (`Success<T>`, `Error`) for type-safe error handling — `shared/src/commonMain/kotlin/io/jitrapon/astro/data/Result.kt`.
- **Repository pattern** — a repository (e.g. `LoginRepository`) wraps a data source (`LoginDataSource`) and manages cached state.
- **Package layout** — base package `io.jitrapon.astro`, organized by layer (`data/`, `ui/`).
- **UI stays out of `:shared`** — Compose code lives in `androidApp`, SwiftUI code lives in `iosApp`. The shared module exposes platform-agnostic models and logic only.
- **Calendar layout-engine seam** (reserved, M-2+) — `io.jitrapon.astro.calendar.layout` in `commonMain` is the reserved home of the shared, UI-agnostic calendar layout engine (column-pack + multi-day segmentation). Per astro-plans `ADR-calendar-layout-engine-sharing-strategy`, it is a single source compiled to JS for web and native on mobile; its public seam takes/returns plain JSON-like values only (no `kotlinx.datetime`/`Flow`/sealed objects/`Result<T>` crossing it, so the same source compiles to JS). The golden-vector corpus lives at `shared/src/commonTest/resources/calendar-layout-golden/`. See the README in each directory.

## Conventions

Naming, comments, and file conventions the `refactor` and `spec-development` skills treat as the canonical rule set.

### Naming — communicate role + intent (the "cold reader" test)

A teammate opening the file on `main` with no branch/SPEC context should guess what a symbol does from its name alone.

- **Functions/methods** lead with an action verb naming *what* they do, not *how* they mutate a parameter. Surface multi-step work in the name (`dedupAndPersistEvents`, not `appendEvents`).
- **Types** name the specific artifact, not a generic noun — prefer `SyncOutcome` / `EventClassification` over `Result2` / `State` / `Mode` (the project already reserves `Result<T>` for error handling).
- **Class/module scope**: the name must cover the broadest case it handles; if it does two things, the name shouldn't reference only one.
- **Constants / enum members**: `SCREAMING_SNAKE_CASE` for Kotlin enum-like constants; idiomatic Swift casing on the iOS side. No mixed conventions within a language.
- Kotlin code style is `official` (set in `gradle.properties`); Swift follows standard Swift API Design Guidelines.

### Comments

- Self-contained: name the invariant, the failure mode, or the contract — never a `SPEC §`, `round N`, or `adversarial review` reference (those rot the moment the branch merges).
- Cross-references between durable production identifiers (function/class/file names that survive on `main`) are fine.

### File naming

- Kotlin files are `PascalCase.kt` matching the primary declaration; platform `actual`s conventionally suffix or live in the platform source set (`androidMain`/`iosMain`). Rename files via `git mv` to preserve history.

## Linting

- **Kotlin formatting:** ktfmt (`./gradlew ktfmtFormat` to apply, verified by `./gradlew check`), using ktfmt's `kotlinLangStyle()` preset to match `kotlin.code.style=official`. `./gradlew check` runs `ktfmtCheck` across both modules (including the Android app's `src/main/java`) and fails on unformatted Kotlin.
- **Kotlin static analysis:** Detekt (`./gradlew detekt`), wired into `./gradlew check`. No custom complexity thresholds are configured; honor Detekt's defaults (the only override is the narrow Compose-rule exemption in `config/detekt/detekt.yml`). Do **not** add a Detekt baseline file or `@Suppress` to silence findings — refactor instead. This convention will be enforced mechanically once the scaffolding lands: a `checkNoDetektBaseline` task (fails if any `detekt-baseline.xml` exists) plus Detekt's `ForbiddenSuppress` rule.
- **iOS:** swift-format for the SwiftUI app — enforced at parity with the Kotlin ktfmt gate. Config lives at `iosApp/.swift-format` (Apple's toolchain-bundled `swift format`, 4-space indent, 100-col). Run `./gradlew swiftFormatCheck` to lint (strict — fails on any finding) or `./gradlew swiftFormatApply` to format in place. `swiftFormatCheck` is wired into `./gradlew check`, and the pre-commit hook lints staged `.swift` strictly — the same enforcement points as ktfmt. Both key on whether `swift` resolves on PATH: they enforce wherever the Swift toolchain is installed (macOS dev machines, and CI once it provides the toolchain) and self-skip with a message where it's absent (e.g. a contributor on Linux), so the gate never hard-fails on a missing binary.
- **Git hooks:** install with `./gradlew installGitHooks` (sets `core.hooksPath=.githooks`). The `pre-commit` hook runs ktfmt + Detekt on staged Kotlin via the fast CLI path (standalone fat jars resolved by `resolveLintTools`, not the Gradle daemon) — it aborts on partially-staged Kotlin, fails with a "rerun `resolveLintTools`" message when the resolved jar versions drift from `gradle/libs.versions.toml`, and runs `checkNoDetektBaseline` when a `detekt-baseline.xml` is staged. It also runs `swift format lint --strict` on staged `.swift` files (same partial-staging abort), warn-and-skipping when the Swift toolchain isn't on PATH. The `pre-push` hook (security scanners on pushed commits) is *planned*. Both honor `--no-verify`. The CI gate (`./gradlew check`) is the authoritative enforcement point whether or not the hooks are installed — they are a fast local pre-flight, not a replacement.

## Tech stack & versions

- Kotlin 2.3.10, Gradle 9.x, Android Gradle Plugin 9.x.
- Android: compileSdk 36, minSdk 23, targetSdk 36, Java 17.
- Jetpack Compose for Android UI; SwiftUI for iOS UI.
- iOS targets: `iosX64`, `iosArm64`, `iosSimulatorArm64`.
- Gradle version catalog (`gradle/libs.versions.toml`) — holds the lint toolchain versions (the ncorti ktfmt plugin / `ktfmt-cli` / Detekt) plus the `ktfmt`/`detekt` plugin aliases, so the Gradle plugin, the pre-commit hook's CLI jars, and `verifyKtfmtAlignment` share one source of truth. Deliberately partial: other dependency versions remain declared inline in the `build.gradle.kts` files for now.

## Documented config files

Files with load-bearing detail a reviewer/agent should re-read when they change: `build.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, and (once added) `gradle/libs.versions.toml`, the Detekt config, the `.githooks/` scripts (`pre-commit`, `pre-push`), and `.github/workflows/ci.yml`.

## Development workflow

This repo uses a spec-driven, skill-based workflow (see `.claude/skills/`). Per branch:

1. `scaffold-issue` (or hand-authored) populates `.claude/SPEC.md` sections 1–3.
2. `spec-development` (`review the spec`) writes the implementation/testing checklists (§§4–5) and drives them one item per `resume the plan`.
3. `codex-review` / `address-review` run the adversarial review loop.
4. `finish-branch` resets agent working files to the `main` skeleton, pushes, and opens the PR.

`.claude/SPEC.md`, `.claude/REVIEW_PLAN.md`, and `.claude/REVIEW_ADVERSARIAL.md` are per-branch working files that reset to skeletons on `main`.

## References

- **Adding KMP dependencies** — [multiplatform dependencies](https://kotlinlang.org/docs/multiplatform/multiplatform-dependencies.html) and [upgrading a multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-upgrade-app.html). Canonical how-to for adding/upgrading `commonMain` and platform-specific dependencies (e.g. Ktor Client, kotlinx.serialization) — follow it rather than guessing source-set wiring.
- **Calendar layout-engine sharing** — astro-plans `ADR-calendar-layout-engine-sharing-strategy` (decision D-15) and the `W-S1` spike. Background for the reserved `commonMain` layout seam.
