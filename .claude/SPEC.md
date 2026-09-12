# Specification: Shared calendar-screen observation layer and CalendarViewModel

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
issues: [135]          # issue numbers in THIS repo that merging this branch closes
completes: no          # does merging this branch finish the whole task row?
spec-objective: Make the calendar screen observable from shared code behind a stale-while-revalidate cache, and land CalendarViewModel plus the SwiftUI adapter that collects it.
```

## 1. Overview

First of three branches splitting M-2. It builds the shared layer that observes a calendar screen
over time — a query state, the repository methods that expose it, and the view model that consumes
it — plus the adapter that lets SwiftUI collect it. No navigation, no server-driven UI, no platform
UI work. It is taken first and alone because it is the concurrency-heavy part, its failure modes are
subtle by the governing ADR's own account, and it depends on neither of the other two branches.

## 2. Objective

Replace the data layer's one-shot fetch-only boundary with an observable one, and land a view model
written against that observation seam, such that a calendar screen can be observed, refreshed and
invalidated from shared code and collected from SwiftUI. Done means both platforms can consume the
state flow, the decision on how SwiftUI does so is made and implemented, and the shared tests run
under virtual time on both targets.

## 3. Requirements & Context

**Architectural constraints (load-bearing):**

- **The observation seam is settled and must be built as specified.** Per the mobile server-state
  ownership ADR, the layer is **hand-rolled in the common source set; Store5 is not a dependency**
  (decision: Option A). The repository gains observation, refetch and invalidation alongside the
  existing one-shot fetch, and the view model is written against the observation seam **from its
  first commit** — this is the ADR's stated requirement, not a preference.
- **The query state carries two independent axes**: which of pending / loaded / failed applies, and
  whether an exchange is currently in flight. A refresh over existing data must be able to paint
  content and a progress indicator simultaneously rather than falling back to a blank skeleton, and
  a failed refresh must be able to keep showing the last screen that loaded.
- **The observation flow does not complete.** It is a subscription to one request's state; the
  caller's scope ends it.
- **Business logic stays in the shared module.** The view model lives there; UI does not.
- **No library type may reach the iOS framework's public surface.** Neither the DI framework nor the
  HTTP client may appear in the generated framework header; Swift call sites bind only to
  project-owned types.
- **Preserve the existing `Result<T>` error-handling contract.** Cancellation is deliberately not
  modelled as an error — it propagates, so a superseded request never delivers a failure to a caller
  that has moved on.

**The SwiftUI collection decision (D-24) is in scope and must be settled here**, since this is the
branch that creates the flow:

- The governing ADR settled *what* Swift sees and never *how* Swift collects it.
- Recommended answer: a hand-rolled adapter in the iOS source set.
- **Do not adopt SKIE on this branch** without re-opening the decision. It is recorded as the upgrade
  path, to be re-opened at M-3, when multiple SwiftUI screens make the lack of sealed-interface
  exhaustiveness in Swift an actual cost.

**Store5 is a reference implementation, not a dependency:**

- Read it fresh at the commit pinned in the ADR — nothing here vendors it.
- Adopt its deduplication mechanisms; transpose its two 2026 race fixes into tests here before they
  can be written a second time.
- Cite the origin in a comment wherever a non-obvious mechanism came from it. A file that ends up
  closely following its source carries an Apache-2.0 notice.

**Verification constraints:**

- Shared coroutine tests must settle under the virtual clock on **both** targets — this was a
  deciding factor in the ADR's choice and must not regress.
- This is the first branch to put real coroutine and Flow code in the common source set, arming lint
  rules that have had nothing to match until now. The three KMP common-source-set rules are active
  and blocking. Several Flow and Channel rules currently sit at zero weight tagged unverified;
  expect some to fire. Per the project's own protocol, a rule is promoted to the blocking tier only
  after a probe shows a true positive and no false one.
- The repo forbids the `@Suppress` escape hatch and detekt baselines. A finding is cleared by
  refactoring.

**Out of scope:**

- Navigation, app shell and placeholder screens — branch 2 of the split.
- SDUI component registry, action model and the release-shrinker keep rules — branch 3 of the split.
- The month grid, agenda, time-grid and year views (M-3, M-4, M-6, M-7).
- The real backend base URL, Google login and live data (M-5); first-run states (M-8).
- Persistence or a database, and the write path — both deferred by the ADR, which lists the
  questions that must be answered before a database is chosen.
- Completing the no-`@Suppress` gate, deliberately sequenced after M-2, since this branch is the
  probe that settles the rule tiers that gate would have to encode.

## 4. Implementation Plan and Progress Tracking (for agent)

Order is dependency-driven: the query orchestrator (item 6) needs the clock, cache, policy and
single-flight runner beneath it, and nothing above the repository can be written until its seam
exists. Items 1–5 are independently testable leaves; 6–7 are the concurrency core.

- [x] 1. Add `data/query/Ticker.kt` — an `internal fun interface Ticker { fun readTickNanos(): Long }` plus `MonotonicTicker`, the production implementation reading `TimeSource.Monotonic`. Injected, never read globally, so staleness is drivable from a test scheduler.
- [x] 2. Add the remembering seam in `data/query/`: `CachedScreen` (screen, `storedAtTick`, `storedUnderSchemaVersion`), `interface ScreenCache` (`read` / `write` / `evict(matches)`), and `InMemoryScreenCache` capped at `maxEntries`, dropping the least-recently-written entry when full. **Age alone never evicts** — an aged entry stays readable so it can paint while a fresh one loads.
- [x] 3. Add `data/query/ScreenCachePolicy.kt` — a three-valued `ScreenCacheVerdict` (`FRESH` / `STALE` / `UNUSABLE`), the `fun interface ScreenCachePolicy` taking the *entry* plus `nowTick`, and the default policy: `FRESH` inside the staleness window, `STALE` outside it, `UNUSABLE` when the stored schema version is not the supported one. Document that the `UNUSABLE` branch is inert while the cache is in-memory and becomes load-bearing when a disk implementation lands.
- [x] 4. Add `data/query/SingleFlightRunner.kt` — one `Mutex` over a `MutableMap<K, Deferred<V>>`. Three invariants, each a defect if missed: the work runs on the **layer's own scope** via `scope.async { … }.await()` (never `withContext`, never `inline`, so the caller's context is not captured into a coroutine that outlives it); the entry is removed **inside the same critical section** as completion, never from an async callback; and that removal is wrapped in `withContext(NonCancellable)` so a cancelled caller's `finally` does not throw at `Mutex.lock()` and strand the entry forever. Observers are deliberately **not** ref-counted and an in-flight fetch is **not** cancelled when the last observer leaves. **Removal is driven by the shared `Deferred`'s completion, never by an awaiting caller's exit** — the ADR's "remove it from a `finally`" does not say whose `finally`, and the per-caller reading orphans a still-running call so the next caller starts a duplicate exchange. Removal is identity-*guarded* — the key is released only while the entry filed under it is still this flight — so a late removal cannot evict a newer flight registered under the same key. A conditional guard rather than the `check(existing === deferred)` this item first named: the removal runs in the `finally` that delivers the result, so a throw there would swap a perfectly good result for a bookkeeping failure at every waiter — the one thing the cancellation contract forbids. The flight is `CoroutineStart.LAZY` and started outside the critical section, so its own removal can never wait on the caller that is mid-creation.
- [x] 5. Add `data/calendar/CalendarScreenQueryState.kt` — the public sealed interface (`Pending` / `Loaded(response, servedFromCache, isFetching)` / `Failed(error, lastLoadedResponse, isFetching)`) with `isFetching` declared on the interface itself, so it is an independent axis readable on every case without a downcast. The payload is the whole `CalendarScreenResponse`, not the `CalendarScreen` inside it — the envelope carries the theme, server clock, time zone and locale a renderer needs and the screen does not. Properties are named `response` / `lastLoadedResponse` rather than the `screen` / `lastLoadedScreen` this item first named, which read as `state.screen.screen.title` at a Swift call site. This is the only new name that reaches Swift.
- [ ] 6. Add `data/calendar/CalendarScreenQuery.kt` with `observeScreen(request)`: find-or-create the per-request `MutableStateFlow` under one exclusive `Mutex` and hand it back **before** any network work; ask `ScreenCachePolicy` what to do with the cached entry; set `isFetching = true` on whatever is already showing rather than collapsing to a skeleton; run the exchange through `SingleFlightRunner`; write through to `ScreenCache` **on dispatch**, never inside `CalendarScreenApi`; publish `Failed` with the last loaded response still attached. Cancellation emits nothing and never produces `Failed`.
- [ ] 7. Add `refetchScreen(request)` (exchanges even inside the staleness window) and `invalidateScreens(matches)` to `CalendarScreenQuery`. One map, one `Mutex`, no reader/shared-lock optimisation. Two invariants the ADR's own test list does not pin down, both of which this item must settle:
  - **A publication guard against responses that predate an invalidation.** An in-flight exchange is deliberately never cancelled, so one started before `invalidateScreens` can complete after it and write a screen the server has already said is wrong back into the cache. Carry a per-key generation that invalidation bumps; a response may only publish if its generation is still current, and a request arriving after an invalidation starts a new flight rather than joining an obsolete one. A `Mutex` over the tables does not answer this — it orders the writes, it does not establish whether one is still valid.
  - **Invalidation refreshes live subscribers, not just the next observation.** Eviction alone leaves an already-open screen painting stale content indefinitely, which is exactly the case the seam exists for.
- [ ] 8. Extend `CalendarScreenRepository` with `observeCalendarScreen` / `refetchCalendarScreen` / `invalidateCalendarScreens`, each a thin forward to `CalendarScreenQuery`; leave `fetchCalendarScreen` untouched. Replace the KDoc paragraph asserting the repository holds no state with one stating what the cache now guarantees — chiefly that a response is only ever published under the request that asked for it — and update `CalendarScreenRepositoryTest`'s class KDoc, which currently pins the opposite.
- [ ] 9. Wire the graph in `di/DataLayerModule.kt`: a `CoroutineDispatcher` binding, a `CoroutineScope(SupervisorJob() + dispatcher)` with `onClose { it?.cancel() }`, the ticker, cache, policy, single-flight runner and query; the repository's constructor takes the query. `DataLayerModule.kt` is the **only** file permitted to name a dispatcher. Add the matching `DependencyGraphResolutionTest` assertion for every new binding **in the same commit** — the graph has no compile-time validation, so an unasserted binding has no signal at all.
- [ ] 10. Add `CalendarViewModel` in the shared module, consuming `observeCalendarScreen` and exposing its own UI state. It owns no `CoroutineScope` it did not receive, and exposes no library type.
- [ ] 11. Settle D-24 and add the `iosMain` adapter that lets Swift collect the state flow, plus the `DependencyGraph` facade method that hands it out. A Kotlin `Flow` does not bridge to Swift — only `suspend` functions do — so the adapter converts subscription into something Swift can drive and returns a cancellation handle. Hand-rolled; **do not add SKIE**. Record the decision and its rejected alternative in the adapter's KDoc.
- [ ] 12. Point `iosApp/iosApp/ContentView.swift`'s existing placeholder at the item-11 adapter instead of the one-shot fetch, preserving its discipline: cancellation settles nothing, and the three `CalendarScreenQueryState` cases are matched by downcast the way `ResultSuccess` / `ResultError` already are. **Scope guard:** this is boundary verification, not UI work — §1 excludes platform UI and #136 owns the real shell. It exists only because the `xcodebuild` app build is the one gate that type-checks Kotlin-declared symbols at their Swift call sites, so an adapter with no Swift caller is unverified at exactly the boundary D-24 exists to settle. Keep the edit to the smallest call site that compiles; behavioural coverage is §5.11's, not this item's.
- [ ] 13. Add the mechanical guard that the linked framework header exposes none of this layer's implementation types. **This check does not exist yet** — the invariant is asserted only in prose today — so it must be built, not wired up: link the framework and assert the generated `shared.h` names no Koin, Ktor, `Flow`, or `internal` query-layer symbol.
- [ ] 14. Update `.claude/CLAUDE.md`: the "Key patterns" entry describing `CalendarScreenRepository` as "a **stateless forwarding boundary** today — no cached screen, no refresh policy" becomes false with item 8, and the new `data/query/` package needs naming in the package-layout entry.

## 5. Testing & Validation (for agent)

Every behavioural case lives in `commonTest`, so both the JVM host and the iOS simulator run it.
That is not redundancy: a collection mutated during iteration fails as a catchable exception on the
JVM and as `EXC_BAD_ACCESS` on Kotlin/Native, so **a green host run is not evidence on its own**.

Commands: `./gradlew :shared:testAndroidHostTest` (host), `./gradlew :shared:iosSimulatorArm64Test`
(simulator), `./gradlew check` (full local gate), and the CLAUDE.md `xcodebuild` invocation for the
iOS app target.

- [x] 1. Ticker: a test implementation reading `TestScope.testScheduler.currentTime` makes `advanceTimeBy` move the clock with no sleeping. Verified by items 3 and 6's staleness cases actually settling under virtual time.
- [x] 2. `InMemoryScreenCache`: a write is readable; `evict(matches)` drops only matching entries; exceeding `maxEntries` drops the least-recently-written; **an aged entry is still readable** (age never evicts).
- [x] 3. `ScreenCachePolicy`: `FRESH` inside the window, `STALE` outside it, `UNUSABLE` on an unsupported stored schema version — driven by the test ticker across the boundary in both directions.
- [x] 4. `SingleFlightRunner`: two concurrent `runOnce` calls on one key produce **one** invocation and both receive the same value; two different keys produce two; a cancelled caller does not cancel the shared call; and — the `#740` regression — **cancelling the only caller and immediately re-running the same key resolves rather than hanging**, proving the entry was removed in the same critical section as completion. That case alone is **not sufficient**: a runner that removes the entry on the *caller's* exit also resolves, by starting a second exchange. So hold the original call pending on the suspending responder, cancel its sole waiter, attach a new waiter to the same key, then release — and assert **exactly one** invocation and that the new waiter receives the original call's result.
- [x] 5. `CalendarScreenQueryState` needs no test of its own; it is exercised by every item-6 case. Confirm `isFetching` is readable on all three cases without a downcast.
- [ ] 6. `CalendarScreenQuery.observeScreen`, one test per ADR case: cold emits `Pending` → `Loaded(servedFromCache = false, isFetching = false)`; a second observation inside the window emits `Loaded(servedFromCache = true)` and performs **no** exchange; two concurrent observers of one request cause **one** exchange and see the same screen; two observers of *different* requests cause two exchanges and never see each other's screen; observation after the window emits the stale screen with `isFetching = true` then the fresh one; a failure with no prior screen emits `Failed(lastLoadedResponse = null)`; a failure after a successful load emits `Failed(lastLoadedResponse = <previous>)` and does not blank the screen; cancelling the collector delivers **no** `Failed`.
- [ ] 7. `refetchScreen` exchanges even inside the staleness window; `invalidateScreens { … }` drops matching entries so the next observation re-fetches while non-matching entries survive; and — the `#735` regression — **concurrent observe / refetch / invalidate against the same key do not corrupt the tables**, run on **both** targets, since the Native failure mode is a hard crash rather than a catchable exception. Three cases beyond the ADR's list, each pinning an item-7 invariant that eviction-only would otherwise satisfy:
  - **A live subscriber refreshes.** Keep a collector attached *across* the invalidation and assert it observes a new exchange and updated state **without re-invoking `observeCalendarScreen`**; assert a non-matching subscription emits nothing new.
  - **A response that predates an invalidation cannot publish.** Hold an exchange open on the suspending responder, invalidate its key, let a replacement flight start, then release the original — and assert it repopulates neither the cache nor the observed state.
  - **A post-invalidation request does not join an obsolete flight.** With an exchange held open, invalidate, then observe the same request and assert a *second* exchange is started rather than the stale one joined.
- [ ] 8. Repository: the three new methods forward to the query and nothing more; `fetchCalendarScreen`'s existing cases still pass unchanged; the existing case asserting two different requests get their own answers still holds.
- [ ] 9. `DependencyGraphResolutionTest` resolves every new binding and asserts each is a singleton; the layer's `CoroutineScope` is cancelled when the graph is torn down, asserted the way the existing engine-release case asserts `isActive`.
- [ ] 10. `CalendarViewModel`: it maps each `CalendarScreenQueryState` onto its UI state, stops collecting when its scope ends, and delivers no state after cancellation.
- [ ] 11. The D-24 adapter, in `iosTest` so it runs on the simulator — and this is where the adapter's *behaviour* is pinned, because §5.12 only proves it compiles: a subscription delivers successive states to the callback; the returned handle stops delivery and does not leak the subscription's coroutine; disposing mid-flight delivers nothing further and reports no failure, since cancellation is not an error; and disposing twice is safe.
- [ ] 12. `./gradlew :shared:iosSimulatorArm64Test` green, and the CLAUDE.md `xcodebuild` app-target build compiles `ContentView.swift` against the regenerated framework header — the only gate that type-checks Kotlin-declared symbols at their Swift call sites. **This is a compile gate only**; it cannot catch a runtime subscription or cancellation regression, which is why §5.11 carries the behavioural assertions rather than leaving them to the app build.
- [ ] 13. The new header guard fails when a forbidden symbol is present. Prove it by construction — temporarily exposing a Koin or `Flow` type must turn it red — so the check is known to be capable of failing rather than merely green.
- [ ] 14. `./gradlew check` green end to end: `ktfmtCheck`, `detekt` (including the now-armed `structured-coroutines` Flow rules), `verifyCheckPartition`, and both test targets. **No `@Suppress` and no detekt baseline** — a finding is cleared by refactoring. If a weight-0 `(UNVERIFIED)` Flow rule fires, judge it as a true or false positive and record which; promote it to the blocking tier only on a confirmed true positive with no false one.

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

- https://github.com/jitrapon/astro-mobile/issues/135
- https://github.com/jitrapon/astro-mobile/issues/133
- https://github.com/jitrapon/astro-mobile/issues/136
- https://github.com/jitrapon/astro-mobile/issues/137
- https://github.com/jitrapon/astro-mobile/pull/117
- astro-plans `ADR-mobile-server-state-ownership` — the observation seam, the Option A decision, the Store5 source study, and the implementation skeleton scheduled for M-2 step 3
- astro-plans `ADR-mobile-di-framework` — the guardrail keeping library types off the iOS framework's public surface
- astro-plans `current-plan.md` decision D-24 — how SwiftUI consumes the shared state flow
- https://github.com/MobileNativeFoundation/Store — read as a reference implementation, not added as a dependency
- https://touchlab.co/skie-swiftui — SKIE, recorded as the D-24 upgrade path
