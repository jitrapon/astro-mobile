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

### Step 0 — Capture the plan anchor (before anything resets it)

Read `.claude/SPEC.md` section 0 (**Plan anchor**) and hold `lane`, `task`, `issues`, `completes`,
and `spec-objective` in memory now. Step 1 checks SPEC.md back out from main's skeleton, so after it
runs the anchor is gone — and re-deriving lane and task from the branch name at step 6 would be
inference feeding an unattended writer. Capture it in the same pass that reads SPEC §§1–2 for the
title.

If section 0 is missing or still holds skeleton placeholders, do not guess. Use `lane: -`,
`task: -`, `completes: no` in the Plan Update block and say so in the final summary; `sync-plan`
treats that as non-plan work and leaves Status and Next action alone, which is the correct outcome
for a branch that never claimed a task row.

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

### The PR title and body convention (steps 5 and 6 both apply it)

**Title — imperative, verb-first, and true of the whole branch.** A squash merge uses the PR title as
the squashed commit subject, so the title has to read the way a commit message does: phrase it so
that **"Applying this commit will <title>"** is a correct sentence. Lead with a verb, imperative
mood, no trailing period.

The title must describe the branch's *work*, not the branch's *setup*, and it must cover **all** of
that work. Three failure modes to check for by name, because each produces a title that is
grammatical and still wrong:

- **A stale title on a pre-existing PR.** A draft opened at scaffolding time is usually titled after
  the scaffolding commit ("Scaffold SPEC for X"). Squashed, that records an entire branch in `main`'s
  history as the act of creating a spec file.
- **The most recent commit subject.** By the time this skill runs, the newest commit is usually the
  skeleton reset or a one-line review fix — neither summarizes anything. Derive the title from what
  the branch *delivers*: read SPEC §§1–2 **before step 1 resets them**, or read `git log <base>..HEAD`
  as a whole. Never from a single commit.
- **A title that was true when it was written and now covers only part of the branch.** The quietest
  of the three, because it passes both checks above: it is imperative, verb-first, and about real
  work — but if the branch has since grown two more capabilities, squashing it records a third of the
  change. A title is only correct if it is true of the branch's *complete* delivered work, so check
  it against `git log <base>..HEAD` as a whole rather than against its own plausibility.

**Body — written for the human who has to review it**, and carrying at least these sections:

- `## Description` — what this branch delivers and why, in a few sentences. If the PR already had
  user-authored prose, fold it in here rather than discarding it.
- `## What changed` — the summary of the work, organized by the thing changed rather than by commit.
  Name the types/files a reviewer should open first.
- `## Gotchas` — the highest-value section, and the one worth spending effort on. Pin the numbers a
  reviewer would otherwise have to derive (a bounded knob and its ceiling; a real window that differs
  from the nominal one), and name the decisions that *look* like oversights but are deliberate,
  each with its reason.
- `## Plan Update` — the machine-read section. Copy the anchor captured in step 0 verbatim and add
  `deferred` (the same issue numbers the `## Adversarial review` section reports as DEFERRED). It is
  parsed unattended by `sync-plan` in astro-docs, so it is a fenced YAML block with fixed keys and
  nothing else — format and field semantics live in `plan-update-contract.md` in astro-docs. A
  malformed block is dropped and the plan silently does not move, so keep the fence intact.
  When the PR already carries a `## Plan Update` block (the draft opened at scaffold time does),
  **replace it in place** — two blocks in one body is a parse failure, and the scaffold-time block
  is stale by definition: it was written before the branch had a `deferred` list or a settled
  `completes`.

Preserve any trailers (`Co-Authored-By`, session links) and stack-tooling footers already present in
the body — those are attribution and tooling state, not prose.

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

### Step 5 — Bring the existing PR up to convention, then post the audit-trail comment

An existing PR is the case where a stale title is most likely, because the PR was usually opened as a
draft before the work existed. **Read its current title and body** (`gh pr view --json title,body`)
and check both against **The PR title and body convention** above.

- Rewrite the title (`gh pr edit <n> --title "<new title>"`) if **any** of the convention's three
  failure modes applies: it fails the "Applying this commit will <title>" test, it describes the
  branch's setup rather than its work, or it no longer summarizes the branch's *complete* delivered
  work. Judge the last one against `git log <base>..HEAD` as a whole — a title that only ever covered
  the branch's first commit stays grammatical and stays about real work, so nothing but the full log
  exposes it.
- Rewrite the body (`gh pr edit <n> --body-file <file>`) if the Description / What changed / Gotchas
  sections are missing **or their content no longer describes the completed branch**. Headings
  present is not conformance: a body written at scaffolding time can carry all three and still
  describe only the scaffolding, or a design the branch has since abandoned. Read the *content*
  against the branch's actual diff, not just its structure. When rewriting, fold any user-authored
  prose into Description and keep every trailer and footer verbatim. This is the one place the skill
  edits a description the user may have written, so *fold in and restructure* — never discard.
- **Always refresh `## Plan Update`**, even when the rest of the body conforms. The block a draft
  carries was written at scaffold time, before the branch had a `deferred` list or a settled
  `completes` — and `completes` is the field that tells `sync-plan` the task row is finished. Leaving
  the scaffold-time block in place is how a completed task never clears from the plan's Status.
  Replace the existing block in place; never append a second one.
- If title and body already conform — in content, not just in shape — change nothing else and say so.

Then post a brief summary as a PR **comment** (the audit trail is a comment, never the description):

```
🤖 finish-branch wrap-up

- CI gate: green (`./gradlew check`)
- Adversarial review: <N> round(s), all findings <RESOLVED | DEFERRED → #N | NOT AN ISSUE>
- Skeleton reset: <commit SHA, or "no-op (already in sync)">
- Branch ready for review.
```

Then stop. Tell the user the PR URL.

### Step 6 — Create a fresh PR

Build the title per **The PR title and body convention** above — imperative and verb-first, derived
from what the branch delivers rather than from its newest commit.

Build the body in this shape:

````markdown
## Description

<What this branch delivers and why — a few sentences, from SPEC §§1–2 paraphrased for PR-readers.>

## What changed

<The work, grouped by the thing changed rather than by commit. Name the types/files a reviewer
should open first, and for anything security- or correctness-critical, state the property it holds.>

## Gotchas

- <A number a reviewer would otherwise derive — a bounded knob and its ceiling, a real window that
  differs from the nominal one.>
- <A decision that looks like an oversight but is deliberate, with its reason.>
- <Anything whose blast radius is larger than its diff — a frozen encoding, an ordering contract, a
  configuration value two services must agree on.>

## Plan Update

```yaml
lane: <backend | mobile | web | docs | infra | ->
task: <task ID from current-plan.md, or ->
completes: <yes if merging finishes the whole task row, else no>
issues: [<issue numbers in this repo closed by this PR>]
deferred: [<issue numbers this branch deferred rather than fixed>]
spec-objective: <SPEC section 2, collapsed to one line>
```

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
````

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
- The `## Plan Update` block is read by an unattended skill that writes to astro-docs `main`. Copy it
  from the step 0 anchor; never author its values at wrap-up time from what the branch looks like.
- `completes: yes` only when merging this PR finishes the **whole** task row. A branch that is one
  layer of a stack is `completes: no` — marking it `yes` clears the lane's Status while the rest of
  the task is still open.
- Never omit the `## Plan Update` section. If the anchor is absent, emit it with `-` values rather
  than dropping it; a missing section and a null section mean different things to `sync-plan`.
- **A PR title is a commit subject.** It is squashed into `main` verbatim, so it is held to the same
  standard as a commit message — imperative, verb-first, true of the whole branch. Apply the check to
  an existing PR's title too, not only to one this skill creates; a title inherited from a scaffolding
  draft is the common case and the easiest to miss.
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
