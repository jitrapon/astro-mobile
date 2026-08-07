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
./gradlew :shared:testAndroidHostTest    # Shared module Android/JVM host unit tests
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
| Full CI gate (local)         | `./gradlew check`                            |
| CI host-portable half        | `./gradlew verifyAndroidCommon`              |
| CI macOS-only half           | `./gradlew verifyIos`                        |
| CI partition drift guard     | `./gradlew verifyCheckPartition`             |

### Continuous integration — split runners, one local gate

`./gradlew check` stays **the** local gate: one command, runs everything. CI (`.github/workflows/ci.yml`) does *not* run `check` directly — it splits the same work across two runners so the expensive `macos-latest` runner (~10x the per-minute cost of Linux) only carries the genuinely Mac-bound surface:

- **`verify-android-common`** (`ubuntu-latest`) → `./gradlew verifyAndroidCommon` — Android build/unit tests, JVM/common host tests, and Kotlin Detekt + ktfmt. ktfmt/Detekt parse *every* source set, so this also lints `iosMain` Kotlin **source** (parsing needs no Mac; only iOS *compilation* does). Then `:androidApp:assemble` for APK packaging (release exercises R8).
- **`verify-ios`** (`macos-latest`) → `./gradlew verifyIos` — the shared module's iOS simulator tests plus the Swift gates (swift-format + SwiftLint), since Kotlin/Native cross-compiles iOS only on macOS. Then `:shared:linkReleaseFrameworkIosArm64` to link the shipping framework.

The two aggregates and the split are defined in the root `build.gradle.kts` from a single data list. **`verifyCheckPartition`** is the drift guard: it walks the action-bearing task closure of `check` versus the two aggregates and fails if they diverge, so local (`check`) and CI (the aggregates) can never silently drift apart. It runs inside every `check` and inside `verifyAndroidCommon`. When you wire a new verification task into `check`, this guard fails until you classify it into `verifyIos` or `verifyAndroidCommon`. The per-job `:androidApp:assemble` / `:shared:linkReleaseFrameworkIosArm64` steps are deliberately **outside** the guard — they assemble artifacts, which is coverage beyond `check`, not part of it.

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
- **Coroutine correctness:** two syntactic engines enforce structured concurrency without type resolution (Detekt 1.23.8 bundles Kotlin 2.0.21 and cannot resolve types against this repo's Kotlin 2.4.10, so Detekt's own type-aware `coroutines` rules stay inert — see the `coroutines:` block in `config/detekt/detekt.yml`, which keeps only `GlobalCoroutineUsage` live). (1) The third-party **`structured-coroutines`** Detekt ruleset (wired via `detektPlugins` in each module and `--plugins` in the pre-commit hook), tiered in `config/detekt/detekt.yml`: BLOCKING rules fail the build; WARN rules report at `weight: 0` via `build.weights`. The three KMP `commonMain` rules — `DispatchersIOInCommonMain`, `RunBlockingInCommonMain`, `MainScopeWithoutCancel` — are **active and blocking** here (unlike a JVM-only backend, this repo has a real `commonMain` where each is a portability defect). (2) **`.semgrep/coroutines.yml`** (`kotlin-hardcoded-dispatcher`) catches the top-level / builder-argument `Dispatchers.*` cases Detekt's class-scoped `HardcodedDispatcherInClass` misses; it runs in the CI `semgrep` job (`.github/workflows/security.yml`) as a directory scan so its `paths:` test/fixture excludes apply, with fixtures in `.semgrep/coroutines.kt` pinned by `semgrep --test`. It is deliberately **not** wired into the file-based `pre-push` hook (an explicit file list bypasses `paths:` excludes). Together these are the machine-enforceable half of the `kotlin-coroutines-skill`.
- **iOS:** a two-tool toolchain mirrors the Kotlin ktfmt + Detekt pair — **swift-format** for formatting and **SwiftLint** for static analysis — both enforced at parity with their Kotlin counterparts.
  - **swift-format** (formatting, the ktfmt analog). Config lives at `iosApp/.swift-format` (Apple's toolchain-bundled `swift format`, 4-space indent, 100-col). Run `./gradlew swiftFormatCheck` to lint (strict — fails on any finding) or `./gradlew swiftFormatApply` to format in place.
  - **SwiftLint** (static analysis, the Detekt analog). Config lives at `iosApp/.swiftlint.yml`. Run `./gradlew swiftLintCheck` (strict — warnings become errors) or directly `swiftlint lint --strict --config iosApp/.swiftlint.yml iosApp/iosApp`. The config disables only the formatting-only rules that overlap with swift-format (so swift-format stays the sole formatter and the two never fight); genuine findings are fixed in source, never blanket-disabled — the same posture as Detekt leaving formatting to ktfmt. SwiftLint is **not** toolchain-bundled: install via `brew install swiftlint`.
  - Both `swiftFormatCheck` and `swiftLintCheck` are wired into `./gradlew check`, and the pre-commit hook runs both on staged `.swift` strictly — the same enforcement points as ktfmt + Detekt. Each keys on whether its binary (`swift` / `swiftlint`) resolves on PATH: they enforce wherever the tool is installed (macOS dev machines, and CI once it provides the toolchain) and self-skip with a message where absent (e.g. a contributor on Linux, or a machine without SwiftLint), so the gate never hard-fails on a missing binary.
  - **Periphery** (unused-code / dead-declaration analysis, config `iosApp/.periphery.yml`) is the parity for Detekt's unused-member rules. It is **on-demand only** (`./gradlew peripheryScan`), deliberately **not** in `./gradlew check`: unlike the per-file Swift tools it is a whole-program analysis that runs a full `xcodebuild` to produce an index store (a scan takes minutes and needs macOS + Xcode + the `shared` framework built), so it can't self-skip cleanly on a toolchain-less host. It is the iOS analog of the on-demand `:androidApp:debugStabilityDump` Compose-stability task — run it manually before a refactor or release. Install via `brew install periphery`; the task self-skips with a message where `periphery` is absent.
- **Git hooks:** install with `./gradlew installGitHooks` (sets `core.hooksPath=.githooks`). The `pre-commit` hook runs ktfmt + Detekt on staged Kotlin via the fast CLI path (standalone fat jars resolved by `resolveLintTools`, not the Gradle daemon; Detekt loads the `structured-coroutines` ruleset jar via `--plugins`) — it aborts on partially-staged Kotlin, fails with a "rerun `resolveLintTools`" message when the resolved jar versions (ktfmt, detekt, and the `structured-coroutines` ruleset) drift from `gradle/libs.versions.toml`, and runs `checkNoDetektBaseline` when a `detekt-baseline.xml` is staged. It also runs `swift format lint --strict` and `swiftlint --strict` on staged `.swift` files (one shared partial-staging abort), each warn-and-skipping independently when its binary (`swift` / `swiftlint`) isn't on PATH so the tool that is present still runs. The `pre-push` hook runs the security scanners over the commits the push adds (reachable from the pushed tips but not already on a remote): betterleaks (secret scan over the pushed git history, gated on the `.betterleaks.toml` version floor) and semgrep (the `.semgrep/astro-mobile.yml` ruleset over the changed `.kt`/`.kts` files) — each warn-and-skips independently when its CLI is absent or below floor so the other still runs. Both honor `--no-verify`. The CI gate (`./gradlew check`) is the authoritative enforcement point whether or not the hooks are installed — they are a fast local pre-flight, not a replacement.

## Tech stack & versions

- Kotlin 2.4.10, Gradle 9.x, Android Gradle Plugin 9.x.
- Android: compileSdk 37, minSdk 23, targetSdk 37, Java 17.
- Jetpack Compose for Android UI; SwiftUI for iOS UI.
- iOS targets: `iosX64`, `iosArm64`, `iosSimulatorArm64`.
- Gradle version catalog (`gradle/libs.versions.toml`) — holds the lint toolchain versions (the ncorti ktfmt plugin / `ktfmt-cli` / Detekt / the `structured-coroutines` Detekt ruleset) plus the `ktfmt`/`detekt` plugin aliases and the `structured-coroutines-detekt-rules` library alias, so the Gradle plugin, the pre-commit hook's CLI jars, and `verifyKtfmtAlignment` share one source of truth. Deliberately partial: other dependency versions remain declared inline in the `build.gradle.kts` files for now.

## Documented config files

Files with load-bearing detail a reviewer/agent should re-read when they change: `build.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, and (once added) `gradle/libs.versions.toml`, the Detekt config, the `.semgrep/` rulesets (`astro-mobile.yml`, `coroutines.yml`), the `.githooks/` scripts (`pre-commit`, `pre-push`), the CI workflows (`.github/workflows/ci.yml`, `security.yml`), and `.claude/settings.json` (plugin marketplace + enabled plugins).

## Development workflow

This repo uses a spec-driven, skill-based workflow (see `.claude/skills/`). Per branch:

1. `scaffold-issue` (or hand-authored) populates `.claude/SPEC.md` sections 1–3.
2. `spec-development` (`review the spec`) writes the implementation/testing checklists (§§4–5) and drives them one item per `resume the plan`.
3. `codex-review` / `address-review` run the adversarial review loop.
4. `finish-branch` resets agent working files to the `main` skeleton, pushes, and opens the PR.

`.claude/SPEC.md`, `.claude/REVIEW_PLAN.md`, and `.claude/REVIEW_ADVERSARIAL.md` are per-branch working files that reset to skeletons on `main`.

### Agent skill routing & precedence

The repo carries skills from several families with **mostly non-overlapping domains** — route by where the work lands, not by keyword overlap. The two places domains do meet — Compose *authoring* vs. Android *framework features*, and Kotlin *idiom* vs. KMP *architecture* — are split by concern in the precedence rules below, not left to chance:

| Work domain | Skills | Where they live |
| --- | --- | --- |
| **Compose authoring & review** (any module) — *writing or reviewing* Jetpack Compose: composable structure, state authoring/hoisting/holder-UI split, side-effects, recomposition & stability performance, modifier/layout style, slot APIs, animations, focus/D-pad nav, UI-testing strategy | Chris Banes's **`chrisbanes-skills:compose-*`** skills (`compose-state-authoring`, `-state-hoisting`, `-state-holder-ui-split`, `-side-effects`, `-recomposition-performance`, `-stability-diagnostics`, `-state-deferred-reads`, `-modifier-and-layout-style`, `-slot-api-pattern`, `-animations`, `-focus-navigation`, `-ui-testing-patterns`) | plugin marketplace **`chrisbanes/skills`**, declared in `.claude/settings.json`; loads after a one-time per-dev trust/install prompt |
| **Android framework features** — Navigation 3, adaptive/foldable layouts, the Styles API / theming, edge-to-edge insets, Android test-infra setup, and driving the SDK/deploy/doc-search tooling (the framework surfaces `chrisbanes-skills:compose-*` does *not* cover) | the official Google **`android/*`** skills | `android-cli` is vendored at `.claude/skills/android-cli/`; the rest are served **on-demand** by the Android CLI |
| **`iosApp/` & SwiftUI** — SwiftUI APIs/best-practices, UIKit-interop modernization, XCTest→Swift Testing migration, and Xcode security-settings hardening | Apple's first-party **Xcode Agent Skills** (`swiftui-specialist`, `swiftui-whats-new-27`, `uikit-app-modernization`, `modernize-tests`, `audit-xcode-security-settings`, `device-interaction`) | `.claude/skills/` (committed, vendored from Xcode 27) |
| **Runtime / on-device visual verification** (either platform) — the build→install→launch→screenshot→inspect loop that confirms a change actually renders/behaves on a device or emulator/simulator ("run the app", "screenshot it on the emulator", "does it run on device") | **`android-device-debug`** (Android) · **`ios-device-debug`** (iOS) — repo-specific wrappers that own the runtime loop; UI *development* still routes to the rows above | `.claude/skills/` (committed) |
| **`:shared` / KMP architecture** — data layer, repositories, `expect`/`actual` placement, module boundaries, KMP Gradle structure, refactor safety | the vendored **`kotlin-*`** skills | `.claude/skills/` (committed) |
| **Kotlin authoring idiom** (any module) — Flow/StateFlow/SharedFlow & one-shot event modeling, subject `when`/smart-casts, member/extension/factory function ownership, value classes, semantic `expect`/`actual` boundary *design* | Chris Banes's **`chrisbanes-skills:kotlin-*`** skills (`kotlin-flow-state-event-modeling`, `kotlin-control-flow`, `kotlin-functions`, `kotlin-types-value-class`, `kotlin-multiplatform-expect-actual`) | same **`chrisbanes/skills`** marketplace as above |
| **Async / concurrency (any module)** — the canonical coroutine authority; its ruleset provenance backs the Detekt `structured-coroutines` + semgrep lint gate | the vendored **`kotlin-coroutines-skill`** (*not* chrisbanes' `kotlin-coroutines-structured-concurrency`) | `.claude/skills/` (committed) |

**Precedence rule:** for any Android-platform / `:androidApp` / Android-*framework-feature* / Android-tooling task, prefer the official `android/*` skill over a `kotlin-*` skill that merely mentions the same surface — the `android/*` skills are Google-authored, versioned, and kept current by the CLI. The `kotlin-*` skills own everything inside `:shared` and cross-module KMP architecture and do **not** cover Android UI. There is no genuine build-tooling clash: the official `agp-9-upgrade` skill explicitly excludes KMP projects, so KMP Gradle work stays with `kotlin-build-kmp-gradle-governance`. The mirror of this rule holds for iOS: any `iosApp/` / SwiftUI / Xcode task routes to the Apple Xcode Agent Skills below — they own the Swift/Xcode surface just as `kotlin-*` owns `:shared`, and never cross into it.

**`chrisbanes-skills` precedence (Compose + Kotlin authoring):** these Apache-2.0 skills by Chris Banes are the authority for *how to write and review* idiomatic Compose and Kotlin. They sit alongside — not on top of — the rule above:

- **Compose authoring vs. framework features:** for writing or reviewing composable code (state, side-effects, recomposition/stability, modifiers, slots, animations, focus, UI-test strategy), reach for `chrisbanes-skills:compose-*` **first**. The `android/*` skills keep the specific framework *features* they uniquely cover (Navigation 3, adaptive layouts, the Styles API, edge-to-edge, test-infra setup). The split is clean: chrisbanes = how to structure the composable; `android/*` = which framework API to reach for. This refines the "Compose-UI" reading of the rule above — Compose *authoring* is chrisbanes, Compose *framework features* stay `android/*`.
- **Kotlin idiom vs. KMP architecture:** `chrisbanes-skills:kotlin-*` cover idiomatic authoring (Flow/event modeling, control flow, function ownership, value classes, semantic `expect`/`actual` design); the vendored `kotlin-*` remain authoritative for `:shared`/cross-module architecture and source-set placement. Where both touch `expect`/`actual`, chrisbanes designs the semantic boundary and `kotlin-platform-kmp-bridges` decides where it lives in the hierarchy.
- **Coroutines — one deliberate exception:** the vendored `kotlin-coroutines-skill` stays canonical for all coroutine work and is **not** superseded by chrisbanes' `kotlin-coroutines-structured-concurrency`. That vendored skill's ruleset provenance backs the enforced Detekt `structured-coroutines` + semgrep gate (see Linting), so keeping it authoritative keeps guidance and enforcement in lockstep.
- **Availability:** the marketplace is declared in `.claude/settings.json` (`extraKnownMarketplaces` + `enabledPlugins`), but an external-marketplace plugin loads only after each developer accepts the repo's trust/install prompt — until then Claude Code reports it "not installed" and shows the `claude plugin install chrisbanes-skills@chrisbanes-skills` command to run. Skills surface as `chrisbanes-skills:<skill-name>` (e.g. `chrisbanes-skills:compose-state-hoisting`). The `implement-issue` / `shepherd` workflow skills in that marketplace are intentionally left out of the table — this repo drives implementation through its own `scaffold-issue` → `spec-development` workflow.

**Apple Xcode Agent Skills** — vendored verbatim from Xcode 27's first-party bundle (Apple ships them via the `agent` CLI; each carries a `PROVENANCE.md`). Refresh on an Xcode upgrade via the **`update-xcode`** skill ("update Xcode", "a new Xcode beta is out") — it drives the whole loop: `xcodes` install, license, hardcoded-path updates, mcpbridge re-registration, the re-export (`xcrun agent skills export --output-dir <absolute path to .claude/skills> --replace-existing`, which needs a running Xcode 27+ — skills are served live, not stored as static files in the app bundle), `PROVENANCE.md` restoration, and post-upgrade checks. One skill has a runtime dependency:

- **`device-interaction`** is the bridge to **DeviceHub** (Xcode 27's connected-device inspector) — it lets an agent install/run the app and *see* it via screenshots + UI hierarchy and drive taps/swipes on a real device or simulator. It works **only when the agent is connected to Xcode's `mcpbridge` MCP host**, which exposes the `mcp__xcode__*` toolset (`DeviceInteractionStartSession`, `DeviceInteractionInstallAndRun`, `DeviceInteractionSynthesize`, `RenderPreview`, `BuildProject`, etc.). The other five are portable knowledge skills that run anywhere.

  **Setup (per machine, one-time):** register the bridge as a **local-scope** MCP server (machine-specific — it pins an absolute Xcode-beta path, so it must NOT go in the committed `.mcp.json`):

  ```bash
  DEV=/Applications/Xcode-27.0.0-Beta.3.app/Contents/Developer   # adjust to your Xcode
  claude mcp add xcode -s local -e DEVELOPER_DIR=$DEV -- $DEV/usr/bin/mcpbridge
  ```

  The bridge auto-connects to the running Xcode (or honors `MCP_XCODE_PID`). The tool service only enumerates tools once a **project/workspace is open** in that Xcode — with none open, `tools/list` times out and the health check shows "Connected · tools fetch failed" (open `iosApp/iosApp.xcodeproj` to resolve). Alternatively, `xcrun agent run-agent claude` launches Claude Code already wired to the bridge. This is deliberately not in the committed `.mcp.json` because the path is per-developer.

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

From Xcode 27's seven bundled skills, **`adopt-c-bounds-safety`** (named `c-bounds-safety` before Xcode 27 Beta 3) is deliberately **not** vendored — it covers C/C++ bounds-safety adoption, and this app has no C surface (Swift UI + Kotlin shared logic). Re-export it if C/C++/Objective-C code is ever introduced under `iosApp/`.

## References

- **Adding KMP dependencies** — [multiplatform dependencies](https://kotlinlang.org/docs/multiplatform/multiplatform-dependencies.html) and [upgrading a multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-upgrade-app.html). Canonical how-to for adding/upgrading `commonMain` and platform-specific dependencies (e.g. Ktor Client, kotlinx.serialization) — follow it rather than guessing source-set wiring.
- **Calendar layout-engine sharing** — astro-plans `ADR-calendar-layout-engine-sharing-strategy` (decision D-15) and the `W-S1` spike. Background for the reserved `commonMain` layout seam.
