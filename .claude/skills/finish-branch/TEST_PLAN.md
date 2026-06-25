# finish-branch — test plan

How to verify the skill behaves correctly. These are exercises to run on a throwaway branch before relying on the skill for real PRs.

## Pre-flight gate cases (each must hard-fail with a clear message)

| # | Setup                                                                                              | Expected stop reason                                                                  |
|---|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| 1 | `git checkout main` then invoke                                                                   | "currently on main — nothing to finish"                                               |
| 2 | Working tree dirty on a non-skeleton path (e.g. `echo x >> README.md`)                            | "working tree has uncommitted changes outside the skeleton paths"                     |
| 3 | `./gradlew check` fails                                                                            | test/lint failure summary; no skeleton-reset or push happens                          |
| 4 | `.claude/REVIEW_ADVERSARIAL.md` missing or empty (no `## Latest round` heading)                   | "Run a Codex adversarial review first (`codex-review` skill)"                         |
| 5 | Latest round has a finding without `RESOLVED` / `DEFERRED` / `NOT AN ISSUE`                       | lists the open findings                                                               |
| 6 | Latest round has a `**DEFERRED**` without `→ #N`                                                  | lists the unlinked DEFERRED entries                                                   |

For each: confirm no commit, no push, no PR action happened.

## Happy path — fresh PR

Setup: clean branch with adversarial review fully closed; no PR exists yet.

1. Confirm `git status` is clean on the branch HEAD.
2. Invoke `/finish-branch`.
3. Expect:
   - Tests run and pass.
   - `git log -1 -- .claude/REVIEW_ADVERSARIAL.md .claude/REVIEW_PLAN.md .claude/SPEC.md` shows the new "Reset agent working files to main's skeleton" commit.
   - `git diff main..HEAD -- .claude/REVIEW_ADVERSARIAL.md` is empty (skeleton matches main).
   - `git rev-parse HEAD` == `git rev-parse @{u}` (everything pushed).
   - `gh pr view --json state,isDraft` returns `OPEN` and `false`.
   - PR body contains: Summary, Approach, Adversarial review counts, Test plan checklist.

## Happy path — existing draft PR

Setup: same as above but with `gh pr create --draft` already run.

1. Invoke `/finish-branch` (without `--draft`).
2. Expect:
   - Skeleton-reset commit and push.
   - `gh pr view --json isDraft` returns `false` (draft → ready transition).
   - A new comment posted on the PR with the audit-trail summary.

## Happy path — existing ready PR

Setup: same as above with a ready PR already open.

1. Invoke `/finish-branch`.
2. Expect:
   - Skeleton-reset commit and push.
   - PR remains in `OPEN` `isDraft=false` state (no transition).
   - A new comment posted summarizing what changed since the last review round.

## Edge cases

| # | Setup                                                          | Expected behavior                                                              |
|---|----------------------------------------------------------------|--------------------------------------------------------------------------------|
| E1 | Branch never touched the three skeleton files                 | Step 1 detects no diff after `checkout main --`; skip the commit, push as-is, open PR |
| E2 | `--draft` arg passed                                          | Skip "convert to ready"; if creating fresh, use `gh pr create --draft`         |
| E3 | `gh` not installed / not authed                                | Print the would-be PR title + body; user creates manually; do NOT use API     |
| E4 | Pre-commit hook reformats the skeleton files on commit         | Commit succeeds with the reformatted content; do NOT `--amend`                 |
| E5 | Upstream branch not yet pushed (`git push -u` needed)          | Detect missing upstream; use `git push -u origin <branch>`                     |

## Trigger-announcement integration

Verify the prompts surface at the right moments:

- After `address-review` Phase 3 wraps up with a merge-ready branch: the final summary contains the literal line *"Branch is merge-ready. Want me to run `/finish-branch` ..."*
- After `spec-development` resume ticks the last item AND the adversarial review has no open findings: summary contains the literal line *"All SPEC items are ticked and the adversarial review has no open findings. Want me to run `/finish-branch` ..."*
- After `spec-development` resume ticks the last item AND the adversarial review still has open findings: summary contains the *"open findings"* version pointing at `address-review`, NOT the `/finish-branch` prompt.
- After `spec-development` resume ticks the last item AND the adversarial review has not yet been run: summary contains the *"Want to run a Codex adversarial review now?"* prompt, NOT the `/finish-branch` prompt.

Confirm none of those announcements result in auto-invocation — each waits for explicit user yes.
