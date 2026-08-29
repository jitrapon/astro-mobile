# Specification: <branch-scoped title>

> Per-branch working file owned by the `spec-development` skill. Each branch
> overwrites the section bodies; this file in `main` is a skeleton that
> documents the canonical structure so every branch follows the same shape.

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
