# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-13
- Base ref: main
- Focus sent to Codex: This branch replaces the shared data layer's one-shot calendar-screen fetch with a hand-rolled stale-while-revalidate observation seam (Ticker, ScreenCache, ScreenCachePolicy, SingleFlightRunner, two-axis CalendarScreenQueryState, CalendarScreenQuery with per-key generation invalidation, repository observe/refetch/invalidate), plus CalendarViewModel (stateIn WhileSubscribed on an injected scope), the iOS CalendarScreenObserver adapter with an idempotent-cancel subscription delivering on a main-thread scope, Koin wiring, and a framework-header guard task keeping Koin/Ktor/coroutines/query-layer types off shared.h. Scrutinize races between invalidation, refetch and in-flight exchanges, cache-key correctness, and the Swift adapter's delivery/cancel semantics. Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, and missing cross-platform test coverage. Additional focus: this is round 2 — round 1's fixes moved exchange publication and remembered-screen read+publish inside CalendarScreenQuery's guard mutex, and hosted refetchScreen on the query scope with join(); check those changes for lock-ordering/deadlock risk (guard -> ScreenCache mutex, guard held across suspending cache calls), newly introduced races, and cancellation behaviour. Known and deferred (#139, do not re-raise): a subscriber arriving during an in-flight refetch publishes a FRESH remembered screen with isFetching=false.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

**Round status (2026-09-13):** 1 finding — RESOLVED. No open findings.

Do not ship yet: same-generation exchanges can still publish out of order and restore older content. Read-only review; tests were not run.

Findings:
- [high] Publish once before releasing the single-flight exchange (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenQuery.kt:249-254)
  **RESOLVED** — publication moved out of the waiters and into the exchange's own work: `fetchAndRemember` became `fetchRememberAndPublish`, which checks the generation, writes the cache and publishes the delivered screen or failure in one `guard` section, and `exchangeAndPublish` now only joins the exchange. `SingleFlightRunner` releases a key only after that work returns, so an exchange's answer is published before a later same-generation exchange can start — ordering by construction, and one publication per round trip instead of one per waiter. `refetchScreen`'s KDoc was corrected to the reason it still runs on the query scope (a caller cancelled between `markFetching` and the exchange starting). No deterministic regression: the window sits between the runner's key release and a waiter's resumption with no suspension point to hold; `testAndroidHostTest` (89) and `iosSimulatorArm64Test` (97) pass.

Next steps:
- Fix exchange publication ownership and run the ordering and cancellation regressions on both targets.

<!-- previous-rounds:start -->

## Previous rounds

### 2026-09-13 — base main
- Status when archived: 2 resolved in 7fb5b94, 08f912f; one split finding resolved in 00952ae with its isFetching half deferred → #139; round recorded in dd18593
- Base ref: main
- Focus sent to Codex: This branch replaces the shared data layer's one-shot calendar-screen fetch with a hand-rolled stale-while-revalidate observation seam (Ticker, ScreenCache, ScreenCachePolicy, SingleFlightRunner, two-axis CalendarScreenQueryState, CalendarScreenQuery with per-key generation invalidation, repository observe/refetch/invalidate), plus CalendarViewModel (stateIn WhileSubscribed on an injected scope), the iOS CalendarScreenObserver adapter with an idempotent-cancel subscription delivering on a main-thread scope, Koin wiring, and a framework-header guard task keeping Koin/Ktor/coroutines/query-layer types off shared.h. Scrutinize races between invalidation, refetch and in-flight exchanges (stale publishes, lost refreshes, never-completing flows, cancellation modelled as error), cache-key correctness, and the Swift adapter's delivery/cancel semantics. Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, and missing cross-platform test coverage.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

**Round status (2026-09-13):** 3 findings — 2 RESOLVED (#1 `7fb5b94`, #3 `08f912f`), 1 split: snapshot overwrite RESOLVED (`00952ae`), isFetching-during-refresh DEFERRED → #139. No open findings.

Do not ship yet: publication races can restore obsolete screens, and cancelling a refresh can leave observers loading indefinitely. Findings are from read-only diff inspection; tests were not run.

Findings:
- [high] Make generation validation and response publication atomic (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenQuery.kt:223-226)
  **RESOLVED** — `exchangeAndPublish` now checks `observed.isCurrent(key)` and publishes the delivered screen / failure inside one `guard.withLock`, the same lock `invalidateScreens` holds across eviction and the generation bump, so an invalidation can no longer land between a passing check and the publication. No deterministic regression is possible (nothing suspends between the check and the publish, so a single-threaded test dispatcher cannot interleave them); the fix is structural and the existing refresh/invalidation suite passes on both targets.
- [high] Prevent cached snapshots from overwriting newer publications (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenQuery.kt:180-187)
  **RESOLVED (snapshot overwrite)** — `serveAndRefresh` now reads, classifies and publishes the remembered screen inside `guard.withLock`, the lock `invalidateScreens` holds across eviction and the generation bump, so a snapshot read before an invalidation is published before that invalidation's refresh can publish its replacement (lock order guard → cache matches the existing `fetchAndRemember` write). Structural fix; no regression test, since reproducing the window needs a suspending cache double the fixture deliberately does not use.
  **DEFERRED → #139 (isFetching lowered by a subscriber during refresh)** — valid: `serveAndRefresh` publishes a FRESH remembered screen with `isFetching = false` while a `refetchScreen` exchange for the same request is still in flight. Deferred on impact and v1 necessity: content stays correct and the indicator hides early only until `publishDelivered` lands, and `refetchCalendarScreen` has no production caller yet. The proper fix (a per-`ObservedScreen` in-flight exchange count deriving `isFetching`) is attached to plan task M-8, the first task that both calls refetch and paints from the flag (astro-docs#21).
- [high] Keep refresh publication alive when its caller cancels (shared/src/commonMain/kotlin/io/jitrapon/astro/data/calendar/CalendarScreenQuery.kt:121-124)
  **RESOLVED** — `refetchScreen` now hosts find-or-create, `markFetching` and `exchangeAndPublish` on the query's own `scope` and only `join()`s it, so cancelling the caller abandons the wait and the answer is still published to every remaining observer. Regression `CalendarScreenQueryRefreshTest.aRefreshWhoseCallerIsCancelledStillSettlesTheScreenForItsObservers` holds the refresh's exchange, cancels its sole caller, releases, and asserts the observer settles on exchange 2 with `isFetching = false`; it failed before the fix (`UncompletedCoroutinesError` after 1m) and passes on `testAndroidHostTest` and `iosSimulatorArm64Test`.

Next steps:
- Fix publication ownership and synchronization, then run deterministic race and cancellation regressions on JVM and iOS.

<!-- previous-rounds:end -->
