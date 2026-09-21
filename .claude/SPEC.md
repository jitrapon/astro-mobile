# Specification: Fail the iOS framework link on partial-linkage errors

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
task: -                # task ID from current-plan.md (M-2, M-3), or - if not plan work
issues: [151]          # issue numbers in THIS repo that merging this branch closes
completes: no          # does merging this branch finish the whole task row?
spec-objective: -      # section 2, collapsed to one line
```

## 1. Overview

When the iOS framework is linked, Kotlin/Native does not fail on a reference it cannot resolve. It
substitutes a stub that throws a linkage error only when that code actually runs on a device
("partial linkage"), and since Kotlin 1.9.20 the compiler detects these problems silently by
default, so nothing in the build output shows them. This branch makes such problems fail the build
instead of surfacing as an iOS-only runtime crash on whichever code path reaches the stub.

## 2. Objective

A partial-linkage problem in the shared module fails the framework link on every iOS target rather
than producing a stub, and the existing local and CI gates pass with that enforcement switched on.

## 3. Requirements & Context

**Why this repo is exposed.** The shared module compiles against Kotlin libraries published by
others — its DI container, its HTTP client, and the coroutines and serialization libraries — each
built against its own versions of the transitive dependencies they have in common. Where two of them
want different versions, Gradle selects the newest and the framework links against that. If the
newest version removed or changed an API an older library still calls, the link succeeds anyway. The
failure then appears only on iOS, only at runtime, and only on the path that hits the stub. The
exposure grows once the generated backend client and further multiplatform dependencies arrive.

**Existing gates do not catch it.** CI already links the debug simulator framework and builds the
iOS app against it, but a link containing stubs passes both.

**The proposed mechanism.** The issue proposes raising the compiler's partial-linkage log level to
`ERROR` (`-Xpartial-linkage-loglevel=ERROR`) on every Native target of the shared module, written in
the build's current compiler-options style rather than a new one.

**The flag must reach the link, not only compilation.** Stubs are manufactured when the framework is
linked, so a setting that applies only to library compilation would leave the defect in place while
looking enforced. Confirming where the flag actually takes effect is part of the work, not an
assumption to carry.

**Anything it reports is fixed at the source.** If enabling the enforcement surfaces a real
partial-linkage problem, it is resolved by aligning versions in the version catalog — never by
suppressing the report or lowering the level again.

**The reason must survive a later cleanup.** A comment beside the setting has to explain why it is
`ERROR` rather than the silent default, so that a future tidy-up does not read it as a leftover
compiler argument and remove it.

**Relationship to the catalog work.** Making the version catalog authoritative closed version drift
at its source. This closes the drift that arrives through transitive dependencies, which no catalog
rule reaches; the two are complementary rather than overlapping.

**Acceptance.** The enforcement applies to every iOS target's framework link; the existing CI
framework link passes with it on; the explanatory comment is present; and the full local gate is
green.

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

https://github.com/jitrapon/astro-mobile/issues/151
https://github.com/jitrapon/astro-mobile/issues/146
https://kotlinlang.org/docs/whatsnew1920.html
https://touchlab.co/gradle-transitive-dependency-resolution
