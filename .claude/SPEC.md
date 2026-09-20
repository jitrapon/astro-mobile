# Specification: Make the version catalog authoritative

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
issues: [146]          # issue numbers in THIS repo that merging this branch closes
completes: no          # does merging this branch finish the whole task row?
spec-objective: -      # section 2, collapsed to one line
```

## 1. Overview

The version catalog is documented as this repo's single source of truth for dependency versions, but
nothing holds anything to it. Two consequences of that gap are addressed together. A Kotlin security
bump moved the Kotlin version on its own and left the Compose stability analyzer — a Kotlin compiler
plugin that must track the Kotlin release exactly — behind on a build targeting an older compiler,
where it stayed for twelve days without any gate noticing. Separately, a block of the Android app's
older dependencies is still declared inline rather than through the catalog, which the catalog's own
header already records as unmigrated debt.

## 2. Objective

A Kotlin version bump that leaves the stability analyzer behind fails the build or blocks the pull
request rather than compiling by luck, and the Android app's remaining inline dependency
declarations resolve to the same versions through the catalog as they do today.

## 3. Requirements & Context

**Prior art already on a held branch.** The analyzer pin itself and the stale version prose it left
behind are already fixed in a commit held locally on `hold/analyzer-pin`, verified by compiling the
Android app (which applies the plugin, and so exercises the registration path where a mismatch
throws). That commit is to be cherry-picked onto this branch as its starting point; what remains is
enforcement, not the fix.

**Why a comment was not enough.** The lockstep is currently stated only in prose, and prose is what
a bot editing one version line does not read. Whatever lands must fail a build or block a pull
request — a warning reproduces exactly the present situation.

**The enforcement mechanism is an open decision, with a known trap.** The repo already has an
alignment guard for its formatter toolchain, but that guard compares two refs that must be *equal*,
so correctness is derivable from the refs themselves. Here the two refs hold different, unrelated
values whose pairing exists only in upstream release notes, so a build-time guard can only assert a
hardcoded pairing — a third place to update, though one that fails loudly. A dependency-bot grouping
rule prevents the drift at its source but catches no hand-edit. The two fail at different moments
and neither subsumes the other; choosing one, the other, or both is part of planning.

**Wiring a new verification task has a second obligation.** This repo splits its local gate across
two CI aggregates and guards the partition mechanically, so any new task added to the gate must also
be classified into one half or the partition guard fails. That failure is the intended signal, not
an obstacle to route around.

**The inline-dependency migration must be behaviour-preserving.** Resolved versions must be
identical before and after; it is a sourcing change, not an upgrade. Because it touches dependency
resolution, the software-composition gate's bill of materials is an output worth diffing rather than
assuming. One trap is already known: the Material icon artifacts stopped publishing at an earlier
version than the rest of the Compose libraries, so they must keep their own version reference and
must not be folded into a general Compose one by package-name similarity.

**Suppression is not an available answer.** No static-analysis baseline file and no inline
suppressions to quiet anything either change surfaces.

**Out of scope.** Upgrading any dependency to a newer version than it resolves to today, and porting
any of this to the other repositories in the fleet.

## 4. Implementation Plan and Progress Tracking (for agent)

<Filled by `spec-development` in plan mode. GitHub-style checkboxes (`- [ ]`), one item per concrete task small enough to finish in a single resume pass.>

## 5. Testing & Validation (for agent)

<Filled by `spec-development` in plan mode. Each item pairs 1:1 with a §4 item: the test/build/lint command that verifies it.>

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

- `.claude/CLAUDE.md` — the tech-stack version line and the linting section's restatement of it are
  corrected in the held commit; the linting section additionally needs whatever enforcement this
  branch adds, described alongside the existing gates.
- `gradle/libs.versions.toml` — its own header and per-ref comments are the primary documentation
  for catalog policy, and both the lockstep rule and the inline-declaration note change here.
- Upstream note, not owned by this branch: decision D-24 in astro-docs quotes the catalog's
  pre-existing lockstep sentence verbatim, so that quotation drifts once this branch rewords it.
  Worth raising there rather than fixing here.

## 8. References

https://github.com/jitrapon/astro-mobile/issues/146
https://github.com/jitrapon/astro-mobile/pull/132
https://github.com/skydoves/compose-stability-analyzer
https://github.com/InsertKoinIO/koin/issues/2431
