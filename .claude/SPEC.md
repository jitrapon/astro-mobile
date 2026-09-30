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
spec-objective: Raise Bouncy Castle to 1.85 on the AGP buildscript classpath and the Android Lint runtime through catalog-versioned constraints, keeping signing, Lint and the release-run job green so the four Dependabot alerts close.
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

Traced surface (plan time, AGP 9.4.1): Bouncy Castle 1.80.2 (`bcprov-jdk18on`, `bcpkix-jdk18on`,
`bcutil-jdk18on`) resolves in exactly two places — the **root** buildscript `classpath` (AGP →
`com.android.tools:sdk-common` → bcpkix → bcutil → bcprov) and the `androidLintTool` configuration
of `:androidApp`, `:androidAppReleaseTest` and `:shared`. No subproject buildscript classpath and no
compile/runtime/test configuration carries it. The dependency-submission job excludes `^classpath$`,
so the four open alerts are fed by `androidLintTool` alone; the buildscript raise is for the signing
path, not for alert closure.

- [x] **1. Catalog entries + buildscript-classpath constraint.** Add a `bouncycastle = "1.85"` ref and
  `bouncycastle-bcprov` / `bouncycastle-bcpkix` / `bouncycastle-bcutil` library entries to
  `gradle/libs.versions.toml`, with a comment stating that they are consumed **only** as dependency
  constraints on AGP-selected build tooling (never declared as dependencies), why (the advisories
  patched in 1.84/1.85), and the removal condition (drop the entries and every constraint using
  them once the AGP version on the classpath resolves Bouncy Castle ≥ this ref on its own). Extend
  the file header's family list to name this family. Then, in the root `build.gradle.kts`
  `buildscript { dependencies { constraints { … } } }`, add a `classpath` constraint for each of the
  three entries with a `because(...)`. Plain (required) versions, not `strictly`: the constraint is a
  floor, so a later AGP that ships a newer Bouncy Castle wins conflict resolution rather than being
  held back.
- [x] **2. Lint-runtime constraint on every Android module.** In the root `build.gradle.kts`, for
  every subproject that applies an Android plugin (`com.android.application`, `com.android.test`,
  `com.android.kotlin.multiplatform.library` — the three this repo uses), add the same three
  constraints to its `androidLintTool` configuration, reading the versions from the catalog entries
  of item 1. Look the configuration up **by name** (not `matching { … }`) so an AGP upgrade that
  renames or stops creating it fails configuration loudly instead of the constraint silently
  binding to nothing. Comment the block with the same removal condition as item 1 and cross-reference
  the catalog entries. If `androidLintTool` does not yet exist when the plugin's `withId` callback
  fires, find the earliest hook where it does and record why in the comment.
- [ ] **3. Prove nothing that ships changed.** Build the CycloneDX SBOM on this branch and on `main`
  (a temporary `git worktree` under the scratchpad, removed afterwards) and diff their sorted
  component `purl` lists; scan every configuration of every project for Bouncy Castle and confirm it
  still appears only in `androidLintTool` (plus the root buildscript `classpath`). No code change
  expected — if either comparison differs, stop and re-plan.
- [ ] **4. Exercise the signing path on the new version.** (a) Debug keystore creation — the
  `sdk-common` path that generates a self-signed certificate with Bouncy Castle: run
  `:androidApp:assembleDebug` with `ANDROID_USER_HOME` pointed at a fresh scratchpad directory so AGP
  must create a new `debug.keystore`, then verify the APK with `apksigner verify --print-certs`.
  (b) Release signing — generate an ephemeral PKCS12 keystore exactly as `CONTRIBUTING.md` shows,
  export the four `ASTRO_KEYSTORE_*` / `ASTRO_KEY_*` variables, run `:androidApp:assembleRelease`
  (which also runs `lintVitalRelease` on the raised Lint runtime), and `apksigner verify
  --print-certs` the release APK against that keystore's certificate. No code change expected.
- [ ] **5. Run the shrunk, signed app the way `verify-android-release` does.** With the same
  ephemeral keystore variables, run `:androidApp:aospAtd34MinifiedTestAndroidTest` and
  `:androidAppReleaseTest:aospAtd34ReleaseLoopbackAndroidTest` locally on the Gradle Managed Device.
  If this host cannot boot the managed device, stop and ask rather than tick — the fallback is the
  PR's CI run of `verify-android-release`, which only happens after a push this skill does not make.
- [ ] **6. Documentation.** Update every place that describes the Bouncy Castle stack as AGP-pinned
  and unremediable: the SBOM skip-list comment in the root `build.gradle.kts` (`^androidLintTool$`
  entry — the skip stays because the configuration's *other* tooling, e.g. httpclient and
  commons-lang3, is still AGP-pinned; Bouncy Castle inside it is now raised by the item-2
  constraint), the `dependency-submission` comment in `.github/workflows/security.yml` that lists
  "apksig → BouncyCastle … pinned by AGP", and `.claude/CLAUDE.md` — the SBOM-gate paragraph
  ("Only build-time tooling this repo cannot remediate is skipped …") and the version-catalog
  paragraph in "Tech stack & versions" (the families list, and a sentence on the Bouncy Castle
  floor, its constraint-only use, and its removal condition).
- [ ] **7. Full gate.** Run `./gradlew ktfmtFormat`, then `./gradlew check` (includes
  `verifyCheckPartition`, Lint via `:androidApp:lint`, and the iOS half on this Mac).

## 5. Testing & Validation (for agent)

- [x] **1.** `./gradlew -q buildEnvironment | grep bouncycastle` shows each of the three modules as
  `1.80.2 -> 1.85` (or resolved at 1.85) and no line resolving below 1.85;
  `./gradlew :androidApp:help` still configures. The `libs` accessor resolves inside `buildscript`
  constraints (a configuration error here means it does not — use the same accessor form the
  existing `classpath(libs.…)` lines use). Floor semantics, not a pin: temporarily add a direct
  `classpath("org.bouncycastle:bcprov-jdk18on:1.86")` to the root buildscript, confirm
  `buildEnvironment` resolves bcprov at 1.86 (a `strictly` constraint would fail resolution
  instead), and revert. Constraint-only: `git diff main -- build.gradle.kts` shows the three
  entries only inside a `constraints { }` block, with no `strictly`/`version { }` override and no
  new `classpath(libs.bouncycastle…)` outside it.
- [x] **2.** `./gradlew -q :<p>:dependencies --configuration androidLintTool | grep bouncycastle` for
  `androidApp`, `androidAppReleaseTest`, `shared` shows every Bouncy Castle node at 1.85, and
  `:androidApp:dependencyInsight --configuration androidLintTool --dependency bcprov-jdk18on`
  names the constraint (its `because` text) as the selection reason — proving it was raised by a
  constraint, not a new direct dependency. Floor semantics: temporarily declare a direct
  `androidLintTool("org.bouncycastle:bcprov-jdk18on:1.86")` in `:androidApp`, confirm it resolves
  at 1.86, and revert.
  Negative check: temporarily change the looked-up configuration name to a nonexistent one, confirm
  `./gradlew help` fails naming it, and revert (evidence: the failure line in the resume summary).
- [ ] **3.** `diff` of sorted `purl`s from `build/reports/cyclonedx/bom.json` (branch vs. `main`
  worktree) is empty, and neither contains `org.bouncycastle`; the all-configuration scan (awk over
  `:<p>:dependencies` for each project plus `buildEnvironment`) lists only `androidLintTool` and the
  root `classpath`, every entry at 1.85. Worktree removed (`git worktree list` clean).
- [ ] **4.** (a) `debug.keystore` exists in the fresh `ANDROID_USER_HOME` after the build, and
  `apksigner verify --print-certs` on the debug APK exits 0. (b) `assembleRelease` succeeds (build
  output shows `lintVitalAnalyzeRelease` executed, not `UP-TO-DATE`/`FROM-CACHE` — pass
  `--rerun-tasks` for that task or clean first), and `apksigner verify --print-certs` on the release
  APK exits 0 with a SHA-256 digest equal to `keytool -list -v` of the ephemeral keystore.
- [ ] **5.** Both managed-device tasks pass with a non-zero test count (the root build's zero-tests
  guard fails the task otherwise); quote the UTP result summary.
- [ ] **6.** `grep -rn -i "bouncy" build.gradle.kts .github/workflows/security.yml .claude/CLAUDE.md
  gradle/libs.versions.toml` shows no remaining claim that the Bouncy Castle stack is AGP-pinned or
  unremediable; `./gradlew ktfmtCheck` passes on the root script.
- [ ] **7.** `./gradlew check` exits 0 (quote `BUILD SUCCESSFUL` and confirm `verifyCheckPartition`
  and `:androidApp:lint` ran). Post-merge, out of this branch's reach: the next `main`
  `dependency-submission` reports 1.85 and alerts #17, #18, #50, #51 close — the item-2/3 scans are
  the pre-merge proxy for that, since the submission's graph is every configuration except
  `^classpath$`.

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
