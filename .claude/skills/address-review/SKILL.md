---
name: address-review
description: Per-iteration worker for the adversarial review loop — evaluate and address open findings in `.claude/REVIEW_ADVERSARIAL.md`'s latest round, get user confirmation on verdicts, fix code, commit, and update the review file. Owns the per-iteration mechanics. The user-facing triggers ("run the review", "resume the review") route to `spec-development`'s Review-loop mode, which delegates to this skill via the Skill tool — it owns the surrounding loop (continue / finish-branch decision and the recommendation signals). This skill is still directly invokable via the Skill tool for a one-shot pass without the surrounding loop (e.g. when a user explicitly only wants to address findings without committing to the iterative loop), but has no user-facing trigger phrase of its own.
argument-hint: [--severity <critical|moderate|minor|all>] [--skip-confirm]
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, Agent, AskUserQuestion
---

Evaluate and address adversarial review issues one by one.

## Input

- `$ARGUMENTS` may contain:
  - `--severity <level>`: Only address issues of this severity or higher. Default is `moderate` (addresses critical + moderate, skips minor).
  - `--skip-confirm`: Skip user confirmation and address all fixable issues automatically.

## Evaluation framework — honest assessment, not deferential agreement

**Default mindset: challenge Codex by default.** Adversarial reviews are paid to find edges; their job is to surface every theoretically-possible failure mode regardless of how likely it is to actually occur. Your job is the opposite — decide which findings are *actually worth this branch's effort*. The honest verdict mix on a healthy round is usually a few FIXes and several DEFERs / NOT AN ISSUEs. If you find yourself classifying everything as FIX, you are likely accepting findings deferentially instead of evaluating them.

Run every open finding through these four axes BEFORE assigning a verdict. Use `Grep`/`Read` to verify claims — do not trust Codex's prose for concrete facts:

1. **Validity** — Is Codex's claim actually true? Does the named file/function/path exist? Does the failure mode it predicts match what the code/SPEC actually says? Codex hallucinates symbols and over-reaches scope. Treat every concrete claim as a hypothesis to confirm.

2. **Rarity** — How likely is the failure mode to trigger in production *as it actually exists today*? Adversarial scenarios with vanishingly rare trigger conditions (specific clock skew + specific user action + specific timing window) earn a different verdict from common races. A bug that needs ten unlikely things to align is not a bug that pages on-call next week.

3. **Proportionality** — Does the proposed fix's complexity match the impact of the bug? Adding a 50-line subsystem (new proto fields, server state, client retry contract) to handle a 1-in-10,000 scenario is over-engineering. If the fix is more complex than the bug is consequential, defer.

4. **V1 necessity** — Pre-production services have different risk profiles than mature ones. A bug that requires hundreds of real users to manifest, or that surfaces a UX wart (transient error, stale-for-minutes data healed by the next sync, etc.) versus data corruption, may not need to ship in v1. The token-based sync, the existing convergence guarantees, the production telemetry that doesn't exist yet — all of these factor in.

**Watch for the spiral.** If `Previous rounds` in REVIEW_ADVERSARIAL.md shows that recent rounds have been finding edges in the previous round's fixes (R(n) flags a problem in R(n-1)'s solution; R(n+1) flags a problem in R(n)'s solution), the design is in a diminishing-returns spiral. Consider reverting the recent enhancements + filing them as deferred issues instead of layering another patch. Surface the option to the user explicitly when you see the pattern.

**Default to NOT FIX, not FIX.** A finding should earn its FIX verdict by being valid AND non-rare AND proportional AND v1-necessary. Missing any of those, the honest verdict is DEFER (file as issue so it survives) or NOT AN ISSUE (Codex is wrong on the facts). Both are first-class outcomes, not failure modes.

**Bias toward shipping the simplest contract that works**, not toward closing every Codex finding. The goal of the review loop is a SPEC/codebase you'd be comfortable shipping today, not a SPEC with zero Codex findings.

## Steps

### Phase 1: Read and evaluate

1. **Read the review**: Read `.claude/REVIEW_ADVERSARIAL.md` in full. If the file does not exist, invoke the `codex-review` skill first to generate it, then continue. Stop and tell the user if generation fails.

2. **Identify open issues**: Find all issues that are NOT already marked as `RESOLVED`, `DEFERRED`, or `NOT AN ISSUE`. Group them by severity (critical → moderate → minor).

3. **Evaluate each issue against the Evaluation Framework above.** For each open finding:
   - Run it through the four axes (validity, rarity, proportionality, v1 necessity).
   - Check the spiral signal — if this finding is about an edge in a recent fix, surface that to the user as part of your rationale.
   - Land on one of three verdicts (default-to-NOT-FIX bias applies):
     - **FIX**: Valid AND non-rare AND proportional AND v1-necessary. Apply the fix on this branch.
     - **DEFER**: Valid but fails one or more of rarity / proportionality / v1-necessity. Will be filed as a tracking issue in Phase 3 so it survives the review-file regeneration on future branches.
     - **NOT AN ISSUE**: Invalid (Codex is wrong on the facts, or the claim doesn't apply to what this branch actually does). State the disconfirming evidence.

   Be specific in the rationale — name the trigger conditions, the actual impact (data corruption vs. transient UX wart vs. unobservable), and what would change the verdict (e.g., "would re-evaluate to FIX once real-user telemetry exists").

4. **Present the evaluation**: Show the user a summary table:
   - Issue ID, title, verdict (FIX / DEFER / NOT AN ISSUE), and a one-line rationale citing the axis (validity / rarity / proportionality / v1-necessity) and the trigger conditions
   - List which issues will be fixed and which will be deferred/dismissed
   - If you detected the spiral signal, flag it explicitly: *"Recent rounds have been finding edges in previous rounds' fixes; consider reverting <X, Y, Z> and filing as deferred issues instead of patching further."*

5. **Get confirmation**: Ask the user if they agree with the evaluation or want to override any verdicts. Wait for user response before proceeding. (Skip this step if `--skip-confirm` was passed.) If the user pushes back on a verdict, treat the pushback as authoritative — they have context you don't (real production risk, on-call workload, scope they've already discussed with stakeholders, etc.).

### Phase 2: Address each issue

For each issue marked as **FIX**, in order (critical first, then moderate, then minor):

6. **Implement the fix**: Make the minimal code change that addresses the issue. Follow project conventions from CLAUDE.md:
   - Platform-agnostic logic in `shared/src/commonMain`; `expect`/`actual` for platform abstractions.
   - Kotlin `data class`es for models; the sealed `Result<T>` (`Success`/`Error`) for error handling.
   - Coroutines for async; Compose (Android) / SwiftUI (iOS) for UI, kept out of the shared module.
   - Tests use `kotlin.test` in `commonTest` (cross-platform), or `androidTest` / `iosTest` for platform-specific cases.

7. **Run tests**: Run `./gradlew test` (or `./gradlew clean test` if new files were added; add `./gradlew :shared:iosSimulatorArm64Test` when shared/iOS code changed). All tests must pass. If a test fails because it asserted the OLD behavior that the fix intentionally changes, update the test to match the new behavior.

8. **Update the review**: Edit `.claude/REVIEW_ADVERSARIAL.md` — replace the issue's full description with a short `**RESOLVED**` block stating what was done.

9. **Commit**: Create a git commit for the fix with a descriptive message. Do NOT push unless explicitly asked. Continue to the next issue.

### Phase 3: Wrap up

10. **Update deferred/dismissed issues**: For all issues marked DEFER or NOT AN ISSUE, update their entries in `.claude/REVIEW_ADVERSARIAL.md` with the assessment.

11. **Track DEFERRED findings as GitHub issues**: For each DEFERRED finding, create a tracking issue so it survives the review file being overwritten on a future branch.
    - Confirm with the user before creating issues (issues are user-visible state). Show the proposed title + body for each, and get a single batched approval.
    - Create with `gh issue create --title "<finding title>" --label "deferred-review" --body "<body>"`. Pipe the body via heredoc to preserve formatting.
    - Body should include: original adversarial-review excerpt, deferral rationale, current branch name, and PR # if one exists.
    - Write the issue number back into the `**DEFERRED**` block in `.claude/REVIEW_ADVERSARIAL.md` (e.g. `**DEFERRED → #1234**`) so the audit trail survives even when the file is regenerated on the next branch.
    - If `gh` is unavailable or auth fails, fall back to listing the proposed issues for the user to file manually, and skip writing back issue numbers.

12. **Update the verdict**: If the review has a verdict section, update it to reflect the current state (how many resolved, deferred, not an issue).

13. **Final summary**: Present to the user:
    - How many issues were fixed, deferred, and dismissed
    - GitHub issue numbers created for DEFERRED findings (or a list of titles to file manually if `gh` was unavailable)
    - Any remaining open issues
    - Whether the branch is now merge-ready

    **Stop after the summary. Do NOT prompt for `/finish-branch` or a follow-up `codex-review` here.** When invoked from `spec-development`'s Address-review loop mode (the user-facing path), the surrounding loop owns the next-step prompt (continue with another `codex-review` round vs. wrap up via `/finish-branch`) — duplicating it here would conflict with the loop's signal-based recommendation. When invoked directly for a one-shot pass, the user already knows whether they want another round and will say so. Either way, end the summary with a neutral one-liner like *"Latest round in `.claude/REVIEW_ADVERSARIAL.md` is now: \<state\>. Loop control returns to you."* and stop.

## Important

- Address issues **one at a time** in severity order. Do not batch multiple fixes into one commit.
- If a fix requires clarification from the user, stop and ask rather than guessing.
- If tests fail after a fix, diagnose and fix the test failures before committing.
- Do not push to remote unless the user explicitly asks.
- **Be honest about evaluation, in BOTH directions.** Don't mark issues as NOT AN ISSUE just to reduce work — but equally, don't classify issues as FIX just because Codex flagged them. Apply the four-axis framework (validity, rarity, proportionality, v1 necessity) defined in the Evaluation framework section above. DEFER and NOT AN ISSUE are first-class outcomes when the evidence supports them.
- **Watch for the diminishing-returns spiral.** If recent rounds have been finding edges in the previous round's fixes (rather than new bugs in the original SPEC/code), the design is in a refinement loop without clear ROI. Surface the option to the user: revert the recent enhancements + file them as deferred issues, rather than layering another patch. A SPEC that grows by 50 lines per round to handle increasingly hypothetical edge cases is over-engineering.
- **Pre-production projects ship simpler.** If the service has no real users, no on-call rotation, and no production telemetry, the bar for "what must land in v1" is lower. Many findings that are valid concerns at scale are appropriately DEFERRED until real telemetry justifies them. Note this in the verdict rationale when it applies.
