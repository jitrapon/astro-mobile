# Specification: Mobile navigation, app shell, SDUI registry, and CalendarViewModel

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
issues: [133]          # issue numbers in THIS repo that merging this branch closes
completes: no          # does merging this branch finish the whole task row?
spec-objective: -      # section 2, collapsed to one line
```

## 1. Overview

M-2 is the mobile lane's first product work since the shared data layer landed. It builds the app's
navigation and shell, the server-driven UI component registry and action model, and the view model
that sits between the shared data layer and the platform UIs — replacing placeholder screens on both
Android and iOS. All three prerequisites are cleared: the API client and contract models exist, CI
compiles the iOS app scheme, and the SBOM-based SCA gate is already in place.

## 2. Objective

Ship a navigable app shell on both platforms whose bottom navigation is driven by the contract's
semantic nav destinations rather than a hardcoded list, backed by an SDUI component registry and a
`CalendarViewModel` written against the shared observation seam. Done means both platforms navigate
between placeholder screens over that machinery, the release build's shrinker behavior is verified
rather than assumed, and the open question of how SwiftUI consumes the shared state flow is decided
and implemented.

## 3. Requirements & Context

**Scope, in five ordered steps.** Steps 1–3 are the critical path; 4–5 may be split into a follow-up
branch if this one runs long.

1. Navigation framework and app shell, with bottom navigation rendered from the shell tree's
   semantic nav destinations (Calendar, Agenda) and placeholder screens behind each.
2. SDUI component registry plus the action model, consuming `resolvedPreferences` from the response
   envelope.
3. The observation layer and `CalendarViewModel`.
4. Decide and implement how SwiftUI collects the shared state flow (plan decision D-24).
5. Address the release-shrinker keep-rule gap (issue #128) before the registry ships.

**Architectural constraints (load-bearing):**

- **Business logic stays in the shared module.** UI stays out of it — Compose in the Android app,
  SwiftUI in the iOS app. The shared module exposes platform-agnostic models and logic only.
- **The observation seam is settled and must be built as specified.** Per the mobile server-state
  ownership ADR, the layer is **hand-rolled in `commonMain`; Store5 is not a dependency** (decision:
  Option A). The repository gains observation, refetch, and invalidation alongside the existing
  one-shot fetch, and `CalendarViewModel` is written against the observation seam **from its first
  commit** — this is the ADR's stated requirement, not a preference.
- **The query state carries two independent axes**: which of pending / loaded / failed applies, and
  whether an exchange is currently in flight. A refresh over existing data must be able to paint
  content and a progress indicator simultaneously rather than falling back to a blank skeleton, and
  a failed refresh must be able to keep showing the last screen that loaded.
- **No library type may reach the iOS framework's public surface.** Neither the DI framework nor the
  HTTP client may appear in the generated framework header; Swift call sites bind only to
  project-owned types.
- **Preserve the existing `Result<T>` error-handling contract.** Cancellation is deliberately not
  modelled as an error — it propagates.
- **D-24 — do not adopt SKIE on this branch** without re-opening the decision. The recommended
  answer is a hand-rolled adapter in the iOS source set. SKIE is recorded as the upgrade path, to be
  re-opened at M-3 when multiple SwiftUI screens make the lack of sealed-interface exhaustiveness in
  Swift an actual cost.
- **Store5 is a reference implementation, not a dependency.** Read it fresh at the commit pinned in
  the ADR, adopt its deduplication mechanisms, and transpose its two 2026 race fixes into tests here
  before they can be written a second time. Cite the origin in a comment wherever a non-obvious
  mechanism came from it; a file that ends up closely following its source carries an Apache-2.0
  notice.

**Verification constraints:**

- Coroutine tests must settle under the virtual clock on **both** targets — this was a deciding
  factor in the ADR's choice and must not regress.
- Assembling the release variant proves only that the shrinker completed, not that the shrunk app
  runs. The registry's polymorphic serialization is exactly what full-mode shrinking strips when no
  keep rule holds it, and the failure is **release-only and runtime-only**. Close that gap by
  running the release variant, not just building it.
- This is the first branch to put real coroutine and Flow code in the common source set, which arms
  lint rules that have had nothing to match until now. Several Flow and Channel rules currently sit
  at zero weight tagged unverified; expect some to fire. Per the project's own protocol, a rule is
  promoted to the blocking tier only after a probe shows a true positive and no false one.
- The repo forbids the `@Suppress` escape hatch and detekt baselines. A finding is cleared by
  refactoring.

**Out of scope:**

- The month grid, agenda, time-grid and year views (M-3, M-4, M-6, M-7) — this task ships
  placeholders.
- The real backend base URL, Google login, and live data (M-5).
- First-run connect and syncing states (M-8).
- Persistence or a database, and the write path — both deferred by the ADR.
- Completing the no-`@Suppress` gate (issue #109), deliberately sequenced after this branch, since
  this branch is the probe that settles the rule tiers that gate would have to encode.

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

- https://github.com/jitrapon/astro-mobile/issues/133
- https://github.com/jitrapon/astro-mobile/issues/128
- https://github.com/jitrapon/astro-mobile/issues/115
- https://github.com/jitrapon/astro-mobile/pull/117
- https://github.com/jitrapon/astro-mobile/issues/116
- https://github.com/jitrapon/astro-mobile/pull/122
- https://github.com/jitrapon/astro-mobile/pull/123
- astro-plans `ADR-mobile-server-state-ownership` — the observation seam, the Option A decision, and the implementation skeleton scheduled for M-2 step 3
- astro-plans `ADR-mobile-di-framework` — the guardrail keeping library types off the iOS framework's public surface
- astro-plans `current-plan.md` decision D-24 — how SwiftUI consumes the shared state flow
- https://github.com/MobileNativeFoundation/Store — read as a reference implementation, not added as a dependency
- https://touchlab.co/skie-swiftui — SKIE, recorded as the D-24 upgrade path
