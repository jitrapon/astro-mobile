# Specification: Raise Bouncy Castle past its advisories on the AGP build and Lint classpaths

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
task: -
issues: [165]
completes: no
spec-objective: <placeholder — spec-development fills this>
```

## 1. Overview

Four open Dependabot alerts on `main` (one critical, one high, two medium) are Bouncy Castle
1.80.2, below its patched releases. It reaches the build only through the Android Gradle Plugin: on
the buildscript classpath (through the SDK tooling that handles keystores and certificates, so it
runs in the release-signing path) and in the Android Lint runtime. Nothing ships in an app artifact,
but a patched Bouncy Castle can be selected without waiting for AGP, so this branch raises it.

## 2. Objective

The build and Lint classpaths resolve Bouncy Castle at a release that clears all four advisories
(1.85), a signed release build and Lint still work, and the four alerts close once the next `main`
dependency submission reports the new version.

## 3. Requirements & Context

- **The alerts:** `bcprov-jdk18on` critical, high and medium (patched in 1.85 and 1.84), and
  `bcpkix-jdk18on` medium (patched in 1.84). The companion `bcutil-jdk18on` is on the same chain and
  moves with them.
- **Where it resolves:** the AGP buildscript classpath and the Android Lint runtime configuration in
  each Android module. Both are AGP-selected, which is why the SBOM `sca` gate deliberately skips them.
  Dependabot still sees them, because the dependency-submission job uploads every configuration.
- **Raise, don't add:** select the newer version through dependency constraints rather than new
  direct dependencies. The version lives in the version catalog, per the repo's no-inline-versions
  rule.
- **Verify what AGP does with it:** a signed release build, Android Lint, and the release-run CI job
  that installs and runs the shrunk, signed app.
- **Temporary by design:** once AGP itself ships Bouncy Castle at or above the patched release, the
  constraint only pins, and should be removed.
- **Out of scope:** anything that ships in the Android or iOS artifacts; the SBOM `sca` gate's scope,
  which stays unchanged; and the three other alerts from the same sweep (Lint-runtime httpclient and
  commons-lang3, and KGP's unused Swift Export classpath), already dismissed as not used.
- **Gates:** `./gradlew check` and the CI partition stay green.

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

https://github.com/jitrapon/astro-mobile/issues/165
https://github.com/advisories/GHSA-9pwp-9qqc-pr26
https://github.com/advisories/GHSA-qp49-qgx5-5m26
https://github.com/advisories/GHSA-c3fc-8qff-9hwx
https://github.com/advisories/GHSA-wg6q-6289-32hp
