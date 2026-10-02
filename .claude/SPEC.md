# Specification: Vendored design tokens, plain-value codegen, and the bundled theme registry (M-9 1/3)

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
task: M-9
issues: [161]
completes: no
spec-objective: Vendor astro-docs' published design artifacts into :shared behind a CI byte-parity gate, generate plain Kotlin token values from them at build time, and add a bundled theme registry keyed by id@version that resolves a screen's theme reference with an OS-scheme cold-start fallback.
```

## 1. Overview

This is the first of three branches for M-9, the mobile design-system foundation. It brings the
design system's published artifacts into the mobile repo, and turns them into plain values the
shared module exposes to both platforms. That covers the two theme documents, the theme-invariant
base, the font manifest and the validation rules. It also gives the client its first bundled theme
registry. It is shared-module only: no UI toolkit, no fonts, no app code. The Android `AstroTheme`,
the iOS design system and the fonts follow in part 2, and components in part 3.

## 2. Objective

The published design artifacts are vendored and held byte-equal to astro-docs by a CI parity gate.
A build-time generator turns them into plain Kotlin values in the shared module. The shared module
carries a registry of the bundled themes by `id@version`, which resolves a screen response's theme
reference and falls back to the OS scheme's theme at cold start.

## 3. Requirements & Context

- **Source of truth.** The artifacts are astro-docs' *published* build output (both theme documents,
  the base, fonts and rules), never the token source. The astro-docs submodule pin must move to a
  merged `main` commit that carries them; the current pin predates them. The contract mirror must
  still pass its existing parity gate after the bump.
- **Vendored, never read at build time.** Committed copies live in the shared module. A parity gate
  byte-compares each against the submodule, runs in the only CI half that checks the submodule out,
  and fails the same way the contract parity gate does when the submodule is missing. Codegen reads
  only the vendored copies, because the submodule is private, the iOS job doesn't check it out, and
  fresh clones have none.
- **Plain-value generated surface.** These values reach Swift through the framework, so:
  - colors are 64-bit ARGB integers, because 32-bit overflows and unsigned types bridge boxed;
  - dp and sp values are plain numbers, and font ids come from the font manifest;
  - hairline-family entries keep a distinct hairline marker, so zero and hairline never collapse to
    the same value;
  - the surface is plain objects with values plus enums: no value classes, no generic maps, no
    library types.
- **Generated at build time, committed never.** Follow the existing precedent for generating
  test-time contract sources: output under the build directory, wired lazily as a source directory.
  A unit test on both the JVM host and the iOS simulator asserts a sample of generated values against
  the vendored JSON.
- **Bundled registry.** It carries each built-in theme's `id@version`, which is what M-5 will send as
  the known theme and what the client paints at cold start. It resolves a response's theme reference
  to a bundled theme. With no match, or no response yet, the fallback is the bundled theme matching
  the OS color scheme: a boot default only, not follow-system.
- **Header surface unchanged.** Nothing generated may introduce a library type into the iOS framework
  header, so the existing header-surface gate must stay green without modification.
- **CI partition.** The new gate and any generation task must be classified into a CI half, or the
  partition drift guard fails.
- **Out of scope:** fonts and the font gate, `AstroTheme`, the iOS design system, components, the
  access-rule lint (parts 2 and 3), and the whole theme network path (M-5: known theme on the wire, the
  read-time validator, applying a delivered document).
- **Governing decisions:** `ADR-design-system-digestion` §1 (published artifacts, vendored
  byte-for-byte) and §6 (plain-value codegen, bundled registry). Full task scope: astro-plans
  `tasks/M-9.md` items 1 (JSON half), 2 and 3.

## 4. Implementation Plan and Progress Tracking (for agent)

Facts fixed at plan time (re-check before relying on them):

- **Pin target.** `docs/astro-docs` is pinned at `015932e`, which has no `design/`. astro-docs
  `main` is `fa73099`. It carries `design/build/{themes/light.json, themes/dark.json, base.json,
  fonts.json, rules.json}`, published by W-11 at `67b1fee`. At `fa73099`, `openapi.yaml` is
  byte-identical to the vendored contract. The month-screen fixture is **not**: W-11 moved it to
  `tokenSetVersion` 2, theme `light@72b388da3a0737d9630429d471334101`, and 12 new status roles. So
  the bump must re-vendor the fixture, or the existing parity gate fails.
- **Vendored location.** The obvious `shared/design/build/` is swallowed by `.gitignore`'s
  `**/build/`, so the copies live at `shared/design-system/`, path-for-path under the upstream
  `design/build/`.
- **Generated surface.** `base.json` has three `HairlineFamily` forms: a number, `{hairline: true}`
  and `{value, scalesWithType: true}`. Radius has a number or `{full: true}`. `layout.web` is not
  consumed on mobile (ADR §2 unit table). `states` is deferred to part 3 (user decision 2026-10-02).
  `rules.json` is vendored and parity-gated for M-5, and codegen reads only its theme-id and
  theme-version patterns.

- [ ] **1. Move the astro-docs pin and re-vendor the fixture it changed.** Point `docs/astro-docs`
  at `fa7309986c` (a merged `main` commit carrying `design/build/`). Copy the mirror's
  `calendar-month-screen.v0.example.json` over
  `shared/src/commonTest/resources/contract/calendar-month-screen.v0.example.json` unedited, since
  the mirror is upstream. If any test asserts a fixture value that moved (the theme version, the
  token-set version, a color count), update that assertion to the new fixture and name each one in
  the commit body. Nothing else changes.
- [ ] **2. Vendor the five design artifacts and gate them against the mirror.** Copy
  `design/build/{themes/light.json, themes/dark.json, base.json, fonts.json, rules.json}` byte-for-byte
  into `shared/design-system/`. Register `verifyVendoredDesignArtifactParity` in the root
  `build.gradle.kts` beside `verifyVendoredContractParity`, with the same shape:
  - `File`s captured at configuration time, no declared inputs, so it never reports UP-TO-DATE;
  - one failure listing every missing mirror file plus the `git submodule update --init` hint, with
    no skipping;
  - one failure for missing vendored files;
  - a byte comparison naming the first differing file, with the "re-copy, don't edit" remedy.

  Factor the comparison out of the contract gate only if that leaves the contract gate's messages
  unchanged. Wire the task into every subproject's `check` exactly as the contract gate is wired,
  and classify it into `androidCommonVerification`, the half that checks the submodule out, with a
  comment saying so.
- [ ] **3. Scaffold the token generator, starting with font ids.** Add two typed tasks in
  `shared/build.gradle.kts`, beside `GenerateEmbeddedContractSource`. They are separate tasks, not one
  task with two outputs, because `kotlin.srcDir(<task provider>)` adds **every** output of the task
  to the source set, which would compile test-only payloads into the framework:
  - Inputs are the vendored JSON files under `shared/design-system/` (`@InputFile`,
    `PathSensitivity.NONE`) and nothing else, so neither task can read the submodule.
  - Parsing uses `groovy.json.JsonSlurper`, so no new buildscript dependency.
  - It wipes its outputs on each run, writes `// GENERATED FILE — do not edit.` headers, and fails
    the build on any shape it does not model: a missing required key, an unknown value form, or a
    duplicate generated name after kebab/dotted → camelCase.
  - `GenerateDesignTokenSource` writes `build/generated/designTokens/commonMain/kotlin`, package
    `io.jitrapon.astro.design.tokens`, wired with
    `commonMain { kotlin.srcDir(generateDesignTokenSource) }`.
  - `GenerateEmbeddedDesignArtifactSource` writes
    `build/generated/designTokens/commonTest/kotlin`: an `internal object EmbeddedDesignArtifacts`
    holding each vendored file's text as constants (chunked like the contract embed), so tests on
    both targets read the JSON without a resource loader. It is wired with
    `commonTest { kotlin.srcDir(generateEmbeddedDesignArtifactSource) }` and nothing else.
  - Add both tasks to the existing `lintAnalyze*` / `generate*LintModel` dependency edge, which
    exists because Lint drops the source-dir producer edge.
  - The first emitted surface is `enum class FontId(val id: String, val family: String)` from
    `fonts.json`.
- [ ] **4. Generate the theme-invariant values from `base.json`.** Emit:
  - `class Dimension(val dp: Double, val hairline: Boolean, val scalesWithType: Boolean)`. A
    `{hairline: true}` entry is `dp = 0, hairline = true`, so zero and hairline stay distinct.
  - `class Radius(val dp: Double, val full: Boolean)`.
  - `enum class FontRole { DISPLAY, BODY }`.
  - `class TypeRamp(val fontRole: FontRole, val sizeSp: Double, val weight: Int, val lineHeightSp:
    Double, val letterSpacingEm: Double)`. An absent `letterSpacing` is `0.0`, never a nullable
    `Double`, which would bridge to Swift boxed.
  - Objects `Spacing`, `Radii`, `ComponentMetrics`, `ComponentRadii` and `Typography`, one `val` per
    key, plus `BASE_SET_VERSION`.
  - Skip `layout.web` and `states`. The generator fails if a `dp` family declares another unit, or
    `typography.sizeUnit` is not `sp`.
- [ ] **5. Generate the two bundled themes and the color bindings.** Emit:
  - `enum class ColorRole(val key: String)` from the theme color keys. Fail if light and dark
    declare different key sets, since a bundled theme missing a role would have nothing to paint.
  - `class ThemeColors`, one `Long` ARGB `val` per role, plus `fun color(role: ColorRole): Long` (a
    `when`, not a map). ARGB is `alpha = round(alpha × 255)` over the six hex digits, so `scrim`
    alpha 0.32 → `0x52000000`.
  - `class Shadow(offsetXDp, offsetYDp, blurDp, spreadDp: Double, color: Long)` and `class
    ThemeShadows`.
  - `class ThemeFonts(val display: FontId, val body: FontId, val thai: FontId)`. Fail if a font id is
    not in `fonts.json`, or is bound to a role outside its `bindableRoles`.
  - `class BundledTheme(id, version, label, colorScheme: ColorScheme, tokenSetVersion, colors,
    shadows, fonts)` with `val knownThemeReference: String` (`"$id@$version"`). It reuses the wire
    `io.jitrapon.astro.data.calendar.ColorScheme`.
  - `object BundledThemes { val light; val dark; val all: List<BundledTheme> }`.
  - `object ColorBindings`, one `ColorRole` per `bindings.color` key. Fail if a binding names a role
    the themes do not declare.
  - At generation time, check each id and version against `rules.json`'s `themeId` / `themeVersion`
    patterns, and require exactly one bundled theme per `ColorScheme`.
- [ ] **6. Add the bundled theme registry.** Hand-write `BundledThemeRegistry` in
  `shared/src/commonMain/kotlin/io/jitrapon/astro/design/tokens/` over the generated `BundledThemes`.
  `fun resolve(reference: ThemeRef?, systemColorScheme: ColorScheme): BundledTheme` returns the theme
  whose id **and** version both equal the reference. With no reference, or no match (an unknown id,
  or a known id at another version), it returns the bundled theme whose `colorScheme` equals
  `systemColorScheme`. That is a boot default only; nothing observes the OS scheme. KDoc states that
  M-5 sends `knownThemeReference` and that this is not follow-system.
- [ ] **7. Confirm the generated surface reaches Swift as plain values.** No code change expected.
  Link the debug simulator framework and inspect `shared.h` for the generated types. The plan
  checks are listed in §5 item 7.
- [ ] **8. Documentation.** Update `.claude/CLAUDE.md`:
  - the command table gains `verifyVendoredDesignArtifactParity`;
  - the `verify-android-common` bullet says the submodule job now also feeds the design parity gate;
  - a new "Design tokens and the bundled theme registry" key-pattern bullet covers the vendored
    location (and why not `design/build/`), the parity gate, the generator, the plain-value rules
    (`Long` ARGB, `Dimension`'s hairline/scalesWithType flags, no value classes/maps/nullable
    numbers), the registry's resolve-or-scheme fallback, and parts 2/3 still to come;
  - the package layout names `design/tokens/`;
  - "Documented config files" adds `shared/design-system/`.
- [ ] **9. Full gate, with and without the submodule.** Run `./gradlew ktfmtFormat`, then
  `./gradlew check` in the main checkout. Then build and test from a fresh worktree with no submodule
  and no build outputs, including the CLAUDE.md `xcodebuild` iOS app build, as §5 item 9 sets out.

## 5. Testing & Validation (for agent)

- [ ] **1.** `git ls-tree HEAD docs/astro-docs` shows `fa7309986c…`, and `ls
  docs/astro-docs/design/build/` lists the five artifacts. `./gradlew verifyVendoredContractParity`
  passes. `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` passes, including
  `ContractParityTest`, which reads the re-vendored fixture. From a detached worktree with an
  ephemeral keystore (the root `keystore.properties` overrides the env vars),
  `:androidApp:aospAtd34MinifiedTestAndroidTest` and
  `:androidAppReleaseTest:aospAtd34ReleaseLoopbackAndroidTest` pass with non-zero test counts.
  Quote the UTP counts, since both decode or serve the fixture.
- [ ] **2.** `cmp` of each vendored file against its mirror is silent, and `git check-ignore -v
  shared/design-system/base.json` prints nothing. `./gradlew verifyVendoredDesignArtifactParity` and
  `./gradlew verifyCheckPartition` pass. Run three negative checks, each reverted afterwards and
  each failure quoted:
  - Flip one byte in the vendored `base.json`: the gate fails naming that file.
  - Move the mirror's `design/build/` aside: it fails with the missing-mirror message listing all
    five files and the `git submodule update --init` hint.
  - Drop the partition entry: `verifyCheckPartition` fails naming the task.

  `./gradlew verifyVendoredContractParity` still prints its unchanged messages. Prove it with the
  same byte flip on `openapi.yaml`, reverted.
- [ ] **3.** All of these compile with the generated `FontId` in place: `./gradlew
  :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64 :shared:testAndroidHostTest
  :shared:iosSimulatorArm64Test :androidApp:lint`. The generated files exist under
  `shared/build/generated/designTokens/` and `git status` shows nothing generated under `src/`. A
  second identical run reports `:shared:generateDesignTokenSource UP-TO-DATE`; touching a vendored
  file reruns it. A new commonTest `DesignTokensParityTest` parses `EmbeddedDesignArtifacts`'
  `fonts.json` with kotlinx.serialization (independent of the generator's JsonSlurper) and asserts
  `FontId.entries` ids and families equal its keys and `family` fields. Fail-closed check: add a
  bogus value form to a scratch copy wired in temporarily, confirm the generator fails naming the
  key, and revert. Source-set separation: after `:shared:compileAndroidMain`, `find shared/build
  -name 'EmbeddedDesignArtifacts*.class'` finds nothing outside a test compilation's output, while
  `DesignTokensParityTest` (which imports it) compiles and runs on both targets. Submodule
  independence: in a fresh `git worktree` of `HEAD` under the scratchpad (no submodule, no
  `build/`), `./gradlew --no-build-cache :shared:compileKotlinIosSimulatorArm64
  :shared:testAndroidHostTest` passes. Remove the worktree afterwards.
- [ ] **4.** `DesignTokensParityTest` grows `base.json` assertions on both targets:
  - `Spacing.gutter` is `hairline = true, dp = 0.0`;
  - `ComponentMetrics.gridLineWidth.hairline` and `chipAccentEdgeWidth.dp == 3.0`;
  - `Spacing.eventGap` is `scalesWithType = true, dp = 2.0`;
  - `Radii.full.full` and `Radii.base.dp == 8.0`;
  - `Typography.displayLg` letter spacing `-0.02`, `bodyBase.lineHeightSp == 22.0`,
    `labelSm.weight == 500`;
  - `BASE_SET_VERSION` equals the JSON's.

  Every `spacing` / `component.metrics` / `radius` / `typography.ramps` key in the parsed JSON has a
  generated value, compared as a key set through a generated name map or an explicit list the test
  holds. Both host and simulator tests pass.
- [ ] **5.** `DesignTokensParityTest` asserts that `ColorRole.entries` keys equal each theme
  document's color keys, and that every `ThemeColors.color(role)` equals the ARGB the test computes
  from the parsed hex and alpha, for both themes. That covers `light.primary == 0xFFB81311` and
  `scrim == 0x52000000`. It also checks one shadow per theme, the fonts, ids, versions, labels and
  `colorScheme`, and each `ColorBindings` value against `bindings.color`. A fail-closed check like
  item 3's covers a binding to an undeclared role. Both host and simulator tests pass.
- [ ] **6.** A commonTest `BundledThemeRegistryTest`, on both targets, covers:
  - **every** theme in `BundledThemes.all`: its exact `ThemeRef(id, version)` resolves to that theme
    under **both** system schemes, so a dark reference on a light-scheme device returns dark;
  - the fixture's `theme`, decoded from `EmbeddedContract.MONTH_SCREEN_FIXTURE_JSON`, resolves to
    `BundledThemes.light` under **both** system schemes;
  - a known id at an unknown version falls back by scheme;
  - an unknown id falls back by scheme;
  - `null` resolves to `light` / `dark` for each scheme;
  - every `knownThemeReference` matches the contract's `knownTheme` pattern from
    `EmbeddedContract.CALENDAR_SCREEN_PARAMETERS`.
- [ ] **7.** `./gradlew :shared:verifyFrameworkHeaderSurface` passes with `shared.h` unmodified.
  `grep` of the generated declarations in `shared.h` shows `int64_t` for every color, `double` for
  dimensions, and the generated enums and classes under their `Shared` names. It must show no
  `SharedKotlinx_serialization`, `SharedKotlinLong`, `SharedKotlinULong`, `SharedKotlinUInt` or
  `SharedKotlinDouble` in them, and no `NSDictionary`. Quote the relevant header lines.
- [ ] **8.** `grep -n "design-system\|verifyVendoredDesignArtifactParity\|design/tokens"
  .claude/CLAUDE.md` shows each named place updated, and the CLAUDE.md `xcodebuild` command row is
  untouched.
- [ ] **9.** `./gradlew check` exits 0 in the main checkout. Quote `BUILD SUCCESSFUL` and confirm
  `verifyVendoredDesignArtifactParity`, `verifyCheckPartition`, `:androidApp:lint` and
  `:shared:verifyFrameworkHeaderSurface` ran. Then, in a fresh `git worktree` of `HEAD` under the
  scratchpad, with `docs/astro-docs` left uninitialized and no `build/` (the state of `verify-ios` and
  of a fresh clone), run:
  - `./gradlew --no-build-cache :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`, which
    passes;
  - the CLAUDE.md `xcodebuild` iOS app build, with `-derivedDataPath` inside that worktree, which
    succeeds;
  - `./gradlew verifyVendoredDesignArtifactParity`, which **fails** with the missing-mirror message.
    That shows the gate, not the build, is the only reader of the submodule.

  Remove the worktree afterwards (`git worktree list` clean).

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

<Which docs need updating: `.claude/CLAUDE.md`, `.claude/LOCAL_DEV.md`, `README.md`, etc.>

## 8. References

https://github.com/jitrapon/astro-mobile/issues/161
https://github.com/jitrapon/astro-mobile/issues/160
https://github.com/jitrapon/astro-docs/blob/main/tasks/M-9.md
https://github.com/jitrapon/astro-docs/blob/main/adr/ADR-design-system-digestion.md
