# Specification: SDUI component registry, action model, and release keep rules (M-2, 3/3)

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
task: M-2
issues: [137]
completes: no
spec-objective: <placeholder — spec-development fills this>
```

## 1. Overview

Third and last of the three PRs that split M-2. It ships the server-driven UI component registry and the action model into the app shell the previous PR delivered, consuming the view model the first PR delivered, and it closes the release-shrinker gap the registry makes reachable: the Android release variant is minified with no app-level keep rules, and the polymorphic serialization the registry needs is exactly what the shrinker strips when nothing keeps it. That failure is release-only and runtime-only, so today's CI — which only assembles the release APK — cannot see it.

## 2. Objective

The component registry and the action model are implemented, `resolvedPreferences` from the response envelope is consumed, and the Android release variant declares its own keep rules and is **run** in CI rather than only assembled — so a reflectively reached registry type the shrinker strips fails CI instead of a device.

## 3. Requirements & Context

**In scope**

- A registry mapping server-driven component types to platform renderers.
- The action model.
- Consuming `resolvedPreferences` from the response envelope.
- The carried rider from the release-shrinker issue, landed **before** the registry ships:
  - explicit keep-rule files declared for the release variant (today every keep rule applied comes from library consumer rules);
  - the merged rule set reviewed through the release mapping output;
  - any third-party rule broad enough to suppress optimization app-wide identified and neutralized by ignoring its source, **not** by adding counter-keeps;
  - the release variant run, not just assembled.

**Dependencies**

- Renders into the app shell delivered by the second M-2 PR and consumes the view model delivered by the first. Both have merged.

**Out of scope**

- The shared observation layer and the calendar view model (first M-2 PR).
- Navigation and the app shell (second M-2 PR).
- Everything the M-2 umbrella issue lists as out of scope for M-2 as a whole.

**Constraints**

- `./gradlew check` stays green, with no `@Suppress` and no Detekt baseline added.
- Dependency-injection bindings added here follow the plan's M-2 binding rules (recorded 2026-09-21 from the compile-time graph-check proof of concept), so that check can be adopted afterwards without rework: bindings live in a module each platform's graph start reaches through plain composition, never through a module-list parameter; same-typed bindings are told apart by type, not by a named qualifier; an unavoidable named qualifier is written as an inline string literal. Adopting the check itself is **not** this branch's work — it is a separate PR after this one.

**Plan**

- This is the PR that completes the M-2 plan row and closes the release-shrinker issue.

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

https://github.com/jitrapon/astro-mobile/issues/137
https://github.com/jitrapon/astro-mobile/issues/133
https://github.com/jitrapon/astro-mobile/issues/128
https://github.com/jitrapon/astro-mobile/issues/135
https://github.com/jitrapon/astro-mobile/issues/136
https://github.com/jitrapon/astro-mobile/issues/153
https://github.com/jitrapon/astro-docs/blob/main/tasks/M-2.md
