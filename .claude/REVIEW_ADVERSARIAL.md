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
Verdict: resolved — the clean-runner evidence Codex asked for was obtained and is recorded below.

Codex's condition for shipping was that the new required macOS step pass on an actual clean PR runner. It has: PR #122, run `33261738641`.

Findings:
- [medium] Required iOS build gate lacks clean-host CI evidence (.github/workflows/ci.yml:127-136)
  This unconditional step can block every PR if hosted Xcode, simulator SDK availability, signing overrides, or the Gradle embed phase behave differently from the developer machine. The branch's own SPEC explicitly leaves the authoritative clean-environment check unchecked, so there is no evidence that this exact workflow step runs and succeeds on `macos-latest` or that the existing SwiftLint, `verifyIos`, and release-link coverage remain healthy in the same job.
  Recommendation: Run the branch in a real PR on `macos-latest` and inspect the log for the xcodebuild step, `BUILD SUCCEEDED`, no signing/team failure, successful `verifyIos` and release-link steps, and actual SwiftLint execution. Resolve any runner-specific failure before merging.

  **RESOLVED** — accepted as valid and answered with the evidence it asked for, rather than argued
  down. The finding was not a defect in the change; it was the observation that the branch had no
  clean-host evidence, which was true, and which the branch itself already tracked as its own
  unclosed testing item. Because the workflow fires only on `pull_request`, that evidence could not
  exist before the branch was pushed — so the branch was pushed as a **draft** PR (#122), deliberately
  unmergeable, and the log read before readying it.

  Run `33261738641`, `verify-ios` on `macos-latest`, every point Codex named:
  - The `xcodebuild` step ran unconditionally and emitted `** BUILD SUCCEEDED **`.
  - No `CodeSign` build phase and no development-team diagnostic — the credential-free override set
    behaves on a runner with no signing identity exactly as it does on a machine that has one. The
    only `codesign`-shaped strings present are Xcode's `CODESIGNING_FOLDER_PATH` and
    `CODE_SIGN_CONTEXT_CLASS` environment exports, which are not phases.
  - `./gradlew verifyIos` and `:shared:linkReleaseFrameworkIosArm64` both `BUILD SUCCESSFUL` in the
    same job; the step order on the runner was `verifyIos` → `xcodebuild` → release link.
  - SwiftLint genuinely executed: `swiftlint 0.65.0` installed, and `:swiftFormatCheck` and
    `:swiftLintCheck` both appear as executed Gradle tasks rather than self-skipping. This was the
    sharpest part of the finding — those gates warn-and-skip when their binary is absent, so a green
    job is not by itself evidence they enforced anything.
  - The Gradle embed phase, which Codex flagged as a divergence risk, behaved as designed:
    `compileKotlinIosSimulatorArm64` UP-TO-DATE, `linkDebugFrameworkIosSimulatorArm64` executed, and
    zero `IosX64` tasks — the compilation was reused and the arch pin held.

  Cost, which the finding did not ask for but the branch's own constraints do: the step measures
  **45s** against a ~4m44s `main` baseline for the job.

Next steps:
- None. The log was obtained and reviewed; no runner-specific failure appeared, so there is
  nothing to fix and nothing to rerun.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
