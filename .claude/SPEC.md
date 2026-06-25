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

## 3. Requirements & Context

**Reference / prior art.** Mirror `astro-web`'s scaffolding (the `W-1` scaffold) for structure and rigor, re-deriving every concrete value for the KMP/Gradle/Swift stack — do not carry over pnpm/Vite/FSD specifics. Follow `claude-template/SPECIALIZATION_GUIDE.md` Steps 0–7 for the `.claude` specialization. The four relevant sibling repos: `astro-web` (worked example), `claude-template` (portable core + guide), `astro-mobile` (this repo).

**Locked decisions (set during the `.claude` specialization step of this branch):**
- Canonical project doc lives at `.claude/CLAUDE.md`; root `CLAUDE.md` is a pointer. The conventions heading the skills reference is **`Conventions`**.
- CI/finish-branch gate command is **`./gradlew check`**; full test command is **`./gradlew test`** (+ `./gradlew :shared:iosSimulatorArm64Test` for shared/iOS code).
- Kotlin toolchain: **ktfmt** (format) + **Detekt** (static analysis), no baseline file, honor Detekt defaults. iOS: SwiftLint/swift-format.
- **No pre-commit hook** — formatting/lint is manual locally and enforced by the CI gate.
- Plugins enabled (project scope): `codex@openai-codex`, `code-simplifier`, `claude-md-management`, `semgrep`, `github`, `kotlin-lsp`, `swift-lsp` (all from already-configured marketplaces).

**Constraints:**
- Keep platform-agnostic business logic in `shared/src/commonMain`; use `expect`/`actual`, not runtime platform branching. UI stays out of `:shared`.
- CI action references must be pinned to commit SHAs (as `astro-web` does).
- The KMP test runner does not auto-discover newly-added test files reliably on incremental builds — a clean build (`./gradlew clean test`) is the safe path when new test sources land.

**In scope:** repo-level scaffolding and tooling only — the `.claude` harness, CI gates, MCP list, lint/format toolchain, dependency/security config, and contributor docs.

**Out of scope:** any product feature work — calendar/events/reminders/tasks/budgeting screens, networking/persistence implementations, AI features, and the iOS app's product UI. Those land on later (M-2+) branches scaffolded from this foundation.

## 4. Implementation Plan and Progress Tracking (for agent)

<Filled by `spec-development` in plan mode. GitHub-style checkboxes (`- [ ]`), one item per concrete task small enough to finish in a single resume pass.>

## 5. Testing & Validation (for agent)

<Filled by `spec-development` in plan mode. Each item pairs 1:1 with a §4 item: the test/build/lint command that verifies it.>

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
