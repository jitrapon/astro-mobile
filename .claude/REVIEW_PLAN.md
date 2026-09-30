Status: clear

# Plan Review

- **Date:** 2026-09-30
- **Branch:** `165-bouncy-castle-1-85`
- **Base ref:** `main`
- **Focus sent:** Review the plan in .claude/SPEC.md sections 4 (implementation) and 5 (testing)
  against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong
  ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot
  actually verify them, scope drift beyond §§1–3, and any way a regression could land without the
  plan catching it. Do NOT review code — the diff is doc-only (SPEC.md).

## Round 1 — Codex output (verbatim)

Verdict: needs-attention

The plan covers current-version behavior, but its validation cannot prove the required
upgrade-safe constraint semantics.

Findings:
- [medium] Resolution checks cannot distinguish a version floor from a strict pin
  (.claude/SPEC.md:119-127)
  §4.1 explicitly requires ordinary constraints so a future AGP can select newer Bouncy Castle
  versions. However, §5.1–2 only check resolution against the current AGP, which requests 1.80.2.
  An implementation using strictly(1.85) would pass these checks and all current signing/Lint
  tests, yet could prevent the next AGP upgrade from selecting its required newer version. The
  paired checks also do not establish §3's constraint-only requirement: direct dependencies could
  produce the same resolved-version output.
  Recommendation: Add an explicit inspection of all six constraint declarations for
  catalog-backed, non-strict versions and absence of new direct dependencies. Add a temporary
  newer-version resolution probe on both classpaths, confirming the newer version wins, then
  revert the probe.

Next steps:
- Extend §5.1–2 to verify constraint semantics as well as the currently selected version.

## Resolution log

### Round 1

- **Floor vs. strict pin / constraint-only (medium) — AGREE.** Against the current AGP (which
  requests 1.80.2) a `strictly(1.85)` pin or a direct dependency resolves identically to the
  intended floor constraint, so the version-only checks could not tell them apart. Folded into
  §5.1 (temporary direct 1.86 probe on the buildscript classpath + diff inspection for
  constraint-only, non-strict declarations) and §5.2 (`dependencyInsight` selection reason names
  the constraint + temporary direct 1.86 probe on `androidLintTool`).

No critical/high findings: gate is `clear` after round 1; no further iteration required.
