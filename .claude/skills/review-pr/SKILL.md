---
name: review-pr
description: Fetch and summarize PR review comments from Codex or other reviewers. Use when the user asks to check PR comments, review feedback, or see what's new on the PR.
argument-hint: [pr-number]
allowed-tools: Bash, Read, Grep, Glob
---

Fetch the latest PR review comments and present a summary.

## Steps

1. Resolve the PR number, then fetch comments via `gh`. If `$ARGUMENTS` contains a PR number, use it; otherwise auto-detect from the current branch with `gh pr view --json number --jq .number`.

   ```bash
   PR="${ARGUMENTS:-$(gh pr view --json number --jq .number)}"
   OWNER_REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
   # Inline (diff-anchored) review comments:
   gh api "repos/$OWNER_REPO/pulls/$PR/comments" --paginate \
     --jq '.[] | {path, line, body, user: .user.login, in_reply_to: .in_reply_to_id}'
   # Top-level issue/PR conversation comments:
   gh api "repos/$OWNER_REPO/issues/$PR/comments" --paginate \
     --jq '.[] | {body, user: .user.login}'
   ```

   If `gh` is unavailable or unauthenticated, stop and tell the user to install / `gh auth login`.

2. Parse the JSON output. For each comment extract:
   - Priority badge (P1, P2, etc.)
   - Title (the bold text at the start of the body)
   - File path and line number if available
   - Whether it looks like a "Fixed in..." reply (meaning already addressed)

3. Present a summary table grouped by status:
   - **Unaddressed** — comments with no "Fixed in" reply
   - **Already addressed** — comments that have a corresponding fix reply

4. For unaddressed comments, show:
   - Priority level
   - One-line summary of what the comment asks for
   - Which file/area is affected

5. Do NOT take any action on the comments — just report. Let the user decide what to do next.
