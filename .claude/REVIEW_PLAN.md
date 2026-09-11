Status: clear

# Plan review — `.claude/SPEC.md` §§4–5

- **Branch:** `135-shared-calendar-screen-observation-viewmodel`
- **Base ref:** `main`
- **Focus sent:** Review the plan in `.claude/SPEC.md` sections 4 (implementation) and 5 (testing) against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot actually verify them, scope drift beyond §§1–3, and any way a regression could land without the plan catching it. Do NOT review code — the diff is doc-only (SPEC.md).

## Round 1 — 2026-09-11

Verdict: **needs-attention** — 3 high, 1 medium.

> Do not ship this plan yet. The working-tree SPEC contains invalidation and cancellation gaps that its proposed tests can miss, plus explicit scope drift.

### Findings

1. **[high] Invalidation has no protection against pre-invalidation responses** (§4.6–7). An exchange may survive invalidation and write its response into cache and observed state. A mutex protecting the tables orders writes but does not establish whether one is still valid; the §5.7 no-corruption assertion can pass while invalidated data is republished.
2. **[high] The cancellation regression check can pass with broken deduplication** (§4.4/§5.4). If the map entry is removed when a cancelled *waiter* exits while the exchange stays alive, the next caller starts a duplicate. Both duplicates completing still satisfies "resolves rather than hangs".
3. **[high] The invalidation test does not verify existing subscribers refresh** (§5.7). §4.7 promises state reset so observers re-fetch, but the test only checks the *next* observation. An eviction-only implementation passes while an already-open screen stays stale indefinitely.
4. **[medium] The plan adds platform UI work explicitly excluded by the overview** (§4.12). Replacing `ContentView`'s fetch and state handling contradicts §1's exclusion of platform UI, and its paired §5.12 check only compiles the app.

### Resolution log — round 1

| # | Disposition | Rationale |
|---|---|---|
| 1 | **AGREE** | Verified against the ADR: its appendix deliberately never cancels an in-flight fetch, and its test 10 covers only "the next observation re-fetches". A response crossing an invalidation is unaddressed by the design, not merely by this plan. §4.7 now requires a per-key generation guard and §5.7 two deterministic cases for it. |
| 2 | **AGREE** | Verified at ADR line 454: *"remove it from the map inside the same lock, from a `finally`"* — it does not say **whose** `finally`, and the per-caller reading orphans a running call. ADR test 13 provably cannot distinguish the two. §4.4 now pins removal to the shared `Deferred`'s completion with an identity check; §5.4 now asserts **exactly one** invocation across the interleaving. |
| 3 | **AGREE** | The ADR's own prose ("resets their state, so anything observing re-fetches") is stronger than its test 10. The test list under-tests the design. §5.7 now keeps a collector attached across the invalidation and forbids re-invoking `observeCalendarScreen`. |
| 4 | **PARTIAL** | The scope concern is fair, but deleting the Swift call site is worse than keeping it: CLAUDE.md records the `xcodebuild` app build as the *only* gate that type-checks Kotlin-declared symbols at Swift call sites, so an adapter with no Swift caller is unverified at precisely the boundary D-24 exists to settle. Kept as the smallest compiling call site, explicitly labelled boundary verification with the shell deferred to #136; the behavioural coverage Codex asked for moved into §5.11 (`iosTest`, runs on the simulator) rather than resting on the compile gate. §§1–3 are user prose and were not edited. |

All four findings were checked against the repo before disposition: `MockCalendarBackend.respondTo` is already suspending, so every interleaving case above is constructible with the existing harness and needs no new test infrastructure.

## Round 2 — 2026-09-11

Verdict: **approve** — no material findings.

> The revised plan closes the prior invalidation, deduplication, and live-subscriber findings. The minimal Swift call site plus simulator adapter tests adequately addresses the boundary-verification concern. No remaining material blocker is supported by this doc-only review.

### Resolution log — round 2

All four round-1 findings confirmed closed. Findings 1–3 were folded into §§4.4, 4.7, 5.4, 5.7 as
accepted; finding 4's PARTIAL disposition — keeping the smallest Swift call site while moving
behavioural coverage into §5.11 — was accepted rather than re-raised.

No §§1–3 / §7 prose was edited in either round, so no user-prose escalation was needed.
