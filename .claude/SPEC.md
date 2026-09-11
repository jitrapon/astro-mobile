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
spec-objective: -      # section 2, collapsed to one line
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

<Filled by `spec-development` in plan mode. GitHub-style checkboxes (`- [ ]`), one item per concrete task small enough to finish in a single resume pass.>

## 5. Testing & Validation (for agent)

<Filled by `spec-development` in plan mode. Each item pairs 1:1 with a §4 item: the test/build/lint command that verifies it.>

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
