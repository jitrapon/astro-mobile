Status: blocking

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

## Resolution log

## Override justification

_None._
