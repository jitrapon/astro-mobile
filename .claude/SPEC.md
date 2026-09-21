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
spec-objective: Make a Kotlin/Native partial-linkage problem in the shared module fail the framework link on every iOS target instead of producing a runtime-only stub, with the existing local and CI gates green.
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

Context the plan rests on: `shared/build.gradle.kts` declares the three iOS targets and their
`shared` framework binaries but sets **no** compiler arguments anywhere today, so "the build's
current compiler-options style" means KGP's `compilerOptions` DSL (never the deprecated
`kotlinOptions`). No new Gradle task is added, so `verifyCheckPartition` has nothing to classify.

- [ ] Raise the partial-linkage log level to `ERROR` for every Kotlin/Native target in
      `shared/build.gradle.kts`, with the explanatory comment beside it. Configure it over
      `targets.withType<KotlinNativeTarget>()` rather than the hand-written three-target list, so a
      Native target added later inherits the enforcement instead of having to opt in. **Choose the
      attachment point by evidence, not by reading the DSL:** compilation-level `compilerOptions`
      and a binary's own `freeCompilerArgs` are different inputs to a `KotlinNativeLink` task, and
      stubs are manufactured at link. Start from the target/compilation `compilerOptions` form the
      issue proposes; if §5's link-argument check shows the flag missing from a framework link, add
      it to the binaries as well. The comment must state (a) that the compiler's default is to
      detect these problems silently, (b) that the consequence is an iOS-only crash on the code
      path that reaches the stub, and (c) that a report is fixed by aligning versions in the
      catalog, never by lowering the level — and it must reference no SPEC section or review round.
- [ ] Resolve anything the enforcement reports by aligning versions in `gradle/libs.versions.toml`
      — never by suppression, a lower level, or a per-target carve-out. **If it reports anything,
      stop and split this item** into one sub-item per conflicting dependency before editing: the
      size of that work is unknowable until the flag is on, and version alignment re-opens the
      behaviour-preservation question (resolved graphs, the SBOM) that a one-line build change does
      not. If it reports nothing, tick this as a no-op and record the evidence under its §5 pair.
- [ ] Update `.claude/CLAUDE.md` where it describes what the iOS gates catch (the `verify-ios`
      bullet, and the shared-module patterns if a second mention reads naturally): the framework
      link now fails on a partial-linkage problem, why the silent default was not acceptable here,
      and that the remedy is catalog alignment. `shared/build.gradle.kts` is on the documented
      config-files list, so an undocumented behaviour change there is what `finish-branch`'s drift
      check would flag anyway. *(Not listed in §7, which the issue left empty — strike this item if
      the comment beside the flag is documentation enough.)*

## 5. Testing & Validation (for agent)

- [ ] **Flag accepted, not ignored (item 1).** Kotlin treats an unknown `-X` argument as a
      *warning*, so a misspelt or renamed flag builds green while enforcing nothing — the same
      vacuous pass this branch exists to remove. Confirm the Kotlin 2.4.20 Native compiler accepts
      `-Xpartial-linkage-loglevel=ERROR`: a forced re-run of a Native compile and a framework link
      emits no unsupported/unknown-argument warning naming it.
- [ ] **Reaches the link, on every target (item 1).** Force-re-run the framework link for each iOS
      target with Gradle's `--info` logging — `linkDebugFrameworkIosSimulatorArm64`,
      `linkDebugFrameworkIosX64`, `linkDebugFrameworkIosArm64` and `linkReleaseFrameworkIosArm64`
      (the one CI ships) — and find the flag in the argument list of the **link** invocation
      itself. Seeing it on `compileKotlinIos*` proves nothing: that is klib compilation, where no
      stub is made. Record which attachment point was needed.
- [ ] **Prove it fails, not just that it passes (item 1).** On a scratch basis, never committed,
      force an older version of a transitive dependency the third-party klibs call into (first
      candidate: the I/O library underneath the HTTP client, whose API moved substantially between
      releases) so a real unresolved reference exists. Then show both halves: with the flag the
      framework link **fails** and names the unresolved symbol; with the flag removed the *same*
      perturbation links **green**. The second half is what demonstrates the silent default and
      that the flag — not something else — is doing the work. Revert and confirm `git status` shows
      no trace. If no perturbation yields a linkage problem, do **not** tick this on the strength of
      the argument check alone — stop and ask.
- [ ] **Clean on today's graph (item 2).** With the flag on, every framework link above plus
      `./gradlew verifyIos` is green — which also covers the simulator **test** binary's link and
      `:shared:verifyFrameworkHeaderSurface`. If item 2 changed any version: re-diff the resolved
      `:androidApp` and `:shared` graphs and the `:cyclonedxBom` component inventory against
      `origin/main`, and account for every changed line.
- [ ] **The app still builds against the framework (items 1–2).** Run CI's exact headless
      `xcodebuild` invocation (the *iOS app build (simulator)* row in `.claude/CLAUDE.md`). It
      links the framework through the Xcode embed path rather than the task `verifyIos` runs, so it
      is a second, independent route to the same enforcement.
- [ ] **Docs (item 3).** `./gradlew check` green, and a read-through confirms CLAUDE.md claims
      nothing the build does not enforce — in particular, it must not imply the Android/JVM side
      gained a comparable check.
- [ ] **Whole gate, local.** `./gradlew check` green with every §4 item landed.

CI is deliberately not a checkbox here. `verify-ios` and the release-framework link step only run
once `finish-branch` pushes the branch to its PR, and the review loop refuses to start while any
item above is unticked — so a CI checkbox would deadlock the two. `finish-branch` reads CI green
and records it in the PR's test plan instead.

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
