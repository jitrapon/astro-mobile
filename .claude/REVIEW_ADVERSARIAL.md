# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-10-02
- Base ref: main
- Focus sent to Codex: Branch raises Bouncy Castle (bcprov/bcpkix/bcutil) from AGP's 1.80.2 to 1.85 through catalog-versioned, non-strict dependency constraints on the root buildscript classpath and on every Android module's androidLintTool configuration (looked up by name), to close four Dependabot alerts without changing anything that ships; the rest of the diff is docs (SBOM skip-list comment, security.yml dependency-submission comment, CLAUDE.md) plus SPEC verification of signing, Lint and the managed-device release runs. Review whether the constraints actually reach every configuration Dependabot sees, whether they stay floors rather than pins, whether the plugin-id hooks fire for every Android module and fail loudly on AGP changes, and whether any doc claim is inaccurate. Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, and missing cross-platform test coverage.

### Codex Adversarial Review

Target: branch diff against main
Verdict: approve

No material blocker found. Constraints cover the root buildscript and all three current Android modules, permit newer versions, and fail if androidLintTool disappears. This matches Gradle’s documented constraint semantics (https://docs.gradle.org/current/userguide/dependency_constraints.html). Live resolution verification was blocked by read-only sandbox permissions; signing and test results remain repository-recorded evidence.

No material findings.

Next steps:
- Correct the documentation: submission excludes classpath, and ordinary constraints do not become pins when AGP catches up.
- Confirm the next main dependency submission closes all four alerts.

### Assessment (2026-10-02)

Round verdict: approve, no material findings. Codex's next steps make three points:

1. **Ordinary constraints do not become pins when AGP catches up.** **RESOLVED.** `gradle/libs.versions.toml`
   and `.claude/CLAUDE.md` said the constraints "only pin" once AGP selects ≥ 1.85. A plain
   constraint never pins: at that point it selects nothing. Both now say the constraints select nothing and are
   only stale config, which is why the removal condition stands.
2. **Submission excludes classpath.** **NOT AN ISSUE.** Every durable doc already says so:
   `build.gradle.kts` names the Lint graph as what the dependency-graph submission reports, and the
   `security.yml` comment says `^classpath$` is excluded and `androidLintTool` is submitted. The
   only sentence claiming "uploads every configuration" is user prose in SPEC §3. That file resets at
   finish-branch, and its §4 traced-surface note already records the correction.
3. **Confirm the next main dependency submission closes all four alerts.** **NOT AN ISSUE** for
   this branch. It can only be checked after merge; SPEC §5 item 7 records it as out of reach, with the
   item-2/3 scans as the pre-merge proxy.

Tally: 1 resolved, 0 deferred, 2 not an issue. No open findings.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
