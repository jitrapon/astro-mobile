# Specification: Vendored design tokens, plain-value codegen, and the bundled theme registry (M-9 1/3)

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
issues: [161]
completes: no
spec-objective: <placeholder — spec-development fills this>
```

## 1. Overview

This is the first of three branches for M-9, the mobile design-system foundation. It brings the
design system's published artifacts into the mobile repo, and turns them into plain values the
shared module exposes to both platforms. That covers the two theme documents, the theme-invariant
base, the font manifest and the validation rules. It also gives the client its first bundled theme
registry. It is shared-module only: no UI toolkit, no fonts, no app code. The Android `AstroTheme`,
the iOS design system and the fonts follow in part 2, and components in part 3.

## 2. Objective

The published design artifacts are vendored and held byte-equal to astro-docs by a CI parity gate.
A build-time generator turns them into plain Kotlin values in the shared module. The shared module
carries a registry of the bundled themes by `id@version`, which resolves a screen response's theme
reference and falls back to the OS scheme's theme at cold start.

## 3. Requirements & Context

- **Source of truth.** The artifacts are astro-docs' *published* build output (both theme documents,
  the base, fonts and rules), never the token source. The astro-docs submodule pin must move to a
  merged `main` commit that carries them; the current pin predates them. The contract mirror must
  still pass its existing parity gate after the bump.
- **Vendored, never read at build time.** Committed copies live in the shared module. A parity gate
  byte-compares each against the submodule, runs in the only CI half that checks the submodule out,
  and fails the same way the contract parity gate does when the submodule is missing. Codegen reads
  only the vendored copies, because the submodule is private, the iOS job doesn't check it out, and
  fresh clones have none.
- **Plain-value generated surface.** These values reach Swift through the framework, so:
  - colors are 64-bit ARGB integers, because 32-bit overflows and unsigned types bridge boxed;
  - dp and sp values are plain numbers, and font ids come from the font manifest;
  - hairline-family entries keep a distinct hairline marker, so zero and hairline never collapse to
    the same value;
  - the surface is plain objects with values plus enums: no value classes, no generic maps, no
    library types.
- **Generated at build time, committed never.** Follow the existing precedent for generating
  test-time contract sources: output under the build directory, wired lazily as a source directory.
  A unit test on both the JVM host and the iOS simulator asserts a sample of generated values against
  the vendored JSON.
- **Bundled registry.** It carries each built-in theme's `id@version`, which is what M-5 will send as
  the known theme and what the client paints at cold start. It resolves a response's theme reference
  to a bundled theme. With no match, or no response yet, the fallback is the bundled theme matching
  the OS color scheme: a boot default only, not follow-system.
- **Header surface unchanged.** Nothing generated may introduce a library type into the iOS framework
  header, so the existing header-surface gate must stay green without modification.
- **CI partition.** The new gate and any generation task must be classified into a CI half, or the
  partition drift guard fails.
- **Out of scope:** fonts and the font gate, `AstroTheme`, the iOS design system, components, the
  access-rule lint (parts 2 and 3), and the whole theme network path (M-5: known theme on the wire, the
  read-time validator, applying a delivered document).
- **Governing decisions:** `ADR-design-system-digestion` §1 (published artifacts, vendored
  byte-for-byte) and §6 (plain-value codegen, bundled registry). Full task scope: astro-plans
  `tasks/M-9.md` items 1 (JSON half), 2 and 3.

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

https://github.com/jitrapon/astro-mobile/issues/161
https://github.com/jitrapon/astro-mobile/issues/160
https://github.com/jitrapon/astro-docs/blob/main/tasks/M-9.md
https://github.com/jitrapon/astro-docs/blob/main/adr/ADR-design-system-digestion.md
