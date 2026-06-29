---
name: codex-review
description: Run a Codex adversarial review of the current branch against a base branch via the `/codex:adversarial-review` slash command, then save results to `.claude/REVIEW_ADVERSARIAL.md`. Use when the user says "run a Codex adversarial review", "ask codex to review", "codex review", or wants an adversarial code review before opening a PR.
argument-hint: [--base <ref>] [extra focus text]
allowed-tools: Skill, Bash, Read, Write, Edit
---

Run `/codex:adversarial-review` against the current branch and persist the results.

## Steps

1. **Resolve the base ref.** Default `main`. If the user passed `--base <ref>` in the args, that **overrides** the default.

2. **Build the focus string.** The focus is passed as free text *after* the flags (the `/codex:adversarial-review` command takes no `--focus` flag — steering is free-form).

   a. Read `.claude/SPEC.md` from the current branch. Parse §1 Overview + §2 Objective for the *intent*, and the **checked** items in §4 for the *scope of what changed*.

   b. Collapse that into a compact focus blurb — ≤ 3 sentences. It should tell Codex what the branch is *trying to accomplish* and what was *actually touched*, so its review stays anchored to the real diff. Do NOT list every checkbox verbatim; summarize.

   c. If the user supplied any extra focus text in the args, **augment** by appending it after the SPEC-derived summary (do not replace). Example: `<SPEC summary>. Additional focus: <user text>.`

   d. If no `.claude/SPEC.md` exists on this branch, fall back to a compact summary derived from `git log <base>..HEAD --oneline` and any user-supplied text.

3. **Invoke the slash command** via the Skill tool:

   ```
   Skill(skill: "codex:adversarial-review", args: "--base <ref> <focus string>")
   ```

   Run it inline (no `--background`) so the review response is available to this skill in the same turn.

   Project context to include *inside* the focus string (one short clause, not a bulleted list):
   *Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, and missing cross-platform test coverage.*

4. **Persist the output** to `.claude/REVIEW_ADVERSARIAL.md` — **accumulate, do not overwrite.** The file is a running log: the newest round lives at the top, prior rounds stay below as historical context (so resolved / deferred issues remain discoverable for future reviewers and for the next Codex pass).

   File layout:

   ```markdown
   # Adversarial Review

   ## Latest round — <YYYY-MM-DD>
   - Base ref: <ref>
   - Focus sent to Codex: <focus string>

   <Codex's findings verbatim>

   <!-- previous-rounds:start -->
   ## Previous rounds

   ### <YYYY-MM-DD> — base <ref>
   - Status when archived: <e.g. "all critical addressed in commits abc1234, def5678; one minor deferred — see commit message">
   - Focus sent to Codex: <focus string>

   <prior round's Codex output verbatim>

   ### <older date> — base <ref>
   ...
   <!-- previous-rounds:end -->
   ```

   Procedure:

   a. If `.claude/REVIEW_ADVERSARIAL.md` does NOT exist, write a fresh file with the layout above and an empty `Previous rounds` section (the markers must still be present so the next run can find them).

   b. If the file DOES exist:
      1. Read it.
      2. Extract the existing **Latest round** block (everything between `## Latest round` and the `<!-- previous-rounds:start -->` marker).
      3. Demote that block: change its `## Latest round — <date>` heading to `### <date> — base <ref>`, and prepend a one-line **Status when archived** entry. Best-effort fill that line by looking at `git log <previous-base>..HEAD --oneline` since the prior review's date for commits whose messages reference review fixes (`Address review`, `fix:`, etc.); if nothing matches, write `Status when archived: not yet addressed at time of next review`. Do not invent resolutions.
      4. Splice the demoted block in as the FIRST entry inside `Previous rounds` (newest-prior-first ordering).
      5. Replace the **Latest round** block with the new review's content.
      6. Preserve all older entries inside `Previous rounds` verbatim — never edit, summarize, or delete them. The file grows monotonically; pruning is a manual, deliberate operation by the user.

   c. Codex's findings, in both Latest and archived rounds, are verbatim. Do not paraphrase or soften them.

   d. If the existing file is missing the `<!-- previous-rounds:start -->` / `<!-- previous-rounds:end -->` markers (e.g. it was written by an older version of this skill), treat the entire existing body as a single legacy round, wrap it under a `### <file mtime date> — legacy entry` heading inside a freshly-created `Previous rounds` section, and proceed.

5. **Summarize to the user** in ≤ 100 words:
   - Counts of critical / moderate / minor issues.
   - Merge-readiness verdict (Codex's own wording).
   - Remind them the full review is saved in `.claude/REVIEW_ADVERSARIAL.md`.

## Rules

- Never modify source code from this skill — `/codex:adversarial-review` is read-only and so is this wrapper. If Codex flags issues, the user drives the fixes (optionally via the `address-review` skill).
- Never silently drop user-supplied focus text. It must end up in the final focus string.
- Never swap `--base main` to another ref unless the user asked for it.
- Keep the focus string compact. A bloated focus dilutes the review quality.
