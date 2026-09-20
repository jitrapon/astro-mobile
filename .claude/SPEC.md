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
spec-objective: Enforce the Kotlin/stability-analyzer lockstep with a build gate, and move the Android app's inline dependency declarations into the catalog at identical resolved versions.
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

Ordering note: item 1 deliberately bundles registration, `check` wiring, and CI classification into
one pass. Splitting them would leave `verifyCheckPartition` failing between commits, and this
workflow commits per item — the tree must be green at every tick.

- [x] Register `verifyStabilityAnalyzerKotlinAlignment` in the root build script, wire it into every
      subproject's `check` (mirroring `checkNoDetektBaseline`), and classify it into
      `androidCommonVerification` — all in one commit. The task reads the `kotlin` and
      `compose-stability-analyzer` catalog refs and asserts the analyzer version is the release
      recorded for that Kotlin version, in a mapping transcribed from the analyzer's README. It must
      **fail closed** on a Kotlin version the mapping does not cover, since that is exactly the
      unattended-bump case; the failure message must name both versions and point at the README
      table. Host-portable (reads only catalog strings), so it belongs in the Android/common half.
- [x] Replace the `com.github.skydoves…` rule's description in `renovate.json` so it names the new
      guard as the enforcement partner, matching how the `com.facebook:ktfmt` rule reads against
      `verifyKtfmtAlignment`. Keep `enabled: false` — see §5 for why re-enabling or grouping is
      rejected rather than deferred.
- [x] Capture the pre-migration dependency baselines and commit them to a scratch location outside
      the repo: resolved graphs for the debug, release, and androidTest compile *and* runtime
      classpaths, plus a `:cyclonedxBom` run. These must be taken **before** any catalog edit —
      afterwards the baseline is unrecoverable without stashing, and a migration verified only
      against the debug graph cannot detect a coordinate that changed variant scope.
      - Captured at commit `9e8fd5a` into
        `/private/tmp/claude-501/-Users-jitrapon-Developer-Projects-Astro-astro-mobile/33dbb241-17fd-4f58-b79b-38029f5610dc/scratchpad/146-baselines/`:
        one `<configuration>.txt` per graph (`:androidApp:dependencies --configuration <name>` for
        `debug`/`release`/`debugAndroidTest` × `CompileClasspath`/`RuntimeClasspath`), `bom.json`,
        and `bom-components.txt` (sorted purls — diff this, not the raw BOM, whose serial number
        and timestamp change every run). The directory is under `/tmp` and does not survive a
        reboot; if it is gone, recreate it from a `git worktree` at `9e8fd5a` with the same
        commands rather than from the migrated tree.
- [ ] Add `compose`, `androidx-lifecycle`, and `activity-compose` version refs to the catalog at the
      versions the inline declarations currently pin, plus one library alias per artifact they
      cover. Keep `compose-material-icons` a separate ref; it must not be folded into `compose`.
- [ ] Replace the inline dependency declarations in the Android app's build script with the new
      catalog aliases, preserving each declaration's existing configuration
      (`implementation` / `androidTestImplementation` / `debugImplementation`).
- [ ] Update the catalog's header and per-ref comments: drop the sentence recording inline
      declarations as unmigrated debt (no longer true), and rewrite the `kotlin` and
      `compose-stability-analyzer` comments to name the guard as what enforces the lockstep rather
      than describing it as a convention to remember.
- [ ] Update `.claude/CLAUDE.md`: add the guard to the Linting section alongside the existing drift
      guards, and correct the version-catalog paragraph in Tech stack & versions, which still names
      the Android Gradle Plugin as the *one* inline exception while the app block also exists.

## 5. Testing & Validation (for agent)

- [x] **Guard (item 1) — prove it fails, not just that it passes.** `./gradlew
      verifyStabilityAnalyzerKotlinAlignment` passes on the current refs. Then perturb each side in
      turn — analyzer moved off its recorded release, and `kotlin` moved to a version absent from
      the mapping — and confirm each turns the task red with a message naming both versions. Restore
      and re-run. A guard only verified green is a guard never verified: the analyzer sat mismatched
      for twelve days while every build passed.
- [x] **Guard wiring (item 1).** `./gradlew verifyCheckPartition` passes, proving the new task is
      classified; `./gradlew verifyAndroidCommon` reaches it. Confirm the partition guard *would*
      have caught an unclassified task by checking it runs the new task in its closure rather than
      by trusting the green result.
- [x] **Renovate (item 2).** `npx --yes renovate-config-validator renovate.json` (or the repo's
      existing validation path) accepts the edited file. This item changes only a description
      string, so the check is that nothing else moved: `git diff` touches one `description` value.
- [x] **Baselines (item 3).** Confirm the captures exist and are non-empty for every configuration
      named there *before* any catalog edit lands. A missing baseline is discovered too late to
      recreate.
- [ ] **Catalog refs + inline replacement (items 4–5) — variant scope, not just version.** Diff the
      post-migration graphs against every baseline: debug, **release**, and **androidTest**, compile
      and runtime. All diffs must be empty. Diffing only the debug runtime graph cannot detect a
      declaration that changed configuration — moving `ui-test-manifest` from `debugImplementation`
      to `implementation`, or `ui-test-junit4` from `androidTestImplementation`, leaves the debug
      graph byte-identical while adding a test-only artifact to the shipping release APK.
- [ ] **Configuration preservation (item 5) — assert per coordinate.** Independently of the graph
      diffs, walk the ten migrated declarations and confirm each kept its original configuration
      (`implementation` / `androidTestImplementation` / `debugImplementation`). The graph diff is
      the detector of last resort; this is the direct check, and it is the one that names the
      mistake rather than showing a symptom.
- [ ] **Icons ref (item 5).** Confirm `androidx.compose.material:material-icons-core` still resolves
      to its own pinned version and was not dragged onto the Compose release train.
- [ ] **SBOM (items 4–5).** `./gradlew :cyclonedxBom`, diffed against the baseline. Note its limit
      explicitly: the SBOM is a component *inventory*, so an artifact that merely changed variant
      scope still appears once and the diff stays empty. It verifies no component was added,
      removed, or re-versioned — it does **not** verify scope, which is why the per-configuration
      diffs above carry that burden.
- [ ] **Docs (items 6–7).** `./gradlew check` passes end to end, and a read-through confirms no
      surviving claim that the lockstep is merely a convention or that inline declarations remain.
- [ ] **Whole gate.** `./gradlew check` green locally, and CI green on the PR — including `sca`,
      which re-reads the dependency graph this branch touches.

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
