# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-20 (round 2)

- Base ref: `main`
- Focus sent to Codex: the same branch summary as round 1, plus: "This is review round 2; round 1 raised two findings, both now fixed, and the fixes themselves are unreviewed and in scope: (a) the Android bottom bar and Scaffold applied zero window insets under enforced edge-to-edge — now they pass `BottomNavigationDefaults.windowInsets` / `ScaffoldDefaults.contentWindowInsets` and `MainActivity` calls `enableEdgeToEdge()`; (b) a loaded screen carrying no routable destinations projected to an indefinite `Loading` — now there is a settled `AppShellState.NoDestinations` case, guarded by `content != null && !isLoading`, rendered as a terminal no-progress message on both platforms. Scrutinise those two changes as hard as the rest." Plus the standard KMP watch-list, extended with inset and edge-to-edge regressions.

# Codex Adversarial Review

Target: branch diff against main
Verdict: approve

No substantive ship-blocking finding supported by the inspected diff. Both round-one fixes address their reported failures. Review was static and read-only; builds, tests, and device behavior were not independently rerun.

No material findings.

<!-- previous-rounds:start -->

## Previous rounds

### 2026-09-20 — base `main`
- Status when archived: both findings addressed on this branch — the high inset finding in commit 60834f2, the medium empty-navigation finding in commit f96f217. Round 2 re-reviewed both fixes and returned `approve` with no material findings.

- Base ref: `main`
- Focus sent to Codex: This branch gives both mobile apps a bottom-navigation app shell whose tabs render from the calendar screen contract's delivered `navigation.destinations` rather than a hardcoded list, with a placeholder screen reachable behind each destination. It adds a shared UI-agnostic projection in `:shared/commonMain` (`presentation/shell/AppShellState.kt` — `AppShellState`/`AppShellTab` and the pure `toAppShellState` mapping of `CalendarUiState`), an Android `AppShellViewModel` + Navigation 3 `AppShell` composable wired into `MainActivity` (Navigation 3 and material-icons-core declared in the version catalog), and a SwiftUI `AppShellView` wired into `ContentView` through `CalendarScreenObserver` with the deployment target raised to iOS 18. Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, tab-identity/back-stack state bugs, and missing cross-platform test coverage.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention — **both findings resolved** (1 high, 1 medium; 0 deferred, 0 dismissed).
Verified after the fixes: `./gradlew check` exits 0 (including `verifyFrameworkHeaderSurface`) and
the simulator `xcodebuild` of the `iosApp` scheme succeeds.

Do not ship yet: Android tabs overlap system navigation, and valid empty navigation responses leave both apps loading indefinitely. Review was read-only; tests were not rerun.

Findings:
- [high] Apply system-bar insets to the Android bottom navigation (androidApp/src/main/java/io/jitrapon/astro/ui/shell/AppShell.kt:111)
  The app targets SDK 37, but this Material 2 BottomNavigation overload passes ZeroInsets, confirmed in the resolved 1.12.0 sources. Scaffold also uses zero insets, and MainActivity supplies no outer inset handling. On Android versions enforcing edge-to-edge, the tabs therefore extend beneath system navigation, obscuring labels and placing tap targets behind system controls. [Android documentation](https://developer.android.com/develop/ui/compose/system/material-insets) confirms Material 2 requires explicit inset handling.
  Recommendation: Pass appropriate windowInsets to BottomNavigation and contentWindowInsets to Scaffold. Verify the loaded shell in MainActivity with gesture and three-button navigation.

  **RESOLVED** — Verified at source: `material-android` 1.12.0 declares insets-aware overloads of
  both `BottomNavigation` (taking `windowInsets`) and `Scaffold` (taking `contentWindowInsets`),
  and the no-insets overloads this code called fall back to `ZeroInsets`. `targetSdk = 37` with no
  `themes.xml` and no `android:theme` means nothing opts out of the API 35+ edge-to-edge
  enforcement, so the defect was live on every Android 15+ device in every tabbed state.
  `ShellBottomBar` now passes `BottomNavigationDefaults.windowInsets` (system bars restricted to
  horizontal + bottom, so a rotated device's side gesture bar is handled too) and `TabbedShell`'s
  `Scaffold` passes `ScaffoldDefaults.contentWindowInsets`; `Scaffold` excludes what the bar
  consumes, so the content is not double-padded. `MainActivity` now calls `enableEdgeToEdge()` so
  layout is identical below API 35 rather than varying by release.

  **Amended after on-device verification** — the above was necessary but *not sufficient*, and
  measurement on an emulator (API 36, gesture navigation, 420dpi) caught it. With the insets
  requested but nothing else changed, the tab labels still occupied y=2335–2378 against a
  navigation bar frame of `[0,2337][1080,2400]` — byte-identical bounds to a control build with the
  inset arguments removed, i.e. the fix was doing nothing. A second control with a hard-coded
  `WindowInsets(bottom = 24.dp)` moved the labels to 2272–2315, proving `windowInsetsPadding`
  worked and that `BottomNavigationDefaults.windowInsets` (`systemBars.union(displayCutout)`) was
  resolving to **zero**. Cause: the app declared no `android:theme`, so it inherited the platform
  default *with an action bar*, and the legacy action-bar decor consumes the window insets before
  the Compose content is laid out. Added `res/values/themes.xml` + `values-night/themes.xml`
  (`Theme.Astro`, a no-action-bar platform theme, split day/night because `DayNight` needs API 29
  and this app supports 23), applied via `android:theme` on `<application>`. With both halves in
  place the labels sit at 2272–2315, clear of the navigation bar; the stray action bar is gone; and
  the bar's background still extends behind the gesture area as edge-to-edge intends.
- [medium] Distinguish completed empty navigation from loading (shared/src/commonMain/kotlin/io/jitrapon/astro/presentation/shell/AppShellState.kt:79-82)
  A successful response with no destinations, or only non-NavigateAction destinations, maps to Loading even when isLoading is false. Both apps then show an indefinite spinner with no navigation or recovery action. These responses are permitted by the vendored contract: destinations has no minimum length and accepts other action types. CalendarScreenQuery does not automatically schedule another exchange after a completed response, so waiting cannot resolve this state.
  Recommendation: Represent completed content with no routable destinations as an explicit empty or unsupported-navigation state, render a terminal message on both platforms, and test empty and non-navigating-only responses with isLoading=false.

  **RESOLVED** — Confirmed against the vendored contract: `Navigation` declares `destinations` as
  `required` but sets no `minItems`, and `NavDestination.action` accepts kinds other than
  `NavigateAction`, so the response is legal and the projection did fall through to `Loading`.
  Added `AppShellState.NoDestinations`: a screen that has already arrived carrying nothing routable,
  with no exchange still in flight (`content != null && !isLoading`), is settled rather than
  pending and now projects to it. A failure still wins over it, and an in-flight refresh over a
  destination-less screen still shows `Loading`, since that exchange may yet deliver tabs. Both
  apps render it as a terminal message with no progress indicator — Android
  `NoDestinationsPlaceholder` behind `R.string.app_shell_no_destinations`, iOS an explicit
  `AppShellStateNoDestinations` case ahead of the `default:` branch that previously absorbed it.
  Covered by `aSettledScreenWithNoRoutableDestinationsHasNowhereToGo` (both the non-navigating-only
  and the empty-array responses at `isLoading = false`) and
  `aScreenWithNoRoutableDestinationsIsStillFailedOrLoadingWhenEitherApplies`, plus an instrumented
  case and a preview on each platform.

Next steps:
- Fix inset handling and verify the actual Android activity with loaded tabs.
- Add a completed-empty shell state and cross-platform rendering coverage.

<!-- previous-rounds:end -->
