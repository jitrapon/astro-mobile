---
name: spec-development
description: Plan and drive implementation of a branch's .claude/SPEC.md. Use when the user says "review the spec", "review the plan" (first invocation — plan only), or "resume the spec", "resume the plan", "resume the implementation" (subsequent invocations — execute the next unchecked checklist item and tick it off). Also use when the user says "run the review" (first iteration of the post-implementation review loop, kicks off after all checklist items are ticked) or "resume the review" (subsequent iterations) — orchestrates iterative `codex-review` ↔ `address-review` rounds, gated on user input each iteration. Reads SPEC.md sections 1–3 and 7 to write actionable checklists into sections 4, 5, and optionally 6, then works through them one item at a time.
argument-hint: [optional focus note]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, TaskCreate, ToolSearch, LSP, TaskUpdate, TaskList
---

# spec-development

Branch-scoped spec-driven development. Each branch owns a `.claude/SPEC.md`. The user authors sections 1 (Overview), 2 (Objective), 3 (Requirements & Context), 7 (Documentation), 8 (References). You own the checklists inside sections 4, 5, and optionally 6, and tick them off as you implement.

## Model & effort

This skill assumes **Claude Opus 4.8 at `high` effort** (Claude Code's default — `high` is equivalent to omitting the parameter). One model drives every mode; there is no per-item model assignment. `high` covers planning, implementation, and complex reasoning across all three modes — confirm you're on Opus 4.8 before starting and don't downgrade the model mid-branch.

- **Plan mode** fans out across the repo, verifies Codex's concrete claims with repeated symbol lookups (prefer the Kotlin/Swift LSP tool for source files; fall back to `Grep`/`Read` when no LSP is available or it can't resolve a symbol), and can drive an autonomous review loop of up to 6 iterations — exactly the exploratory, long-horizon agentic profile Anthropic recommends starting at `xhigh` for. If you find the planning reasoning shallow or the review loop stalling, bump effort to `xhigh` for the planning session; otherwise `high` is sufficient.
- **Resume** and **Review-loop** modes work one scoped item / one round per invocation and run fine at `high`. Reserve `xhigh` for an item the SPEC flags as deep multi-file architectural work, and `max` only for genuinely frontier problems where evals show headroom — on most work `max` adds cost for little gain.

## Pick the mode from the trigger phrase

| Trigger phrase                                                                          | Mode             |
|-----------------------------------------------------------------------------------------|------------------|
| "review the spec", "review the plan"                                                    | **Plan**         |
| "resume the spec", "resume the plan", "resume the implementation", "continue the plan"  | **Resume**       |
| "run the review" (first iteration), "resume the review" (subsequent iterations)         | **Review loop**  |

If the trigger is ambiguous, check SPEC.md and `.claude/REVIEW_ADVERSARIAL.md`: empty §§4/5 ⇒ Plan; existing `- [ ]` items in §§4/5 and no `## Latest round` in REVIEW_ADVERSARIAL.md ⇒ Resume; every §§4/5 item is `- [x]` ⇒ Review loop. When still unsure, ask.

The Review-loop mode mirrors the Plan/Resume naming convention: `run the review` is the entry equivalent of `review the plan` (kicks the loop off after implementation), and `resume the review` is the iteration equivalent of `resume the plan` (continues to the next iteration after `codex-review` has produced a fresh round).

## Preflight (both modes)

1. Confirm `.claude/SPEC.md` exists in the current repo. If not, tell the user and stop.
2. Read the file in full. Note the branch name (`git branch --show-current`) so summaries are anchored to the right SPEC.

## Plan mode — "review the spec"

1. Parse sections **1. Overview**, **2. Objective**, **3. Requirements & Context**, and **7. Documentation**. Ignore sections 4–6 (agent-owned — you're about to write them) and 8 (References) for planning input.

   **Assess scope and recommend effort.** Once you've read sections 1–3, judge how hard the planning is: if the objective implies deep multi-file or architectural reasoning, many interlocking invariants, or long-horizon work spanning many files, recommend the user switch to `xhigh` effort for the planning session *before* you draft the checklists (see **Model & effort**). State the recommendation in one line with a brief rationale and let them decide; if the scope reads as a routine, localized change, `high` is sufficient and you proceed without prompting.
2. Draft actionable checklists:
   - **Section 4 — Implementation Plan and Progress Tracking (for agent)**: ordered, concrete tasks. Each item must be small enough to finish in one resume pass and verifiable on its own. Fold documentation updates from section 7 in as real checklist items — not an afterthought.
   - **Section 5 — Testing & Validation (for agent)**: how each section-4 item will be verified. Pair 1:1 with section 4 items where possible (unit tests, integration tests, manual checks, CI gates, lint/build commands).
   - **Section 6 — Deployment**: only fill if section 6 does not already say "Not applicable…". If it does, leave it alone.

3. Use GitHub-style checkboxes: `- [ ] item`. Keep items imperative and specific ("Add the rate-limit middleware to the `/auth/token` route and config key" — not "add rate limiting").
4. Write the checklists back via the Edit tool. Replace the placeholder lines ("To-be-updated as you make changes…") but **preserve every other section verbatim** — including user-authored text in sections 1–3 and 7.
5. **Adversarially review the plan before presenting it.** Invoke `/codex:adversarial-review` via the Skill tool to critique the just-written §§4–5 (and §6 if filled). This is a plan review, not a code review — the diff is doc-only.

   ```
   Skill(
     skill: "codex:adversarial-review",
     args: "--base <default main; override if the user passed one> Review the plan in .claude/SPEC.md sections 4 (implementation) and 5 (testing) against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot actually verify them, scope drift beyond §§1–3, and any way a regression could land without the plan catching it. Do NOT review code — the diff is doc-only (SPEC.md)."
   )
   ```

   Persist the output to `.claude/REVIEW_PLAN.md` (NOT `.claude/REVIEW_ADVERSARIAL.md`, which is reserved for post-implementation code review). Top of file: a date header, the base ref used, the focus string sent, and a `Status:` line — see next step.
6. **Blocking gate on critical findings.** Classify the review output:
   - If Codex reports any **critical** / **high** / **no-ship** finding (or equivalent wording), write `Status: blocking` on the first line of `.claude/REVIEW_PLAN.md`. The plan is **not** ready to implement.
   - Otherwise write `Status: clear`.

   On a re-review iteration, preserve the prior findings as a **Resolution log** section so future readers can see which findings were resolved, partially addressed, overridden, or still open. Don't overwrite history — append a new round heading per iteration.
7. **Commit each review iteration.** Stage `.claude/REVIEW_PLAN.md` plus any `.claude/SPEC.md` edits made during this iteration (always by explicit file name, never `git add -A` / `git add .`). Create a NEW commit (do NOT `--amend`) with a concise message naming the iteration outcome — e.g. `Draft <topic> SPEC plan and capture adversarial review` for the first pass, or `Address adversarial review iteration N — <one-line summary>` for follow-ups. Include the `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` trailer. This guarantees the review loop's intermediate state is preserved in git history before the next iteration mutates it.
8. **Autonomous review loop (when `Status: blocking`).** Do NOT hand back to the user just because the first round was blocking — work the loop yourself until termination (step 9). Per iteration:

   a. **Evaluate each finding against the actual repo, not against Codex's prose.** When Codex names a file, test, function, or claims a path goes through certain layers, verify it: look up the named entity (prefer the Kotlin/Swift LSP tool for source files; fall back to `Grep`/`Read` when no LSP is available or it can't resolve it), confirm the call chain it describes actually exists, check whether the failure mode it predicts is possible given what this branch changes. Codex hallucinates names and over-reaches on scope; treat every concrete claim as a hypothesis to confirm.
   b. **Decide each finding's disposition.** For each finding: AGREE (the finding is valid and lands inside this branch's scope), DISAGREE (Codex is wrong on facts, or the recommendation falls outside §§1–3 / §7 scope, or it asks to test code this branch doesn't change), or PARTIAL (some of it lands). Record the disposition with a one-sentence rationale in the new round heading of REVIEW_PLAN.md's Resolution log. DISAGREE is a legitimate outcome — do not capitulate to a finding you can defensibly rebut. **Apply the same four-axis evaluation framework documented in `address-review`'s SKILL.md (validity, rarity, proportionality, v1 necessity) and watch for the diminishing-returns spiral across iterations** — the criteria for accepting a finding on a plan review are the same as on a code review, and reverting recent SPEC enhancements + filing them as deferred issues is a legitimate disposition when the spiral signal fires.
   c. **Edit only the sections you own.** For AGREE/PARTIAL findings, fold the fix into §4 / §5 / §6 — never §§1–3 / §7 / §8 (user prose). If a finding can only be addressed by changing user prose, escalate to the user (step 9 termination case d).
   d. **Re-run the adversarial review.** Invoke `/codex:adversarial-review` again with the same scope, but bump the iteration number in the focus text and briefly summarize the prior rounds so Codex can confirm closure: *"This is iteration N. Prior rounds flagged (a) … — addressed by item X; (b) … — disagreed because …; …"*
   e. **Commit and rewrite Status.** Same shape as step 7 — new commit, append round to Resolution log, reclassify Status from the new Codex output.
   f. **Loop back to (a) until termination.**
9. **Termination — exit the loop when any of these hold:**

   - **Cleared:** Codex's new verdict is `approve` / `clear` / equivalent. Update `Status: clear`, present to the user with the standard summary (headline findings/counts, commit SHA, pointer to REVIEW_PLAN.md), and end with: *"Plan is unblocked. Want me to `resume the plan` now?"*
   - **Override:** the remaining findings aren't worth addressing — Codex is repeating a previously-rebutted point, the recommendation contradicts §§1–3, marginal correctness gain is lower than the implementation cost, or the finding would force scope drift the architectural constraints explicitly forbid. Edit `Status: overridden`, add a one-line justification in REVIEW_PLAN.md's Override justification section, present to the user with: *"Plan overridden — <one-line reason>. Want me to `resume the plan` now?"*
   - **Iteration cap:** the loop has run 6 iterations without converging or finding a defensible override. Stop, summarize the remaining sticking point to the user, and ask for direction. Don't loop indefinitely.
   - **User-prose blocker:** the only path to closure requires editing §§1–3 / §7 / §8 (user prose). Pause, name the specific sentences the user needs to change, and hand back. Don't ghost-edit user prose to make Codex happy.

   The user can interrupt the loop at any point with a corrective instruction — autonomous does not mean exclusive. Treat any interjection as a steering signal and route accordingly.

## Review-loop mode — "run the review" / "resume the review"

Post-implementation iteration loop. This is the **user-gated** analog of Plan mode's autonomous review loop (steps 8–9): the same `codex-review` ↔ evaluate ↔ fix shape, but every iteration is gated on explicit user input. The loop runs ONLY after all SPEC §§4 / 5 checklist items are ticked (use the Resume-mode completion check in step 10 to surface the entry prompt naturally).

The two trigger phrases parallel Plan/Resume:

- **"run the review"** — the entry trigger, equivalent to "review the plan" but for the post-implementation phase. Use it the FIRST time the loop runs on a branch — when all checklist items are ticked and you're ready to start iterating on Codex's findings. If `.claude/REVIEW_ADVERSARIAL.md` does not yet exist, the loop kicks off by invoking the `codex-review` skill to produce round 1.
- **"resume the review"** — the per-iteration trigger, equivalent to "resume the plan" but for the review loop. Use it to enter each subsequent iteration after `codex-review` has refreshed `.claude/REVIEW_ADVERSARIAL.md` with a new round. One iteration = one user invocation; the loop never advances autonomously.

### Preflight (loop mode)

1. Confirm every SPEC §§4 / 5 item is `- [x]`. If anything is still `- [ ]`, refuse and surface that the loop is premature — the user should `resume the plan` first.
2. If the trigger was **"run the review"** AND `.claude/REVIEW_ADVERSARIAL.md` does not exist OR has no `## Latest round —` heading: this is the loop kickoff. Invoke the `codex-review` skill via the Skill tool to produce round 1, then proceed to the loop body below.
3. If the trigger was **"resume the review"** AND `.claude/REVIEW_ADVERSARIAL.md` has no `## Latest round —` heading: refuse and tell the user to use **"run the review"** instead (kickoff vs. continuation mismatch).
4. Otherwise (latest round exists): proceed to the loop body.

### Loop body — one iteration per user invocation

The loop runs ONE iteration per user invocation. Each iteration is a complete cycle of (evaluate the latest round's open findings → present options → implement approved fixes → recommend next step). The user re-invokes "resume the review" to enter the next iteration after `codex-review` produces a fresh round.

**Step A — Delegate the per-iteration eval-and-fix work to the `address-review` skill.** Invoke it via the Skill tool:

```
Skill(skill: "address-review")
```

`address-review` owns the per-iteration mechanics: read the latest round, evaluate each open finding's verdict (FIX / DEFER → #N / NOT AN ISSUE), present the verdict table via `AskUserQuestion`, implement approved fixes one at a time (severity order, one commit each), update `.claude/REVIEW_ADVERSARIAL.md` with `**RESOLVED**` / `**DEFERRED → #N**` / `**NOT AN ISSUE**` blocks, track DEFERRED findings as GitHub issues, and update the round verdict line. Do NOT duplicate that logic here.

**Step B — After `address-review` returns, decide the loop's next step and surface a continue-or-finish prompt.** This is what makes spec-development's loop mode distinct from a one-shot `address-review` invocation. Use `AskUserQuestion` with two options:

  1. `Continue the adversarial review` — runs the `codex-review` skill to produce a fresh round.
  2. `Run /finish-branch` — wraps up the branch (invokes the `finish-branch` skill).

Set the **first** option's label to the recommendation, suffixed with `(Recommended)`. Pick the recommendation from the signals below.

### Signals — when to recommend `/finish-branch`

Recommend stopping when ANY of these hold (read `.claude/REVIEW_ADVERSARIAL.md`'s Previous rounds to evaluate):

- **Repetition**: the same finding (matched by source-file + symbol-name or by Codex's own wording) has been DEFERRED for two consecutive rounds, OR a finding has been re-raised in three or more rounds without producing a durable fix on this branch. The series-identity hazard in rounds 2–5 of this branch is the canonical example — durable fix requires a schema change explicitly out of scope per SPEC §3 in-scope rules, so re-raising can continue forever without converging.
- **Deferrable-heavy round**: ≥ 50% of the just-finished round's findings classified as DEFER → #N (i.e., they require out-of-scope work that belongs to a future PR, schema change, or design-discussion). One or two genuine FIXes plus a stack of deferrals is the diminishing-returns signal.
- **Narrow fixes**: the just-finished round's FIXes are cosmetic — message text tweaks, docstring rephrases, single-line guard additions that don't change observable behavior, or one-line refactors. If a future regression of the just-landed fix would not be visible to any caller, the fix is too narrow to justify another round.
- **No new findings**: the just-finished round's findings are an exact subset of the prior round's (Codex has hit a fixed point). Continuing produces no signal.

If none of these hold — Codex is still uncovering new substantive findings, the FIXes are landing on real production code, and the DEFER ratio is below 50% — recommend `Continue the adversarial review`.

### Recommendation prompt — exact shape

```
AskUserQuestion(
  questions: [{
    question: "Continue the adversarial review or wrap up the branch?",
    header: "Next step",
    multiSelect: false,
    options: [
      {
        label: "<Recommended option> (Recommended)",
        description: "<one-sentence rationale grounded in the signals above>"
      },
      {
        label: "<Other option>",
        description: "<one-sentence note on the trade-off>"
      }
    ]
  }]
)
```

The rationale must cite the signal: e.g. "Two consecutive DEFER → #973 rounds and the just-finished round was 67% deferrable — likely diminishing returns" or "Round 4 introduced a new high-severity boundary finding and the just-finished round resolved it — Codex is still uncovering substantive issues."

### After the user picks

- **Continue**: invoke the `codex-review` skill via the Skill tool. `codex-review` demotes the current Latest round to Previous rounds, runs Codex, persists the new round, and returns control to the user. The loop pauses; the user re-invokes **"resume the review"** to enter the next iteration. End your turn with a one-line nudge naming that exact trigger so the user knows what to say next.
- **Finish**: invoke the `finish-branch` skill via the Skill tool. Don't run it preemptively even if the user already approved — `finish-branch` itself confirms before pushing / opening PRs per its own contract.

### Rules specific to this mode

- **Never auto-invoke `codex-review` or `finish-branch` from step B.** Both require an explicit user pick from the step-B `AskUserQuestion`. The recommendation is advisory only; always offer both options even when leaning hard toward one. (The preflight kickoff in step 2 is the one exception — there, invoking `codex-review` to produce round 1 IS the documented behavior of "run the review".)
- **One iteration per user invocation.** Do not loop step A → step B → step A inside a single turn — that would be autonomous, which this mode explicitly is not. Each iteration ends after step B and waits for the user's next "resume the review" trigger.
- **Don't override `address-review`'s decisions.** If the user disagreed with a verdict and overrode it in step A's `AskUserQuestion`, treat that as authoritative. The step-B recommendation operates on what landed, not on what you would have proposed.
- **Read `Previous rounds` before recommending.** The signals above (repetition, defer ratio, narrowness, no-new-findings) require comparing the just-finished round against prior rounds. A first-round invocation (the "run the review" kickoff path) never triggers a stop recommendation — there's nothing to compare against yet — unless the first round itself was entirely DEFER and cosmetic.

## Resume mode — "resume the plan"

1. Re-read `.claude/SPEC.md`.
2. **Plan-review gate.** If `.claude/REVIEW_PLAN.md` exists and its first line is `Status: blocking`, refuse to resume. Tell the user the plan is blocked by unresolved critical findings and point them at the file. The user must either address the findings (re-run "review the plan") or mark `Status: overridden` with a justification before resume will proceed. `Status: clear` or `Status: overridden` is fine; absence of the file is also fine (pre-existing SPECs that never ran a plan review).
3. Find the first unchecked item (`- [ ]`) in **section 4**. That is the single next task. Do not bundle later items.
4. Implement it. If the item turns out to need sub-steps, pause and update section 4 to split it, then work on the first sub-step.
5. Verify via the paired item(s) in section 5: run the tests/build/lint the plan specified. Use Bash (`./gradlew test`, `./gradlew :shared:iosSimulatorArm64Test`, `./gradlew build`, etc.) and report actual output.
6. **Only after verification passes**, tick both the section-4 item and its paired section-5 item(s) to `- [x]` via Edit.
7. **Commit the tick.** Stage SPEC.md plus every file modified during this resume pass (code, tests, any supporting docs) — always by explicit file name, never `git add -A` / `git add .`. Write a concise commit message in the repo's existing style that names the SPEC item being satisfied and describes the change in the body. Include the standard `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` trailer. This repo has **no pre-commit hook** — run the formatter (`./gradlew ktfmtFormat`) and linter (`./gradlew detekt`) manually before committing; the CI gate (`./gradlew check`) enforces them. If a check fails after you commit, diagnose the root cause and create a NEW commit (do NOT `--amend`).
8. If verification fails: leave items unticked, do NOT commit, report the failure and the likely root cause, and ask the user how to proceed — do NOT silently retry or weaken the check.
9. Summarize in ≤100 words: what was done, what was verified, the commit SHA, and what's next (the following unchecked item). Stop. The user re-invokes for the next item.
10. **Completion check.** After ticking, scan sections 4 and 5 for any remaining `- [ ]`. If none remain (every actionable item is now `- [x]`):

    a. If `.claude/REVIEW_ADVERSARIAL.md` does NOT yet exist, OR exists but contains no `## Latest round —` heading: append the literal line *"All SPEC items are ticked. Say `run the review` to kick off the review loop — it'll run `codex-review` to produce round 1, then evaluate / fix / recommend a next step."* Do not enter the loop yourself; wait for the user's explicit trigger.

    b. If `.claude/REVIEW_ADVERSARIAL.md` exists AND has at least one round AND every finding in the latest round is marked `**RESOLVED**`, `**DEFERRED → #N**`, or `**NOT AN ISSUE**` (no open findings remain): append the literal line *"All SPEC items are ticked and the adversarial review has no open findings. Want me to run `/finish-branch` to reset agent working files to skeleton, push, and open the PR?"* Do not invoke `/finish-branch` yourself; wait for the user's explicit yes.

    c. If `.claude/REVIEW_ADVERSARIAL.md` exists with open findings (any finding without one of the three closed markers above), append: *"All SPEC items are ticked, but the adversarial review still has open findings. Say `resume the review` to enter the next iteration of the review loop (see Review-loop mode above) — it'll evaluate and fix this round, then recommend running another `codex-review` round or wrapping up via `/finish-branch`."* Do not enter the loop yourself; wait for the user's explicit trigger.

## Rules

- Never invent requirements beyond what sections 1–3 and 7 actually say. If something is ambiguous, ask before baking it into the plan.
- Never tick an item without evidence it works (passing tests, successful build, confirmed manual check with output shown).
- Commit after each resume-mode tick (step 7) so plan progress and the code it represents land together as one reviewable unit. Never push or open PRs from this skill — the user drives those.
- SPEC.md is per-branch. Don't copy checklists between branches or assume another branch's plan applies.
- If resume-mode work reveals the plan itself is wrong (not just incomplete), stop and ask whether to re-plan before editing further.
- Keep the user's prose in sections 1, 2, 3, 7, 8 untouched — you only own the checklists. This rule applies inside the autonomous review loop too: if a Codex finding can only be addressed by editing user prose, escalate rather than self-edit.
- **Verify Codex's concrete claims before acting on them.** When Codex names a file, test, function, layer, or call chain in a finding, treat the claim as a hypothesis: confirm the named entity exists and matches Codex's description — prefer the Kotlin/Swift LSP tool, falling back to `Grep`/`Read` when no LSP is available or it can't resolve it. Codex sometimes hallucinates names or misreads which path a layer belongs to. Acting on an unverified claim can introduce checklist items that target non-existent code or mis-target the wrong layer (and waste a full review iteration recovering).
- Before committing in step 7, run the project's formatter on any files you modified during the resume pass (`./gradlew ktfmtFormat` for Kotlin; `swift format` / your iOS formatter for Swift sources) and re-stage. There is no pre-commit hook, so this is a manual step — but CI's `./gradlew check` gate runs the formatter/linter in verify mode, so skipping it will fail CI. If the formatter rewrites layout (spacing, line wrapping), run it *before* capturing any line numbers in SPEC.md so the captured numbers match the final formatted file.
- **No `SPEC §` / `round N` / `plan-review round N` / `adversarial review` references in production or test code comments.** SPEC.md is per-branch and reverts to the main-branch skeleton after merge; `.claude/REVIEW_PLAN.md` and `.claude/REVIEW_ADVERSARIAL.md` are wiped each round. A comment that cites those locations becomes a dead reference the moment the branch lands. Write self-contained comments — name the invariant, the failure mode, or the contract; never the SPEC item number or review round. Cross-references between *production* code identifiers (function / class / file names that survive on `main`) are fine. Example:

  ```kotlin
  // ❌ Don't write this:
  // SPEC §4 item 7 post-lock writer protocol — acquire calendar-scoped lock first, then
  // sweep tombstones (cf. adversarial review round 3 finding #1).

  // ✅ Write this:
  // Post-lock writer protocol — acquire the calendar-scoped advisory lock first, then sweep
  // expired tombstones. Order is calendar → series; reversing would deadlock against the
  // per-series locks taken inside `insertRecurrencesForSeries`.
  ```

  The same rule applies to test docstrings, KDoc, inline comments, SQL comments, and migration files. KDoc on a function whose existence is itself tied to a SPEC contract should describe *what the function does and why*, not *which SPEC item it satisfies*. Aim for comments that read as if the branch never existed — only the durable code and its invariants remain.
- **Names must communicate role + intent — apply the "cold reader" test before settling on a name.** Code generated from a SPEC tends to inherit the SPEC's vocabulary verbatim, which often produces names that are mechanically accurate but undersell or under-describe what the code actually does. Before you finalize a method, class, enum, or file name, imagine a teammate opening the file on `main` six months from now with no SPEC context. Could they guess what it does from the name alone? Concrete checks (see CLAUDE.md "Conventions" for the full convention with examples):
  - **Method/function:** leads with an action verb that names *what* it does, not *how* it modifies parameters. If it runs multiple steps, surface them (`dedupAndPersistOverridesBeyondWindow`, not `appendOutOfWindowOverrides`).
  - **Type:** names the specific artifact, not a generic noun. Prefer `ReadWindowClassification` over `Regime`; `AnchorRefreshOutcome` over `Outcome` / `Result`.
  - **Class/module scope:** the name covers the broadest case it handles. If it implements two regimes, the name shouldn't reference only one of them (`ReadWarmupOrchestrator`, not `RecenterOrchestrator`).
  - **File naming:** follow your project's documented file-naming scheme (see Conventions).

  If a SPEC item literally names a symbol that fails one of these checks (e.g. the SPEC says "implement `Regime.classifyRegime`"), pick a better name in the code AND update SPEC.md to match. SPEC.md is per-branch and ephemeral; the durable code and its name are what survive.
