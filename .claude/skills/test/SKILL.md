---
name: test
description: Build and run tests, then report results. Use after writing or modifying code to verify correctness.
argument-hint: [test filter, e.g. a class/file/test name]
allowed-tools: Bash, Read, Glob
---

Build the project and run tests, then report a pass/fail summary.

## Steps

1. Determine the test command based on arguments:
   - No arguments: `./gradlew test` (runs the JVM/Android unit tests across modules). For the shared module's iOS tests, use `./gradlew :shared:iosSimulatorArm64Test`; run it too when the change touches `commonMain`/`iosMain` so both platforms are covered.
   - If `$ARGUMENTS` contains a test class/file/name filter: `./gradlew test --tests "$ARGUMENTS"`
   - If new test files were recently created (check git status for untracked/new files in `shared/src/commonTest/`, `shared/src/androidTest/`, `shared/src/iosTest/`, or `androidApp/src/test/`): use `./gradlew clean test` instead — Gradle does not always recompile/discover newly-added test sources on an incremental run.

2. Run the command with a 5-minute timeout.

3. If the build **succeeds**:
   - Report the number of tests run (parse from the runner output).
   - Confirm all tests passed.

4. If the build **fails**:
   - Check the HTML report under `shared/build/reports/tests/**/index.html` (or `androidApp/build/reports/tests/**/index.html`) or the Gradle console output for failure details.
   - Report which test(s) failed and the error message.
   - Show the relevant failing assertion or exception.
   - Do NOT attempt to fix the failures — just report them clearly.

5. Keep the output concise. No need to list every passing test.
