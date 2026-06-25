---
name: address-comment
description: Address a single PR review comment end-to-end -- understand the issue, fix code, adjust tests, build, verify, and commit.
argument-hint: <comment-summary-or-number>
disable-model-invocation: true
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, Agent
---

Address a single PR review comment through the full fix cycle.

## Input

`$ARGUMENTS` is either:
- A comment number/index from a previous `/review-pr` output
- A description of the issue to fix

## Steps

1. **Understand the comment**: Read the relevant source files mentioned in the comment. Understand the current behavior and what the reviewer is asking for.

2. **Plan the fix**: Before writing code, identify:
   - Which file(s) need to change
   - Whether existing tests need updating
   - Whether new tests are needed
   - The minimal change that addresses the comment

3. **Implement the fix**: Make the code changes. Follow project conventions from CLAUDE.md:
   - Put platform-agnostic business logic in `shared/src/commonMain`; use `expect`/`actual` for platform abstractions, not `if (platform)` branches.
   - Model data with Kotlin `data class`es; use the sealed `Result<T>` (`Success`/`Error`) for type-safe error handling.
   - Use Kotlin coroutines for async work; keep suspend functions main-safe (dispatch off the main thread where appropriate).
   - Android UI is Jetpack Compose; iOS UI is SwiftUI consuming the `shared` framework — keep UI out of the shared module.

4. **Adjust tests**: If existing tests break or new coverage is needed:
   - Shared logic: `kotlin.test` assertions in `commonTest` (runs on both Android and iOS).
   - Android-specific: place under `androidTest`; iOS-specific: under `iosTest`.
   - Keep new tests in the source set that matches the code under test — don't push common logic tests into a platform set.

5. **Build and verify**: Run `./gradlew test` (or `clean test` if new files were added). All tests must pass before proceeding.

6. **Commit**: Create a commit with a descriptive message explaining the fix. Do NOT push unless explicitly asked.

## Important
- Only address ONE comment per invocation. Do not move on to the next comment.
- If the fix requires clarification from the user, stop and ask rather than guessing.
- If tests fail after the fix, diagnose and fix the test failures before committing.
