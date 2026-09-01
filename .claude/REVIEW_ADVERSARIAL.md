# Adversarial Review

> Per-branch working file owned by the `codex-review` / `address-review` skills.
> Each branch accumulates its rounds here; this file in `main` is an empty
> skeleton. The newest round lives directly under this header; prior rounds are
> demoted into the `Previous rounds` section between the markers below.

## Latest round — 2026-09-01
- Base ref: main
- Focus sent to Codex: This is review round 2 of this branch. Round 1's findings and their disposition, so you can confirm closure rather than re-raise: (a) the severity guard's line-scanning TOML parser had three MEASURED bypasses — `[PackageOverrides.vulnerability] ignore = true` took a critical Log4Shell fixture scan from exit 1 to exit 0, and `id = 'GHSA-…'` / `"id" = "GHSA-…"` let a critical id go unrated — now FIXED by replacing the awk scanner in `scripts/check-ignore-severity.sh` with a `python3` + `tomllib` parser that walks for `ignore = true` at any depth (exempting only `license.ignore`) and emits PARSE/MALFORMED records so unreadable TOML fails closed; (b) "the required `sca` check is defined by the PR it gates" — DEFERRED to issue #126 as a pre-existing repo-wide property with no CODEOWNERS to extend, remedy is governance outside this branch's scope; (c) "no completeness guard for the unresolvable KMP CInterop configurations" — DEFERRED, since those three are commonizer-derived from per-target configurations that do resolve. Please concentrate this round on the NEW parsing code in `scripts/check-ignore-severity.sh`, which is unreviewed and guards the gate's severity threshold: whether any osv-scanner-honoured suppression spelling still escapes it, whether the `license.ignore` exemption is correct, whether the bash/python record protocol (tab-separated, `read -r kind value detail`) can lose or misattribute a record, and whether the added `python3` dependency or its failure modes can turn a real violation into a pass. Branch context: converts the dormant osv-scanner `sca` job into a PR-blocking gate — `:cyclonedxBom` renders the resolved Gradle graph into a CycloneDX SBOM that fail-closed `scripts/scan-sbom.sh` scans by path, with `scripts/check-sbom-fixture.sh` as a committed-vulnerable-SBOM regression guard. Build/CI tooling and docs only, no product code, so weigh shell-script robustness and quoting, exit-code and fail-closed semantics, and any way the gate could silently stop blocking above app-level concerns.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention (as issued by Codex)
Round status after evaluation: 1 resolved, 0 deferred, 0 open.

No-ship: the new fail-closed path can return success while silently skipping every parsed record when Bash cannot create its temporary redirection files.

Findings:
- [high] Bash redirection failure makes the severity guard pass (scripts/check-ignore-severity.sh:96-206) — **RESOLVED**
  Codex's specific reproduction did not reproduce here: on macOS bash 3.2 the guard ran normally with `TMPDIR` set to a nonexistent directory, a read-only directory, and `/dev/null`. Its architectural claim held regardless — nothing distinguished "every record was rated and all were clean" from "the loop body never ran", since `violations` stays 0 and the success message prints either way.
  Auditing the same path turned up a second fragility Codex did not name, and a more reachable one: the parser-failure fallback `|| echo "PARSE<TAB>…"` worked only because the file happened to contain a real tab byte (confirmed with `od -c`). Written the obvious way — `\t` inside a double-quoted string — bash emits two literal characters, `kind` becomes the whole line, no `case` arm matches, and the guard exits 0 with its parser dead. Reproduced in a probe script. The most likely trigger is a `python3` older than 3.11, which has no `tomllib` — stock macOS ships 3.9.
  Fixed in three parts. (1) `python3`'s exit status is captured explicitly and failure exits 1 with a message naming the 3.11+/`tomllib` requirement, replacing the sentinel record smuggled through the output channel — so no invisible whitespace is load-bearing. (2) Records are consumed by splitting on newlines and slicing fields with parameter expansion instead of a here-string-fed `while read`, so no temporary file and no subshell sit between the parser and the policy. (3) A processed-vs-parsed tally fails closed when any record went unrated, which covers the whole class — a skipped loop, a counter lost to a subshell, an unclassifiable record — rather than only the mechanism known today.
  Verified: all 11 round-1 matrix cases still behave correctly; a `python3` without `tomllib` now exits 1 naming the requirement; and, the decisive case, a `[[IgnoredVulns]]` entry naming a CRITICAL advisory is caught under all three hostile `TMPDIR` conditions rather than passing. `shellcheck -x` clean, fixture guard still exits 0 on its required non-zero scan, real SBOM scan still 1 source / 431 packages clean, `./gradlew check` green.

Next steps:
- Fix the redirection/error-propagation path before shipping the gate.

<!-- previous-rounds:start -->

## Previous rounds

### 2026-09-01 — base main
- Status when archived: 1 resolved (commit 26772e6, TOML-parser hardening), 2 deferred (#126 and one deliberate no-issue deferral, commit 255b1fb); 0 open.
- Base ref: main
- Focus sent to Codex: This branch converts the repo's dormant osv-scanner `sca` job into a PR-blocking software-composition gate: a pinned CycloneDX Gradle plugin renders the resolved dependency graph (`:shared` + `:androidApp`, empty `includeConfigs` so test graphs are in scope, only AGP-pinned build tooling skipped via `^classpath$`/`^androidLintTool$`/`^unified-test-platform-.*$`) into an SBOM at `build/reports/cyclonedx/bom.json`, which a fail-closed `scripts/scan-sbom.sh` scans by file path, backed by `scripts/check-sbom-fixture.sh` (a committed vulnerable SBOM that must exit 1) and `scripts/check-ignore-severity.sh` (rates every `osv-scanner.toml` `[[IgnoredVulns]]` id against the OSV API, rejects high/critical and `[[PackageOverrides]] ignore = true`). The SBOM task is deliberately outside `check` and the `verifyCheckPartition` drift guard, and the post-merge `dependency-submission` job is retained unchanged as the Dependency Graph / Renovate inventory channel. Context: Kotlin Multiplatform Mobile app (shared Kotlin logic, Jetpack Compose on Android, SwiftUI on iOS) — but this diff is build/CI tooling and docs only, no product code, so weigh shell-script robustness and quoting, Gradle configuration correctness across the KMP target set, exit-code and fail-closed semantics, and any way the gate could silently stop blocking (wrong artifact, dropped step, suppressed advisory, renamed required check) above app-level concerns.

# Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention (as issued by Codex)
Round status after evaluation: 1 resolved, 2 deferred, 0 open. No finding remains unaddressed.

NO-SHIP: the suppression policy has supported bypasses, and the required check is controlled by the PR being gated.

Findings:
- [high] Supported vulnerability-only overrides bypass the severity gate (scripts/check-ignore-severity.sh:92-99) — **RESOLVED**
  Confirmed by measurement before fixing, each form added to `osv-scanner.toml` and reverted. `[PackageOverrides.vulnerability]` + `ignore = true`: guard passed AND the Log4Shell fixture scan went from exit 1 to exit 0 — a complete bypass. `id = 'GHSA-…'` (single-quoted) and `"id" = "GHSA-…"` (quoted key): guard passed while osv-scanner honoured the suppression, so a CRITICAL id went unrated. Root cause: the awk header pattern `^\[\[?[A-Za-z_][A-Za-z_0-9]*\]\]?` cannot match a dotted table name, so on `[PackageOverrides.vulnerability]` `match()` returned 0 and the table was set to the empty string — the block's `ignore = true` was then attributed to no table at all.
  Fixed by taking Codex's first recommendation: the line scanner is replaced with a real TOML parser (`python3` + `tomllib`), which collapses every spelling of a construct to one structure and so leaves no syntax for a suppression to hide in. The `[[PackageOverrides]]` walk now finds `ignore = true` at any depth rather than in the two known spots, exempting only `license.ignore` (which filters license findings and cannot hide an advisory). Unparseable TOML, a non-table entry, and a non-string `id` are each emitted as their own record and treated as violations rather than skipped. `python3` joins `curl`/`jq` as a declared dependency.
  Verified over a 12-case matrix: committed config passes; all four measured bypasses now fail; a top-level inline-table `IgnoredVulns = [{id = "…"}]` (which osv-scanner does honour) is caught; the sub-`high` entry is still allowed; unknown id, non-string id, and malformed TOML all fail closed; `license.ignore` alone still passes. `shellcheck -x` clean. `osv-scanner.toml` and `.claude/CLAUDE.md` updated so both describe the nested spelling and the parser choice.
- [high] The required `sca` check can be replaced by the PR it is meant to gate (.github/workflows/security.yml:203-204) — **DEFERRED → #126**
  Valid, but a pre-existing repo-wide property rather than one this gate introduced: every check here runs from the pull request's own commit — `semgrep`, `betterleaks`, `actions-pin`, and both `ci.yml` jobs are reachable the same way — so hardening `sca` alone closes one door in a building with no walls. Verified while evaluating that no `CODEOWNERS` file exists anywhere in the repository, so there is no protection layer to extend; this would be new governance, not a gap to patch. The remedy (CODEOWNERS + required review, and/or a base-branch-controlled reusable workflow) is outside SPEC §3's "CI and build-tooling work" scope and materially changes the merge workflow for a sole maintainer — a repo-wide decision worth making deliberately rather than as a side effect of shipping one gate. Re-evaluate when a second person gains write access, or alongside any other reason to adopt CODEOWNERS.
- [medium] Known unresolvable KMP configurations have no completeness guard (build.gradle.kts:585-602) — **DEFERRED** (no tracking issue; deliberate call by the repository owner)
  Accurate that coverage was proven once by set comparison during implementation and is never re-asserted. Deferred on rarity and proportionality. For a coordinate to be missing it would have to be unique to `appleMainCInterop` / `iosMainCInterop` / `nativeMainCInterop` and absent from every per-target configuration — but those three are commonizer-derived from the per-target `ios<Target>CInterop` / `ios<Target>CompileKlibraries` configurations that do resolve, and a dependency declared in a shared native source set propagates to each target. The scenario therefore needs a change in how Kotlin derives those configurations, not merely a new dependency. Both suggested remedies are disproportionate to that: making unresolved configurations fatal would break generation today, since all three always fail, and the alternative is a whole new completeness-verification task. Re-evaluate if a generator or Kotlin upgrade changes how commonized cinterop configurations resolve.

Next steps:
- Close the suppression-policy bypasses and add regression fixtures.
- Protect the PR-blocking workflow and its enforcement inputs.
- Add an automated guard for unresolved KMP configuration coverage.


<!-- previous-rounds:end -->
