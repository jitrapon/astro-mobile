# Specification: <branch-scoped title>

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

<One short paragraph: what this branch changes and why. Author this before invoking the `spec-development` skill.>

## 2. Objective

<The concrete goal — what does "done" look like. One or two sentences.>

## 3. Requirements & Context

<Known constraints, affected files, prior art, references to similar PRs. Author this section before invoking the `spec-development` skill — the skill writes the implementation checklist in section 4 below.>

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

<Links to designs, similar PRs, external docs, RFCs.>
