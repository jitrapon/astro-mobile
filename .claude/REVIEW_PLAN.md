Status: clear

# Plan adversarial review — `.claude/SPEC.md` §§4–5

- Date: 2026-09-20
- Base ref: `main`
- Scope: doc-only (branch diff against `main` — SPEC.md plus the cherry-picked catalog/CLAUDE.md edits)
- Focus sent: steps missing to satisfy §2, wrong ordering, items too coarse for one resume pass,
  §5 checks that cannot verify their §4 item, scope drift beyond §§1–3, and regressions that could
  land without the plan catching them. Codex was given two verified facts up front: that
  `renovate.json` already disables the analyzer (manual-pin posture mirroring `com.facebook:ktfmt`),
  and that the analyzer's built-against Kotlin version cannot be derived from the resolved graph.

## Round 1 — verdict `needs-attention`, 1 finding (medium)

**Finding [medium] — Verify dependency placement beyond the debug runtime graph.**
Item 4 (now items 4–5) requires preserving each declaration's configuration, but the paired checks
compared only `debugRuntimeClasspath` and a debug build. Migrating `ui-test-manifest` from
`debugImplementation` to `implementation` would leave the debug graph unchanged while introducing
the artifact into release; the SBOM component-list diff cannot catch it either, because the
component already exists in the inventory via debug. Successful builds and vulnerability scans do
not establish configuration equivalence.

**Disposition: AGREE — addressed.**

Verified rather than taken on trust: `implementation` is a superset of `debugImplementation` for the
debug variant, so the debug graph is genuinely identical under that mistake, and the branch's own
`:cyclonedxBom` gate renders a component *inventory* (`includeConfigs` empty, so scope is not
represented per component). Both halves of the finding hold. The same hole applies to
`androidTestImplementation(ui-test-junit4)`, which Codex did not name.

Changes made:

- **§4** — inserted a new item before the catalog-ref work: capture pre-migration baselines for the
  debug, release, and androidTest compile *and* runtime classpaths plus a `:cyclonedxBom` run,
  explicitly **before** any catalog edit, since the baseline is unrecoverable afterwards. This also
  fixes the ordering half of Codex's recommendation, which the original plan left implicit.
- **§5** — replaced the single debug-graph check with per-configuration diffs across all three
  variants, compile and runtime; added a direct per-coordinate assertion that each of the ten
  declarations kept its original configuration; and rewrote the SBOM check to state its own limit
  (an inventory cannot see a scope change) so a future reader does not mistake it for coverage it
  does not provide.

## Override justification

None — no finding was overridden.

## Notes

No critical / high / no-ship finding was raised, so the plan was not classified `blocking` and the
autonomous review loop was not entered. The single medium finding was folded in during round 1.
