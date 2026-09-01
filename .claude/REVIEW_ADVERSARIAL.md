# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-01
- Base ref: main
- Focus sent to Codex: This branch converts the repo's dormant osv-scanner `sca` job into a PR-blocking software-composition gate: a pinned CycloneDX Gradle plugin renders the resolved dependency graph (`:shared` + `:androidApp`, empty `includeConfigs` so test graphs are in scope, only AGP-pinned build tooling skipped via `^classpath$`/`^androidLintTool$`/`^unified-test-platform-.*$`) into an SBOM at `build/reports/cyclonedx/bom.json`, which a fail-closed `scripts/scan-sbom.sh` scans by file path, backed by `scripts/check-sbom-fixture.sh` (a committed vulnerable SBOM that must exit 1) and `scripts/check-ignore-severity.sh` (rates every `osv-scanner.toml` `[[IgnoredVulns]]` id against the OSV API, rejects high/critical and `[[PackageOverrides]] ignore = true`). The SBOM task is deliberately outside `check` and the `verifyCheckPartition` drift guard, and the post-merge `dependency-submission` job is retained unchanged as the Dependency Graph / Renovate inventory channel. Context: Kotlin Multiplatform Mobile app (shared Kotlin logic, Jetpack Compose on Android, SwiftUI on iOS) — but this diff is build/CI tooling and docs only, no product code, so weigh shell-script robustness and quoting, Gradle configuration correctness across the KMP target set, exit-code and fail-closed semantics, and any way the gate could silently stop blocking (wrong artifact, dropped step, suppressed advisory, renamed required check) above app-level concerns.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

NO-SHIP: the suppression policy has supported bypasses, and the required check is controlled by the PR being gated.

Findings:
- [high] Supported vulnerability-only overrides bypass the severity gate (scripts/check-ignore-severity.sh:92-99) — **RESOLVED**
  Confirmed by measurement before fixing, each form added to `osv-scanner.toml` and reverted. `[PackageOverrides.vulnerability]` + `ignore = true`: guard passed AND the Log4Shell fixture scan went from exit 1 to exit 0 — a complete bypass. `id = 'GHSA-…'` (single-quoted) and `"id" = "GHSA-…"` (quoted key): guard passed while osv-scanner honoured the suppression, so a CRITICAL id went unrated. Root cause: the awk header pattern `^\[\[?[A-Za-z_][A-Za-z_0-9]*\]\]?` cannot match a dotted table name, so on `[PackageOverrides.vulnerability]` `match()` returned 0 and the table was set to the empty string — the block's `ignore = true` was then attributed to no table at all.
  Fixed by taking Codex's first recommendation: the line scanner is replaced with a real TOML parser (`python3` + `tomllib`), which collapses every spelling of a construct to one structure and so leaves no syntax for a suppression to hide in. The `[[PackageOverrides]]` walk now finds `ignore = true` at any depth rather than in the two known spots, exempting only `license.ignore` (which filters license findings and cannot hide an advisory). Unparseable TOML, a non-table entry, and a non-string `id` are each emitted as their own record and treated as violations rather than skipped. `python3` joins `curl`/`jq` as a declared dependency.
  Verified over a 12-case matrix: committed config passes; all four measured bypasses now fail; a top-level inline-table `IgnoredVulns = [{id = "…"}]` (which osv-scanner does honour) is caught; the sub-`high` entry is still allowed; unknown id, non-string id, and malformed TOML all fail closed; `license.ignore` alone still passes. `shellcheck -x` clean. `osv-scanner.toml` and `.claude/CLAUDE.md` updated so both describe the nested spelling and the parser choice.
- [high] The required `sca` check can be replaced by the PR it is meant to gate (.github/workflows/security.yml:203-204)
  This workflow runs on `pull_request`, and the `sca` job name is only a convention. A PR can retain `name: sca` while removing the generation/scan steps, changing the scripts, or replacing them with a successful command; the required status check will still be green. The action-pin check validates action references, not the semantics or presence of these shell steps, and the dormant review workflow does not provide an independent enforcement path. GitHub documents that workflows use the version present in the event's commit; see its [workflow documentation](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflows).
  Recommendation: Protect the enforcement workflow, scanner scripts, fixture, and policy file with CODEOWNERS/required review, or run a base-branch-controlled trusted workflow/reusable workflow whose enforcement logic cannot be modified by the PR.
- [medium] Known unresolvable KMP configurations have no completeness guard (build.gradle.kts:585-602)
  The empty `includeConfigs` setting scans resolvable configurations, while the repository already records that `appleMainCInterop`, `iosMainCInterop`, and `nativeMainCInterop` fail to resolve and that the generator tolerates those failures (lines 585-595). The current graph was manually shown to duplicate those coordinates through other target configurations, but no CI assertion enforces that invariant. A future dependency unique to one of these failed configurations can therefore be omitted from the SBOM while the aggregate still contains packages and the scan passes.
  Recommendation: Make unresolved configurations fail generation, or explicitly account for them with an automated independent dependency-set completeness check that fails when any declared coordinate lacks a resolvable SBOM witness.

Next steps:
- Close the suppression-policy bypasses and add regression fixtures.
- Protect the PR-blocking workflow and its enforcement inputs.
- Add an automated guard for unresolved KMP configuration coverage.

<!-- previous-rounds:start -->

## Previous rounds

<!-- previous-rounds:end -->
