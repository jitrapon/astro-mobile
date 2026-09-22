# Specification: SDUI component registry, action model, and release keep rules (M-2, 3/3)

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
lane: mobile
task: M-2
issues: [137, 128]
completes: yes
spec-objective: Implement the SDUI component registry and action model, consume resolvedPreferences, and close the release-shrinker gap — app-level R8 keep rules plus a CI gate that runs the shrunk release variant rather than only assembling it.
```

## 1. Overview

Third and last of the three PRs that split M-2. It ships the server-driven UI component registry and the action model into the app shell the previous PR delivered, consuming the view model the first PR delivered, and it closes the release-shrinker gap the registry makes reachable: the Android release variant is minified with no app-level keep rules, and the polymorphic serialization the registry needs is exactly what the shrinker strips when nothing keeps it. That failure is release-only and runtime-only, so today's CI — which only assembles the release APK — cannot see it.

## 2. Objective

The component registry and the action model are implemented, `resolvedPreferences` from the response envelope is consumed, and the Android release variant declares its own keep rules and is **run** in CI rather than only assembled — so a reflectively reached registry type the shrinker strips fails CI instead of a device.

## 3. Requirements & Context

**In scope**

- A registry mapping server-driven component types to platform renderers.
- The action model.
- Consuming `resolvedPreferences` from the response envelope.
- The carried rider from the release-shrinker issue, landed **before** the registry ships:
  - explicit keep-rule files declared for the release variant (today every keep rule applied comes from library consumer rules);
  - the merged rule set reviewed through the release mapping output;
  - any third-party rule broad enough to suppress optimization app-wide identified and neutralized by ignoring its source, **not** by adding counter-keeps;
  - the release variant run, not just assembled.

**Dependencies**

- Renders into the app shell delivered by the second M-2 PR and consumes the view model delivered by the first. Both have merged.

**Out of scope**

- The shared observation layer and the calendar view model (first M-2 PR).
- Navigation and the app shell (second M-2 PR).
- Everything the M-2 umbrella issue lists as out of scope for M-2 as a whole.

**Constraints**

- `./gradlew check` stays green, with no `@Suppress` and no Detekt baseline added.
- Dependency-injection bindings added here follow the plan's M-2 binding rules (recorded 2026-09-21 from the compile-time graph-check proof of concept), so that check can be adopted afterwards without rework: bindings live in a module each platform's graph start reaches through plain composition, never through a module-list parameter; same-typed bindings are told apart by type, not by a named qualifier; an unavoidable named qualifier is written as an inline string literal. Adopting the check itself is **not** this branch's work — it is a separate PR after this one.

**Plan**

- This is the PR that completes the M-2 plan row and closes the release-shrinker issue.

## 4. Implementation Plan and Progress Tracking (for agent)

Design decisions this plan encodes, settled with the user before drafting:

- **The registry keys on the contract's versioned component id, and lives per platform.** `:shared`
  exposes each decoded component's id plus a preference-applied render model; each app owns the map
  from id to its own renderer, with a fallback for ids it has no renderer for. Nothing renders in
  `:shared`.
- **Renderers are per-component placeholders that read their own props** — the month placeholder
  shows `headerLabel` and its chips honouring `chipDensity.maxSubtitleLines`, `chipStyle` and
  `weekStart`; the agenda placeholder shows day headers and cards. The real grids are M-3/M-4.
- **All five action types are routed from the affordances that can produce them.** The contract
  attaches an `Action` to exactly one thing — `NavDestination` — and the shell projection currently
  discards every destination whose action is not `NavigateAction`, so that filter is where four of
  the five types are lost today and it is this branch's job to remove it. An event carries
  *permissions*, never an action, so an event tap is a client-side affordance that **constructs**
  `OpenEventDetailAction` from the event's own id rather than reading a delivered one. Both apps
  also render the delivered `viewSwitcher`, which is what gives `switchCalendarView` a trigger.
- **The release variant is run via a Gradle Managed Device** with `testBuildType = "release"`, on a
  new host-portable CI job, so instrumented tests execute against the shrunk, signed release APK.
- **Phase A lands before the registry**, per §3: the shrinker gate exists first, so the registry's
  first commit is already covered by a gate that runs the shrunk APK.

### Phase A — R8 keep rules and the release-run gate (#128 rider, before the registry)

- [x] A1. Move `libs.androidx.compose.ui.tooling` from `implementation` to `debugImplementation` in `androidApp/build.gradle.kts`, so the tooling runtime and the AAPT-generated `-keep class androidx.compose.ui.tooling.PreviewActivity` rule stop applying to the release APK. `ui-tooling-preview` stays on `implementation` — `AppShellPreviews.kt` needs its `@Preview` annotation at compile time.
- [x] A2. Add `androidApp/proguard-rules.pro` and declare it explicitly: `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`. Record in the file's header what the regenerated `configuration.txt` shows about AGP 9's implicit default (the merged set observed before this branch already carried the extracted `proguard-android-optimize.txt`, so the explicit declaration is about giving the app's own rules a home and pinning the default, not about adding one that was missing).
- [x] A3. Audit the merged rule set: run `:androidApp:assembleRelease`, then read `androidApp/build/outputs/mapping/release/configuration.txt` and AGP's `configanalyzer` report beside it. Identify every rule broad enough to suppress optimization app-wide (`-dontoptimize`, `-dontshrink`, `-dontobfuscate`, a whole-package `-keep class **`). Neutralize each offender with `android.optimization.keepRules.ignoreFrom("<artifact>")`, never a counter-keep. **If none is found, record that outcome and the audited artifact list in `proguard-rules.pro`'s header** — a clean audit is the expected result for this dependency set and must be durable, not silently re-derived later.
- [x] A4. Make the release variant installable for tests without weakening shipping signing: keep the all-or-nothing `ASTRO_KEYSTORE_*` resolution, and fail a release-variant instrumented-test invocation with a message naming the four missing credentials instead of letting it reach an install of an unsigned APK.
- [x] A5. Add a Gradle Managed Device to `:androidApp` (`aosp-atd`, API 34, named so its task name is stable) and set `testBuildType = "release"`, so instrumented tests run against the shrunk release APK. Re-point the root build's CI partition from `testDebugUnitTest`/`lintDebug` to their release forms — AGP aims the unit-test and lint lifecycles at whatever `testBuildType` names, so `verifyCheckPartition` fails until the partition follows.
- [ ] A6. Refactor `AppShellTest` off `androidx.compose.ui:ui-test-manifest` — a `debugImplementation` artifact absent from the release variant — by replacing `createComposeRule()` with `createAndroidComposeRule<MainActivity>()`, which uses the app's own manifest-declared activity.
- [ ] A7. Add `ReleaseShrinkerSmokeTest` (`androidApp/src/androidTest`): launch `MainActivity`, assert the Koin graph resolved and the shell composed under R8. Extended in C8 once the registry exists.
- [ ] A8. Add the `verify-android-release` job to `.github/workflows/ci.yml` (`ubuntu-latest`): enable KVM via a udev rule, generate an ephemeral keystore with `keytool` and export the four `ASTRO_KEYSTORE_*` variables, then run the managed-device release test task. Comment it as artifact/runtime coverage deliberately outside `verifyCheckPartition`, the same classification `:androidApp:assemble` carries.

### Phase B — the shared component identity, render models, and action model

- [ ] B1. Give `CalendarBody` and `EventPresentation` a `componentId` property, each branch supplying its own versioned wire name the way `EventPresentation.region` is supplied rather than decoded, and add a `CalendarComponentIds` object holding those names as constants so the `@SerialName` values and the platform registries read one source.
- [ ] B2. Add `shared/.../presentation/calendar/CalendarBodyUiState.kt`: `CalendarBodyUiState` (sealed, each case carrying its `componentId`) with `MonthBodyUiState` / `AgendaBodyUiState` / `AgendaDayUiState`, and `EventChipUiState` carrying `componentId`, title, already-capped subtitle lines, chip style, the accent colour resolved from `calendars[calendarId]`, and the accessibility label.
- [ ] B3. Add `CalendarScreen.toCalendarBodyUiState()` — the one place `resolvedPreferences` is applied: `chipDensity.maxSubtitleLines` caps the subtitle lines, `chipStyle` reaches only the filled-bar components the contract scopes it to, `weekStart` reaches the month state. Document that clients must not re-derive either density value from the other.
- [ ] B4. Add `ViewSwitcherUiState` / `ViewSwitcherOptionUiState` (`id`, `label`, `isActive`, and the `SwitchCalendarViewAction` selecting it produces), projected from the delivered `ViewSwitcher`.
- [ ] B5. Add `shared/.../presentation/action/ActionEffect.kt` — the sealed set of effects a platform executes (`ShowScreen`, `OpenExternalUrl`, `ShowEventDetail`, `ShowEvents`), each naming what the client does rather than restating the wire type.
- [ ] B6. Make `CalendarViewModel`'s request observable — a private `MutableStateFlow<CalendarScreenRequest>` collected with `flatMapLatest` — and add `dispatch(action: Action): ActionEffect?`, which consumes `SwitchCalendarViewAction` by re-pointing the request (returning `null`) and returns the matching effect for every other action type.
- [ ] B7. Extend `CalendarUiState` with `title`, `body: CalendarBodyUiState?` and `viewSwitcher: ViewSwitcherUiState?`, filled in `toCalendarUiState()` so a failure that kept its last screen keeps its body and switcher too.
- [ ] B7a. Stop `toAppShellState()` discarding non-navigating destinations — the filter that currently loses four of the five action types. Replace `AppShellTab.targetScreenId` with the destination's `action`, so every delivered destination becomes a tab and selecting one dispatches its action; `targetScreenId` becomes a derived convenience for the `NavigateAction` case. Rewrite the projection's documented rules accordingly: contract order and first-occurrence-wins survive unchanged, "only navigating destinations become tabs" does not, and a tab whose action does not navigate holds no back stack.
- [ ] B8. Replace the `MAIN_THREAD_DELIVERY_SCOPE` `val` qualifier in `IosPresentationModule` with an inline `named("…")` string literal, and declare every binding this branch adds in a module the platform graphs reach through plain composition, distinguished by type rather than by qualifier — the M-2 binding rules, so the follow-up Koin compiler-plugin adoption needs no rework.

### Phase C — the platform registries, renderers, and action execution

- [ ] C1. Android: add `androidApp/.../ui/component/CalendarComponentRegistry.kt` — component id to `@Composable` renderer for the body components and for the five event-presentation components, with an explicit fallback renderer for an id nothing is registered for. Document why an unregistered *body* id cannot arrive through decoding today, and that the fallback is reachable for an event presentation a body's renderer does not draw.
- [ ] C2. Android: implement the per-component placeholder renderers, each reading its own props — month shows `headerLabel` with its chips ordered by `weekStart`, agenda shows day headers and cards — honouring the capped subtitle lines and `chipStyle`.
- [ ] C3. Android: add a top bar carrying the screen `title` and the view switcher, and route a switcher selection through `AppShellViewModel` into `CalendarViewModel.dispatch`.
- [ ] C4. Android: dispatch a tab selection through `CalendarViewModel.dispatch` (no longer assuming it navigates) and execute the returned `ActionEffect`s — `OpenExternalUrl` through an `Intent`, `ShowScreen` by showing the matching destination's screen, `ShowEventDetail` / `ShowEvents` through one explicit not-yet-built surface rather than a silent drop.
- [ ] C4a. Android: make the event placeholders tappable, dispatching an `OpenEventDetailAction` built from the tapped event's own id — the contract attaches no action to an event, so this is the client-side affordance that makes that action type reachable at all. Give the month placeholder's overflow affordance a `PresentModalAction` over the ids it hides, for the same reason.
- [ ] C5. iOS: extend the observer bridge so Swift can send an action back — `CalendarScreenSubscription` (or the observer) gains a `dispatch(action:)` that reaches the same `CalendarViewModel`, with no coroutines type on the framework surface.
- [ ] C6. iOS: add `iosApp/iosApp/CalendarComponentRegistry.swift` — component id to view builder with a fallback — plus the per-component placeholder views reading the same props as their Android counterparts.
- [ ] C7. iOS: render the view switcher in the toolbar, dispatch a tab selection through the bridge rather than assuming it navigates, and execute the effects (`OpenExternalUrl` via `openURL`, `ShowScreen` by showing the matching destination's screen, the two event effects through the same explicit not-yet-built surface). Make the event placeholders tappable, mirroring C4a.
- [ ] C8. Extend `ReleaseShrinkerSmokeTest` to decode the vendored contract fixture through the app's real `BackendJson` codec, resolve every decoded body and event component through the Android registry, and dispatch one action of each of the five types — the polymorphic-deserialization path R8 would break, exercised inside the shrunk APK.

### Phase D — documentation

- [ ] D1. Update `.claude/CLAUDE.md`: the registry / render-model / action-model seams under "Key patterns"; the `presentation/action/` and `ui/component/` package additions; the `verify-android-release` job and why it sits outside `verifyCheckPartition`; `testBuildType = "release"` and the managed device; and `androidApp/proguard-rules.pro` added to the "Documented config files" list.
- [ ] D2. Update `README.md` / `CONTRIBUTING.md` with how to run the release-variant instrumented tests locally, including the ephemeral-keystore one-liner and the four environment variables.

## 5. Testing & Validation (for agent)

Paired 1:1 with §4. `./gradlew check` must stay green throughout, with no `@Suppress` and no Detekt
baseline added; run `./gradlew ktfmtFormat` before each commit.

- [x] A1. `./gradlew :androidApp:assembleRelease` succeeds and `androidApp/build/outputs/mapping/release/configuration.txt` no longer contains the AAPT-generated `-keep class androidx.compose.ui.tooling.PreviewActivity` rule. `./gradlew :androidApp:assembleDebug` still compiles `AppShellPreviews.kt`.
- [x] A2. `configuration.txt` regenerated after the change names `proguard-rules.pro` as an originating file, and `./gradlew :androidApp:assemble` stays green on a machine with no keystore.
- [x] A3. The audit is recorded in `proguard-rules.pro`'s header with the list of artifacts contributing rules and the verdict for each broad-rule category; `grep` over the regenerated `configuration.txt` for `-dontoptimize` / `-dontshrink` / `-dontobfuscate` returns what the header claims.
- [x] A4. `./gradlew :androidApp:verifyReleaseSigningCredentials` fails naming every absent credential (all four unset, and the missing subset when only some are), and succeeds once all four resolve. `--dry-run` on a release instrumented-test task shows the guard scheduled ahead of it when the release variant is unsigned and absent once credentials resolve; the same dry-run on `connectedDebugAndroidTest` never shows it, so a signed tested variant is untouched. Configuration succeeds with no credentials present, so `:androidApp:assemble` stays green on a keystore-less machine.
- [x] A5. `./gradlew tasks --all | grep ReleaseAndroidTest` lists the managed-device task, and `./gradlew :androidApp:check` does **not** reach it. With the four credentials unset, invoking that task fails with A4's named message rather than an install error — the end-to-end pairing A4 could only verify against `connectedReleaseAndroidTest`, since the managed device did not exist yet. `./gradlew verifyCheckPartition` and `./gradlew check` both stay green after the partition follows `testBuildType`.
- [ ] A6. `AppShellTest`'s four tests pass on the managed device against the release variant.
- [ ] A7. `ReleaseShrinkerSmokeTest` passes on the managed device; temporarily adding a `-keep`-defeating change (or inspecting `usage.txt`) confirms the test fails when the graph's classes are stripped.
- [ ] A8. `./gradlew verifyCheckPartition` stays green, `./gradlew check` passes locally, and the new CI job is green on the pull request.
- [ ] B1. A `commonTest` conformance test asserts each branch's `componentId` equals the `@SerialName` the contract declares, read from the generated `EmbeddedContract` constants rather than a hand-copied list. `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`.
- [ ] B2. `commonTest` covers each `CalendarBodyUiState` case's construction from the contract fixture, including an event whose `subtitleLines` exceed the cap.
- [ ] B3. `commonTest` asserts: subtitle lines are capped at `maxSubtitleLines` (including `0`), `chipStyle` reaches only the filled-bar components, `weekStart` is carried through unmodified, and a missing calendar id yields no accent colour rather than throwing.
- [ ] B4. `commonTest` asserts exactly one option is `isActive`, that it matches the delivered `activeSelection`, and that contract order is preserved.
- [ ] B5. Covered by B6's dispatch tests — `ActionEffect` has no behaviour of its own.
- [ ] B6. `commonTest` (`kotlinx-coroutines-test` virtual clock, both targets) asserts: `SwitchCalendarViewAction` re-points the request and yields a new observation without returning an effect; each other action type returns its matching effect; and the superseded observation is cancelled rather than delivering a failure.
- [ ] B7. `CalendarViewModelTest` extended: a failure carrying a last-loaded response still exposes its `title`, `body` and `viewSwitcher`.
- [ ] B7a. `AppShellStateTest` (both targets) extended: a destination carrying each of the five action types becomes a tab and carries that action; contract order and first-occurrence-wins still hold; a screen delivering destinations that all carry non-navigating actions is `Tabs`, not `NoDestinations`. Add a fixture-driven case asserting no delivered destination is dropped.
- [ ] B8. `DependencyGraphResolutionTest` (both targets) and `iosTest`'s `DependencyGraphTest` still resolve every binding, and `./gradlew :shared:verifyFrameworkHeaderSurface` confirms no library type reached the header. Those prove resolution, not the structural rules, so verify the rules directly: `grep -rn "named(" shared/src/*/kotlin/io/jitrapon/astro/di/` shows every remaining qualifier as an inline string literal and none introduced by this branch; and each binding added here is read back from a `module { }` that `startDependencyGraph` reaches through `listOf` / `+` / a direct call, never through the `platformModules` parameter. Record the trace in the commit body. Adopting the compiler plugin itself stays out of this branch.
- [ ] C1. An instrumented test asserts every contract-declared component id resolves to a registered renderer and that an unregistered id resolves to the fallback.
- [ ] C2. `AppShellTest`-style instrumented assertions: the month placeholder shows `headerLabel`, a chip with more subtitle lines than the cap renders exactly the cap, and the agenda placeholder shows its day headers.
- [ ] C3. An instrumented test asserts the top bar shows the delivered `title` and one switcher option per delivered option with the active one selected — and then **taps** a non-active option and asserts the observed request changed and the newly selected option reads as active. Starting from displayed state alone would pass even if the switcher's callback never reached `dispatch`.
- [ ] C4. An instrumented test **taps** a delivered destination carrying each of the five action types in turn, asserting the user-visible outcome each produces: the matching destination's screen shown, the URL handler invoked with the delivered URL, the event surface opened with the delivered id, the events surface opened with the delivered ids, and the view changed. It must drive the real shell dispatch path from the tap rather than injecting an `ActionEffect`, since injection would pass with the tab callback disconnected.
- [ ] C4a. An instrumented test taps an event placeholder and asserts an `OpenEventDetailAction` carrying that event's id reached the handler; likewise the overflow affordance and `PresentModalAction`.
- [ ] C5. `iosTest` asserts a dispatched `SwitchCalendarViewAction` produces a new delivered state on the subscription and that `cancel()` stays idempotent afterwards.
- [ ] C6. `xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath iosApp/build/DerivedData ARCHS=arm64 CODE_SIGNING_ALLOWED=NO DEVELOPMENT_TEAM=""` succeeds and `./gradlew swiftFormatCheck swiftLintCheck` pass — but those only compile and lint, so pair them with the two checks that can actually fail on wrong behaviour: a `commonTest` assertion that `CalendarComponentIds` covers exactly the component ids the embedded contract declares (so an id missing from either registry is a named, failing gap rather than a silent fallback), and a runtime pass through the `ios-device-debug` loop capturing each registry case and the fallback on the simulator. **There is no Swift test target in `iosApp.xcodeproj` today**; adding one, plus the booted-simulator `xcodebuild test` the macOS CI job would need, is deliberately out of scope here.
- [ ] C7. Same build and Swift gates as C6, plus a runtime `ios-device-debug` pass that **taps a delivered destination carrying each of the five action types** through the real tab dispatch path, asserting each visible outcome and its payload — the target screen shown, the delivered URL opened, the event surface carrying the delivered id, the events surface carrying the delivered ids, and the view changed — then a switcher selection and an event tap. Compilation proves none of this, and the shared dispatch tests prove only that the effect was produced, so a missing Swift handler for any one effect would otherwise pass every gate. Mirrors C4 on the Android side; needs no Swift test target and no extra CI job.
- [ ] C8. The extended `ReleaseShrinkerSmokeTest` passes on the managed device. Confirm it has teeth: with `isMinifyEnabled = true` and a keep rule the decode needs deliberately removed, the test fails — then restore.
- [ ] D1. `./gradlew check` stays green and a re-read of `.claude/CLAUDE.md` shows every changed config file listed under "Documented config files".
- [ ] D2. The documented local command runs end-to-end on a clean checkout with no `keystore.properties`.

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

https://github.com/jitrapon/astro-mobile/issues/137
https://github.com/jitrapon/astro-mobile/issues/133
https://github.com/jitrapon/astro-mobile/issues/128
https://github.com/jitrapon/astro-mobile/issues/135
https://github.com/jitrapon/astro-mobile/issues/136
https://github.com/jitrapon/astro-mobile/issues/153
https://github.com/jitrapon/astro-docs/blob/main/tasks/M-2.md
