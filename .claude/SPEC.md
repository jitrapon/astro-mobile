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
      phase must still succeed with signing off. Verify locally from a genuinely cold state: the
      generated framework lives under `shared/build/`, *outside* Xcode's derived data, so clearing
      derived data alone leaves a previously-built framework in place and can mask whether this
      checkout produced the one the app linked against. Clear both.
- [ ] **3. Prove the gate actually catches a Kotlin-facade break.** The mutation must be one only
      the *generated framework header* can reject, or the test proves nothing beyond "Swift
      compiles": misspell a member on a Kotlin-declared type at an existing call site — e.g.
      `DependencyGraph.shared.calendarScreenRepository()` or `repository.fetchCalendarScreen(...)`
      in `ContentView.swift`, or `DependencyGraph.shared.start(baseUrl:)` in `iOSApp.swift`. A
      Swift-only error (a bad literal, an unused variable) would satisfy a naive non-zero-exit check
      while leaving the Kotlin-to-Swift binding completely untested — which is the exact regression
      class this branch exists to catch. Confirm the item-2 command fails, then revert and confirm
      it passes again.
- [ ] **4. Add the build step to the `verify-ios` job.** Wire the item-2 command into
      `.github/workflows/ci.yml` **immediately after `verifyIos`**, ahead of the release-framework
      link. Placement is not a dependency: no existing step produces the artifact this build needs.
      `linkReleaseFrameworkIosArm64` is a *device-release* framework, and the app target's own build
      phase resolves `$(CONFIGURATION)`/`$(SDK_NAME)` and invokes Gradle to produce the *debug
      simulator* framework (`linkDebugFrameworkIosSimulatorArm64`) itself. Placing the step right
      after `verifyIos` is therefore about cost, not order — it runs while the `iosSimulatorArm64`
      klibs that step just compiled are hot in the Gradle/Konan caches, so the added work is a link
      rather than a fresh compile. State that explicitly in the step comment, alongside two
      decisions: (a) it sits **outside** the partition drift guard, as artifact/compile coverage
      beyond `check` — the same classification the Android assemble and the framework link already
      carry; and (b) it is a direct `xcodebuild` step rather than a Gradle task, because the app
      target's first build phase itself shells out to `./gradlew`, so wrapping it in Gradle would
      nest one build inside another and contend for the same project locks. The step must be
      unconditionally blocking — no `if:`, no `continue-on-error:` — and keep default output (no
      log-formatter dependency) so a failure stays diagnosable.
- [ ] **5. Confirm the CI partition is undisturbed, and bound the branch's blast radius.** The new
      step adds no task to `check`, so the drift guard and both aggregates must be unaffected in what
      they run. Two constraints make that provable cheaply rather than by re-deriving `main`'s task
      closure: this branch must touch **no** `*.gradle.kts` (the partition list is the only thing
      that could move a task between halves) and **no** `.kt` / `.swift` source (§3 forbids changing
      the framework's public surface — an implementation must not be able to make the app compile by
      editing the facade). Assert both from the branch diff, then run the guard.
- [ ] **6. Update the docs named in §7.** `.claude/CLAUDE.md` enumerates exactly what the
      `verify-ios` job runs and which steps are deliberately outside the guard — both statements
      become stale with item 4. Update that bullet and the outside-the-guard sentence, and add a
      command-table row for the local simulator app build so the invocation is discoverable without
      reading YAML. `README.md` mirrors that command table and needs the same row. Re-read
      `CONTRIBUTING.md` and change it only if its Xcode prerequisite line no longer covers the new
      command.

## 5. Testing & Validation (for agent)

- [ ] **1.** `git ls-files` lists the shared scheme. Then simulate a fresh clone —
      `git archive HEAD | tar -x -C <scratchpad>/clean` — and run `xcodebuild -list -project` against
      the extracted project: `iosApp` must appear under `Schemes`. Checking the working tree alone
      would pass on the ignored user scheme and prove nothing.
- [ ] **2.** The settled command reports `** BUILD SUCCEEDED **` after both the derived-data
      directory and the generated framework output under `shared/build/` have been removed, and the
      log shows the framework being rebuilt, no code-signing step, and no "requires a development
      team" diagnostic. Re-run to confirm repeatability. A local run can only approximate a fresh
      checkout; the CI job on the PR is the authoritative clean-environment evidence, which is why
      §5.4 requires reading its log rather than trusting a local pass.
- [ ] **3.** With the injected error the command exits non-zero **and the diagnostic names the
      Kotlin-declared symbol** (`DependencyGraph`, `CalendarScreenRepository`, or whichever was
      mutated) — a non-zero exit alone does not distinguish a facade break from any other Swift
      error, so the symbol in the message is the evidence. After reverting, the command succeeds and
      `git status` is clean. Record both logs.
- [ ] **4.** `.github/workflows/ci.yml` parses as YAML, and `actionlint` reports no findings if it
      resolves on PATH. Assert mechanically that the workflow's command is byte-for-byte the command
      item 2 settled on, and that the step carries no `if:` and no `continue-on-error:` — a green job
      proves nothing about a step that was skipped or made advisory. Assert the job's **full** run
      step sequence, not just the new entry: item 4 reorders steps, and an edit that displaced
      `./gradlew verifyIos` or `:shared:linkReleaseFrameworkIosArm64` would pass every other check
      here while silently dropping the iOS tests, the Swift gates, or the release-framework link. The authoritative check is the
      `verify-ios` job on the PR, and "green" is not sufficient evidence: read that job's log and
      confirm the step ran and emitted `** BUILD SUCCEEDED **`. Local verification cannot cover the
      runner image, so do not claim CI coverage until that log exists — and confirm in that same log
      that `verifyIos` and the release-framework link ran and succeeded alongside it. Record the step's wall-clock
      duration from the same run — §3 makes added time on the macOS runner a real cost, and this is
      the only point at which it becomes measurable.
- [ ] **5.** `git diff main...HEAD --name-only` lists no `*.gradle.kts` and no `*.kt` / `*.swift`
      path — run this *after* item 3's injected error is reverted, so the deliberate break cannot
      hide in it. With the diff so bounded, `./gradlew verifyCheckPartition` passing and
      `./gradlew check` completing green is sufficient evidence the partition is unchanged: no edit
      capable of moving a task between the two halves is present in the branch.
- [ ] **6.** Re-read the edited sections against the final YAML for accuracy. Copy the documented
      command out of each table and run it — a doc command that has drifted from item 2's is worse
      than none. Scope the cross-table assertion to the **newly added row only** — the two tables
      already diverge on `main` (`README.md` carries "Install Android app" and "Build shared module
      only"; `.claude/CLAUDE.md` carries the CI-aggregate and contract-parity rows), so requiring
      them to match would either fail unsatisfiably or drag an unrelated reconciliation into this
      branch. Confirm no doc file outside §7's list was touched.

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

- **`.claude/CLAUDE.md`** — two places go stale the moment the workflow step lands: the
  "Continuous integration" section's `verify-ios` bullet, which enumerates exactly what that job
  runs, and the sentence naming which per-job steps sit deliberately outside the
  `verifyCheckPartition` guard. Also add a Build & Run command-table row for the local simulator app
  build.
- **`README.md`** — carries a mirrored Commands table (same rows, own formatting). It needs the same
  new row, or the two tables drift. It contains no CI-split description, so nothing else there
  changes.
- **`CONTRIBUTING.md`** — no change expected. Its prerequisites line already names Xcode as required
  for the iOS app and Swift tooling, which covers the new command; re-read it once the command is
  settled and update only if that stops being true.
- No `.claude/LOCAL_DEV.md` exists in this repo.

## 8. References

- https://github.com/jitrapon/astro-mobile/issues/116
- https://github.com/jitrapon/astro-mobile/issues/115 (the shared-data-layer issue whose review deferred this finding)
