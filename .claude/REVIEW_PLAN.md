Status: clear

# Plan adversarial review — astro-mobile M-1 scaffold

Date: 2026-06-26
Base ref: main
Branch: 835-set-up-astro-mobile-project-structure
Focus sent to Codex: Review `.claude/SPEC.md` §§4–5 (plan + testing) against §§1–3 and §7 — flag missing steps, wrong ordering, items too coarse for one resume pass, §5 checks that can't verify their §4 item, scope drift, and regressions that could land unnoticed. Doc-only diff (SPEC.md).

## Round 1 — 2026-06-26 (Codex verdict: needs-attention → addressed)

Codex raised three findings. Dispositions:

- **[high] Swift lint/format has no impl/verification path (§3 locks SwiftLint/swift-format).** — **PARTIAL / AGREE.**
  Rationale: Objective §2's "Done" criteria list only the Kotlin toolchain, and the iOS product UI is out of scope with no Swift code to lint, so forcing SwiftLint into the CI gate would be scope drift. But §3 names SwiftLint/swift-format as a locked decision, so the plan must not silently drop it.
  Action: Added **item 5b** — establish `.swiftlint.yml` / swift-format config for `iosApp` and document the command, advisory/inert until iOS product UI lands (mirrors the blessed "Compose Stability Analyzer is inert until M-2" treatment). Tightened **item 4** to scope the "planned"-qualifier removal to the now-wired Kotlin ktfmt/Detekt tasks only, explicitly leaving the iOS line to item 5b.

- **[high] CI objective (§2 bullet 4: green on PR) can be checked off without a real green PR run.** — **AGREE.**
  Rationale: The objective literally requires the GitHub Actions gate to conclude success on this branch's PR; §5 item 14 only ran actionlint + local Gradle + a SHA grep and punted PR-green to finish-branch.
  Action: Added terminal **item 20** — after the PR opens, confirm via `gh pr checks` / `gh run list` that the required CI workflow ran and concluded success (including the iOS shared-test job where feasible); if red, fix and re-verify.

- **[medium] Third-party skill vetting (items 6/7) too subjective to catch architecture regressions.** — **PARTIAL.**
  Rationale: Conflict risk (shared-Compose-UI advice, competing error-result idioms, package-layout drift) is real; a separate signoff-artifact file is disproportionate for two small skill imports.
  Action: Strengthened **items 6/7** to record the upstream source + commit SHA and an explicit "dropped conflicts" note inside the landed skill, and added a mechanical forbidden-terms grep (`Compose Multiplatform`, shared-UI, competing result types) to the paired §5 checks.

All fixes landed inside agent-owned §§4–5; no §§1–3/§7 user prose touched. Re-reviewed in round 2.
