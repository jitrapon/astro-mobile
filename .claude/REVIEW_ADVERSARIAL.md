# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-27
- Base ref: main
- Focus sent to Codex: Branch completes M-2 (3/3): adds the SDUI component registry keyed on the contract's versioned component ids (per-platform registries on Android Compose and iOS SwiftUI with visible fallbacks, over :shared render models that apply resolvedPreferences — chipDensity, chipStyle, weekStart), the action model (all five contract Action types mapped once to ActionEffects, dispatched through CalendarViewModel and the iOS CalendarScreenSubscription, with the shell projection no longer dropping non-navigating destinations, plus client-built event-detail and overflow actions), and closes the release-shrinker gap with app-level R8 keep rules, a minifiedTest build type with generated instrumented-test keep rules, and a CI job running the shrunk app's instrumented tests on a Gradle Managed Device. Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, and missing cross-platform test coverage. Additional focus: whether the R8 keep rules and the minifiedTest variant actually prove the shipping release build decodes polymorphic contract types (i.e. could a test-only keep mask a release-only strip), and the view-switch dispatch race between an in-flight request and a new one.

### Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

Do not ship yet: failed view switches cannot be retried normally, and the minified gate can mask release-only serialization failures. Static review only; tests were not rerun.

Findings:
- [high] Test-only model keeps undermine the release decoding gate (androidApp/build.gradle.kts:459-469)
  **RESOLVED** — Partly valid. The named mechanism does not reach release: release decodes `Action` only nested in `CalendarScreenResponse`, whose generated serializer builds the sealed serializer statically, and the generated test keeps carry no `Companion` / `serializer()` member for any contract model (the reflective root lookup's dependency). The real gap was that `minifiedTest` `-keep`s ~40 contract models whole, exempting them from R8's merging/inlining where `release` does not. Closed with a black-box gate on the shipping shrink: a `releaseLoopback` build type (`initWith(release)`, no keep rule, `minSdk` unchanged; only a `BuildConfig.BACKEND_BASE_URL` of `http://127.0.0.1:18080/api` and a loopback-only cleartext overlay differ) and a self-instrumenting `:androidAppReleaseTest` `com.android.test` module whose `ReleaseContractRenderingTest` serves the vendored fixture from an on-device MockWebServer, launches the app by intent, and asserts via UiAutomator that the delivered destinations render as tabs, delivered event text renders, and no fixture component id appears (the registry fallback). Wired into `verify-android-release`; the zero-tests guard and timeout moved to the root script so they cover both modules; lint/format classified into `verifyAndroidCommon`. Verified: GMD run green (1 test); negative control ignoring kotlinx-serialization-core's consumer rules on `releaseLoopback` turns it red ("The delivered destination \"ปฏิทิน\" never appeared as a tab."); `./gradlew check` green.
- [medium] Allow retrying a view selection after its request fails (shared/src/commonMain/kotlin/io/jitrapon/astro/presentation/calendar/CalendarViewModel.kt:116-118)
  **RESOLVED** — Valid: re-selecting the failed view produced an equal `StateFlow` value, and both shells show tabs over any failure, so the tap was silently dead. `CalendarViewModel` now observes a `RequestObservation` compared by identity; `switchView` builds a fresh one for the same request when the painted state carries a failure with nothing in flight (each new subscription makes `CalendarScreenQuery` exchange again), and changes nothing for a view that loaded or is loading. New `selectingAFailedViewAgainRetriesItAndSelectingALoadedOneDoesNot` (commonTest, JVM + iOS simulator) pins both halves; it fails with the retry branch removed.

Next steps:
- Verify polymorphic decoding with shipping release shrink settings and no test-generated app keeps.
- Implement and test failed-switch retry behavior on the shared dispatch path.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
