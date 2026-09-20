# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-20

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

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
