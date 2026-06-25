---
name: finish-branch
description: Reset agent working files to main's skeleton, push all commits, and open (or ready) a PR. Use when the user says "finish the branch", "wrap up the branch", or "open the PR" after the spec is complete and the adversarial review has no open findings.
argument-hint: [--draft]
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, AskUserQuestion
---

End-of-branch wrap-up: scrub branch-specific agent working files back to the skeleton that lives on `main`, push, and open / ready a PR. The skeleton-reset commit lives on the BRANCH, so a squash merge produces zero net change for those files in `main`.

## Pre-flight gates (all hard-fail; do NOT proceed past any failure)

Run these in order. Each failure should be reported with a clear remediation hint and then the skill stops without making changes.

1. **Not on `main`**. `git branch --show-current` must NOT be `main`. If it is, stop — there's nothing to finish.

2. **Working tree is clean for non-skeleton paths.** Run `git status --porcelain`. The only paths permitted to be dirty here are the three skeleton files (`.claude/REVIEW_ADVERSARIAL.md`, `.claude/REVIEW_PLAN.md`, `.claude/SPEC.md`) — and even those should be committed by the time the user invokes this. If anything else is uncommitted (modified, staged, or untracked), stop and tell the user to either commit, stash, or discard before re-running.

3. **CI gate passes.** Run `./gradlew check` (the aggregate gate — compiles all modules and runs Detekt + ktfmt verification + unit tests). When the branch touched `commonMain`/`iosMain`, also run `./gradlew :shared:iosSimulatorArm64Test` so the iOS side is covered. If the build fails or any test/lint check fails, stop with the failure summary. Do not run a clean rebuild automatically — if the user wanted that, they'd have run it manually.

4. **Adversarial review has been run at least once.** Read `.claude/REVIEW_ADVERSARIAL.md`. It must exist AND contain at least one `## Latest round —` heading. If the file is missing or only contains the skeleton (no round headings), stop and tell the user: *"Run a Codex adversarial review first (`codex-review` skill)."*

5. **No open adversarial findings.** Inside the `## Latest round` block, every `- [<severity>]` finding line must be followed (on the same or next line) by one of: `**RESOLVED**`, `**DEFERRED**`, `**DEFERRED → #<n>**`, or `**NOT AN ISSUE**`. Any finding without one of those markers is "open" and blocks the wrap-up. If any is open, stop and list them. The user must finish address-review (or mark explicitly) before wrap-up proceeds.

6. **All DEFERRED findings have a tracking issue number.** Every `**DEFERRED**` marker in the latest round must be followed by `→ #<number>`. A bare `**DEFERRED**` (no issue link) blocks wrap-up — that audit trail will not survive the next branch's review file regeneration. If any are unlinked, stop and tell the user to file the issue (and reference it in the review file) before re-running.

## Advisory: CLAUDE.md drift check (non-blocking, fix-in-branch)

Runs after the pre-flight gates pass, before Step 1. **Unlike the gates above, this NEVER hard-fails the wrap-up** — if it finds nothing, or the user declines, the skill proceeds normally. Its job: catch the case where this branch's durable code moved / renamed / added something `.claude/CLAUDE.md` documents, and offer to refresh the doc **on this same branch** so the fix ships in the same PR as the work that caused it.

**Emit nothing if no signal fires** — clean branches and pure-implementation branches that touch no documented surface stay quiet and skip straight to Step 1.

Compute the branch's net change against `origin/main` (the fetch is idempotent with Step 1's):

```bash
git fetch origin main --quiet
git diff --name-status origin/main...HEAD
```

Evaluate three drift signals. For each that fires, record the exact CLAUDE.md anchor:

- **Signal A — a backtick-named symbol was renamed / moved / deleted (highest confidence, usually mechanically fixable).** For every file with status `R`/`D`, or any source file whose diff *removes* a top-level declaration (Kotlin: `class`/`object`/`interface`/`fun`/`val`/`enum class`; Swift: `func`/`struct`/`class`/`enum`/`protocol`/`extension`), extract the affected identifier(s) and grep CLAUDE.md for them in backticks: `grep -nF '`<identifier>`' .claude/CLAUDE.md`. A hit means the doc still references a symbol this branch moved or removed. Before proposing a fix, **verify the symbol's new home** (grep/LSP for the new definition) so the replacement is correct, not guessed.

- **Signal B — new durable architectural surface the doc likely wants.** Any added (`A`) file matching a documented seam pattern (a new `expect`/`actual` platform pair under `shared/src/{androidMain,iosMain}/`, a new repository/data source or model in `shared/src/commonMain/kotlin/io/jitrapon/astro/data/`, a new Compose screen under `androidApp/src/main/`, or a new SwiftUI view under `iosApp/iosApp/`) OR a new config key. A backtick-grep MISS is *expected* (the doc can't cite brand-new code), so flag the *presence* of the new surface and the CLAUDE.md section it belongs in.

- **Signal C — a file with its own detailed CLAUDE.md section was modified (`M`).** The build/CI/dependency/config files CLAUDE.md describes in detail: `build.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` (if present), the Detekt config, and `.github/workflows/ci.yml`. Flag with a pointer to re-read that section for stale specifics (version pins, thresholds, command names).

If any signal fired, present the candidates and ask once via `AskUserQuestion` whether to fix on this branch now:

```
AskUserQuestion(
  questions: [{
    question: "Possible CLAUDE.md drift from this branch — update the doc on this branch before wrapping up?",
    header: "CLAUDE.md drift",
    multiSelect: false,
    options: [
      { label: "Fix on this branch (Recommended)",
        description: "Apply the targeted CLAUDE.md edits, commit them on this branch, then continue the wrap-up. Ships in this PR." },
      { label: "Skip — wrap up as-is",
        description: "Leave CLAUDE.md unchanged and proceed. Nothing blocks." }
    ]
  }]
)
```

List the concrete candidates in the surrounding prose so the choice is informed, e.g.:

```
- [A] CLAUDE.md:142 references `<oldSymbol>` — moved/renamed to `<newSymbol>` in this branch.
- [B] New seam `<path/to/NewModule>` — not yet in the architecture layer list.
- [C] `<linter-config-file>` changed — re-check the Linting thresholds section.
```

**On "Fix on this branch":**
1. Apply only edits you can substantiate against the actual code. Signal-A reference corrections are mechanical once the new location is verified. For Signal B/C, add prose only where you can ground it in the diff (e.g. a new migration's table row, a changed threshold value) — never invent doc content to satisfy a nudge; downgrade anything you can't substantiate to a one-line note in the final summary.
2. Commit the CLAUDE.md change as **its own commit** on the current branch (stage `.claude/CLAUDE.md` by explicit name; no `git add -A`; no `--amend`). Canonical message: `Refresh CLAUDE.md for <branch topic> (drift check)`, with the standard `Co-Authored-By` trailer. Do this **before Step 1** so the working tree is clean again for the skeleton reset.
3. Continue to Step 1.

**On "Skip":** continue to Step 1 unchanged. Note the skipped candidates in the final summary so they're not lost.

## Steps

### Step 1 — Reset agent working files to main's skeleton

```bash
git fetch origin main
git checkout origin/main -- .claude/REVIEW_ADVERSARIAL.md .claude/REVIEW_PLAN.md .claude/SPEC.md
```

This pulls the latest `main` skeleton (via `origin/main`, not the possibly-stale local `main`). Fetching first is required — a long-running branch whose local `main` is behind can otherwise resurrect skeleton bugs that have already been fixed upstream.

After the checkout, run `git status --porcelain -- .claude/REVIEW_ADVERSARIAL.md .claude/REVIEW_PLAN.md .claude/SPEC.md`:

- If the working tree is now clean for those three paths (no diff vs. last commit), the branch was already in sync with main's skeleton — skip the commit and continue to step 2.
- Otherwise stage exactly those three paths (`git add` by name — never `-A` / `.`) and commit:

  ```
  Reset agent working files to main's skeleton

  Per the finish-branch skill: REVIEW_ADVERSARIAL.md, REVIEW_PLAN.md,
  and SPEC.md are per-branch working state. The audit trail for this
  branch lives in earlier commits; main keeps the canonical skeleton.

  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  ```

  The pre-commit hook may reformat — if it fails, diagnose and create a NEW commit (no `--amend`). Standard rules apply.

### Step 2 — Push

`git push`. If the upstream isn't set, `git push -u origin <branch>`. Report the commit range pushed.

### Step 3 — Detect existing PR

Run `gh pr view --json number,state,isDraft,url` (in the current branch's context).

| `gh` exit | Meaning                                 | Branch action       |
|-----------|-----------------------------------------|---------------------|
| 0, `OPEN` and `isDraft=false`  | Ready PR already exists           | go to step 5  |
| 0, `OPEN` and `isDraft=true`   | Draft PR exists                   | go to step 4  |
| non-zero (no PR)               | No PR yet                          | go to step 6  |

### Step 4 — Convert draft PR to ready

If the user passed `--draft`, skip this step (they want it to stay draft); just note the PR URL and continue to step 5.

Otherwise:

```bash
gh pr ready
```

Then proceed to step 5.

### Step 5 — Post audit-trail comment on existing PR

Post a brief summary as a PR comment (not a new description — don't overwrite what the user wrote). Body:

```
🤖 finish-branch wrap-up

- CI gate: green (`./gradlew check`)
- Adversarial review: <N> round(s), all findings <RESOLVED | DEFERRED → #N | NOT AN ISSUE>
- Skeleton reset: <commit SHA, or "no-op (already in sync)">
- Branch ready for review.
```

Then stop. Tell the user the PR URL.

### Step 6 — Create a fresh PR

Build the title from the most recent commit subject on the branch (or the branch name if commits are fix-by-fix and don't summarize the whole work).

Build the body in this exact shape (keep it tight — Approach below should be 1–3 short bullets, not paragraphs):

```markdown
## Summary

<1-line restatement of the SPEC §1 Overview, paraphrased for PR-readers>

## Approach

- <bullet from SPEC §2 Objective, paraphrased>
- <bullet from the most-load-bearing SPEC §4 items that were ticked>

## Adversarial review

<N> round(s) of `/codex:adversarial-review` against `main`. All findings closed:
- <count> RESOLVED (commits: <short SHAs>)
- <count> DEFERRED (issues: <#N, #M>)
- <count> NOT AN ISSUE

Latest round verdict: <copy from REVIEW_ADVERSARIAL.md latest-round verdict line>

## Test plan

- [ ] `./gradlew check` (verified green at finish-branch time, SHA <branch HEAD>)
- [ ] `./gradlew :shared:iosSimulatorArm64Test` (if the branch touched shared/iOS code)
- [ ] CI lint + tests pass
- [ ] <any manual / E2E checks called out in SPEC §5>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

Default to `--ready` (omit `--draft`); if the user passed `--draft`, use `gh pr create --draft`.

```bash
gh pr create --title "<title>" --body "$(cat <<'EOF'
<body from above>
EOF
)"
```

Report the URL.

## Final summary

Tell the user, in ≤ 80 words:

- Commit SHAs pushed (skeleton reset, plus anything that wasn't yet pushed).
- PR state — created / readied / already ready — and its URL.
- One-line note on what to do next: review CI, request reviewers, etc.

## Rules

- Never push to `main`. The skill operates on the current feature branch.
- Never `git add -A` / `git add .`. Stage by explicit file name only.
- Never `--amend`. If the pre-commit hook fails, create a NEW commit.
- Never push with `--force` / `--force-with-lease` from this skill.
- Never silently bypass a pre-flight gate. If a gate fails, stop and report — the user fixes it and re-invokes.
- The skeleton-reset commit message must clearly say what's happening (the wording above is the canonical form). Future readers will see it in the squash diff log.
- If `gh` is unavailable or auth fails at step 3, fall back to: print the PR title and body that *would* have been used so the user can create the PR manually, then stop. Do not attempt to create via API or open a browser.
- The `--draft` flag is the only argument this skill accepts. Anything else → stop and ask.
- The CLAUDE.md drift check is advisory: it NEVER hard-fails the wrap-up and is silent when no signal fires. "Skip" is always available.
- It is a heuristic — false positives are expected (e.g. a renamed private helper the doc never mentioned). Always confirm via `AskUserQuestion` before editing; never auto-edit CLAUDE.md, and never assert drift as fact.
- A CLAUDE.md fix is its own commit on the CURRENT branch (explicit file name, no `--amend`), made before Step 1 so the skeleton reset starts from a clean tree. It ships in this branch's PR — do not open a separate PR.
- When fixing, edit only what the diff substantiates — verify a moved symbol's new location (grep/LSP) before rewriting its reference; never invent doc prose to satisfy a Signal-B/C nudge.
