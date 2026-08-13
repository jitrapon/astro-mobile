# Specification: M-1 part 2 — shared data layer (Ktor, serialization, contract models)

> Per-branch working file owned by the `spec-development` skill. Each branch
> overwrites the section bodies; this file in `main` is a skeleton that
> documents the canonical structure so every branch follows the same shape.

## 1. Overview

M-1 ("Set up astro-mobile project structure") landed only its first half: the `shared` / `androidApp` / `iosApp` restructure, followed by CI and toolchain work. Its remaining scope — a real networking and serialization stack in the shared module, plus data models shaped to the BFF's calendar screen contract — was never built, so the shared module still holds nothing but the original POC's login/greeting sample code. This branch closes that gap so it lands before M-2 rather than inside it, and additionally settles how dependencies are wired together in the shared module.

M-1 part 2 is deliberately separated from M-2 by risk class as well as size: this is KMP toolchain work (multiplatform HTTP engines, the serialization compiler plugin, iOS target linking), while M-2 is UI architecture (navigation, app shell, SDUI registry, view models). An iOS linking failure should not share a PR with the SDUI registry design.

## 2. Objective

The shared module builds for both the Android and iOS targets with a working HTTP client, JSON serialization, a dependency-injection graph, an API client for the BFF's range-based calendar screen endpoint, and hand-written data models matching that contract — all exercised by a contract-parity test rather than left as unused code, so M-2's view model and M-5's switch to generated models are a swap rather than a rewrite.

## 3. Requirements & Context

**Contract version.** Write the models against `schemaVersion` **0.2.0**, not the earlier "interim" shape the milestone plan's M-1 row describes. In 0.2.0 the response envelope carries a required `theme` reference and an optional theme document, and every screen operation accepts a shared `knownTheme` parameter; `resolvedPreferences.theme` is no longer a `light | dark | system` string enum. Models written to the 0.1.0-era shape would turn M-5's move to generated models into a rewrite.

**Model coverage.** The canonical event and calendar core, plus the month and agenda view-models. The API client targets the BFF's range-based calendar screen endpoint, parameterized by view, start, end, timezone, and locale.

**Contract vendoring and parity gating.** Mirror the pattern `astro-web` already established: vendor the BFF OpenAPI contract as a plain checked-in file (not a submodule — the BFF repo does not exist yet), add the upstream planning/docs repo as a submodule to provide the upstream mirror, and pair them with a shared-module test that holds all four artifacts to the same contract version: the vendored spec, the upstream mirror, the fixture, and the hand-written models. The parity test must decode the canonical month-screen example fixture through the real models and fail on drift between any of the four. This is what makes the branch self-verifying instead of shipping dead code with no caller until M-2.

**Dependency injection — user-directed scope addition beyond the issue.** Adopt **Koin** as the DI framework for the shared module on this branch, and wire the HTTP client, API client, and repository graph through it. Rationale: it is KMP-first, needs no KSP or compiler-plugin in the build, and keeps the iOS-facing surface small. The accepted trade-off is runtime resolution with no compile-time graph validation. Requirements:

- The DI graph is declared in shared common code; each platform contributes only its platform-specific pieces (notably the HTTP engine) and starts the graph at app launch.
- The iOS side must be able to initialize and resolve from the graph through the shared framework's public surface — no Swift-side reimplementation of the graph.
- Adopting Koin here means DI is settled before M-2 builds view models on top of it.
- Record the choice as an architecture decision in the planning repo, since that is where this project's architectural decisions live.

**Out of scope.** The BFF contract submodule and generated-client codegen (deferred to M-5, since the BFF is not yet scaffolded). Any UI, navigation, SDUI registry, or calendar view model (M-2). Google login and the auth flow (M-5).

**POC cleanup.** The leftover greeting / login sample types in the shared module must be removed, or explicitly retained with a stated reason.

**Downstream trigger.** This branch introduces the project's first real product dependencies, which is the trigger condition recorded on the deferred issue for a PR-blocking Gradle SCA (SBOM-based) gate. That issue's trigger was written against M-2 and should be re-pointed at this work.

**Blocking relationship.** This branch blocks M-2, whose calendar view model consumes these models and this DI graph.

**Local verification constraint.** The shared module's iOS unit tests must be verified with the simulator ARM64 target on Apple Silicon; the x64 iOS test task fails locally with a CPU-type error and is not a valid signal.

## 4. Implementation Plan and Progress Tracking (for agent)

Standing constraints that apply to every item below:

- **No hardcoded dispatchers or blocking in `commonMain`.** `DispatchersIOInCommonMain`, `RunBlockingInCommonMain`, and `MainScopeWithoutCancel` are blocking Detekt rules here, and `.semgrep/coroutines.yml`'s `kotlin-hardcoded-dispatcher` catches the top-level / builder-argument cases Detekt misses. The API client is `suspend`-only; Ktor is already main-safe, so no `withContext(Dispatchers.IO)` wrapper is needed or permitted.
- **No cleartext HTTP literal.** `.semgrep/astro-mobile.yml`'s `kotlin-cleartext-http-url` fires on an `http://` string literal in Kotlin. The base URL is injected, never a literal default.
- **No Detekt baseline and no `@Suppress`.** Findings are refactored away (`checkNoDetektBaseline` + `ForbiddenSuppress` enforce this).
- **All new dependency versions go in `gradle/libs.versions.toml`,** not inline — this branch is where the catalog stops being lint-only.

Ordered items:

- [ ] 1. Add the product-dependency versions and aliases to `gradle/libs.versions.toml` (kotlinx-serialization runtime + its Kotlin-pinned Gradle plugin, kotlinx-coroutines, Ktor client with the OkHttp and Darwin engines, Koin) and add the `kotlin-serialization` Gradle plugin artifact to the root `buildscript` classpath alongside the existing Kotlin/AGP entries. Rewrite the catalog's header comment, which currently declares the catalog deliberately lint-only.
- [ ] 2. Apply the serialization plugin in `shared/build.gradle.kts` and wire the source-set dependencies: `commonMain` gets ktor-client-core + content-negotiation + the JSON serialization converter + kotlinx-serialization-json + koin-core; `androidMain` gets the OkHttp engine; `iosMain` gets the Darwin engine; `commonTest` gets ktor-client-mock, kotlinx-coroutines-test, and koin-test. Do not add `export(...)` to the framework config — Koin and Ktor types must stay off the iOS framework's public surface (item 14 gives Swift a Kotlin facade instead).
- [ ] 3. Add `android.permission.INTERNET` to `androidApp/src/main/AndroidManifest.xml`. Without it every Ktor request on Android fails at runtime with a permission error no unit test would surface.
- [ ] 4. Add `docs/astro-docs` as a git submodule (`git@github.com:jitrapon/astro-docs.git`), matching astro-web's placement, and vendor the two contract artifacts from it: `contracts/astro-bff/openapi.yaml` (byte-identical copy) and the month-screen example fixture into `shared/src/commonTest/resources/contract/`. Document the `git submodule update --init` bootstrap step wherever the repo's setup instructions live.
- [ ] 5. Set `submodules: true` on the `verify-android-common` job's checkout in `.github/workflows/ci.yml` — only that job, since the mirror comparison runs in the host-portable half. Leave every `.github/workflows/security.yml` checkout at `submodules: false`; those jobs never read the mirror and the explicit `false` is a deliberate posture there.
- [ ] 6. Register a `verifyVendoredContractParity` task in the root `build.gradle.kts` following the repo's existing drift-guard pattern (`checkNoDetektBaseline`, `verifyKtfmtAlignment`): fail if the vendored `openapi.yaml` is not byte-identical to the submodule mirror, or if the vendored fixture differs from the mirror fixture after dropping the fixture-local `_comment` annotation key. Fail with an explicit "run `git submodule update --init`" message when the mirror is absent, rather than silently passing. Keep it configuration-cache-safe (capture `File`s / `String`s; do the comparison in `doLast`).
- [ ] 7. Wire `verifyVendoredContractParity` into every subproject's `check` and add `":" to "verifyVendoredContractParity"` to the `androidCommonVerification` list in the root build's partition data. The `verifyCheckPartition` guard fails until both halves are done, so this item is not complete until the guard reports OK.
- [ ] 8. Add a `:shared` build-time task that generates a Kotlin source file into a generated `commonTest` source directory, embedding the vendored fixture JSON and the contract's declared `info.version` as constants, and register that directory on the `commonTest` source set with the compile task depending on it. Emit long text as a concatenated list of chunks, not one literal — the JVM constant pool caps a single string constant at 65535 bytes and the contract is already within a few KB of that. This exists because `kotlin.test` has no multiplatform resource-loading API and the iOS simulator test binary's working directory is not a reliable base for file reads; the same loader is what the reserved `calendar-layout-golden` corpus will need on M-2.
- [ ] 9. Add the response envelope and theme models to `commonMain` at `schemaVersion` 0.2.0: the envelope's `schemaVersion` / `serverTime` / `locale` / `timeZone` / required `theme` / `screen` plus the optional `themeDocument`, the `ThemeRef`, and the theme document with its colour, shadow, and font token maps. Declare a single `SUPPORTED_SCHEMA_VERSION` constant holding `"0.2.0"` that both the client and the parity test read.
- [ ] 10. Add the canonical calendar core and the month view-model to `commonMain`: `Calendar` with its six-slot colour block, the presented-event wrapper (`itemType` + `presentation` + `props`), the timed / all-day event props discriminated on `kind`, the month body's range / anchor / header / calendars / events, and the month presentation components. Model the discriminated unions as sealed hierarchies rather than stringly-typed fields.
- [ ] 11. Add the agenda view-model plus the screen chrome shared by both views: screen id/title, navigation destinations, the view switcher and its view-selection union, the action union, and `ResolvedPreferences` — which at 0.2.0 carries `weekStart` / `chipStyle` / `chipDensity` and deliberately **no** `theme` field. Add a comment naming why the absence is load-bearing (a screen with no preferences block could not discover the theme when the reference lived there), so a future edit does not helpfully re-add it.
- [ ] 12. Add the shared `Json` configuration and the `CalendarScreenApi` client in `commonMain`: one `suspend` function taking the view selection, range start/end, timezone, locale, and `knownTheme`, hitting the BFF's range-based calendar screen path against an injected base URL, returning the existing sealed `Result<T>`. Production decoding is lenient (unknown keys ignored) so a BFF field addition does not break a shipped client; the parity test uses its own strict `Json` (item 16). Map transport and decode failures onto `Result.Error` rather than letting exceptions escape.
- [ ] 13. Declare the Koin graph: a `commonMain` module providing the `HttpClient`, the `Json`, and the API client, plus an `expect`/`actual` platform module supplying the `HttpClientEngine` (OkHttp on Android, Darwin on iOS). Expose a single `initKoin(...)` entry point taking the base URL, so neither platform reimplements the graph.
- [ ] 14. Start the graph on both platforms. Android: add an `Application` subclass calling `initKoin`, registered via `android:name` in the manifest. iOS: add a Kotlin facade on the shared framework's public surface that Swift calls to initialize and to resolve the API client, so no Koin or Ktor type crosses the framework boundary; call it from the SwiftUI app entry point.
- [ ] 15. Remove the POC leftovers: `Greeting`, `LoginDataSource`, `LoginDataValidator`, `LoginRepository`, `LoggedInUser`, their three greeting tests, and the now-unused JUnit dependency on the `androidHostTest` source set. Rewrite `iosApp`'s `iOSApp.swift` / `ContentView.swift`, which construct the login types today and will not compile otherwise — reduce the view to the minimum that proves the Koin facade resolves from Swift, not new product UI. **Retain `Result<T>`** and `Platform` / `Utils`: `Result<T>` is a documented project pattern and is the API client's return type, and the `expect`/`actual` pair is the documented platform-abstraction example. State the retention reason in `Result<T>`'s KDoc.
- [ ] 16. Write the contract-parity test in `commonTest`: decode the embedded fixture through the real models with a **strict** `Json` (unknown keys rejected) after stripping the fixture-local `_`-prefixed annotation keys, assert the decoded `schemaVersion` equals `SUPPORTED_SCHEMA_VERSION` equals `"0.2.0"`, assert the contract's embedded declared version agrees, and assert the delivered `themeDocument`'s `id@version` matches the envelope's `theme` reference. The strict decode is what turns an unmodelled contract field into a failure instead of a silently dropped field.
- [ ] 17. Add mutation cases to the parity test proving it fails on the drift it claims to catch — at minimum: the envelope loses `theme`, the theme document's version diverges from the reference, an event's `kind` disagrees with its temporal fields, the body props gain an unmodelled field, and a preference leaves its enumerated set. Without these the suite passes on an untouched fixture and proves nothing.
- [ ] 18. Add API-client tests in `commonTest` driving Ktor's `MockEngine`: the request path and every query parameter (view, start, end, timezone, locale, `knownTheme`) are what the contract specifies; a 200 decodes to `Result.Success`; a non-2xx or malformed body yields `Result.Error` rather than throwing. Add a Koin graph smoke test asserting every declaration resolves — the accepted cost of runtime DI is that only a test catches a missing binding.
- [ ] 19. Update `.claude/CLAUDE.md`: the "Tech stack & versions" section (the catalog is no longer lint-only; Ktor / kotlinx.serialization / coroutines / Koin are now the stack), the "Key patterns" section (add the Koin graph and the `Result<T>` retention), the CI section (the new partition entry and the submodule checkout), and the "Documented config files" list (`.gitmodules`, the vendored contract, the CI workflow change).
- [ ] 20. Write the DI-framework ADR in the `astro-plans` repo's `adr/` directory following the existing `ADR-<topic>.md` naming: the candidates considered (Koin, kotlin-inject, Metro, manual constructor injection), the decision, and the consequences — runtime resolution with no compile-time graph validation, the graph-resolution smoke test that compensates, and the iOS-facade constraint that keeps Koin off the framework's public surface. Commit it in that repo, not this one.
- [ ] 21. Re-point issue #100's trigger: its PR-blocking Gradle SCA (SBOM-based) gate is written as "for M-2 when real product dependencies land", and those dependencies land here. Update the issue to name this branch as the trigger. The gate itself stays deferred — implementing it is not in this branch's scope.

## 5. Testing & Validation (for agent)

Verification commands, paired to the §4 items. `./gradlew :shared:build` and `./gradlew check` both aggregate `iosX64Test`, which the shared build disables on Apple Silicon — so on an arm64 Mac `iosSimulatorArm64Test` is the valid iOS signal.

- [ ] 1–2. `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinAndroid` succeeds — the serialization plugin resolves and both engines link on their own target. Confirm the catalog change alone did not break the lint toolchain: `./gradlew :shared:verifyKtfmtAlignment`.
- [ ] 3. `./gradlew :androidApp:assembleDebug` succeeds; confirm `android.permission.INTERNET` appears in the merged manifest under `androidApp/build/intermediates/`.
- [ ] 4. `git submodule status` reports `docs/astro-docs` at a commit; `diff contracts/astro-bff/openapi.yaml docs/astro-docs/openapi.yaml` is empty. Confirm a clone without `--recursive` still *configures* the build (item 6's message path, not a hard configuration failure).
- [ ] 5. Read back the workflows: only `verify-android-common` sets `submodules: true`, and every `security.yml` checkout still says `submodules: false`.
- [ ] 6. `./gradlew verifyVendoredContractParity` passes as committed; then temporarily perturb the vendored copy by one byte and confirm it fails naming the file, and temporarily move `docs/astro-docs` aside and confirm the failure message names the `git submodule update --init` remedy. Revert both perturbations.
- [ ] 7. `./gradlew verifyCheckPartition` reports "CI partition OK" with the action-bearing task count incremented by one. This is the item's real gate — the guard fails outright if the task was wired into `check` but not into an aggregate.
- [ ] 8. `./gradlew :shared:testAndroidHostTest` and `./gradlew :shared:iosSimulatorArm64Test` both compile the generated source. Confirm the generator is incremental: touch the vendored fixture, re-run, and confirm the constant changed; re-run unchanged and confirm the task reports `UP-TO-DATE`.
- [ ] 9–11. `./gradlew :shared:compileKotlinIosSimulatorArm64` plus `./gradlew :shared:detekt` — the models compile on both targets and carry no Detekt findings. Model *correctness* is proven by item 16's decode, not by compilation.
- [ ] 12. `./gradlew :shared:detekt` passes with the coroutine ruleset active (no `commonMain` dispatcher/blocking finding), and `semgrep --config .semgrep/astro-mobile.yml --config .semgrep/coroutines.yml shared/` reports no `kotlin-cleartext-http-url` or `kotlin-hardcoded-dispatcher` finding.
- [ ] 13–14. `./gradlew :androidApp:assembleDebug` succeeds with the `Application` registered; `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` then `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' build` succeeds. Runtime confirmation that the graph actually starts comes from the `android-device-debug` / `ios-device-debug` loop — run at least one of the two, since a Koin misconfiguration is invisible to a compile.
- [ ] 15. `grep -rn "Greeting\|LoginRepository\|LoginDataSource\|LoginDataValidator\|LoggedInUser" shared/src androidApp/src iosApp/iosApp` returns nothing; `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` and the `xcodebuild` command above all still succeed. `./gradlew swiftFormatCheck swiftLintCheck` passes on the rewritten Swift.
- [ ] 16–18. `./gradlew :shared:testAndroidHostTest` and `./gradlew :shared:iosSimulatorArm64Test` — the parity, mutation, API-client, and Koin-resolution tests pass on both the JVM host and the iOS simulator. For item 17 specifically, confirm each mutation fails for the reason claimed by inspecting the assertion message, not merely that the suite is green.
- [ ] 19–20. Re-read the edited `.claude/CLAUDE.md` sections against the final build files and CI workflow so no statement describes the pre-branch state; confirm the ADR file exists in `astro-plans/adr/` and is committed in that repo.
- [ ] 21. `gh issue view 100` shows the re-pointed trigger.
- [ ] 22. Final gate: `./gradlew ktfmtFormat` then `./gradlew check` passes end-to-end on an Apple Silicon host, and `./gradlew verifyAndroidCommon` passes (the half CI's Linux runner executes).

## 6. Deployment

Not applicable.

<Otherwise: deployment steps, feature flags, migration ordering, rollback plan.>

## 7. Documentation

- `.claude/CLAUDE.md` — the "Tech stack & versions" section currently states the version catalog is deliberately partial and holds only lint-toolchain versions. This branch adds the first product dependencies, so that description and the tech-stack list need updating.
- An architecture decision record for the DI framework choice, in the planning repo alongside the project's other ADRs.

## 8. References

- Issue: https://github.com/jitrapon/astro-mobile/issues/115
- M-1 part 1 (module restructure): https://github.com/jitrapon/astro-mobile/pull/101
- SCA gate issue whose trigger this branch fires: https://github.com/jitrapon/astro-mobile/issues/100
- Milestone plan (mobile task table, M-1 / M-2): `astro-plans/current-plan.md`
