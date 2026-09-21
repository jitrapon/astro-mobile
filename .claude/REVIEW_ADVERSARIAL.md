# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-21
- Base ref: main
- Focus sent to Codex: This branch makes a Kotlin/Native partial-linkage problem in the KMP `:shared` module fail every iOS framework link instead of silently linking a runtime-throwing stub: `shared/build.gradle.kts` sets `-Xpartial-linkage-loglevel=ERROR` over `targets.withType<KotlinNativeTarget>()` compilerOptions, and adds a configuration-cache-safe gate task `verifyNativeLinksFailOnPartialLinkage` (wired into `:shared:check`, classified into `verifyIos` in the root `build.gradle.kts`, self-skipping off macOS) that enumerates every `KotlinNativeLink` task and fails unless each one's configured free compiler args carry the ERROR level and no other `-Xpartial-linkage…` argument, or if it finds zero framework links; `.claude/CLAUDE.md` documents both. Challenge whether the flag really reaches every link, whether the gate can pass vacuously or read a different argument source than the linker executes (per-binary overrides, `kotlin.native.linkArgs`, KGP upgrades, configuration cache), whether the macOS self-skip or the CI partition leaves a gap, and whether the docs claim more than the build enforces. Project context: Kotlin Multiplatform Mobile app (shared business logic + Jetpack Compose on Android, SwiftUI on iOS); watch for expect/actual correctness, platform behavior divergence, coroutine/concurrency and main-thread-safety issues, null handling, state-management bugs, and missing cross-platform test coverage.

# Codex Adversarial Review

Target: branch diff against main
Verdict: approve

No material blocker found in the diff against main. The installed Kotlin plugin consumes the toolOptions inspected by the gate; enumeration, conflict checks, and macOS CI wiring support the intended enforcement. Builds and configuration-cache reuse were not rerun in this read-only review.

No material findings.

**Round state:** 0 findings — nothing to resolve, defer, or dismiss. No open findings remain.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
