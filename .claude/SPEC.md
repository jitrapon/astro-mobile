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

<Filled by `spec-development` in plan mode. GitHub-style checkboxes (`- [ ]`), one item per concrete task small enough to finish in a single resume pass.>

## 5. Testing & Validation (for agent)

<Filled by `spec-development` in plan mode. Each item pairs 1:1 with a §4 item: the test/build/lint command that verifies it.>

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

- https://github.com/jitrapon/astro-mobile/issues/116
- https://github.com/jitrapon/astro-mobile/issues/115 (the shared-data-layer issue whose review deferred this finding)
