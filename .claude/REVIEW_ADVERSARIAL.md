# Adversarial Review

## Latest round — 2026-06-29
- Base ref: main
- Focus sent to Codex: Round 2 of the astro-mobile KMP scaffold + Claude Code harness review. Anchored on round 1's fix — the SHA-pinned `gradle/actions/dependency-submission` job added to security.yml (push-to-main, `contents: write`) feeding GitHub's Dependency Graph + the renovate.json `osvVulnerabilityAlerts` baseline — asking Codex to confirm closure and the new job's correctness (permissions scoping, `if: github.event_name == 'push'` gating, SHA pinning), plus the standing scaffold scope (Gradle gate, git hooks, iOS lint, SHA-pinned workflows, security/MCP/renovate config, vetted skills) and KMP concerns.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

No-ship: the round-2 SCA fix creates post-merge visibility, but it still does not provide a PR-blocking dependency vulnerability gate.

Findings:
- [high] Gradle SCA still does not block vulnerable dependency changes before merge (.github/workflows/security.yml:237-242) — **DEFERRED → #100**
  Valid (the `dependency-submission` job is push-to-main only, and osv-scanner has no lockfile/SBOM to scan, so no PR blocks on a vulnerable dep). Deferred rather than fixed on this M-1 scaffold branch: this is the diminishing-returns spiral (an edge in round 1's fix, not a new issue), round 1's `dependency-submission` job is independently valuable and kept (Codex agrees), the vulnerable-dep scenario is low-rarity against a frozen scaffold dependency set and is now *detected* post-merge via the Renovate OSV alerts round 1 activated, and the real fix (a CycloneDX/SPDX SBOM pipeline run by osv-scanner at PR time) is disproportionate build/CI surface for a scaffold and belongs with the first real product dependencies in M-2. Tracked at https://github.com/jitrapon/astro-mobile/issues/100.

<!-- previous-rounds:start -->
## Previous rounds

### 2026-06-29 — base main
- Status when archived: Sole finding RESOLVED in commit 40d0303 — added the SHA-pinned `dependency-submission` SCA channel (rejected the lockfile route as un-idiomatic for Gradle).
- Focus sent to Codex: Round 1 — scaffolds the astro-mobile KMP repo + Claude Code dev harness to parity with astro-web (Gradle build gate, `gradle/libs.versions.toml`, git hooks, iOS lint, inert Compose Stability Analyzer, SHA-pinned GitHub Actions, `.mcp.json`/`renovate.json`/`.semgrep`/`.betterleaks.toml`/`osv-scanner.toml`/`check-action-pins.sh`, vetted third-party KMP + coroutines + Android CLI skills, README/CONTRIBUTING). Watch shell-script correctness, config-cache-safe Gradle wiring, availability-guarding/self-skip, SHA-pin/security-gate correctness, never-firing rules, and KMP concerns.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

No-ship: the security scaffold includes a blocking SCA job that can currently pass without auditing the Gradle dependency graph.

Findings:
- [high] SCA gate passes when no dependency lockfiles exist (.github/workflows/security.yml:216-222) — **RESOLVED**
  Codex's claim was valid: with no committed lockfile, the osv-scanner `sca` job passed without auditing the dependency graph. Codex's recommended fix (commit Gradle lockfiles) was rejected as un-idiomatic — the Gradle ecosystem, unlike npm/pnpm, does not commit lockfiles, and this repo already pins exact versions, so a lockfile would add KMP per-configuration fragility + Renovate lockfile-maintenance churn for little benefit. **Fixed via the idiomatic Gradle SCA channel instead:** added a SHA-pinned `dependency-submission` job (`gradle/actions/dependency-submission@3f131e86… # v6.2.0`, `contents: write`, push-to-main only) that resolves the full build dependency graph and submits it to GitHub's Dependency Graph — powering Dependabot/OSV alerts and activating the `osvVulnerabilityAlerts: true` baseline already configured in `renovate.json`, with no lockfile required. This audits the real `:androidApp` Compose/AndroidX transitive graph today. The osv-scanner job stays as a dormant complementary lockfile/SBOM scanner; its job comment and `osv-scanner.toml` were updated to document the live channel. Verified: `actionlint` OK, `check-action-pins.sh` passes, all `uses:` 40-hex SHA-pinned.

Next steps:
- Enable and commit Gradle lockfiles or an equivalent resolved dependency manifest for all modules.
- Re-run the security workflow and verify the SCA job fails if lockfiles are missing.
<!-- previous-rounds:end -->
