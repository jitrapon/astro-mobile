Status: clear

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

### Round 1 dispositions (applied before iteration 2)

| # | Disposition | Rationale |
| --- | --- | --- |
| 1 | **AGREE** | Correct that a green job is not evidence a step ran. §5.4 now asserts the workflow command equals item 2's byte-for-byte, forbids `if:`/`continue-on-error:` on it, and requires reading the PR log for `** BUILD SUCCEEDED **`. Also added a wall-clock reading, which §3's macOS-cost constraint had no acceptance point for at all. |
| 2 | **PARTIAL** | The reasoning is sound but the recommendation (capture `main`'s task closure and diff it) is disproportionate for a branch that never edits Gradle. Replaced with the assertion that subsumes it: the branch diff must contain no `*.gradle.kts`. With no edit capable of moving a task between halves, running the current guard *is* sufficient. |
| 3 | **AGREE** | Verified against the repo before acting: `linkDebugFrameworkIosSimulatorArm64` and `linkReleaseFrameworkIosArm64` are distinct tasks, so the device-release artifact genuinely cannot serve a simulator build. Item 4 now names the real producer (the app's own build phase), moves the step to immediately after `verifyIos`, and states that the placement is a cache-warmth decision, not a dependency. |
| 4 | **AGREE** | Folded into the same scope assertion as finding 2: the branch diff must contain no `.kt`/`.swift`, checked after item 3's injected error is reverted. This makes "don't change the facade to make it compile" mechanically unfalsifiable rather than aspirational. |
| 5 | **AGREE (escalated)** | §7 is user-owned prose, so it was put to the user rather than self-edited; the user authorized populating it. Verifying the proposal before writing it caught an error in my own draft — `README.md` carries a mirrored command table, so "no README change" was wrong. §7 now names `.claude/CLAUDE.md`, `README.md`, and `CONTRIBUTING.md` (no change expected), and item 6 covers all three. |


## Round 2 — verdict `needs-attention`

> No-ship: the plan can produce a green PR while dropping existing iOS coverage, testing the wrong
> Swift failure, or building against stale generated artifacts.

| # | Sev | Finding | Disposition | Rationale |
| --- | --- | --- | --- | --- |
| 1 | high | §5.4 validates the new step but never asserts `verifyIos` and the release-framework link remain and run; an edit could drop either and still pass. | **AGREE** | Item 4 *reorders* the job's steps, which makes this more than theoretical. §5.4 now asserts the job's full run-step sequence and requires the PR log to show all three steps succeeded. |
| 2 | high | Item 3's facade-break test does not name a concrete Kotlin-facade call, so an ordinary Swift-only error would satisfy it and leave the Kotlin-to-Swift binding untested. | **AGREE** | The strongest finding of the round — it defeats the one item whose entire purpose is proving the gate works. Item 3 now names real call sites (`DependencyGraph.shared.calendarScreenRepository()`, `repository.fetchCalendarScreen(...)`, `DependencyGraph.shared.start(baseUrl:)`, all verified present in `ContentView.swift`/`iOSApp.swift`) and §5.3 now requires the diagnostic to name the Kotlin symbol, not merely a non-zero exit. |
| 3 | medium | Item 2 clears only derived data; the generated framework under `shared/build/` persists and can mask whether this checkout produced the linked framework. | **PARTIAL** | The stale-output mechanism is real and the fix is cheap, so item 2 now clears both. Declined the "run everything from an extracted clean clone" half: a full cold Kotlin/Native build locally is disproportionate when CI *is* a clean checkout — §5.2 now says so explicitly and points at §5.4's log requirement as the authoritative clean-environment evidence. |
| 4 | medium | §5.6's "the two command tables carry the same rows" is unsatisfiable — the tables already differ on `main`. | **AGREE** | Verified and correct; this was a defect in my own check, not a judgement call. `README.md` has "Install Android app"/"Build shared module only"; `.claude/CLAUDE.md` has the CI-aggregate and contract-parity rows. The assertion is now scoped to the newly added row, with the divergence recorded so a later reader does not re-file it. |

## Round 3 — verdict `needs-attention`

> No-ship: the plan can silently drop SwiftLint coverage, and its macOS-cost check records duration
> without defining an acceptance bound.

| # | Sev | Finding | Disposition | Rationale |
| --- | --- | --- | --- | --- |
| 1 | high | §5.4's "assert the full run-step sequence" names no expected sequence and no comparator. Dropping `brew install swiftlint` would keep every named step and still pass, while `swiftLintCheck` self-skips. | **AGREE** | The hazard is real and specific to this repo: the Swift gates are deliberately built to warn-and-skip when their binary is absent, so losing the install step converts a gate into a no-op under a green build. §5.4 now compares the branch's step list against `main`'s and requires equality but for exactly one insertion, plus log evidence the Swift gates ran rather than skipped. |
| 2 | medium | The macOS cost constraint is recorded but not enforced — no baseline, no delta bound, no proof Kotlin compilation was reused. | **PARTIAL** | Accepted the half that has real signal: a duration with no comparator was a fair hit, so §5.4 now compares against the same job on `main` and requires Gradle output showing the framework *link* ran while `verifyIos`'s Kotlin compilation was reused. Declined the hard acceptance budget — hosted-runner variance would make a fixed threshold a flake source, and §3 asks for cost-awareness, not a cost SLO. The plan says to escalate a disproportionate delta rather than auto-fail on a number. |

## Round 4 — verdict `needs-attention`

> Needs one more validation constraint: the plan requires adjacency for cost control but its test
> accepts the new step anywhere in the job.

| # | Sev | Finding | Disposition | Rationale |
| --- | --- | --- | --- | --- |
| 1 | medium | §5.4 required main's step list plus one insertion but never pinned *where* the insertion landed, so placing the build before `verifyIos` or after the release link would still pass. | **AGREE** | Correct, and it defeats the exact property item 4 chose the placement for — the adjacency *is* the cache-reuse decision, so an unpinned count leaves the cost objective unmet with everything else green. §5.4 now requires the inserted step's immediate predecessor to be `./gradlew verifyIos` and its immediate successor `:shared:linkReleaseFrameworkIosArm64`. |

## Round 5 — verdict `approve`

> The plan is now sufficient for implementation. No new substantive defect is supported by the
> provided context; §5.4 pins the insertion between verifyIos and the release-framework link and
> validates the critical steps and logs.

No material findings. Loop terminated: cleared.

## Override justification

None required — the loop reached `approve` on iteration 5 rather than terminating on an override.
Two findings were dispositioned **PARTIAL** on proportionality grounds rather than accepted whole
(round 2 #3, declining a full cold-clone rebuild in favour of clearing generated output and naming
CI as the clean-checkout evidence; round 3 #2, declining a hard numeric duration budget as a
hosted-runner flake source). Both rationales are recorded above and neither was re-raised.
