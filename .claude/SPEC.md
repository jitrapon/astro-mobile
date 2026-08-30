# Specification: PR-blocking SBOM-based Gradle SCA gate

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
lane: -                # backend | mobile | web | docs | infra | -
task: -                # task ID from current-plan.md (M-2, M-3), or - if not plan work
issues: []             # issue numbers in THIS repo that merging this branch closes
completes: no          # does merging this branch finish the whole task row?
spec-objective: -      # section 2, collapsed to one line
```

## 1. Overview

Software-composition analysis on the Gradle dependency graph is currently post-merge only. The job that feeds GitHub's Dependency Graph runs on push, so it produces alerts after a vulnerable dependency has already landed on `main`; the pull-request vulnerability scan has no lockfile or SBOM to read and so scans nothing of the Gradle graph. A PR can therefore introduce a known-vulnerable dependency, pass every security check, and surface only afterwards as an alert. This branch closes that window by generating a machine-readable bill of materials from the resolved Gradle graph at PR time and scanning it as a blocking gate.

## 2. Objective

A pull request that adds or upgrades a Gradle dependency carrying a `high`-or-worse advisory fails CI before merge, rather than being reported after it. The existing post-merge dependency-inventory job keeps working as the Dependency Graph / Renovate alert channel.

## 3. Requirements & Context

**Acceptance criteria** (from the issue):

- A PR-time job generates a CycloneDX or SPDX SBOM from the **resolved** Gradle dependency graph and runs the vulnerability scanner against that artifact.
- The gate is **blocking**: `high` and above advisories fail the PR.
- The post-merge `dependency-submission` job is **retained** as the inventory channel feeding Dependency Graph and Renovate — it is not replaced by this gate, and the gate is not bolted onto it.
- The existing `osv-scanner` ignore-list semantics carry over, so an accepted advisory stays accepted.

**Constraints and scope notes:**

- The trigger for this work has fired: the first real product dependencies (Ktor client, kotlinx.serialization, kotlinx.coroutines, Koin, and their test-only counterparts) landed with M-1 part 2 and did not bring the gate with them. The dependency surface now churns, which is what the gate was waiting on.
- The two candidate approaches named in the issue are committed Gradle dependency lockfiles **or** SBOM generation from the resolved graph. The issue's recommendation and acceptance criteria both settle on the SBOM route; lockfiles are the rejected alternative.
- Adding an SBOM generator means adding and pinning a new build plugin. Its multiplatform support is imperfect — the resolved graph spans Android, JVM, and three Kotlin/Native iOS targets — so which configurations are resolved, and whether a native or test-only configuration can be resolved at all on the CI runner, is the load-bearing unknown.
- This is CI and build-tooling work. It must not change product dependencies, product code, or the Kotlin/Swift lint toolchains.
- The gate runs on the host-portable CI half; it must not add work to the macOS runner, whose per-minute cost is the reason the CI split exists.
- The repository's convention is that new dependency versions are declared in the Gradle version catalog rather than inline, and that GitHub Actions are SHA-pinned. Both apply to whatever this branch adds.
- Verification tasks wired into the aggregate local gate are subject to the partition drift guard and must be classified into one of the two CI halves. Whether this gate belongs inside that aggregate or alongside it as a CI-only artifact step is a decision this branch has to make deliberately.

**Prior art:** this finding was deferred from the adversarial review of the M-1 scaffold branch. The reasons for deferring were scope (it was an edge in an earlier fix, not a new defect), rarity (the scaffold's dependency set was frozen and standard), and proportionality (an SBOM pipeline is disproportionate for a scaffold). None of those still hold.

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

https://github.com/jitrapon/astro-mobile/issues/100
https://github.com/jitrapon/astro-mobile/issues/115
https://github.com/jitrapon/astro-mobile/pull/117
