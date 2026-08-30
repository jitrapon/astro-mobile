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

The gate is assembled bottom-up: prove an SBOM can be produced from this KMP graph at all, prove `osv-scanner` actually parses it, make the job fail-closed, then wire it into CI and prove it blocks.

- [ ] 1. Add a CycloneDX SBOM generator to the build: pin its version in `gradle/libs.versions.toml` (new `[versions]` + `[plugins]` entries, never inline) and apply it to the **root** project only, so one aggregate BOM spans `:shared` and `:androidApp`. Change no product dependency. Do **not** wire its task into `check` — SBOM generation produces an artifact, which is coverage beyond `check`, the same classification as `:androidApp:assemble`.
- [ ] 2. Scope the SBOM to the **shipping** dependency graph and record the scoping decision in a comment on the configuration block. Include `:androidApp:releaseRuntimeClasspath` and the `:shared` Android + iOS main compile graphs (`androidRuntimeClasspath`, `iosArm64CompileKlibraries`, `iosSimulatorArm64CompileKlibraries`, `iosX64CompileKlibraries` — the iOS ones are the only place `ktor-client-darwin` appears). Exclude the buildscript `classpath` configuration, matching the `^classpath$` exclusion `dependency-submission` already applies for the same reason: AGP's build-time tooling never ships, and its advisories are unremediable from this repo. Exclude `*Test*` configurations, so a test-only advisory informs via the Renovate baseline rather than blocking a PR.
- [ ] 3. Generate the SBOM locally and assert it is non-empty and actually contains the product coordinates — Ktor, kotlinx.serialization, kotlinx.coroutines, Koin, and Compose/AndroidX — and that it contains **no** AGP buildscript coordinates (BouncyCastle, Netty, jose4j). This is the item that proves the KMP native configurations resolve at all; if a native configuration cannot resolve on a non-Mac host, narrow the scope here and record what was dropped.
- [ ] 4. Choose the SBOM output path and prove `osv-scanner` 2.4.0 parses it. The generated file must land under a gitignored build directory (it is a build artifact, never committed), which means the existing `osv-scanner scan source --recursive .` will **not** see it — `build/` and `**/build/` are gitignored and osv-scanner honours `.gitignore` by default. Resolve this deliberately: scan the SBOM's own directory with `--no-ignore` rather than the repo root, and confirm the filename matches a name the CycloneDX extractor recognises. Do not use the deprecated `--sbom` flag.
- [ ] 5. Make the scan **fail-closed**: prove the run parsed a non-zero package count rather than trusting a clean exit. Drop `--allow-no-lockfiles` from the `sca` job (its whole purpose was to let a source-less scan pass, which is exactly the failure this gate must not have) and add an explicit assertion that the SBOM exists and yielded packages, so a generator that silently emits an empty BOM turns the job red instead of green.
- [ ] 6. Wire SBOM generation into the `sca` job in `.github/workflows/security.yml`: add `actions/setup-java` and `gradle/actions/setup-gradle` steps reusing the SHA pins already used elsewhere in this repo (no new action, so the `actions-pin` gate stays satisfied), generate the SBOM, then run the scan from items 4–5. Keep the job on `pull_request` **and** `push` as it is today, and keep the checkout at `submodules: false` — confirm nothing in the SBOM path reads the `docs/astro-docs` mirror.
- [ ] 7. Confirm the gate is genuinely blocking with a negative test: temporarily pin one product dependency to a version carrying a known `high`+ advisory, confirm the `sca` job fails on it, then revert the pin. A gate that has never been observed to fail is not known to be a gate.
- [ ] 8. Reconcile the sub-`high` policy: run the scan against the real resolved graph and, for each advisory below `high` with no patched parent, add a documented `[[IgnoredVulns]]` entry to `osv-scanner.toml` naming the coordinate and why it is routed to the Renovate `osvVulnerabilityAlerts` baseline. If the graph is clean, add no entries and leave the list empty.
- [ ] 9. Correct the now-false prose that asserts this job is dormant: the `sca` job comment and the `dependency-submission` comment in `.github/workflows/security.yml`, and the scope note in `osv-scanner.toml` (all three currently state the Gradle ecosystem commits no lockfile so the osv-scanner job never activates). State the new division: the SBOM scan is the PR-blocking gate, `dependency-submission` remains the post-merge Dependency Graph / Renovate inventory channel.
- [ ] 10. Update `.claude/CLAUDE.md`: describe the SBOM gate in the CI/Linting narrative, and confirm the "Documented config files" list still names every file this branch changed (`gradle/libs.versions.toml`, `build.gradle.kts`, `.github/workflows/security.yml`, `osv-scanner.toml`).

## 5. Testing & Validation (for agent)

Paired 1:1 with §4. Every Gradle-touching item ends with `./gradlew ktfmtFormat` (the root `build.gradle.kts` is formatted by the root ktfmt plugin and CI verifies it) before the commit.

- [ ] 1. `./gradlew tasks --all` configures without error on Gradle 9.x and lists the new SBOM task; `./gradlew verifyCheckPartition` still passes, proving the new task did not enter the `check` closure; `./gradlew ktfmtCheck` passes on the edited root script.
- [ ] 2. `./gradlew :shared:resolvableConfigurations` / `:androidApp:resolvableConfigurations` confirm every configuration name written into the scope filter actually exists (a typo'd name silently narrows the SBOM to nothing, which is the failure mode item 5 guards).
- [ ] 3. Generate the BOM, then assert on the emitted file: `grep` for `io.ktor`, `org.jetbrains.kotlinx`, `io.insert-koin`, and `androidx.compose` (each must be present) and for `bouncycastle`, `netty`, `jose4j` (each must be absent). Report the component count.
- [ ] 4. `osv-scanner scan source --no-ignore --config=osv-scanner.toml <sbom-dir>` run locally against the generated file, with `--format json --all-packages`, showing a non-zero parsed package count. A zero count means the extractor did not recognise the file and the path/filename must change.
- [ ] 5. Two negative checks: delete the SBOM and confirm the scan step exits non-zero; emit an SBOM with no components and confirm the assertion exits non-zero. Both must be red — this is the item that proves the gate cannot silently regress to dormant.
- [ ] 6. Push the branch and read the `sca` job log on the PR: it must show the SBOM being generated, a non-zero package count, and a pass. `./scripts/check-action-pins.sh` passes locally. Confirm from the log that the job did not need the submodule.
- [ ] 7. Push the temporary vulnerable pin on the branch and confirm the `sca` job goes **red** naming that advisory; then push the revert and confirm it goes green. Capture both run URLs in the commit body.
- [ ] 8. Re-run the scan after any `osv-scanner.toml` edit and confirm a clean pass, with each ignored advisory traceable to a comment naming its coordinate.
- [ ] 9. `grep -n "dormant\|allow-no-lockfiles\|does not commit lockfiles" .github/workflows/security.yml osv-scanner.toml` returns no stale claim.
- [ ] 10. `./gradlew check` passes end-to-end locally, and the full CI run on the PR is green across `verify-android-common`, `verify-ios`, `sca`, `semgrep`, `betterleaks`, and `actions-pin`.

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

https://github.com/jitrapon/astro-mobile/issues/100
https://github.com/jitrapon/astro-mobile/issues/115
https://github.com/jitrapon/astro-mobile/pull/117
