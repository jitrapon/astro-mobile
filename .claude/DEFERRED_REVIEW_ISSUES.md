# Deferred Review Issues — Ranked

- **Milestone:** Current Plan — Milestone 1 (read-only calendar app across web and mobile, five views, backed by a shared view-model contract; mobile lane's next task is **M-2** — navigation, app shell, SDUI registry, `CalendarViewModel`)
- **Milestone source:** `astro-docs/current-plan.md@2d4c563`
- **Ranked at:** 2026-09-11
- **Total open issues:** 3
- **Tie-break order:** blocks-other-issues → older → touches-more-files

## Ranking

### 1. #109 — Complete the no-@Suppress gate with a custom Detekt rule
- **Severity:** Medium — an enforcement gap with no runtime exposure, but it is silent: each equivalent spelling ("`@Suppress("detekt:RunBlockingInCommonMain")`", "`@Suppress("structured-coroutines")`", "`@Suppress("all")`") was "verified against the detekt 1.23.8 CLI to actually suppress a BLOCKING rule while `ForbiddenSuppress` stayed silent".
- **Urgency to milestone:** High — the rules it fails to protect are the three KMP `commonMain` blocking ones, and M-2 ("Create CalendarViewModel in shared module", written against a `Flow<CalendarScreenQueryState>` seam per D-24) is the first task to put real coroutine code in `commonMain`, so the gap becomes reachable exactly on the mobile lane's next branch.
- **Suggested action:** fix-now — **sequenced after M-2 (decided 2026-09-11).** The rule must encode the BLOCKING/WARN tier split, and M-2 is the probe that settles the seven `(UNVERIFIED)` Flow/Channel rules currently at weight 0 (`config/detekt/detekt.yml`, "armed for the branch that introduces one"), so writing it first means amending its tier list afterwards. Accepted risk: the repo has zero `@Suppress` today and the bare spelling is already blocked by `ForbiddenSuppress`, so reaching an uncaught spelling requires deliberate evasion.
- **Cross-refs:** none
- **Files / areas:** `config/detekt/detekt.yml` (the `ForbiddenSuppress` list + `structured-coroutines` BLOCKING/WARN tiering), `detektPlugins` wiring in `shared`/`androidApp`, `.githooks/pre-commit` (detekt CLI `--plugins`), CI `verify-android-common`
- **Opened:** 2026-07-18 on branch `unknown` (references PR #107, which added the 13 BLOCKING ids and removed the attempted semgrep rule)
- **Link:** https://github.com/jitrapon/astro-mobile/issues/109

### 2. #127 — Severity guard cannot rate an alias whose only rating is a CVSS vector
- **Severity:** Medium — the residual gap is a narrow silent-suppression path, "an alias whose only severity signal is a CVSS vector in `severity[]`, not a qualitative rating", and the issue records "Present exposure when deferred: **none measured.**"
- **Urgency to milestone:** Medium — same subsystem as the `sca` gate that now guards every M1 dependency bump, and M-2 adds the first navigation/SDUI dependencies since the gate landed, which is the growth the issue names as a verdict-changer ("the ignore list grows beyond a couple of hand-reviewed entries").
- **Suggested action:** defer-again
- **Cross-refs:** #126 (soft — same `sca` gate surface: both name `osv-scanner.toml` and `scripts/`; different fixes, so not a batch)
- **Files / areas:** `scripts/check-ignore-severity.sh`, `osv-scanner.toml`
- **Opened:** 2026-09-01 on branch `100-add-pr-blocking-gradle-sca-gate-sbom` (PR #123)
- **Link:** https://github.com/jitrapon/astro-mobile/issues/127

### 3. #126 — PR-blocking `sca` gate's enforcement logic is defined by the pull request it gates
- **Severity:** High — a required security status check can be defeated by its own PR: "A PR can retain `name: sca` while removing the generation/scan steps, changing the scripts, or replacing them with a successful command; the required status check will still be green."
- **Urgency to milestone:** Low — orthogonal to M1's calendar-rendering critical path, and the issue's own verdict-changer is org shape, not milestone progress: "More than one person gaining write access to the repository (the threat model becomes 'another contributor', not 'the maintainer bypassing their own gate')."
- **Suggested action:** defer-again
- **Cross-refs:** #127 (soft — same `sca` gate surface)
- **Files / areas:** `.github/workflows/security.yml`, `.github/workflows/ci.yml`, `scripts/`, `osv-scanner.toml`, `scripts/fixtures/`, `CODEOWNERS` (does not exist today), `main` branch ruleset
- **Opened:** 2026-09-01 on branch `100-add-pr-blocking-gradle-sca-gate-sbom` (PR #123)
- **Link:** https://github.com/jitrapon/astro-mobile/issues/126
