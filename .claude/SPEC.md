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

Context the plan rests on, established by a read-only probe of the configured build:
`shared/build.gradle.kts` declares the three iOS targets and their `shared` framework binaries but
sets **no** compiler arguments anywhere today, so "the build's current compiler-options style" means
KGP's `compilerOptions` DSL (never the deprecated `kotlinOptions`). `:shared` has **nine**
`KotlinNativeLink` tasks — six framework links (debug and release × `IosArm64`, `IosSimulatorArm64`,
`IosX64`) and three test-binary links — and each exposes its configured
`toolOptions.freeCompilerArgs` (currently empty on all nine), which is what makes a persistent gate
buildable rather than aspirational.

- [ ] Raise the partial-linkage log level to `ERROR` for every Kotlin/Native target in
      `shared/build.gradle.kts`, with the explanatory comment beside it. Configure it over
      `targets.withType<KotlinNativeTarget>()` rather than the hand-written three-target list, so a
      Native target added later inherits the enforcement instead of having to opt in. **Choose the
      attachment point by evidence, not by reading the DSL:** compilation-level `compilerOptions`
      and a binary's own `freeCompilerArgs` are different inputs to a `KotlinNativeLink` task, and
      stubs are manufactured at link. Start from the target/compilation `compilerOptions` form the
      issue proposes; if §5 shows the flag missing from any link task's effective arguments, attach
      it to the binaries as well. The comment must state (a) that the compiler's default is to
      detect these problems silently, (b) that the consequence is an iOS-only crash on the code
      path that reaches the stub, and (c) that a report is fixed by aligning versions in the
      catalog, never by lowering the level — and it must reference no SPEC section or review round.
- [ ] Add a persistent gate, `verifyNativeLinksFailOnPartialLinkage`, registered in
      `shared/build.gradle.kts` (the only script with the Kotlin plugin's task types on its
      classpath), wired into `:shared:check`, and classified into a CI half **in the same commit**
      so `verifyCheckPartition` never fails between ticks. It enumerates every `KotlinNativeLink`
      task rather than a hand-written list — a list is how a variant goes unverified — and fails
      unless each one's effective free compiler arguments (a) contain
      `-Xpartial-linkage-loglevel=ERROR` and (b) contain **no other** `-Xpartial-linkage…` argument
      (a second log level, or `-Xpartial-linkage=disable`, from a per-binary override). It must also
      fail when it finds **zero** framework link tasks: an enumeration over nothing is the vacuous
      pass. Capture plain strings at configuration time so it stays configuration-cache-safe. It
      guards against the two regressions no green build reveals: someone removing the flag, and a
      Kotlin Gradle plugin upgrade changing how compilation options propagate so the flag still
      sits in the build file but no longer reaches link. Classify into `verifyIos`: whether Native
      link tasks are even registered on a non-macOS host is unverifiable from this machine, and the
      zero-task rule would turn that uncertainty into a red Linux job; the gate costs the macOS
      runner nothing measurable since it executes no compiler.
- [ ] Resolve anything the enforcement reports by aligning versions in `gradle/libs.versions.toml`
      — never by suppression, a lower level, or a per-target carve-out. **If it reports anything,
      stop and split this item** into one sub-item per conflicting dependency before editing: the
      size of that work is unknowable until the flag is on, and version alignment re-opens the
      behaviour-preservation question (resolved graphs, the SBOM) that a one-line build change does
      not. If it reports nothing, tick this as a no-op and record the evidence under its §5 pair.
- [ ] Update `.claude/CLAUDE.md`: the framework link now fails on a partial-linkage problem, why
      the silent default was not acceptable here, that the remedy is catalog alignment, and the new
      gate — in the commands table, in the `verify-ios` description of what that half carries, and
      beside the other enforced-not-asserted guards. `shared/build.gradle.kts` is on the documented
      config-files list and the gate is a new task inside `check`, both of which the doc already
      commits to describing. *(§7 is empty because the issue named no docs — strike this item if
      the comment beside the flag is documentation enough.)*

## 5. Testing & Validation (for agent)

- [ ] **Flag accepted, not ignored (item 1).** Kotlin treats an unknown `-X` argument as a
      *warning*, so a misspelt or renamed flag builds green while enforcing nothing — the same
      vacuous pass this branch exists to remove. Confirm the Kotlin 2.4.20 Native compiler accepts
      `-Xpartial-linkage-loglevel=ERROR`: a forced re-run of a Native compile and a framework link
      emits no unsupported/unknown-argument warning naming it.
- [ ] **Reaches every link, as executed (item 1).** Force each of the **six** framework links to
      actually run (`--rerun` on the task, with `--info`) and find the flag in the argument list of
      the **link** invocation itself — seeing it on `compileKotlinIos*` proves nothing, since that
      is klib compilation, where no stub is made. A task reported `UP-TO-DATE`, `FROM-CACHE` or
      `SKIPPED` is not evidence; re-run it. If `--info` does not print the linker arguments on this
      Kotlin version, say so and substitute the compiler's own argument dump rather than inferring.
      Record which attachment point was needed.
- [ ] **The gate's model matches reality (item 2).** The gate reads *configured* arguments; the
      check above reads *executed* ones. For at least one debug and the release `IosArm64` framework
      link, confirm the two agree — otherwise the gate can be green while the linker never sees
      the flag, which is the defect it exists to catch.
- [ ] **The gate fails when it should (item 2).** Mutation checks, each reverted after: (a) remove
      the flag → red, naming the link tasks missing it; (b) if any attachment form was found above
      that does **not** reach link, switch to it → red; (c) add a conflicting per-binary override
      to one framework (`-Xpartial-linkage-loglevel=WARNING`, then `-Xpartial-linkage=disable`) →
      red, naming that binary; (d) restore → green. Then `./gradlew verifyCheckPartition` passes
      and `./gradlew verifyIos` reaches the gate — confirmed from the task graph, not from the
      green result.
- [ ] **Prove the enforcement fails a real defect (item 1).** Scratch only, never committed. Work
      this ladder in order and stop at the first rung that yields a genuine defect: (1) force an
      older version of the I/O library underneath the HTTP client; (2) force an older version of
      another transitive the third-party klibs share (atomics, coroutines core); (3) build a
      throwaway two-version library *outside the repo* — a consumer klib compiled against an API,
      its provider swapped for a version without it — and link it into a scratch framework. A rung
      counts only if **all** hold: both runs execute the link rather than reuse it; the inputs are
      identical except the flag; with the flag the link **fails** on a partial-linkage diagnostic
      naming the unresolved symbol (a dependency-resolution or klib-compilation error is a
      different failure and does not count); without the flag the same inputs link **green**. That
      second half is what demonstrates the silent default and that the flag — not something else
      — is doing the work. Revert and confirm `git status` shows no trace. If all three rungs fail,
      do **not** tick this on the strength of the argument checks — stop and ask.
- [ ] **Clean on today's graph (item 3).** With the flag on, all six framework links plus
      `./gradlew verifyIos` are green — which also covers the three test-binary links and
      `:shared:verifyFrameworkHeaderSurface`. If item 3 changed any version: re-diff the resolved
      `:androidApp` and `:shared` graphs and the `:cyclonedxBom` component inventory against
      `origin/main`, and account for every changed line.
- [ ] **The app still builds against the framework (items 1–3).** Run CI's exact headless
      `xcodebuild` invocation (the *iOS app build (simulator)* row in `.claude/CLAUDE.md`). It
      links the framework through the Xcode embed path rather than the task `verifyIos` runs, so it
      is a second, independent route to the same enforcement.
- [ ] **Docs (item 4).** `./gradlew check` green, and a read-through confirms CLAUDE.md claims
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
