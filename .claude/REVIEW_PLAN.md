Status: clear

# Plan Review

## Iteration 1 — 2026-09-14
- Base ref: main
- Focus sent to Codex: Review the plan in .claude/SPEC.md sections 4 (implementation) and 5 (testing) against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot actually verify them, scope drift beyond §§1–3, and any way a regression could land without the plan catching it. Do NOT review code — the diff is doc-only (SPEC.md). Context: the two 'Planning decisions' at the top of §4 were confirmed with the user (destinations rendered as the contract delivers them, since the vendored contract makes Agenda a view not a destination; no tabs until a calendar screen loads, verified against the contract fixture because no backend is reachable). Kotlin Multiplatform app: Compose in androidApp, SwiftUI in iosApp, shared presentation state in :shared; iosApp has no XCTest target and CI runs no instrumented Android tests.

### Codex output

Target: branch diff against main
Verdict: needs-attention

The plan can pass every gate while delivered destinations never become reachable tabs in either app.

Findings:
- [high] Exercise successful fixture delivery through both apps' real wiring (.claude/SPEC.md:149-165)
  §5.3 delegates Android state-holder verification to a runtime check that only expects backend failure; §5.7 likewise checks only iOS failure. Projection tests and UI checks supplied with prebuilt tabs bypass the observation-to-shell wiring in §4.3/5/7. Consequently, an implementation that never forwards loaded content could pass every check. On iOS, rendering previews also never verifies that selecting a destination reaches its placeholder. These gaps leave the central acceptance criterion unverified, despite the confirmed decision to validate it using the contract fixture.
  Recommendation: Add a fixture-backed repository or equivalent test seam to the implementation plan, feeding the contract response through each app's actual observation/state-holder and root-shell wiring. Require runtime checks on both platforms for loading-to-loaded transition, delivered tab order, and selecting every tab to reach its matching placeholder. For iOS, specify an executable simulator fixture scenario with recorded interactions, since there is no XCTest target; retain the separate offline failure checks.

Next steps:
- Pair the new fixture-delivery implementation step with explicit Android and iOS runtime acceptance checks.

## Iteration 2 — 2026-09-14
- Base ref: main
- Focus sent to Codex: same scope as iteration 1, plus a summary of iteration 1's PARTIAL disposition and an instruction not to re-raise the declined fixture backend.

### Codex output

Target: branch diff against main
Verdict: needs-attention

Iteration 1 is addressed within the accepted testing boundary. One navigation identity defect remains in the plan.

Findings:
- [medium] Key Android navigation by destination identity (.claude/SPEC.md:123-125)
  §4.4 keys the back stack by target screen id, although §4.1 explicitly identifies destinations by destination id. The vendored contract permits distinct destinations targeting the same screen. Following this plan could alias their navigation entries and display the wrong destination's placeholder. §5.4 only tests fixture-shaped tabs whose screen ids differ, so it cannot detect this failure.
  Recommendation: Use destination id as the navigation key and retain target screen id as route data. Extend §5.4 with two differently labeled destinations sharing a target screen; selecting each must show its own placeholder and selected-tab state.

Next steps:
- Align Android route identity with §4.1 and add the shared-target regression case.

## Resolution log

### Iteration 1 dispositions
- **[high] Exercise successful fixture delivery through both apps' real wiring — PARTIAL.** Valid that no check exercised observation → shell wiring. Not adopted: a fixture-backed repository inside the apps, which is the debug fixture backend the user declined when the planning decisions were confirmed. Addressed instead by moving the whole `CalendarUiState` → shell-state projection into `:shared` (§4.1) with a wiring case driving a real `CalendarViewModel` over the stubbed fixture backend (commonTest, both targets) and one through `CalendarScreenObserver`, the Swift entry point (iosTest) (§5.1); the Android view model is reduced to mapping `CalendarViewModel.state` through that projection (§4.3/§5.3); the Android route test now drives a loading → tabs transition and selects every tab (§4.4/§5.4); iOS previews cover loading, failure and each selected tab (§4.6/§5.6). Remaining unverified glue — the one-line Swift/Kotlin map calls — is compile-checked, consistent with the no-fixture-backend decision.

### Iteration 2 dispositions
- **Iteration 1 [high] — confirmed closed** by Codex ("addressed within the accepted testing boundary").
- **[medium] Key Android navigation by destination identity — AGREE.** Valid: the contract lets two destinations target one screen, and keying by screen id would alias them. §4.4 now keys the Navigation 3 back stack by destination id with the screen id as route data, §4.6 keys iOS tab selection the same way, and §5.4 adds two differently labelled destinations sharing a target screen. No high/critical finding remains, so the gate is clear.

## Override justification

_None._
