# Specification: Scaffold astro-mobile repo & Claude Code harness (M-1)

> Per-branch working file owned by the `spec-development` skill. Each branch
> overwrites the section bodies; this file in `main` is a skeleton that
> documents the canonical structure so every branch follows the same shape.

## 1. Overview

This branch scaffolds the `astro-mobile` repository and its Claude Code development harness to parity with `astro-web`, so that every future feature branch (M-2+) starts from a consistent, gated, well-documented foundation. `astro-mobile` is a Kotlin Multiplatform Mobile (KMP) app — shared Kotlin business logic with Jetpack Compose (Android) and SwiftUI (iOS) UIs. Today the repo has only the bare KMP module skeleton (`:shared`, `:androidApp`, `iosApp/`) and no CI, no lint/format toolchain, no contributor docs, and (until this branch) no `.claude` workflow setup. The scaffolding installs the same spec-driven, gated workflow that `astro-web` uses, adapted to the KMP/Gradle/Swift stack, drawing on the portable core in `claude-template/.claude` and its `SPECIALIZATION_GUIDE.md`.

## 2. Objective

"Done" means a reviewer can verify, on this branch, all of the following observable outcomes:

- **`.claude` harness is specialized and complete** — the portable skill set (`spec-development`, `test`, `finish-branch`, `refactor`, `codex-review`, `address-review`, `address-comment`, `review-pr`, `scaffold-issue`, `rank-deferred-reviews`, `get-api-docs`) carries no `{{PLACEHOLDER}}`s, no `PORT ME` blocks, and no wrong-stack (Vert.x/Postgres/gRPC/calendar-service) leftovers; `.claude/CLAUDE.md`, `.claude/SPEC.md` skeleton, and `.claude/settings.json` exist and are mobile-correct.
- **Claude Code plugins are enabled and resolve** — `claude plugin list` shows the enabled plugins (codex, code-simplifier, claude-md-management, semgrep, github, kotlin-lsp, swift-lsp) as enabled, with the Kotlin/Swift language servers available on PATH.
- **Kotlin lint/format toolchain runs** — `./gradlew ktfmtFormat` and `./gradlew detekt` work, and `./gradlew check` aggregates compile + tests + Detekt + ktfmt verification.
- **CI gate is green on PR** — a GitHub Actions workflow runs the gate (build + `check` + iOS shared tests where feasible) and passes on this branch's PR, mirroring `astro-web`'s `.github/workflows/ci.yml` shape (pinned action SHAs).
- **MCP, dependency, and security configs are present** — `.mcp.json`, `renovate.json`, and the security scanning configuration are established at parity with `astro-web` (scoped to the mobile stack).
- **Contributor docs exist** — `README.md` and `CONTRIBUTING.md` describe the stack, commands, architecture, and the gate-before-push expectation.
- **Agent & Compose tooling is installed and documented** — (a) the Android CLI is installed with the official `github.com/android/skills` registered as a vetted skill source, and its setup + Claude Code usage is documented in `CONTRIBUTING.md`; (b) the vetted third-party KMP and structured-coroutines skills are present under `.claude/skills/` (cherry-picked, with anything conflicting with the locked conventions in §`Conventions` removed); (c) the Compose Stability Analyzer Gradle tooling is wired so the compose-compiler stability report task runs clean from `androidApp`. The Compose *Multiplatform* skill (`felipechaux/kmp-compose-multiplatform-skill`) is **excluded** — its shared-Compose-UI premise contradicts the "UI stays out of `:shared`" constraint; revisit only if the UI architecture ever adopts Compose Multiplatform.

## 3. Requirements & Context

**Reference / prior art.** Mirror `astro-web`'s scaffolding (the `W-1` scaffold) for structure and rigor, re-deriving every concrete value for the KMP/Gradle/Swift stack — do not carry over pnpm/Vite/FSD specifics. Follow `claude-template/SPECIALIZATION_GUIDE.md` Steps 0–7 for the `.claude` specialization. The four relevant sibling repos: `astro-web` (worked example), `claude-template` (portable core + guide), `astro-mobile` (this repo).

**Locked decisions (set during the `.claude` specialization step of this branch):**
- Canonical project doc lives at `.claude/CLAUDE.md`; root `CLAUDE.md` is a pointer. The conventions heading the skills reference is **`Conventions`**.
- CI/finish-branch gate command is **`./gradlew check`**; full test command is **`./gradlew test`** (+ `./gradlew :shared:iosSimulatorArm64Test` for shared/iOS code).
- Kotlin toolchain: **ktfmt** (format) + **Detekt** (static analysis), no baseline file, honor Detekt defaults. iOS: SwiftLint/swift-format.
- **No pre-commit hook** — formatting/lint is manual locally and enforced by the CI gate.
- Plugins enabled (project scope): `codex@openai-codex`, `code-simplifier`, `claude-md-management`, `semgrep`, `github`, `kotlin-lsp`, `swift-lsp` (all from already-configured marketplaces).
- Agent/Compose tooling folded into this scaffold (all installs land on this branch, not a later milestone): the Android CLI + official `android/skills`; the vetted `mmiani/kotlin-kmp-claude-agent-skills` and `santimattius` structured-coroutines skill under `.claude/skills/`; the skydoves Compose Stability Analyzer Gradle tooling. `felipechaux/kmp-compose-multiplatform-skill` is rejected (see §2 rationale).

**Constraints:**
- Keep platform-agnostic business logic in `shared/src/commonMain`; use `expect`/`actual`, not runtime platform branching. UI stays out of `:shared`.
- CI action references must be pinned to commit SHAs (as `astro-web` does).
- The KMP test runner does not auto-discover newly-added test files reliably on incremental builds — a clean build (`./gradlew clean test`) is the safe path when new test sources land.
- **Third-party skills are vetted before import.** The `mmiani` KMP and `santimattius` coroutines skills are cherry-picked, not wholesale-copied: any guidance conflicting with the locked rules in §`Conventions` (ktfmt, `Result<T>`, package layout, "UI out of `:shared`") is dropped before the skill lands under `.claude/skills/`.
- **Compose Stability Analyzer is inert until M-2.** M-1 has no composables, so wiring is verified by the compose-compiler stability-report task running clean (no findings expected), not by any report content. It must not fail `./gradlew check` on an empty composable set.
- **Reserve the shared calendar layout-engine seam.** astro-plans `ADR-calendar-layout-engine-sharing-strategy` (D-15), gated by the `W-S1` KMP-viability spike, defaults to the layout engine living once in shared Kotlin `commonMain` (compiled to JS for web, native on mobile). This branch reserves — but does not implement — its home at `shared/src/commonMain/kotlin/io/jitrapon/astro/calendar/layout/` and the golden-vector corpus at `shared/src/commonTest/resources/calendar-layout-golden/`, each with a README documenting the interop contract (plain JSON-like in/out; no `kotlinx.datetime`/`Flow`/sealed objects/`Result<T>` across the seam, so the same source compiles to JS). The scaffold must not preclude this; the engine itself is M-2+ product work.

**In scope:** repo-level scaffolding and tooling only — the `.claude` harness, CI gates, MCP list, lint/format toolchain, dependency/security config, and contributor docs.

**Out of scope:** any product feature work — calendar/events/reminders/tasks/budgeting screens, networking/persistence implementations, AI features, and the iOS app's product UI. Those land on later (M-2+) branches scaffolded from this foundation.

## 4. Implementation Plan and Progress Tracking (for agent)

Ordered so each later item can rely on `./gradlew check` being a real, green gate. The `.claude` harness specialization (objective §2 bullet 1) and the root-`CLAUDE.md`-as-pointer reduction already landed in a prior commit; item 19 re-verifies they hold end-to-end.

**A. Lint/format toolchain & build gate (foundation)**

- [ ] 1. Commit the reserved calendar layout-engine seam — track the already-authored `shared/src/commonMain/kotlin/io/jitrapon/astro/calendar/layout/README.md` and `shared/src/commonTest/resources/calendar-layout-golden/README.md`, confirming the `commonMain` package path and golden-vector resources path match `.claude/CLAUDE.md`'s architectural-seams description. No engine code.
- [ ] 2. Wire ktfmt into Gradle — apply the ktfmt Gradle plugin (kotlin `official` style) to `:shared` and `:androidApp`, exposing `ktfmtFormat` (apply) and `ktfmtCheck` (verify); run `ktfmtFormat` once to normalize existing sources.
- [ ] 3. Wire Detekt into Gradle — apply the Detekt plugin across modules with Detekt defaults (no baseline file, no custom complexity thresholds), exposing `./gradlew detekt`; resolve any findings by refactoring, never `@Suppress` or a baseline.
- [ ] 4. Aggregate the gate — make `./gradlew check` depend on compile + unit tests + `detekt` + `ktfmtCheck` so the one command runs them all; drop the "planned"/"to be wired up" qualifiers from `.claude/CLAUDE.md`'s Linting and Commands sections for the now-wired **Kotlin** ktfmt/Detekt tasks only (leave the iOS SwiftLint/swift-format line to item 5b).
- [ ] 5. Wire the skydoves Compose Stability Analyzer Gradle tooling into `:androidApp` so the compose-compiler stability-report task runs and emits a report on the (currently empty) composable set — and does **not** fail `./gradlew check`.
- [ ] 5b. Establish the iOS lint/format tooling locked in §3 — add a `.swiftlint.yml` (and/or swift-format config) for `iosApp` and document its command; advisory/inert until iOS product UI lands (no Swift product code exists yet, so it must not block `./gradlew check` or CI), mirroring the inert-Compose-analyzer treatment. Update `.claude/CLAUDE.md`'s iOS Linting line from "planned" to "configured (advisory until M-2)".

**B. Vetted third-party skills & agent tooling**

- [ ] 6. Vet and land the `mmiani/kotlin-kmp-claude-agent-skills` skill(s) under `.claude/skills/`, cherry-picking only guidance compatible with the locked conventions (ktfmt, `Result<T>`, package layout, UI-out-of-`:shared`) and dropping conflicts. Record the upstream source URL + imported commit SHA and an explicit "dropped conflicts" note inside the landed skill (header or a short adjacent VETTING note) so the removals are auditable.
- [ ] 7. Vet and land the `santimattius` structured-coroutines skill under `.claude/skills/`, dropping any guidance that conflicts with the locked conventions. Record the upstream source URL + imported commit SHA and the same "dropped conflicts" note in the landed skill.
- [ ] 8. Install the Android CLI and register the official `github.com/android/skills` as a vetted skill source; capture the install/registration steps for `CONTRIBUTING.md` (item 18).
- [ ] 9. Verify plugin enablement and language servers — `claude plugin list` shows codex, code-simplifier, claude-md-management, semgrep, github, kotlin-lsp, swift-lsp all enabled, and the Kotlin and Swift language-server binaries resolve on PATH.

**C. Dependency, MCP & security config (parity with astro-web, mobile-scoped)**

- [ ] 10. Author `.mcp.json` scoped to the mobile stack — mirror astro-web's structure, including only MCP servers relevant to KMP/mobile work.
- [ ] 11. Author `renovate.json` using the Gradle manager — SHA-pin GitHub Actions, group tightly-coupled Kotlin/AGP/Compose bumps, enable OSV vulnerability alerts, mirror astro-web's scheduling and minimum-release-age policy.
- [ ] 12. Author the semgrep ruleset `.semgrep/astro-mobile.yml` (Kotlin/mobile threat model — hardcoded secrets/URLs, insecure HTTP, sensitive-data logging) and port astro-web's `scripts/check-action-pins.sh` action-pin enforcement script.
- [ ] 13. Author the secret-scan (`.betterleaks.toml`) and SCA (`osv-scanner.toml`) configs scoped to the Gradle/Kotlin stack, mirroring astro-web's filters/floors.

**D. CI & contributor docs**

- [ ] 14. Author `.github/workflows/ci.yml` — the deterministic gate job (JDK 17 setup, Gradle cache, `./gradlew build`, `./gradlew check`, and `./gradlew :shared:iosSimulatorArm64Test` on a macOS runner where feasible), every action reference SHA-pinned.
- [ ] 15. Author `.github/workflows/security.yml` — semgrep + action-pin + secret-scan + SCA jobs mirroring astro-web's security gate, every action reference SHA-pinned.
- [ ] 16. Author the Claude-backed advisory `.github/workflows/security-review.yml` — dormant-by-default (gated on a repo var/secret), mirroring astro-web.
- [ ] 17. Rewrite `README.md` — stack, getting-started, a command table matching the real Gradle tasks, architecture overview, and a link to `.claude/CLAUDE.md`.
- [ ] 18. Author `CONTRIBUTING.md` — the gate-before-push expectation (`./gradlew check`), local setup, the Android CLI + skills usage (from item 8), and a Kotlin/KMP + conventions recap.
- [ ] 19. Final gate sweep — run `./gradlew clean check` (clean build per the KMP incremental test-discovery caveat) plus `./gradlew :shared:iosSimulatorArm64Test`, and re-grep `.claude/` for placeholders / PORT-ME / wrong-stack leftovers to confirm objective §2 bullet 1 holds end-to-end.
- [ ] 20. Confirm CI is green on the PR (objective §2 bullet 4) — after the PR is opened (via finish-branch), verify with `gh pr checks` / `gh run list --branch <branch>` that the required CI workflow ran and concluded success on the PR, including the iOS shared-test job where feasible; if red, diagnose, fix, and re-verify. This is the terminal step and runs after finish-branch pushes/opens the PR.

## 5. Testing & Validation (for agent)

Each item verifies the same-numbered §4 item.

- [ ] 1. `git ls-files` shows both seam READMEs tracked; `./gradlew :shared:build` stays green (the new `commonTest` resources dir doesn't break the build).
- [ ] 2. `./gradlew ktfmtFormat` applies without error and `./gradlew ktfmtCheck` passes on the formatted tree (re-run is a no-op).
- [ ] 3. `./gradlew detekt` completes with zero findings (or findings refactored away, not suppressed); confirm no `detekt-baseline.xml` exists.
- [ ] 4. `./gradlew check` runs compile + tests + `detekt` + `ktfmtCheck` in one invocation and passes; `grep -n 'planned\|to be wired' .claude/CLAUDE.md` shows the now-wired tasks no longer carry the qualifier.
- [ ] 5. The androidApp compose-compiler stability-report task runs and emits a report file; `./gradlew check` remains green on the empty composable set.
- [ ] 5b. SwiftLint/swift-format runs against `iosApp` via its documented command and the config parses; `./gradlew check` and the CI gate stay green (the iOS tooling is advisory and not wired into either); `.claude/CLAUDE.md`'s iOS line reads "configured (advisory until M-2)".
- [ ] 6. The mmiani skill dir(s) exist under `.claude/skills/`; `grep -rniE '\{\{|PORT ME|vitest|pnpm|compose multiplatform|shared.*compose.*ui'` over them is clean (no forbidden/conflicting guidance); the landed skill records the upstream source + commit SHA and its "dropped conflicts" note; `claude` lists the skill.
- [ ] 7. The coroutines skill dir exists under `.claude/skills/`; the same forbidden-terms grep is clean; the landed skill records the upstream source + commit SHA and its "dropped conflicts" note.
- [ ] 8. The Android CLI version command succeeds and the `android/skills` source is registered (appears in its skills/source listing); install steps captured for item 18.
- [ ] 9. `claude plugin list` shows all seven plugins enabled; the Kotlin and Swift language-server binaries resolve on PATH (`which`/equivalent).
- [ ] 10. `jq . .mcp.json` parses; the configured servers are mobile-scoped (no web-only servers).
- [ ] 11. `npx --yes --package renovate -- renovate-config-validator renovate.json` passes (config is valid).
- [ ] 12. `semgrep --config .semgrep/astro-mobile.yml --error shared androidApp` runs clean; `bash scripts/check-action-pins.sh` exits 0 against the current workflows.
- [ ] 13. `.betterleaks.toml` and `osv-scanner.toml` parse under their tools (or a TOML check); a secret scan over the tree runs clean.
- [ ] 14. `actionlint .github/workflows/ci.yml` passes; the gate commands run locally (`./gradlew build check`); every `uses:` is a 40-hex SHA (grep). Actual PR-green is confirmed at finish-branch.
- [ ] 15. `actionlint .github/workflows/security.yml` passes; each job's tool invocation runs locally; all actions SHA-pinned.
- [ ] 16. `actionlint .github/workflows/security-review.yml` passes; the `if:` guard keeps the job dormant without the repo var/secret set.
- [ ] 17. `README.md` renders; the command table matches the real Gradle tasks and the `.claude/CLAUDE.md` link resolves.
- [ ] 18. `CONTRIBUTING.md` documents the `./gradlew check` gate-before-push and the Android CLI usage; every command shown matches reality.
- [ ] 19. `./gradlew clean check` and `./gradlew :shared:iosSimulatorArm64Test` are both green; `grep -rniE '\{\{|PORT ME|vert\.x|postgres|grpc|pnpm|vite'` over `.claude/` finds no leftovers.
- [ ] 20. `gh pr checks <pr>` (or `gh run list --branch <branch>`) shows the required CI workflow run with conclusion `success` on the PR; the run includes the iOS shared-test job where the runner allows it. Re-run after any red-to-green fix until success is confirmed.

## 6. Deployment

Not applicable.

## 7. Documentation

- `.claude/CLAUDE.md` — created on this branch; keep it accurate as the toolchain (Detekt/ktfmt/CI) is wired up.
- `README.md` and `CONTRIBUTING.md` — to be authored/updated as part of this branch (commands, architecture, gate-before-push expectation).
- Root `CLAUDE.md` — reduced to a pointer at `.claude/CLAUDE.md`.

## 8. References

- `claude-template/SPECIALIZATION_GUIDE.md` — the step-by-step specialization runbook (first proven on astro-web).
- `astro-web/` — the worked scaffolding example (`.claude/`, `.github/workflows/ci.yml`, `.mcp.json`, `README.md`, `CONTRIBUTING.md`, `renovate.json`, security configs).
- `claude-template/.claude/` — the portable core (skills + SPEC skeleton + settings).
- `astro-plans/adr/ADR-calendar-layout-engine-sharing-strategy.md` and the `W-S1` spike (`astro-plans/current-plan.md`) — the decision and pass criteria behind the reserved `commonMain` layout-engine seam.
- KMP dependency setup — [adding multiplatform dependencies](https://kotlinlang.org/docs/multiplatform/multiplatform-dependencies.html) and [upgrading a multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-upgrade-app.html); the canonical how-to for the Ktor Client + kotlinx.serialization additions to `:shared`.
- Agent/Compose tooling folded into this branch — Android CLI + skills ([developer.android.com/tools/agents](https://developer.android.com/tools/agents), [github.com/android/skills](https://github.com/android/skills)); Compose Stability Analyzer ([github.com/skydoves/compose-stability-analyzer](https://github.com/skydoves/compose-stability-analyzer)); vetted KMP skills ([github.com/mmiani/kotlin-kmp-claude-agent-skills](https://github.com/mmiani/kotlin-kmp-claude-agent-skills)) and the structured-coroutines skill ([santimattius.github.io/structured-coroutines-docs](https://santimattius.github.io/structured-coroutines-docs/docs/kotlin-coroutines-skill)).
