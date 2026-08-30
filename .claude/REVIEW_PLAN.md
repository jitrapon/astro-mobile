Status: blocking

# Plan Review — PR-blocking SBOM-based Gradle SCA gate

- **Date:** 2026-08-30
- **Base ref:** `main`
- **Target:** `.claude/SPEC.md` §§4–5 (doc-only diff)
- **Focus sent to Codex:** steps missing to satisfy the stated objective, wrong ordering, items too coarse for one resume pass, §5 checks that cannot verify their §4 item, scope drift beyond §§1–3, and regressions the plan would not catch.

## Resolution log

### Round 1 — 2026-08-30 (verdict: needs-attention, 5 high + 1 medium)

| # | Finding | Disposition |
| --- | --- | --- |
| 1 | Plan lets `high` test-only dependencies bypass the gate — §4.2 excluded every `*Test*` configuration while §2 promises any `high`+ Gradle dependency fails the PR | **AGREE** |
| 2 | Root-only plugin application never proven to aggregate every subproject graph; §5 greps too coarse to detect a missing native graph | **AGREE** |
| 3 | `./gradlew :shared:resolvableConfigurations` is an undefined task | **DISAGREE** |
| 4 | `high` threshold enforced only by reviewer discipline — nothing stops a `high`/`critical` entry being added to `osv-scanner.toml` | **AGREE** |
| 5 | Retention of `dependency-submission` is required by §3 but never tested | **AGREE** |
| 6 | The negative test is one-time evidence, leaving no permanent regression guard | **AGREE** |

**1 — AGREE.** The exclusion was the plan quietly narrowing the user-authored objective through a configuration filter. Resolved in the plan's own territory rather than by editing §2: §4.2 now includes the test graphs and excludes only the buildscript `classpath` configuration, matching the `^classpath$` exclusion `dependency-submission` already applies. Coverage now matches §2's plain reading, and narrowing to shipping-only is recorded as a deliberate future amendment to §2 rather than a filter detail.

**2 — AGREE.** §4.1 now requires the aggregation wiring to be written down (root-with-traversal vs. per-module merge, and which task emits the scanned artifact) instead of assumed. §5.3 replaces the aggregate greps with per-graph discriminators — `ktor-client-darwin` for iOS, an `androidx.compose` release coordinate for `:androidApp:releaseRuntimeClasspath`, `ktor-client-mock` for a test graph — so a BOM missing one graph fails the item.

**3 — DISAGREE (factually wrong; verified by execution).** `resolvableConfigurations` is a built-in Gradle diagnostic task, in the same family as `dependencies` and `outgoingVariants`; it is not declared in any build script, which is why grepping the repo found nothing. Both commands were run in this repo before the plan was written and again while triaging this finding: `./gradlew :androidApp:resolvableConfigurations` prints `Configuration releaseRuntimeClasspath` and exits `BUILD SUCCESSFUL`, and `./gradlew help --task resolvableConfigurations` resolves it. §5.2 now says so inline so the claim is not re-raised.

**4 — AGREE.** This is exactly the repo's stated posture that a task enforces a convention rather than a reviewer (`checkNoDetektBaseline`, `verifyKtfmtAlignment`, `verifyCheckPartition`). Added as §4.7: fail if any `[[IgnoredVulns]]` entry names a `high`/`critical` advisory, with §5.7 proving the guard fails when one is introduced.

**5 — AGREE (scoped to verification, not new machinery).** §3 requires retention, so it needs a check. Added §4.11 / §5.11 as static assertions over the job's `if:`, `permissions:`, action SHA, and `DEPENDENCY_GRAPH_EXCLUDE_CONFIGURATIONS` value, plus a post-merge confirmation. Codex's observation that §5.10 could never show this was correct: the job is skipped on pull requests by design, so a PR run is structurally incapable of proving it.

**6 — AGREE.** The strongest finding. The real dependency graph is clean, so every permanent check passes even if a later edit stops invoking the scanner. Added §4.6: a committed vulnerable-SBOM fixture scanned through the identical command and assertion path, expecting a non-zero exit — the same posture as the repo's `semgrep --test` fixture pinning `.semgrep/coroutines.kt`. The one-time vulnerable pin is retained as supplemental evidence, since it exercises the real generator the fixture bypasses.

**Net effect:** §4 grew from 10 items to 13, §5 likewise. Three of the additions (fixture self-test, severity guard, retention assertions) convert reviewer discipline into mechanical enforcement, which is the convention this repo already applies to every other gate.
