# Specification: Mobile navigation and app shell

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
lane: mobile           # backend | mobile | web | docs | infra | -
task: M-2              # task ID from current-plan.md (M-2, M-3), or - if not plan work
issues: [136]          # issue numbers in THIS repo that merging this branch closes
completes: no          # does merging this branch finish the whole task row?
spec-objective: -      # section 2, collapsed to one line
```

## 1. Overview

Second of three branches splitting M-2. It ships navigation and the app shell on both platforms: a
navigation framework, a bottom-navigation shell whose destinations come from the contract's shell
tree rather than a hardcoded list, and placeholder screens behind each destination. It is platform
UI work in the Android and iOS apps, independent of the shared observation layer that landed first,
and sequenced before the SDUI registry branch so the registry has a shell to render into.

## 2. Objective

Both apps present a bottom-navigation shell whose destinations are rendered from the shell tree's
semantic nav destinations (Calendar, Agenda), with a reachable placeholder screen behind each
destination on Android and iOS.

## 3. Requirements & Context

**Scope:**

- A navigation framework on both platforms.
- An app shell with bottom navigation rendered from the shell tree's **semantic nav destinations**
  (Calendar, Agenda) — driven by the contract, not a list hardcoded in Compose or SwiftUI.
- Placeholder screens behind each destination.

**Starting point (as the issue records it):** neither app has navigation today — the Android app is
a single activity with a theme, the iOS app is its entry point plus a placeholder view, and the
version catalog carries no navigation or lifecycle dependency.

**Architectural constraints (load-bearing):**

- **UI stays out of the shared module.** Compose lives in the Android app and SwiftUI in the iOS app;
  the shared module exposes platform-agnostic models and logic only.
- **The destination list is contract-driven.** Bottom navigation must be derived from the shell
  tree's semantic nav destinations; a hardcoded destination list on either platform does not satisfy
  the acceptance criteria.
- **Any new dependency is declared in the version catalog**, not inline.

**Skill routing the issue prescribes:**

- Compose authoring routes to the `chrisbanes-skills:compose-*` skills.
- The Android navigation *framework* choice routes to the official `android/navigation-3` skill.
- SwiftUI routes to the vendored Apple skills.

**Out of scope:**

- The shared observation layer and `CalendarViewModel` (#135, already merged).
- The SDUI component registry, action model and R8 keep rules (#137).
- Everything the M-2 umbrella (#133) lists as out of scope for M-2 as a whole.

**Acceptance (from the issue):**

- Bottom navigation renders from the shell tree's semantic nav destinations, not a hardcoded list.
- Placeholder screens are reachable behind each destination on both platforms.
- New dependencies are declared in the version catalog.
- `./gradlew check` is green, and CI's `verify-ios` job compiles the `iosApp` scheme.

This branch does not complete the M-2 plan row; only the registry branch (#137) does.

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

- https://github.com/jitrapon/astro-mobile/issues/136
- https://github.com/jitrapon/astro-mobile/issues/133
- https://github.com/jitrapon/astro-mobile/issues/135
- https://github.com/jitrapon/astro-mobile/pull/138
- https://github.com/jitrapon/astro-mobile/issues/137
