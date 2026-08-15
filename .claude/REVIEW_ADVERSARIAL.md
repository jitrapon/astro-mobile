# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-08-15

- Base ref: main
- Focus sent to Codex: This branch (M-1 part 2) builds the shared module's real networking/serialization/DI stack: Ktor client 3.x with OkHttp/Darwin engines, kotlinx.serialization JSON, a Koin graph started from both platforms via initKoin/an iOS Kotlin facade, hand-written data models for the BFF calendar-screen contract at schemaVersion 0.2.0, a CalendarScreenApi + stateless CalendarScreenRepository returning the sealed Result<T>, and a vendored OpenAPI contract with a parity gate plus a build-time generator that embeds contract facts into commonTest. It also removes the POC login/greeting types and rewrites the iOS entry point. Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues (especially CancellationException handling at the Ktor boundary), null handling, state-management bugs, and missing cross-platform test coverage.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention — **all 5 findings closed**: 3 resolved, 1 deferred (#116), 1 not an issue. `./gradlew check` green.

Findings:
- [high] Theme-document identity invariant is never enforced (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenApi.kt:46-49)
  The contract requires themeDocument.id/version to match the envelope theme before a client applies it, but the fetch path only checks schemaVersion and returns the decoded response as Success. A bad or inconsistent BFF response can therefore hand a light theme document to a response declaring dark (or vice versa), allowing downstream UI to apply incorrect tokens.
  Recommendation: Validate themeDocument against theme immediately after decoding; return Result.Error (or discard the document) on mismatch, and add a mismatch exchange test.

  **RESOLVED** — commit `1025af0`. Confirmed valid: `openapi.yaml:435-443` states the client MUST verify the pair, and `CalendarScreenResponse.kt:41-43` already claimed the client did — but `CalendarScreenApi` never implemented it. Added `withoutMismatchedThemeDocument()` beside the existing schema-version guard on the same decode path. A mismatch **discards the document** rather than failing the fetch, which is the contract's own prescribed behaviour ("MUST discard the document and fall back, never apply it") and leaves the still-trustworthy theme reference intact. Two exchange tests added; the mismatch case was verified to fail with the guard removed.

- [high] Two accepted request views are guaranteed to fail response decoding (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarBody.kt:13-15)
  The public request model exposes timegrid and year, and the client sends both contract values, but CalendarBody implements only month and agenda. A conforming response for either omitted body discriminator fails deserialization and becomes Result.Error, so valid user-selected views cannot load.
  Recommendation: Implement the time-grid and year body branches before exposing those request views, or restrict the public request/view-switcher surface to the actually supported views.

  **NOT AN ISSUE** — the asymmetry is real but deliberate, and the recommendation contradicts required scope. SPEC §3 caps model coverage at the month and agenda view-models; `CalendarBody`'s KDoc names the decode failure as "the intended signal until those views are built", which beats an `UnknownBody` fallback that would return `Result.Success` with an empty screen. Restricting the request surface would delete `TimeGrid` — the only carrier of `dayCount` — and with it the `dayCount` bounds, conditional-omission, and wire-format assertions three SPEC items require. Unreachable today: no view switcher or view model exists to construct such a request, and the first caller who can is the author adding the missing body branch.

  Partially addressed anyway (commit `7f2f85b`): the limitation was documented only on `CalendarBody` (response side), while whoever trips over it is reading `RequestedCalendarView` (request side). Added a KDoc cross-reference there. Documentation only.

- [high] Android’s only configured backend origin is cleartext HTTP (androidApp/src/main/java/io/jitrapon/astro/AstroApplication.kt:25-29)
  The app targets SDK 37 and points the production Application initialization at http://10.0.2.2. The manifest does not opt this app into cleartext traffic, so Android’s cleartext policy blocks these requests on modern devices/emulators; every fetch becomes a transport error even with a running local BFF.
  Recommendation: Use an HTTPS/configuration-driven endpoint for the shipped build. If local HTTP is deliberately needed, scope an explicit cleartext exception to a debug-only network-security configuration and exercise it on-device.

  **RESOLVED** — commit `d7a2bd8`. Confirmed valid: targetSdk 37 with no `usesCleartextTraffic` and no network-security config meant the development placeholder could not serve its own stated purpose. Took the recommendation's second branch, which is also what `.semgrep/astro-mobile.yml:64-66` prescribes: a `src/debug` network-security config scoped to `10.0.2.2` / `localhost` / `127.0.0.1`, plus a debug manifest overlay pointing at it. Verified from the merged manifests — debug carries `networkSecurityConfig`, release carries none, so a shipped build still sends cleartext nowhere.

- [medium] SwiftUI converts cancellation back into a visible fetch failure (iosApp/iosApp/ContentView.swift:94-97)
  The Kotlin client deliberately propagates CancellationException, but this catch converts every thrown error, including cancellation from SwiftUI’s .task, into .failed. When a view/task is superseded, its cancelled request can still write “No screen” instead of remaining cancelled, producing stale error state and defeating the cancellation boundary tested in shared code.
  Recommendation: Let cancellation escape or explicitly return without updating state when Task.isCancelled; add an iOS-level test covering cancellation of the view task.

  **RESOLVED** — commit `4e4de39`. `forCurrentMonth()` now returns an optional and answers `nil` on cancellation; the `.task` leaves its last state standing rather than overwriting it. Handles both shapes — an explicit `CancellationError`, and a throw arriving while `Task.isCancelled`, since Kotlin's `CancellationException` does not reliably bridge to the former. The recommendation's iOS-level test was **not** added: `iosApp` has no test target, and standing one up is its own task rather than a rider here. Verified by `xcodebuild -scheme iosApp build` against the real framework.

- [medium] CI never type-checks or builds the rewritten iOS app (.github/workflows/ci.yml:97-103)
  The macOS job runs Kotlin/Native tests and Swift format/lint, then links the shared framework. It never invokes xcodebuild for iosApp, so Swift type errors at the new Kotlin facade boundary, framework embedding failures, and app-target build regressions can all merge undetected.
  Recommendation: Add an xcodebuild build/test step for the iosApp scheme and a simulator destination, after generating/embedding the shared framework.

  **DEFERRED → #116** — accurate, but fails proportionality and scope. This branch has a compensating gate: SPEC §5 items 16–17 and 18 both require `xcodebuild -scheme iosApp build` locally, and that command was re-run green after every Swift change here, so the Swift this branch ships is verified. What is missing is protection for *future* changes. Standing up headless `xcodebuild` in CI (shared scheme, runner signing, framework embedding order, added minutes on the ~10x-cost macOS runner) is a self-contained infra task outside the branch's stated scope.

Next steps:
- Block merge until the decoder/view support and Android endpoint policy are resolved.
- Add a real iOS app build plus cancellation coverage to CI.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
