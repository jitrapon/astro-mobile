---
name: scaffold-issue
description: Scaffold a branch's `.claude/SPEC.md` from a GitHub issue. If on `main`, create a new branch (ask the user for the name with a suggestion derived from the issue). Distills the issue body into SPEC sections 1 (Overview), 2 (Objective), and 3 (Requirements & Context), keeping it deliberately code-agnostic so `spec-development` re-discovers files when generating the plan. Commits the populated SPEC and hands off. Use when the user says "scaffold issue #123", "scaffold issue 123", or similar.
argument-hint: <issue number, e.g. #123 or 123>
allowed-tools: Read, Write, Edit, Bash, AskUserQuestion
---

# scaffold-issue

On-ramp into the spec-driven workflow for a GitHub issue. This skill stops once `.claude/SPEC.md` sections 1, 2, 3, and 8 are populated and the result is committed — the implementation checklist (sections 4, 5) is `spec-development`'s job.

## Steps

1. **Parse the issue number from `$ARGUMENTS`.** Accept `123`, `#123`, or a `https://github.com/<owner>/<repo>/issues/123` URL. Strip the leading `#` or extract the trailing path segment. If `$ARGUMENTS` is empty or unparseable, ask the user via `AskUserQuestion` ("Which issue number?") — don't proceed without one.

2. **Fetch the issue via `gh`.**

   ```bash
   gh issue view <N> --json number,title,body,labels,url,author,milestone,comments,state
   ```

   - If `gh` is unavailable or unauthenticated, stop and tell the user to install / `gh auth login`. Do not fall back to a different source.
   - If the issue is `state: CLOSED`, ask the user via `AskUserQuestion` whether to proceed anyway ("Issue #N is closed — scaffold from it anyway?"). A closed issue is sometimes a valid starting point (re-open work, related follow-up), but the user should explicitly confirm.

3. **Decide where SPEC.md will land.**

   a. Get the current branch with `git branch --show-current`.

   b. **If current branch is `main`:**
      - Derive a suggested branch name: `<issue-number>-<kebab-of-title>`. Lowercase ASCII, hyphens between words, strip punctuation, total length ≤ 60 chars (trim from the end on the word boundary if needed — do NOT chop mid-word).
      - Ask the user via `AskUserQuestion` to confirm or override:
        - Question: `"Branch name for issue #<N>?"`
        - Header: `"Branch"`
        - Option 1 (recommended): the derived name.
        - Option 2: `"Other"` (auto-provided) — for free-form override.
      - Create and check out the branch: `git checkout -b <name>`.
      - The fresh branch has the skeleton SPEC.md from `main`, so populating it is safe.

   c. **If current branch is NOT `main`:**
      - Stop and ask the user via `AskUserQuestion`:
        - Question: `"Current branch is `<branch>`, not `main`. How should I proceed?"`
        - Header: `"On feature branch"`
        - Options: `"Populate SPEC here"` (overwrite this branch's SPEC.md), `"Switch to main first"` (abort, user re-invokes after `git checkout main`), `"Abort"`.
      - Honor the user's choice. Never silently overwrite a non-skeleton SPEC.md.

4. **Distill the issue into SPEC sections.** Read the full issue body (and skim comments only for additional constraints the author confirmed, not for speculative discussion). Extract:

   - **Section 1 — Overview**: one short paragraph. What this branch changes and why, as stated by the issue. Don't add framing the issue didn't provide.
   - **Section 2 — Objective**: one or two sentences. What "done" looks like. If the issue describes the problem but not the fix, phrase it as "resolve <described behavior>" — do NOT presume a solution.
   - **Section 3 (Requirements & Context)**: known constraints, scope notes, prior art, behavioral requirements, things the issue says are out-of-scope. A few short paragraphs or a bulleted list of constraints.

   **Rules for the distillation:**
   - **Ignore code-level specifics.** Don't transcribe file paths, function names, line numbers, or exact code blocks from the issue body. They may be outdated. `spec-development` re-explores the code when generating the plan.
   - **Keep behavioral and architectural constraints** — e.g. "logic must stay in the shared module (no platform-specific business logic in androidApp/iosApp)", "must not break the iOS `shared` framework's public API", "Android-only for this milestone", "preserve the existing `Result<T>` error-handling contract". These are the load-bearing part of the SPEC.
   - **Distill, don't transcribe.** Paraphrase tightly. Issue bodies are often long and conversational; SPEC sections are short and declarative.
   - **Section 8 — References**: always include the issue URL. Also include any linked PR URLs, RFC links, or external doc links mentioned in the body. One URL per line.
   - **Section 7 — Documentation**: if the issue explicitly calls out doc updates (CLAUDE.md, LOCAL_DEV.md, README), list them; otherwise leave the skeleton placeholder untouched. Do not fabricate doc obligations.
   - **Sections 4, 5, 6**: leave the skeleton placeholders verbatim. These are `spec-development`'s territory.

5. **Resolve and validate the plan anchor.** The task ID recorded here is consumed unattended by
   `sync-plan` in astro-docs, and this skill is the last point where a human is present to correct
   it — so it is validated against the live plan, never assumed.

   ```bash
   gh api repos/jitrapon/astro-docs/contents/current-plan.md -H "Accept: application/vnd.github.raw"
   ```

   - **Find the task row.** Look for a task table row whose text matches the issue (by title, by an
     issue link in the row, or by the issue naming the ID outright, e.g. “M-2” in the title or body).
   - **Confirm with the user via `AskUserQuestion`** — question `"Which plan task does issue #<N>
     belong to?"`, header `"Plan task"`, option 1 the matched row, option 2 the next-most-plausible
     row, option 3 `"Not plan work"`. Never auto-accept a match; a wrong ID here moves the wrong lane
     later, with nobody watching.
   - **A `task:` that resolves to no row is a hard stop**, not a new task. Ask again or take
     `"Not plan work"`.
   - **`"Not plan work"` is a normal answer**, not a failure. Most PRs in this repo are bug fixes,
     `deferred-review` cleanups, and dependency bumps. Record `lane: -` and `task: -`; `sync-plan`
     then leaves Status and Next action alone, which is correct.
   - **Derive `lane` from the task row's table**, not from the repo name: `C-*`/`A-*`/`B-*` →
     `backend`, `M-*` → `mobile`, `W-*` → `web`, `I-*` → `infra`, docs issues → `docs`.

   Write section 0 of SPEC.md:

   ````markdown
   ## 0. Plan anchor

   ```yaml
   lane: mobile
   task: M-2
   issues: [<N>]
   completes: no
   spec-objective: <placeholder — spec-development fills this>
   ```
   ````

   Leave `completes` at `no` and `spec-objective` at its placeholder. Both belong to
   `spec-development`, which owns section 2 and only knows the delivered scope after it re-explores
   the code. Full field semantics: `plan-update-contract.md` in astro-docs.

6. **Write SPEC.md** via the Edit tool — replace the skeleton bodies in sections 0, 1, 2, 3, 8 (and 7 if applicable). Set the `# Specification:` title to a short branch-scoped title derived from the issue. If the raw issue title is awkward as a SPEC title (too long, too vague, ALL CAPS, etc.), tighten it — don't paste verbatim.

7. **Commit.**
   - Stage `.claude/SPEC.md` by explicit file name (NEVER `git add -A` / `git add .`).
   - Commit with message:

     ```
     Scaffold SPEC from issue #<N>

     <one-line distillation of objective>

     Refs: <issue URL>

     Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
     ```

     Use a HEREDOC for the message so multi-line formatting survives the shell.
   - Do NOT push yet. Step 8 pushes and opens the draft PR.
   - This repo has no pre-commit hook, and this is a SPEC.md-only commit, so no formatter/linter runs here. (When committing code later, run `./gradlew ktfmtFormat` + `./gradlew detekt` manually; CI's `./gradlew check` gate enforces them.)

8. **Push and open a draft PR.** The draft PR is how the plan learns that this lane has started
   work — `sync-plan` reads open PRs (drafts included) for in-flight Status. Without it the plan
   only moves when work *finishes*.

   ````bash
   git push -u origin <branch>
   gh pr create --draft --title "<issue title, imperative>" --body "$(cat <<'BODY'
   ## Description

   Scaffolding for issue #<N>. Implementation has not started; this PR exists so the plan reflects
   that the lane is in flight. `finish-branch` rewrites this body to convention at wrap-up.

   ## Plan Update

   ```yaml
   lane: <from section 0>
   task: <from section 0>
   completes: no
   issues: [<N>]
   deferred: []
   spec-objective: <placeholder until spec-development fills section 2>
   ```
   BODY
   )"
   ````

   The title is provisional — `finish-branch` rewrites it. Do not spend effort on it here.

   If `gh` fails or the push is rejected, **do not stop the skill**: the branch and SPEC commit are
   the deliverable, the draft PR is bookkeeping. Report that the plan will not show this lane as in
   flight until a PR exists, and continue.

9. **Hand off to `spec-development`.** Summarize to the user in ≤ 80 words:
   - The branch name (newly created or already on).
   - One-line description of the distilled objective.
   - The commit SHA, and the draft PR URL (or why there is none).
   - The plan anchor as recorded: `<lane> / <task>`, or "not plan work".
   - End with the literal prompt: *"SPEC sections 0, 1, 2, and 3 are populated. Run `review the spec` next to generate the implementation plan."*

   Do NOT invoke `spec-development` yourself — wait for the user.

## Rules

- **Issue body is the only source of truth.** Don't pad sections 1–3 with details inferred from the codebase, related issues you weren't asked to look at, or your own opinions on what the fix should look like. If the issue is vague, the SPEC should reflect that — `spec-development` surfaces the gaps during planning.
- **Code-agnostic by design.** No file paths, function names, or line numbers in sections 1–3. The plan-generation step will re-grep the code.
- **Never invent objectives.** If the issue describes a bug without prescribing a fix, the Objective is "resolve <bug>", not your guess at the implementation.
- **Never start the implementation plan.** This skill stops at section 3 (Requirements & Context). Leave sections 4 (implementation), 5 (testing), and 6 (deployment) as skeleton placeholders.
- **Push only the scaffold, only as a draft.** Step 8 pushes the branch and opens a *draft* PR so the plan can show the lane in flight. Never open a ready PR, never push to `main`, and never push again after step 8 — `finish-branch` owns everything from there.
- **Never invent a plan task ID.** It is validated against the live `current-plan.md` and confirmed by the user in step 5. `task: -` is always an acceptable answer; a guessed ID is not, because `sync-plan` acts on it with no human in the loop.
- **No `git add -A`.** Always stage `.claude/SPEC.md` by explicit file name.
- **Branch naming.** `<issue-number>-<kebab-description>`, lowercase ASCII, ≤ 60 chars. Always confirm with the user — do not auto-create even when the suggestion looks fine.
- **One issue per invocation.** If the user wants to scaffold from multiple issues, they invoke the skill once per issue (each on its own branch).
