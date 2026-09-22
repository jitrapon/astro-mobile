Status: blocking

# Plan Review

> Per-branch working file owned by the `spec-development` skill's plan-review
> loop. Captures the adversarial review of the SPEC §§4–6 plan _before_
> implementation; this file in `main` is an empty skeleton.
>
> The first line records the gate status once a plan review has run:
> `Status: clear` | `Status: blocking` | `Status: overridden`. A `Resolution
log` section accumulates one heading per review iteration.

- **Date:** 2026-09-22
- **Base ref:** `main`
- **Focus sent:** Review the plan in `.claude/SPEC.md` sections 4 (implementation) and 5 (testing)
  against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong
  ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot
  actually verify them, scope drift beyond §§1–3, and any way a regression could land without the
  plan catching it. Do NOT review code — the diff is doc-only (SPEC.md).

## Resolution log

### Round 1 — 2026-09-22 · verdict `needs-attention`

**Finding 1 [high] — "Wire delivered actions into actual interaction paths."**
Disposition: **PARTIAL.**

Two claims, one false and one valid.

*False:* "no step preserves event actions in render models, connects chip/card taps." The contract
attaches an `Action` to exactly one schema — `NavDestination` (`contracts/astro-bff/openapi.yaml`
line 491 is the file's only `action:` key). `CalendarEvent` carries `EventPermissions`, not an
action. There are no delivered event actions to preserve.

*Valid, and a real gap the plan missed:* `toAppShellState()` drops every destination whose action is
not `NavigateAction` (`AppShellState.kt:109`), and `tasks/M-2.md` names routing the other kinds as
this branch's work. The plan built an action model while leaving the single place the contract
delivers actions still discarding four of the five types.

Addressed by: new §4 item **B7a** (replace `AppShellTab.targetScreenId` with the destination's
`action`, rewrite the projection's rules, keep order and first-occurrence-wins), **C4a** and the C4 /
C7 rewrites (tab selection dispatches rather than assuming navigation; event taps construct
`OpenEventDetailAction` / `PresentModalAction` client-side, which is what makes those two types
reachable given the contract delivers neither), and paired §5 items **B7a**, **C4**, **C4a**. The
design note at the top of §4 now states the contract fact so the false half cannot be re-raised as a
gap.

**Finding 2 [medium] — "Replace iOS build-only checks with behavioral verification."**
Disposition: **PARTIAL.**

The concern is correct: C6/C7's original checks were `xcodebuild` + swift-format + SwiftLint +
previews, none of which fails on a missing registry entry or an ignored preference. The recommended
remedy is disproportionate: `iosApp.xcodeproj` declares exactly one target and it is the application
— there is no Swift test target. Creating one and running `xcodebuild test` needs a **booted**
simulator on the `macos-latest` runner, where today's gate deliberately uses a generic destination
with `ARCHS=arm64` precisely to avoid that cost, and nothing in §§1–3 asks for iOS test
infrastructure.

Addressed by strengthening the checks without adding a macOS CI surface: a `commonTest` assertion
that `CalendarComponentIds` covers exactly the ids the embedded contract declares (so a registry gap
is a named failure on a host-portable runner rather than a silent fallback), plus explicit runtime
verification through the repo's existing `ios-device-debug` loop for the registry cases, the
switcher, a URL-opening tab and an event tap. C6 now records that the Swift test target is out of
scope and why.

**Finding 3 [medium] — "Verify the DI constraints that runtime resolution cannot detect."**
Disposition: **AGREE.**

Correct as stated: `DependencyGraphResolutionTest` and `verifyFrameworkHeaderSurface` prove
resolution and header cleanliness, neither of which can see an opaque `List<Module>` parameter or a
qualified same-typed binding. Addressed by rewriting §5 item **B8** into mechanical checks — a grep
asserting every remaining `named(` qualifier is an inline string literal and that this branch adds
none, plus a recorded trace from each added binding to both platform graph starts through plain
composition. Compiler-plugin adoption stays out of this branch, as §3 already requires.

**Codex's "commit the completed plan" next step** is satisfied by this iteration's commit; §§4–5
were uncommitted working-tree changes when the review ran, which is the normal shape of this gate.
