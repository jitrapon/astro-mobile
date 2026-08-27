# Specification: Compile the iOS app in CI

> Per-branch working file owned by the `spec-development` skill. Each branch
> overwrites the section bodies; this file in `main` is a skeleton that
> documents the canonical structure so every branch follows the same shape.

## 1. Overview

CI formats and lints the iOS app's Swift sources and links the shared Kotlin framework, but it never
compiles the Swift app target. Swift type errors at the Kotlin-facade boundary, framework-embedding
failures, and app-target build regressions can therefore all merge undetected. This branch closes
that gap by adding a simulator `xcodebuild build` of the `iosApp` scheme to the macOS verification
job.

## 2. Objective

The macOS CI job compiles the iOS app against the freshly produced shared framework, on a clean
clone and with no credentials, so a Swift break at the Kotlin-to-Swift boundary fails CI instead of
merging.

## 3. Requirements & Context

**Origin.** Deferred from a Codex adversarial review (round 1, finding 5 of 5, severity medium) on
the shared-data-layer branch. The finding was accepted as accurate. It was deferred because that
branch carried a compensating manual gate — its SPEC required a local simulator build to pass and a
simulator launch to be confirmed — and because standing up a headless `xcodebuild` in CI is
self-contained infrastructure work outside a data-layer branch's scope. The Swift that shipped there
is verified; what is missing is protection for *future* changes.

**Deliverables named by the issue:**

- An `xcodebuild build` step for the `iosApp` scheme on a simulator destination in the macOS
  verification job, ordered after the shared framework is produced.
- A shared Xcode scheme committed to the repository if one is not already, such that
  `xcodebuild -list` resolves `iosApp` on a clean clone.
- Signing disabled or ad-hoc for the simulator build, so the CI runner needs no credentials.
- A decision on whether the new step belongs inside the `verifyIos` Gradle aggregate — and therefore
  under the CI partition drift guard — or alongside the existing release-framework link as
  artifact-build coverage outside that guard. The latter matches how the existing per-job assemble
  and link steps are classified.

**Constraints:**

- **Xcode Cloud is out of scope** (decided 2026-08-27). It was raised on the issue as an
  alternative; the decision is to add the step to the existing GitHub Actions macOS job now, and to
  revisit Xcode Cloud only when real signing and distribution needs arrive with the BFF-wiring
  milestone.
- The macOS runner costs roughly ten times the Linux runner per minute, and the two-job CI partition
  exists specifically to keep Mac-bound work minimal. Added wall-clock on that job is a real cost —
  reuse the iOS Kotlin compilation the job already performs rather than provoking a second one.
- Whichever partition classification is chosen, the drift guard must pass: local `./gradlew check`
  and the union of the two CI aggregates must not silently diverge.
- The iOS framework's public surface must not change to accommodate the build; this branch adds
  verification, not API.

## 4. Implementation Plan and Progress Tracking (for agent)

- [ ] **1. Commit a shared `iosApp` Xcode scheme.** Promote the currently user-scoped scheme to
      `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` so the scheme resolves for any
      contributor and for CI. `.gitignore` excludes only `xcuserdata/`, so no ignore-rule change is
      needed — confirm that rather than assume it. Strip anything user- or machine-specific from the
      promoted file. Note this also repairs `peripheryScan`, whose config already names a scheme it
      cannot resolve on a fresh clone.
- [ ] **2. Establish the credential-free simulator build invocation.** Settle the exact `xcodebuild`
      command: the `iosApp` scheme, `Debug` configuration, a **generic** iOS Simulator destination
      (no booted device and no pinned runtime version, so a runner-image bump cannot break it), an
      explicit derived-data path, and signing disabled via build-setting overrides. The project pins
      `CODE_SIGN_STYLE = Automatic` with a concrete development team, so the overrides are what keep
      a credential-less runner from being asked for one; the shared framework's embed-and-sign build
      phase must still succeed with signing off. Verify locally from a cold derived-data directory.
- [ ] **3. Prove the gate actually catches a Kotlin-facade break.** Temporarily introduce a Swift
      type error against the shared framework's facade, confirm the item-2 command fails with that
      error, then revert and confirm it passes again. Without this the branch ships a step that is
      green for the wrong reason — the whole point of the issue is a failure mode that currently
      merges undetected, so it has to be observed failing once.
- [ ] **4. Add the build step to the `verify-ios` job.** Wire the item-2 command into
      `.github/workflows/ci.yml` after the existing release-framework link step, with a comment
      recording two decisions: (a) it sits **outside** the partition drift guard, as artifact/compile
      coverage beyond `check` — the same classification the Android assemble and the framework link
      already carry; and (b) it is a direct `xcodebuild` step rather than a Gradle task, because the
      app target's first build phase itself shells out to `./gradlew`, so wrapping it in Gradle would
      nest one build inside another and contend for the same project locks. Keep default output (no
      log-formatter dependency) so a failure stays diagnosable.
- [ ] **5. Confirm the CI partition is undisturbed.** The new step adds no task to `check`, so the
      drift guard and both aggregates must be byte-for-byte unaffected in what they run. Verify
      rather than reason about it.
- [ ] **6. Update the canonical project doc.** `.claude/CLAUDE.md` enumerates exactly what the
      `verify-ios` job runs and which steps are deliberately outside the guard — both statements
      become stale with item 4. Update that bullet and add a command-table row for the local
      simulator app build, so the invocation contributors need is discoverable without reading YAML.

## 5. Testing & Validation (for agent)

- [ ] **1.** `git ls-files` lists the shared scheme. Then simulate a fresh clone —
      `git archive HEAD | tar -x -C <scratchpad>/clean` — and run `xcodebuild -list -project` against
      the extracted project: `iosApp` must appear under `Schemes`. Checking the working tree alone
      would pass on the ignored user scheme and prove nothing.
- [ ] **2.** The settled command reports `** BUILD SUCCEEDED **` from a cold derived-data directory,
      and the log shows no code-signing step and no "requires a development team" diagnostic. Re-run
      once more to confirm it is repeatable rather than dependent on warm local state.
- [ ] **3.** With the injected Swift error the command exits non-zero and the log names that error;
      after reverting, the command succeeds and `git status` is clean. Record both outcomes.
- [ ] **4.** `.github/workflows/ci.yml` parses as YAML, and `actionlint` reports no findings if it
      resolves on PATH. Read the job back to confirm step ordering. The authoritative check is the
      `verify-ios` job going green on the PR — local verification cannot cover the runner image, so
      do not claim CI coverage until that run exists.
- [ ] **5.** `./gradlew verifyCheckPartition` passes, and `./gradlew check` completes green with the
      same task set as before the branch.
- [ ] **6.** Re-read the edited sections for accuracy against the final YAML, and confirm the
      documented local command is the one item 2 settled on — copy-paste it and run it.

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

- https://github.com/jitrapon/astro-mobile/issues/116
- https://github.com/jitrapon/astro-mobile/issues/115 (the shared-data-layer issue whose review deferred this finding)
