Status: blocking

# Plan Review — `116-ci-xcodebuild-step-verify-ios`

Adversarial review of `.claude/SPEC.md` §§4–5 (plan only; the diff is doc-only).

- **Date:** 2026-08-27
- **Base ref:** `main`
- **Focus sent:** Review the plan in .claude/SPEC.md sections 4 (implementation) and 5 (testing)
  against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong
  ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot
  actually verify them, scope drift beyond §§1–3, and any way a regression could land without the
  plan catching it. Do NOT review code — the diff is doc-only (SPEC.md).

## Round 1 — verdict `needs-attention`

> No-ship: the plan does not prove the CI gate executes, does not verify partition stability against
> the pre-change task set, and leaves simulator-framework reuse and documentation scope unresolved.

| # | Sev | Finding |
| --- | --- | --- |
| 1 | high | A green CI job may not prove the new build ran (§5.4). YAML parsing, actionlint, and a green `verify-ios` job prove neither that the xcodebuild step executed nor that the YAML command equals the settled item-2 command. |
| 2 | medium | Partition validation cannot prove the partition was undisturbed (§4.5/§5.5). Running the *current* guard and `check` passes even after a symmetric change to both sides; nothing compares against `main`. |
| 3 | medium | The ordering does not define simulator-framework reuse (§4.4). xcodebuild is placed after `linkReleaseFrameworkIosArm64` — a device-release artifact a simulator build cannot use. The app's build phase resolves `$(SDK_NAME)` and triggers its own Gradle framework build, so an extra Kotlin/Native link is implied but unstated, against the macOS-cost constraint. |
| 4 | medium | The no-API-change constraint has no acceptance check (§3). Nothing requires that the Kotlin/Swift public surface is unchanged, so an implementation could alter the facade to make the app compile and still pass every check. |
| 5 | low | Documentation scope is contradictory (§4.6/§5.6 vs §7). §4.6 mandates a `.claude/CLAUDE.md` edit while §7 remains an unfilled placeholder, leaving the doc deliverable outside declared scope. |

## Resolution log

_Round 1 dispositions are recorded under the round-2 heading once evaluated._
