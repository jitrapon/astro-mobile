---
name: rank-deferred-reviews
description: Rank all open GitHub issues labeled `deferred-review` by severity and urgency against a milestone goal (from args, or from `current-plan.md` in the `jitrapon/astro-docs` repo as a fallback), then write the ranked list to `.claude/DEFERRED_REVIEW_ISSUES.md`. Use when the user says "rank deferred reviews", "prioritize deferred review issues", or wants to triage the deferred-review backlog against a specific objective.
argument-hint: [free-form milestone goal text — omit to use astro-docs/current-plan.md]
allowed-tools: Bash, Read, Write, Grep, AskUserQuestion
---

Rank the open `deferred-review` backlog against a milestone goal. Read-only — this skill never modifies issues, never commits, never edits source code. The single output is `.claude/DEFERRED_REVIEW_ISSUES.md`.

## Steps

1. **Resolve the milestone goal.**

   a. **Primary source — `$ARGUMENTS`.** If non-empty, treat the entire string as the milestone goal verbatim (e.g. "ship the auth-service split", "harden read path for prod load", "stabilize Google sync"). It is NOT a GitHub Milestone object — do not try to look it up via `gh`.

   b. **Fallback — `current-plan.md` from `jitrapon/astro-docs`.** If `$ARGUMENTS` is empty or whitespace, fetch the live plan from the default branch:

      ```bash
      gh api repos/jitrapon/astro-docs/contents/current-plan.md \
        -H "Accept: application/vnd.github.raw"
      ```

      Use the full raw markdown body as the milestone context for urgency scoring. For the `**Milestone:**` header line in the output file, do NOT paste the whole document — extract a short label:
      - Prefer the first `# H1` heading.
      - If the doc has a clearly labeled section like `## Current milestone`, `## Current goal`, or `## Now`, use its body's first sentence.
      - Otherwise, take the document's first non-heading sentence.

      Record the resolution path in the output header so the snapshot is auditable: `Milestone source: astro-docs/current-plan.md@<short-sha>` (get the sha via `gh api repos/jitrapon/astro-docs/commits/main --jq '.sha[0:7]'`).

   c. **Last-resort — ask.** Only if `$ARGUMENTS` is empty AND the fetch fails (no `gh`, no auth, repo gone, file missing, network error), ask the user via `AskUserQuestion`:
      - Question: "Couldn't fetch `current-plan.md` from astro-docs — what milestone goal should I rank against?"
      - Header: "Milestone"
      - Provide 2–4 plausible options derived from recent commits / SPEC.md if obvious, otherwise leave the user to type free-form via "Other".
      - Do NOT proceed without a goal — ranking without an objective produces generic noise.

2. **Fetch the open `deferred-review` issues.**

   ```bash
   gh issue list --label deferred-review --state open \
     --json number,title,body,labels,createdAt,updatedAt,url,author,comments \
     --limit 200
   ```

   - If `gh` is unavailable or unauthenticated, stop and tell the user to install / `gh auth login` — do not partially-rank from a different source.
   - If the result is `[]` (no open issues), write a minimal "no open deferred-review issues" version of the output file and exit. Do not invent placeholder rankings.

3. **Extract per-issue facts.** For each issue, parse the body for:
   - **Original finding excerpt** — the verbatim adversarial-review snippet (typically the first quoted/blockquoted paragraph).
   - **Deferral rationale** — why it was punted.
   - **Source branch / PR** — branch name, PR # if mentioned.
   - **Files / areas touched** — grep the body for `path/to/file.kt`, `path/to/file.swift`, `path/to/file.kt:NNN`, module names (`shared`, `androidApp`, `iosApp`), and platform source sets (`commonMain`, `androidMain`, `iosMain`). Collect the set.
   - **Cross-references** — `#NNN` mentions to other issues; also scan comments. If another issue in the backlog touches the same file or names the same root cause, flag it as a soft cross-ref even without an explicit `#NNN` link.

4. **Score each issue on two axes.**

   - **Severity** — judged from the *nature* of the original finding, independent of milestone:
     - **Critical** — data loss, security hole, silent corruption, auth bypass, money/PII leak.
     - **High** — incorrect results returned to caller, silent fallback masking real failures, breaking change to a public contract.
     - **Medium** — performance cliff under non-rare load, observability gap, partial-failure hazard with manual recovery.
     - **Low** — code-quality, refactor-only, test gap with no live exposure, doc drift.

   - **Urgency to milestone** — judged from how directly the issue blocks or amplifies risk for the supplied milestone:
     - **High** — sits on the critical path of the milestone, or the milestone makes the bug more reachable / more damaging.
     - **Medium** — adjacent to the milestone area (same subsystem) but not on the critical path.
     - **Low** — orthogonal to the milestone.

   Both axes must include a 1-line rationale grounded in the issue body and the milestone text — no generic language like "looks important".

5. **Rank.** Primary order: combine severity × urgency, prioritizing items that are High/High and Critical/anything. Then apply tie-breaks in this exact order:

   1. **Blocks other deferred issues** — issues whose fix unblocks or invalidates another open `deferred-review` issue rank ahead. Surface this in the cross-refs section.
   2. **Older `createdAt`** — staler issues rank ahead of newer ones at the same score.
   3. **Touches more files** — broader blast radius ranks ahead.

   Do not silently re-order on other axes (e.g. author, recent activity) — only the three above.

6. **Decide a suggested action** for each issue, one of:
   - **fix-now** — should be on the next branch tied to this milestone.
   - **batch-with-related** — group with the cross-referenced issue(s) into a single follow-up branch (name the issue numbers).
   - **defer-again** — keep on the backlog; revisit at the next milestone.
   - **close** — no longer relevant (e.g. obsoleted by a later refactor visible in `git log`). Recommend only with a concrete reason; the user closes the issue, not this skill.

7. **Write `.claude/DEFERRED_REVIEW_ISSUES.md`** — overwrite, do not accumulate. This file is a snapshot for the current milestone; re-running with a different milestone produces a fresh snapshot.

   Layout:

   ```markdown
   # Deferred Review Issues — Ranked

   - **Milestone:** <short label — verbatim arg, or extracted from current-plan.md per Step 1.b>
   - **Milestone source:** <`$ARGUMENTS` | `astro-docs/current-plan.md@<short-sha>` | `user-supplied via prompt`>
   - **Ranked at:** <YYYY-MM-DD>
   - **Total open issues:** <N>
   - **Tie-break order:** blocks-other-issues → older → touches-more-files

   ## Ranking

   ### 1. #<num> — <title>
   - **Severity:** <Critical|High|Medium|Low> — <1-line rationale>
   - **Urgency to milestone:** <High|Medium|Low> — <1-line rationale tying to milestone text>
   - **Suggested action:** <fix-now|batch-with-related|defer-again|close>
   - **Cross-refs:** <#NNN (relation), #MMM (relation)> | none
   - **Files / areas:** <comma-separated, or "n/a">
   - **Opened:** <YYYY-MM-DD> on branch `<branch>` (PR #<NNN> if any)
   - **Link:** <issue URL>

   ### 2. #<num> — ...
   ```

   - Severity / urgency rationale lines must each be ≤ 1 sentence and cite something concrete from the issue body (file, table, method, scenario) or the milestone text — not generic.
   - If an issue has no cross-refs, write `none` explicitly. Do not omit the line.
   - If a field is unknown (e.g. no source branch in the body), write `unknown` — do not guess.

8. **Summarize to the user** in ≤ 80 words:
   - Counts by suggested action (e.g. "3 fix-now, 2 batch-with-related, 4 defer-again").
   - The top-ranked issue's `#NNN` and one-line "why it's #1 for this milestone".
   - Remind them the full ranking is in `.claude/DEFERRED_REVIEW_ISSUES.md`.

## Rules

- **Read-only.** Never call `gh issue edit`, `gh issue close`, `gh issue comment`, or any write-side `gh` subcommand. Never modify source code, never `git commit`, never `git push`.
- **Never proceed without a milestone goal.** A blank milestone produces generic rankings. Fall back to `astro-docs/current-plan.md` first; only ask if that fetch fails.
- **Do not edit `current-plan.md`.** The fetch is read-only — never push to `jitrapon/astro-docs` from this skill.
- **Never invent fields.** If the issue body lacks deferral rationale or source branch, write `unknown`, not a plausible-sounding guess.
- **Quote, don't paraphrase, the original finding.** When the rationale lines reference the finding, quote the issue verbatim — softened paraphrases dilute the signal.
- **Overwrite the output file.** Do not accumulate prior rankings inside it. The file is a current-snapshot artifact; older snapshots live in `git log` if the user wants them.
- **Do not invoke other skills.** This skill is terminal — the user separately decides whether to feed the top-ranked items into `spec-development` or open a new branch.
