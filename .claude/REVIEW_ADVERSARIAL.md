# Adversarial Review

## Latest round — 2026-06-29
- Base ref: main
- Focus sent to Codex: Scaffolds the astro-mobile KMP repo + Claude Code dev harness to parity with astro-web — the Gradle build gate (ktfmt + Detekt + `checkNoDetektBaseline`/`ForbiddenSuppress` + `verifyKtfmtAlignment`), `gradle/libs.versions.toml`, git hooks (`.githooks/pre-commit`, `.githooks/pre-push`) via `installGitHooks`/`resolveLintTools`, iOS lint (swift-format/SwiftLint/Periphery), inert Compose Stability Analyzer, SHA-pinned GitHub Actions (ci/security/dormant security-review), `.mcp.json`/`renovate.json`/`.semgrep/astro-mobile.yml`/`.betterleaks.toml`/`osv-scanner.toml`/`check-action-pins.sh`, vetted third-party KMP + coroutines + Android CLI skills, README/CONTRIBUTING. Watch shell-script correctness (partial-staging aborts, version-floor gates, push-range computation), config-cache-safe Gradle wiring, availability-guarding/self-skip on toolchain-less hosts, SHA-pin/security-gate correctness, never-firing semgrep/secret-scan rules; plus KMP concerns (expect/actual, coroutine/main-thread-safety, null handling, cross-platform test coverage).

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

No-ship: the security scaffold includes a blocking SCA job that can currently pass without auditing the Gradle dependency graph.

Findings:
- [high] SCA gate passes when no dependency lockfiles exist (.github/workflows/security.yml:216-222) — **RESOLVED**
  Codex's claim was valid: with no committed lockfile, the osv-scanner `sca` job passed without auditing the dependency graph. Codex's recommended fix (commit Gradle lockfiles) was rejected as un-idiomatic — the Gradle ecosystem, unlike npm/pnpm, does not commit lockfiles, and this repo already pins exact versions, so a lockfile would add KMP per-configuration fragility + Renovate lockfile-maintenance churn for little benefit. **Fixed via the idiomatic Gradle SCA channel instead:** added a SHA-pinned `dependency-submission` job (`gradle/actions/dependency-submission@3f131e86… # v6.2.0`, `contents: write`, push-to-main only) that resolves the full build dependency graph and submits it to GitHub's Dependency Graph — powering Dependabot/OSV alerts and activating the `osvVulnerabilityAlerts: true` baseline already configured in `renovate.json`, with no lockfile required. This audits the real `:androidApp` Compose/AndroidX transitive graph today. The osv-scanner job stays as a dormant complementary lockfile/SBOM scanner; its job comment and `osv-scanner.toml` were updated to document the live channel. Verified: `actionlint` OK, `check-action-pins.sh` passes, all `uses:` 40-hex SHA-pinned.

<!-- previous-rounds:start -->
## Previous rounds

<!-- previous-rounds:end -->
