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

- [x] **1. Commit a shared `iosApp` Xcode scheme.** Promote the currently user-scoped scheme to
      `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` so the scheme resolves for any
      contributor and for CI. `.gitignore` excludes only `xcuserdata/`, so no ignore-rule change is
      needed — confirm that rather than assume it. Strip anything user- or machine-specific from the
      promoted file. Establish by control run whether a fresh clone resolves `iosApp` *without* the
      committed file — Xcode autocreates an implicit scheme from the target, so `xcodebuild -list`
      can report the scheme on a project that has none committed. Record what the shared file
      actually buys (a deterministic, version-controlled scheme with pinned per-action
      configurations, which `.periphery.yml`'s `schemes: [iosApp]` and the CI invocation both name)
      rather than asserting it repairs a resolution failure.
- [x] **2. Establish the credential-free simulator build invocation.** Settle the exact `xcodebuild`
      command: the `iosApp` scheme, `Debug` configuration, a **generic** iOS Simulator destination
      (no booted device and no pinned runtime version, so a runner-image bump cannot break it), an
      explicit derived-data path, and signing disabled via build-setting overrides. The project pins
      `CODE_SIGN_STYLE = Automatic` with a concrete development team, so the overrides are what keep
      a credential-less runner from being asked for one; the shared framework's embed-and-sign build
      phase must still succeed with signing off. Verify locally from a genuinely cold state: the
      generated framework lives under `shared/build/`, *outside* Xcode's derived data, so clearing
      derived data alone leaves a previously-built framework in place and can mask whether this
      checkout produced the one the app linked against. Clear both.

      **Settled invocation** (run from the repository root; this exact string is what item 4 wires
      into `.github/workflows/ci.yml` and item 6 documents):

      ```bash
      xcodebuild build \
        -project iosApp/iosApp.xcodeproj \
        -scheme iosApp \
        -configuration Debug \
        -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath iosApp/build/DerivedData \
        ARCHS=arm64 \
        CODE_SIGNING_ALLOWED=NO \
        DEVELOPMENT_TEAM=""
      ```

      **The override set is minimal by measurement, not by convention.** On the `iphonesimulator`
      SDK the platform already forces `CODE_SIGN_IDENTITY = -` (ad-hoc, displayed as "Sign to Run
      Locally") and `PROVISIONING_PROFILE_REQUIRED = NO`, overriding the project's
      `CODE_SIGN_STYLE = Automatic`. Building each subset against a fresh derived-data directory
      shows: `CODE_SIGNING_ALLOWED=NO` is the only override with an observable effect, taking the
      build from three `CodeSign` phases to zero. `CODE_SIGNING_REQUIRED=NO` and
      `CODE_SIGN_IDENTITY=""` are inert — passing either alone still ran all three phases with the
      same ad-hoc identity — so both are dropped rather than carried as decoration.
      `DEVELOPMENT_TEAM=""` is kept for a different reason: it is the one setting the project pins
      to a real, personal team, and it is the one question local evidence cannot settle, because
      this machine's Xcode is signed into that team and a CI runner is not. Blanking it costs
      nothing and removes the only credential-shaped input the build still names.

      Disabling signing does not leave the framework unsigned: `codesign -dv` on the embedded
      `shared.framework` reports `adhoc, linker-signed`, which Kotlin/Native's linker emits for
      arm64 independently of any `CodeSign` build phase.

      **`ARCHS=arm64` is the cost decision, not a cosmetic one.** A generic simulator destination
      resolves `ARCHS = arm64 x86_64`, and `--dry-run` on
      `:shared:embedAndSignAppleFrameworkForXcode` confirms two archs pull
      `:shared:compileKotlinIosX64` and `:shared:linkDebugFrameworkIosX64` into the graph — a second
      complete Kotlin/Native target compile that `verifyIos` never produces (it runs
      `iosSimulatorArm64Test`). `ONLY_ACTIVE_ARCH=YES` cannot fix this: Xcode force-resets it to `NO`
      for generic destinations. Pinning the arch keeps the destination generic — no booted device, no
      pinned runtime version — while restricting the build to the one target CI already compiles.
      Note the arch is pinned to match that KMP target, not to match the runner's hardware, so an
      Intel runner would still cross-compile it correctly.

      **`iosApp/build/DerivedData` is already ignored** by `.gitignore`'s `**/build/` rule
      (`git check-ignore -v` confirms), so no ignore-rule change is needed.
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

- [x] **1.** `git ls-files` lists the shared scheme. Then simulate a fresh clone —
      `git archive HEAD | tar -x -C <scratchpad>/clean` — and run `xcodebuild -list -project` against
      the extracted project: `iosApp` must appear under `Schemes`. Checking the working tree alone
      would pass on the ignored user scheme and prove nothing. `-list` output is necessary but not
      sufficient — Xcode reports an autocreated implicit scheme identically, so also assert the
      extracted tree physically contains
      `iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` and contains no `xcuserdata`, and
      run the same `-list` against `main`'s tree to record whether the scheme name resolved there
      too.
- [x] **2.** The settled command reports `** BUILD SUCCEEDED **` after both the derived-data
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
      here while silently dropping the iOS tests, the Swift gates, or the release-framework link.
      Make that concrete rather than judgemental: extract the job's step list from
      `git show main:.github/workflows/ci.yml` and from the branch, and require the two to be equal
      **but for exactly one insertion at a pinned position** — the inserted step's immediate
      predecessor must be `./gradlew verifyIos` and its immediate successor
      `:shared:linkReleaseFrameworkIosArm64`. Counting the insertion without pinning where it landed
      would accept the step before `verifyIos` or after the release link, which is precisely the
      placement item 4 rejects: the adjacency *is* the cache-reuse decision, so an unpinned check
      would leave the cost objective unmet while every other assertion stayed green. That explicitly covers `brew install swiftlint`, whose loss
      would be invisible — `swiftLintCheck` is built to warn-and-skip when its binary is missing, so
      removing the install step turns a gate into a silent no-op under a green build. Confirm from
      the PR log that the Swift gates actually executed rather than emitting their skip message. The authoritative check is the
      `verify-ios` job on the PR, and "green" is not sufficient evidence: read that job's log and
      confirm the step ran and emitted `** BUILD SUCCEEDED **`. Local verification cannot cover the
      runner image, so do not claim CI coverage until that log exists — and confirm in that same log
      that `verifyIos` and the release-framework link ran and succeeded alongside it. Record the step's wall-clock
      duration from the same run — §3 makes added time on the macOS runner a real cost, and this is
      the only point at which it becomes measurable. A bare number means nothing on its own, so
      compare it against the same job's duration on `main` and record the delta, and read the Gradle
      output to confirm the simulator framework *link* ran while the Kotlin compilation from
      `verifyIos` was reused (up-to-date or from-cache) rather than repeated. Deliberately **no**
      hard pass/fail threshold: hosted-runner variance would make a fixed budget a flake source, and
      §3 asks for cost-awareness, not a cost SLO. If the delta is disproportionate, say so and
      revisit item 4's placement rather than silently accepting it.
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
