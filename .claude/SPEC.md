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

<Filled by `spec-development` in plan mode. GitHub-style checkboxes (`- [ ]`), one item per concrete task small enough to finish in a single resume pass.>

## 5. Testing & Validation (for agent)

<Filled by `spec-development` in plan mode. Each item pairs 1:1 with a §4 item: the test/build/lint command that verifies it.>

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
