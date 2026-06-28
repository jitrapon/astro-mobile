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
| Lint (Swift, format)         | `./gradlew swiftFormatCheck`                 |
| Lint (Swift, static analysis)| `./gradlew swiftLintCheck`                   |
| Unused iOS code (on-demand)  | `./gradlew peripheryScan`                    |
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
- **iOS:** a two-tool toolchain mirrors the Kotlin ktfmt + Detekt pair — **swift-format** for formatting and **SwiftLint** for static analysis — both enforced at parity with their Kotlin counterparts.
  - **swift-format** (formatting, the ktfmt analog). Config lives at `iosApp/.swift-format` (Apple's toolchain-bundled `swift format`, 4-space indent, 100-col). Run `./gradlew swiftFormatCheck` to lint (strict — fails on any finding) or `./gradlew swiftFormatApply` to format in place.
  - **SwiftLint** (static analysis, the Detekt analog). Config lives at `iosApp/.swiftlint.yml`. Run `./gradlew swiftLintCheck` (strict — warnings become errors) or directly `swiftlint lint --strict --config iosApp/.swiftlint.yml iosApp/iosApp`. The config disables only the formatting-only rules that overlap with swift-format (so swift-format stays the sole formatter and the two never fight); genuine findings are fixed in source, never blanket-disabled — the same posture as Detekt leaving formatting to ktfmt. SwiftLint is **not** toolchain-bundled: install via `brew install swiftlint`.
  - Both `swiftFormatCheck` and `swiftLintCheck` are wired into `./gradlew check`, and the pre-commit hook runs both on staged `.swift` strictly — the same enforcement points as ktfmt + Detekt. Each keys on whether its binary (`swift` / `swiftlint`) resolves on PATH: they enforce wherever the tool is installed (macOS dev machines, and CI once it provides the toolchain) and self-skip with a message where absent (e.g. a contributor on Linux, or a machine without SwiftLint), so the gate never hard-fails on a missing binary.
  - **Periphery** (unused-code / dead-declaration analysis, config `iosApp/.periphery.yml`) is the parity for Detekt's unused-member rules. It is **on-demand only** (`./gradlew peripheryScan`), deliberately **not** in `./gradlew check`: unlike the per-file Swift tools it is a whole-program analysis that runs a full `xcodebuild` to produce an index store (a scan takes minutes and needs macOS + Xcode + the `shared` framework built), so it can't self-skip cleanly on a toolchain-less host. It is the iOS analog of the on-demand `:androidApp:debugStabilityDump` Compose-stability task — run it manually before a refactor or release. Install via `brew install periphery`; the task self-skips with a message where `periphery` is absent.
- **Git hooks:** install with `./gradlew installGitHooks` (sets `core.hooksPath=.githooks`). The `pre-commit` hook runs ktfmt + Detekt on staged Kotlin via the fast CLI path (standalone fat jars resolved by `resolveLintTools`, not the Gradle daemon) — it aborts on partially-staged Kotlin, fails with a "rerun `resolveLintTools`" message when the resolved jar versions drift from `gradle/libs.versions.toml`, and runs `checkNoDetektBaseline` when a `detekt-baseline.xml` is staged. It also runs `swift format lint --strict` and `swiftlint --strict` on staged `.swift` files (one shared partial-staging abort), each warn-and-skipping independently when its binary (`swift` / `swiftlint`) isn't on PATH so the tool that is present still runs. The `pre-push` hook runs the security scanners over the commits the push adds (reachable from the pushed tips but not already on a remote): betterleaks (secret scan over the pushed git history, gated on the `.betterleaks.toml` version floor) and semgrep (the `.semgrep/astro-mobile.yml` ruleset over the changed `.kt`/`.kts` files) — each warn-and-skips independently when its CLI is absent or below floor so the other still runs. Both honor `--no-verify`. The CI gate (`./gradlew check`) is the authoritative enforcement point whether or not the hooks are installed — they are a fast local pre-flight, not a replacement.

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

### Agent skill routing & precedence

The repo carries skills from three families with **non-overlapping domains** — route by where the work lands, not by keyword overlap:

| Work domain | Skills | Where they live |
| --- | --- | --- |
| **Android app & UI** — Jetpack Compose UI, navigation, adaptive layouts, theming, edge-to-edge, Android test infra, and driving the build/run/emulator/screenshots/doc-search tooling | the official Google **`android/*`** skills | `android-cli` is vendored at `.claude/skills/android-cli/`; the rest are served **on-demand** by the Android CLI |
| **`:shared` / KMP architecture** — data layer, repositories, `expect`/`actual` platform bridges, module boundaries, KMP Gradle structure, refactor safety | the vendored **`kotlin-*`** skills | `.claude/skills/` (committed) |
| **Async / concurrency (any module)** | **`kotlin-coroutines-skill`** | `.claude/skills/` (committed) |

**Precedence rule:** for any Android-platform / `:androidApp` / Compose-UI / Android-tooling task, prefer the official `android/*` skill over a `kotlin-*` skill that merely mentions the same surface — the `android/*` skills are Google-authored, versioned, and kept current by the CLI. The `kotlin-*` skills own everything inside `:shared` and cross-module KMP architecture and do **not** cover Android UI. There is no genuine build-tooling clash: the official `agp-9-upgrade` skill explicitly excludes KMP projects, so KMP Gradle work stays with `kotlin-build-kmp-gradle-governance`.

**On-demand `android/*` skills** — not vendored (so they never drift from the CLI); fetch a fresh copy with `android skills add <name> --agent=claude-code --project .` when a task needs one:

| Task | Skill |
| --- | --- |
| Phone/tablet/foldable responsive Compose layouts | `adaptive` |
| Jetpack Navigation 3 (multi-backstack, list-detail, two-pane, deep links) | `navigation-3` |
| Edge-to-edge insets / system-bar overlap fixes | `edge-to-edge` |
| Jetpack Compose Styles API / theming migration | `styles` |
| Android test strategy & harness setup (`:androidApp`; **not** `:shared`) | `testing-setup` |

`android skills list` / `android skills find <keyword>` enumerate the full catalog. Always pass `--agent=claude-code` so the skill lands in `.claude/skills/` only — omitting it also writes a stray top-level `skills/` copy.

**Not applicable to this stack** (do not install): `jetpack-compose-m3` (Wear OS only — `androidx.wear.compose.*`), `agp-9-upgrade` (its own description excludes KMP), `camera1-to-camerax` (no camera/legacy), `migrate-xml-views-to-jetpack-compose` (this app is born-in-Compose), `display-glasses-with-jetpack-compose-glimmer` (XR), `engage-sdk-integration` (media content surfaces). Revisit only if the product scope changes.

## References

- **Adding KMP dependencies** — [multiplatform dependencies](https://kotlinlang.org/docs/multiplatform/multiplatform-dependencies.html) and [upgrading a multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-upgrade-app.html). Canonical how-to for adding/upgrading `commonMain` and platform-specific dependencies (e.g. Ktor Client, kotlinx.serialization) — follow it rather than guessing source-set wiring.
- **Calendar layout-engine sharing** — astro-plans `ADR-calendar-layout-engine-sharing-strategy` (decision D-15) and the `W-S1` spike. Background for the reserved `commonMain` layout seam.
