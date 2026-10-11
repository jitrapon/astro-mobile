# Specification: M-9 part 2 — fonts and the font gate, the verify items, AstroTheme and the iOS DesignSystem

> Per-branch working file owned by the `spec-development` skill. Each branch
> overwrites the section bodies; this file in `main` is a skeleton that
> documents the canonical structure so every branch follows the same shape.

## 0. Plan anchor

Where this branch sits in `current-plan.md` (astro-docs). Written by `scaffold-issue` and validated
against the live plan; `completes` and `spec-objective` are settled by `spec-development`. Read by
`finish-branch` **before** it resets this file, and copied into the PR body's `## Plan Update`
section, which `sync-plan` parses unattended. Field semantics: `plan-update-contract.md` in
astro-docs.

```yaml
lane: mobile
task: M-9
issues: [162]
completes: no
spec-objective: <placeholder — spec-development fills this>
```

## 1. Overview

Part 2 of 3 of M-9, the mobile design-system foundation (umbrella #160). Part 1 (#161) vendored the
published design artifacts and generated plain token values in the shared module. This branch puts
those values to work on both platforms: it bundles the three font families and adds a gate that
holds the bundled files to the font manifest. It also answers the four open `verify` questions in
`ADR-design-system-digestion`, replaces the Android template theme with a Material 3 `AstroTheme`,
and adds an iOS `DesignSystem` environment. It covers items 1 (the font gate), 4, 5, 6 and 8 of
astro-plans `tasks/M-9.md`, under the ADR's §3, §4, §6 and §6a.

## 2. Objective

Both apps render their current screens under the Astro design system: Android under an
`AstroTheme` and iOS under a `DesignSystem` environment, each driven by a bundled theme value and
using bundled fonts. A font gate fails on any swapped or missing font file, and the ADR's four
`verify` answers are recorded.

## 3. Requirements & Context

**Order: close the four `verify` items first (item 8).** Each answer is recorded in the ADR's
revision history:
- whether Compose's `FontFamily` falls back per glyph across custom families;
- the minimum Compose version for `dropShadow(Shadow(...))` with spread;
- the blur-to-radius factor for Compose and SwiftUI, taken from a rendered comparison;
- the API level at which Compose honours `FontVariation.Settings`. This decides Trispace: with
  `minSdk` 30, the variable file works unless that level is above 30.

**Bundled fonts (items 1 and 6).**
- Space Mono 400/700, Trispace at every weight in `fonts.json`'s `requiredWeights`, and IBM Plex
  Sans Thai 400–700. Files are keyed by `fonts.json` ids and bundled in both apps; iOS registers
  them as app fonts.
- A variable Trispace file must set `pinnedAxes` (`wdth` 100). Static or variable, the bundled set
  must meet the ADR §3 coverage rule and the CSS Fonts 4 §5.2 matching rule.
- Thai fallback must be verified to at least "legible with the corrected leading".

**The font gate.** Fonts are not byte-compared, because nothing published carries file bytes.
Instead:
- the bundled `(id, weight)` files on both platforms must cover `requiredWeights` for every id,
  and any extra weight must appear in that id's `static` list;
- a committed font manifest records each file's upstream release (matching `fonts.json`), its
  weight, its axis coordinates (matching `pinnedAxes`) and its sha256, so a swapped file fails.

**`AstroTheme` on Android (item 4).**
- Adopts Material 3 and replaces the Material 1 template theme.
- A `ColorScheme` filled from the ADR §6a table (`surfaceTint` takes `surface`), `Typography` from
  `base.json`'s type ramps in `sp`, and `Shapes` from the radius family.
- Composition locals for the color roles Material 3 lacks, the component metrics, and
  `bindings.color` resolved against the active theme.
- It takes a theme **value**: the bundled theme matching the screen response's `ThemeRef`, otherwise
  the one matching the OS color scheme at cold start. The applied theme's `colorScheme` drives
  system-bar icon contrast.
- The app shell's existing edge-to-edge contract must keep holding: the no-action-bar theme plus
  explicit insets.

**`DesignSystem` on iOS (item 5).**
- An `Environment` value carrying the same roles and metrics, plus `.preferredColorScheme` from the
  applied theme's `colorScheme`.
- New Swift files need their entries in the classic-format Xcode project.

**Constraints.**
- Visible stock components get explicit Astro colors. The §6a table governs incidental defaults
  only.
- Builds on part 1's generated token values and bundled theme registry.
- **Out of scope:** the ADR §5 components and the theme-access lint rule (part 3, #163), and the
  theme network path (M-5).

**Done when.**
- `./gradlew check` and the `xcodebuild` app build are green.
- The font gate fails on a swapped or missing file (a negative control).
- The four `verify` answers are recorded in the ADR.
- The app renders its current screens under `AstroTheme` on an emulator and under `DesignSystem` on
  a simulator.

## 4. Implementation Plan and Progress Tracking (for agent)

<Filled by `spec-development` in plan mode. GitHub-style checkboxes (`- [ ]`), one item per concrete task small enough to finish in a single resume pass.>

## 5. Testing & Validation (for agent)

<Filled by `spec-development` in plan mode. Each item pairs 1:1 with a §4 item: the test/build/lint command that verifies it.>

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

- https://github.com/jitrapon/astro-mobile/issues/162
- https://github.com/jitrapon/astro-mobile/issues/160 (M-9 umbrella)
- https://github.com/jitrapon/astro-mobile/issues/161 (part 1, merged as #164)
- https://github.com/jitrapon/astro-mobile/pull/164
- https://github.com/jitrapon/astro-mobile/issues/163 (part 3)
- astro-plans `tasks/M-9.md` and `ADR-design-system-digestion` §3, §4, §6, §6a
