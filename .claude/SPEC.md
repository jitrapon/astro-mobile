# Specification: Mobile navigation and app shell

> Per-branch working file owned by the `spec-development` skill. Each branch
> overwrites the section bodies; this file in `main` is a skeleton that
> documents the canonical structure so every branch follows the same shape.

## 0. Plan anchor

Where this branch sits in `current-plan.md` (astro-docs). Written by `scaffold-issue` and validated
against the live plan; `completes` and `spec-objective` are settled by `spec-development`. Read by
`finish-branch` **before** it resets this file, and copied into the PR body's `## Plan Update`
section, which `sync-plan` parses unattended. Field semantics: `plan-update-contract.md` in
astro-docs.

```yaml
lane: mobile           # backend | mobile | web | docs | infra | -
task: M-2              # task ID from current-plan.md (M-2, M-3), or - if not plan work
issues: [136]          # issue numbers in THIS repo that merging this branch closes
completes: no          # does merging this branch finish the whole task row?
spec-objective: Give both apps a bottom-navigation shell whose tabs are rendered from the contract's semantic nav destinations, with a reachable placeholder screen behind each.
```

## 1. Overview

Second of three branches splitting M-2. It ships navigation and the app shell on both platforms: a
navigation framework, a bottom-navigation shell whose destinations come from the contract's shell
tree rather than a hardcoded list, and placeholder screens behind each destination. It is platform
UI work in the Android and iOS apps, independent of the shared observation layer that landed first,
and sequenced before the SDUI registry branch so the registry has a shell to render into.

## 2. Objective

Both apps present a bottom-navigation shell whose destinations are rendered from the shell tree's
semantic nav destinations (Calendar, Agenda), with a reachable placeholder screen behind each
destination on Android and iOS.

## 3. Requirements & Context

**Scope:**

- A navigation framework on both platforms.
- An app shell with bottom navigation rendered from the shell tree's **semantic nav destinations**
  (Calendar, Agenda) — driven by the contract, not a list hardcoded in Compose or SwiftUI.
- Placeholder screens behind each destination.

**Starting point (as the issue records it):** neither app has navigation today — the Android app is
a single activity with a theme, the iOS app is its entry point plus a placeholder view, and the
version catalog carries no navigation or lifecycle dependency.

**Architectural constraints (load-bearing):**

- **UI stays out of the shared module.** Compose lives in the Android app and SwiftUI in the iOS app;
  the shared module exposes platform-agnostic models and logic only.
- **The destination list is contract-driven.** Bottom navigation must be derived from the shell
  tree's semantic nav destinations; a hardcoded destination list on either platform does not satisfy
  the acceptance criteria.
- **Any new dependency is declared in the version catalog**, not inline.

**Skill routing the issue prescribes:**

- Compose authoring routes to the `chrisbanes-skills:compose-*` skills.
- The Android navigation *framework* choice routes to the official `android/navigation-3` skill.
- SwiftUI routes to the vendored Apple skills.

**Out of scope:**

- The shared observation layer and `CalendarViewModel` (#135, already merged).
- The SDUI component registry, action model and R8 keep rules (#137).
- Everything the M-2 umbrella (#133) lists as out of scope for M-2 as a whole.

**Acceptance (from the issue):**

- Bottom navigation renders from the shell tree's semantic nav destinations, not a hardcoded list.
- Placeholder screens are reachable behind each destination on both platforms.
- New dependencies are declared in the version catalog.
- `./gradlew check` is green, and CI's `verify-ios` job compiles the `iosApp` scheme.

This branch does not complete the M-2 plan row; only the registry branch (#137) does.

## 4. Implementation Plan and Progress Tracking (for agent)

**Planning decisions (confirmed with the user before drafting):**

- **Destinations are whatever the contract delivers.** The vendored contract states that Agenda is a
  calendar *view* (it lives on the view switcher), not a nav destination, and that the concrete
  destination set is product IA; its example response sends Calendar and Expense. The shell
  therefore renders `navigation.destinations` as delivered, and each tab opens a generic placeholder
  keyed by the destination — "Calendar, Agenda" in §§1–3 is read as illustrative.
- **No tabs until a screen has loaded.** Destinations arrive only inside a calendar screen response,
  and no backend is reachable from a device today. Before a screen loads — or when none can — the
  shell shows a loading/failure placeholder with no bottom bar. Tab rendering and placeholder routing
  are verified against the contract fixture, not a live backend.

**Plan:**

- [ ] **1. Shared shell-destination projection.** In `shared/src/commonMain/.../presentation/shell/`,
  add a public, UI-agnostic tab model (destination id, label, icon token, target screen id) and a pure
  function projecting a `CalendarScreenResponse`'s `screen.navigation.destinations` into the ordered
  tab list. Rules, each stated in KDoc: contract order is preserved; only destinations whose action is
  `NavigateAction` become tabs (a tab must route to a screen; other action kinds belong to the action
  model in #137); a repeated destination id keeps its first occurrence, since tab identity keys
  navigation state. Plain data in, plain data out — callable from Swift, no `Flow` or Koin type.
- [ ] **2. Android dependencies in the version catalog.** Fetch the on-demand `navigation-3` skill
  (`android skills add navigation-3 --agent=claude-code --project .`, not committed — the `android/*`
  skills are deliberately not vendored) and use it to pick the stable Navigation 3 runtime/UI
  artifacts. Declare them in `gradle/libs.versions.toml` and reference them from
  `androidApp/build.gradle.kts` by alias, alongside `implementation(libs.koin.core)` (the catalog's
  existing Koin alias, so no new Koin version). Existing inline Compose/lifecycle declarations are left
  as they are.
- [ ] **3. Android shell state holder.** Add an androidx `ViewModel` in `androidApp` that builds the
  current-month `CalendarScreenRequest` from the device's zone and locale (mirroring the iOS app's
  current-month request), resolves `CalendarScreenRepository` from the running Koin graph, constructs
  `CalendarViewModel` on `viewModelScope`, and exposes one `StateFlow` of shell state: loading,
  failure, or the tab list from item 1.
- [ ] **4. Android shell UI.** Add a stateless `AppShell` composable in `androidApp`: a bottom bar with
  one item per tab (label; icon from an icon-token lookup with a generic fallback for unknown tokens),
  a Navigation 3 back stack keyed by the selected tab's screen id, one placeholder screen per
  destination showing its label, and a loading/failure placeholder with no bottom bar when there are
  no tabs. Include `@Preview`s fed with fixture-shaped tabs. Follow the `chrisbanes-skills:compose-*`
  skills for state hoisting and the holder/UI split.
- [ ] **5. Wire the Android shell into `MainActivity`.** Replace the `MessageCard` placeholder with
  `AppShell` collecting the item-3 view model's state (lifecycle-aware collection) inside `AstroTheme`.
- [ ] **6. iOS shell UI.** In `iosApp`, add a SwiftUI shell view taking the tab list: a `TabView` with
  one tab per destination (label; SF Symbol from an icon-token lookup with a generic fallback), each
  hosting a placeholder view showing its label inside a `NavigationStack`, and a loading/failure
  placeholder with no tab bar when there are no tabs. Include `#Preview`s fed with fixture-shaped tabs.
  Follow the vendored `swiftui-specialist` guidance (tab identity, `ForEach` identity).
- [ ] **7. Wire the iOS shell to the observation.** Replace `ContentView`'s diagnostic summary with the
  shell view, projecting each delivered `CalendarUiState`'s content through item 1's function; keep the
  existing subscription lifecycle (subscribe in `.task`, cancel on termination).
- [ ] **8. Document the shell seams.** Update `.claude/CLAUDE.md`: the shared `presentation/shell/`
  projection in the architecture/package notes, Navigation 3 on Android and `TabView` on iOS, the
  rule that tabs come from delivered destinations (Agenda being a view, not a destination), and
  `:androidApp` resolving the repository through Koin.
- [ ] **9. Full gate.** Run `./gradlew check` and the `iosApp` simulator `xcodebuild` from CLAUDE.md
  on the finished branch.

## 5. Testing & Validation (for agent)

- [ ] **1.** New `commonTest` suite for the projection, run by `./gradlew :shared:testAndroidHostTest
  :shared:iosSimulatorArm64Test`: the contract fixture projects to `[calendar, expense]` with their
  labels, icon tokens and screen ids in order; a non-`navigate` destination is dropped; a repeated id
  keeps the first; an empty destination list projects to no tabs. `./gradlew :shared:verifyFrameworkHeaderSurface`
  still passes (the new public types add no library type to `shared.h`).
- [ ] **2.** `./gradlew :androidApp:assembleDebug` resolves and compiles with the new aliases;
  `./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath` shows the Navigation 3
  artifacts at the catalog version; `grep` confirms no Navigation 3 or Koin coordinate is declared
  inline in `androidApp/build.gradle.kts`.
- [ ] **3.** `./gradlew :androidApp:assembleDebug :androidApp:detekt :androidApp:ktfmtCheck` pass. The
  state derivation itself is item 1's tested function; this item is glue, verified at runtime in 5.
- [ ] **4.** Instrumented Compose UI test in `androidApp/src/androidTest`, run locally with
  `./gradlew :androidApp:connectedDebugAndroidTest` on an emulator (CI does not run instrumented
  tests — record the local output): given fixture-shaped tabs the bottom bar shows their labels in
  order; selecting the second tab shows its placeholder; given no tabs there is no bottom bar and the
  loading/failure placeholder shows. Plus `./gradlew :androidApp:detekt :androidApp:ktfmtCheck`.
- [ ] **5.** `./gradlew :androidApp:assembleDebug`, then an on-device run via the `android-device-debug`
  skill: the app launches without crashing and, with no backend reachable, shows the failure
  placeholder with no bottom bar (screenshot recorded).
- [ ] **6.** The CLAUDE.md `iosApp` simulator `xcodebuild` succeeds; `./gradlew swiftFormatCheck
  swiftLintCheck` pass; the previews render tabs for fixture-shaped destinations and the no-tabs
  placeholder (rendered via Xcode's `RenderPreview`, or the `ios-device-debug` skill if the Xcode MCP
  bridge is unavailable).
- [ ] **7.** The simulator `xcodebuild` succeeds; `swiftFormatCheck` / `swiftLintCheck` pass; an
  `ios-device-debug` simulator run launches without crashing and, with no backend reachable, shows
  the failure placeholder with no tab bar (screenshot recorded).
- [ ] **8.** Re-read the edited CLAUDE.md sections against the diff; every symbol it names in backticks
  exists (`grep`).
- [ ] **9.** `./gradlew check` exits 0 (including `verifyCheckPartition` and the header-surface guard)
  and the simulator `xcodebuild` exits 0.

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

- https://github.com/jitrapon/astro-mobile/issues/136
- https://github.com/jitrapon/astro-mobile/issues/133
- https://github.com/jitrapon/astro-mobile/issues/135
- https://github.com/jitrapon/astro-mobile/pull/138
- https://github.com/jitrapon/astro-mobile/issues/137
