# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-08-29

- Base ref: `main`
- Focus sent to Codex: This branch closes a CI gap: the macOS `verify-ios` job linted Swift and
  linked the shared framework but never compiled the iOS app target, so a break at the
  Kotlin-to-Swift facade could merge undetected. It commits a shared `iosApp` Xcode scheme, settles
  a credential-free `xcodebuild build` for a generic iOS Simulator destination
  (CODE_SIGNING_ALLOWED=NO, ARCHS=arm64, explicit derivedDataPath), wires it into
  `.github/workflows/ci.yml` immediately after `./gradlew verifyIos` and deliberately outside the
  `verifyCheckPartition` drift guard, and updates `.claude/CLAUDE.md` + `README.md`; no Kotlin,
  Swift, or Gradle source changed. Context: Kotlin Multiplatform Mobile app (shared business logic +
  Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform
  behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling,
  state-management bugs, and missing cross-platform test coverage.

Target: branch diff against main
Verdict: needs-attention

Do not ship until the new required macOS step passes on an actual clean PR runner; repository checks alone do not validate this runner-dependent integration.

Findings:
- [medium] Required iOS build gate lacks clean-host CI evidence (.github/workflows/ci.yml:127-136)
  This unconditional step can block every PR if hosted Xcode, simulator SDK availability, signing overrides, or the Gradle embed phase behave differently from the developer machine. The branch's own SPEC explicitly leaves the authoritative clean-environment check unchecked, so there is no evidence that this exact workflow step runs and succeeds on `macos-latest` or that the existing SwiftLint, `verifyIos`, and release-link coverage remain healthy in the same job.
  Recommendation: Run the branch in a real PR on `macos-latest` and inspect the log for the xcodebuild step, `BUILD SUCCEEDED`, no signing/team failure, successful `verifyIos` and release-link steps, and actual SwiftLint execution. Resolve any runner-specific failure before merging.

Next steps:
- Obtain and review the clean PR `verify-ios` log, then rerun after any fix.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
