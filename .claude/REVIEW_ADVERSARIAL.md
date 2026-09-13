# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-13
- Base ref: main
- Focus sent to Codex: This branch replaces the shared data layer's one-shot calendar-screen fetch with a hand-rolled stale-while-revalidate observation seam (Ticker, ScreenCache, ScreenCachePolicy, SingleFlightRunner, two-axis CalendarScreenQueryState, CalendarScreenQuery with per-key generation invalidation, repository observe/refetch/invalidate), plus CalendarViewModel (stateIn WhileSubscribed on an injected scope), the iOS CalendarScreenObserver adapter with an idempotent-cancel subscription delivering on a main-thread scope, Koin wiring, and a framework-header guard task keeping Koin/Ktor/coroutines/query-layer types off shared.h. Scrutinize races between invalidation, refetch and in-flight exchanges (stale publishes, lost refreshes, never-completing flows, cancellation modelled as error), cache-key correctness, and the Swift adapter's delivery/cancel semantics. Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, and missing cross-platform test coverage.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

Do not ship yet: publication races can restore obsolete screens, and cancelling a refresh can leave observers loading indefinitely. Findings are from read-only diff inspection; tests were not run.

Findings:
- [high] Make generation validation and response publication atomic (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenQuery.kt:223-226)
  **RESOLVED** — `exchangeAndPublish` now checks `observed.isCurrent(key)` and publishes the delivered screen / failure inside one `guard.withLock`, the same lock `invalidateScreens` holds across eviction and the generation bump, so an invalidation can no longer land between a passing check and the publication. No deterministic regression is possible (nothing suspends between the check and the publish, so a single-threaded test dispatcher cannot interleave them); the fix is structural and the existing refresh/invalidation suite passes on both targets.
- [high] Prevent cached snapshots from overwriting newer publications (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenQuery.kt:180-187)
  Cache read, classification, and publication run outside the query guard. A new subscriber can read fresh response A, pause while invalidation fetches and publishes B, then publish A with isFetching=false. Because its earlier verdict was FRESH, it starts no exchange, leaving every observer on A. Even without invalidation, subscribing during an explicit refresh publishes the fresh cache with isFetching=false while the network is still busy.
  Recommendation: Serialize cache snapshot validation and publication with exchange publication and invalidation, rejecting superseded snapshots. Derive isFetching from tracked active exchange state rather than cache freshness. Test a delayed cache read across invalidation and a new subscriber during refetch.
- [high] Keep refresh publication alive when its caller cancels (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenQuery.kt:121-124)
  refetchScreen runs exchangeAndPublish in the caller's coroutine. After an existing observation has settled, start an explicit refresh and cancel its caller while the HTTP exchange is held. SingleFlightRunner keeps the exchange alive and fetchAndRemember updates the cache, but the cancelled await never reaches publishDelivered or publishFailure. Existing collectors have no remaining publication worker, so they retain the old screen with isFetching=true indefinitely.
  Recommendation: Host the complete exchange-and-publication operation on the query scope; let refetch callers await it without owning its lifetime. Add a test cancelling the sole refetch caller while an already-loaded observer remains subscribed, then verify that completion updates content and clears loading.

Next steps:
- Fix publication ownership and synchronization, then run deterministic race and cancellation regressions on JVM and iOS.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
