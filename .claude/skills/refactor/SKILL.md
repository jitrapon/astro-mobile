---
name: refactor
description: Audit and correct code paths, naming, and complexity against the project's pinned conventions. Use when the user says "refactor X", "rename X", "this name is misleading", "this class does too much", "extract X", "split X", "move X", "what should we call this", or wants a pre-commit pass over a diff for naming/scope/complexity. Reads the project's CLAUDE.md conventions + linting sections as the canonical rule set.
argument-hint: [path-or-symbol]
allowed-tools: Bash, Read, Edit, Grep, Glob, AskUserQuestion
---

Audit a target across three axes — naming, code paths, complexity — and apply corrections that match the project's pinned conventions.

## When to use

- User asks to refactor a file, class, or method.
- User asks to rename something or proposes a name and wants a second opinion.
- A class has accumulated multiple concerns and feels like it should split.
- A method is too long, too nested, or has too many parameters.
- A diff is about to be committed and you want a naming/scope/complexity pass first.
- A symbol's name no longer matches what it does after recent changes.

## Canonical rule sources — read these first

These are the project's pinned rules. If anything in this skill conflicts with them, they win:

- `.claude/CLAUDE.md` → **Conventions** section (naming rules, file-naming, comment rules).
- `.claude/CLAUDE.md` → **Linting** section (thresholds, exception-handling policy).
- The Detekt config (`config/detekt/detekt.yml`, once the scaffolding adds it) for Kotlin, and the Swift linter config (`.swiftlint.yml`) for iOS — the source of truth when CLAUDE.md and the config disagree.

## Naming principles (language-agnostic)

### Method/function names — what, not how; surface multi-step intent; name the artifact

Lead with an action verb that names **what** the function does, not **how** it modifies its parameters. When a function runs multiple steps, surface them in the name. When a function classifies an artifact, name the artifact (`classifyReadWindow`, not `classifyRegime`).

### Type names — artifact-specific, not generic

Prefer `<Artifact>Classification` / `<Artifact>Status` / `<Artifact>Kind` / `<Artifact>Outcome` over generic `<X>Mode` / `<X>State` / `<X>Type` when the artifact is specific.

### Class/module scope — broadest accurate descriptor

A name must cover every case it handles. The **"covers multiple cases" test**: list the regimes/paths the unit handles, then check whether the name covers all of them. If the name references only one of two concerns, rename.

### Constants / enum members

Follow the language's idiomatic convention consistently (e.g. `SCREAMING_SNAKE_CASE` for enum-like members where the language uses it). No mixed conventions within one project.

### File naming

Follow the project's documented file-naming scheme (Conventions). Rename files via `git mv` to preserve history.

## Code-path restructuring patterns

Five restructuring moves — add a worked before/after from your own repo under each:

1. **Move a generic helper out of a specific file.** Trigger: the helper would compile/run unchanged if copy-pasted into a sibling file — it doesn't belong to the current one.
2. **Move a concern to the right domain.** Trigger: read the signature; if its types belong to a different subdomain than the host, move it there. Delete one-line passthroughs that result.
3. **Split along the coupling boundary.** Trigger: part of a file's API references a subdomain type and part doesn't — split so the generic half is reusable.
4. **Extract a collaborator when a class accumulates responsibilities.** Trigger: the class name had to undersell its scope to fit, OR you'd want a parallel class for a sibling concern but the existing one is in the way.
5. **Prefer stdlib/idiomatic constructs.** Trigger: a hand-rolled loop/branch that maps to a standard library idiom in your language.

## Complexity thresholds — refactor, don't suppress

When a function/file trips a linter complexity threshold, refactor. Don't suppress (no `@Suppress`), and don't introduce a Detekt baseline file to grandfather the violation away.

No custom complexity thresholds are configured yet — honor Detekt's defaults (e.g. `LongMethod`, `ComplexMethod`/cyclomatic complexity, `LongParameterList`, `TooManyFunctions`, `LargeClass`) for Kotlin, and SwiftLint's defaults for iOS. If the scaffolding later pins explicit thresholds in the Detekt config, this section should be updated to mirror them.

## Comments

- Self-contained — name the invariant, not a SPEC location or review round.
- BANNED in production/test code: `SPEC §`, `round N`, `adversarial review` references. They rot the moment the branch merges.
- Cross-references between durable production identifiers are fine.

## Workflow

1. **Identify the target.** If `$ARGUMENTS` is a path, `Read` it. If a symbol, find its definition (prefer your language's LSP; fall back to `Grep`). If unspecified, ask which file/symbol/area.

2. **Read the canonical rules.** Open `.claude/CLAUDE.md` (Conventions + Linting) before judging anything. Open the Detekt config (`config/detekt/detekt.yml`) or `.swiftlint.yml` if you need exact thresholds.

3. **Audit across three axes** and collect findings — do NOT edit yet:
   - **Naming**: every function, type, class, file. Apply the principles above; draft a concrete suggestion per candidate rename.
   - **Code paths**: does the file mix two couplings (#3)? Does a method belong elsewhere (#2)? Is a generic helper stuck in a specific file (#1)? Is a class split N/M between two concerns (#4)? Any hand-rolled construct that maps to an idiom (#5)?
   - **Complexity**: eyeball length, depth, parameter count. Run `./gradlew detekt` (Kotlin) or `swiftlint` (iOS) if uncertain.

4. **Present a punch list** before changing code. For each finding: (i) what's wrong; (ii) which rule it violates (link to the CLAUDE.md section or a worked example); (iii) the proposed change. With more than one reasonable rename candidate, use `AskUserQuestion` and offer 2–3 options, recommended first.

5. **Apply the fixes.** Edit in place. After renaming, find all references (production AND test) and update them. For file renames, use `git mv`. Delete one-line passthroughs from moved methods.

6. **Verify with the linter.** Run `./gradlew ktfmtFormat` + `./gradlew detekt` (Kotlin), or `swift format` + `swiftlint` (iOS), before declaring done. If a rule trips, refactor — do NOT grow a baseline or add `@Suppress`.

7. **Sweep comments.** After moving code, re-check no comment references `SPEC §` / round numbers / `adversarial review`. Rewrite to name the invariant directly.

## When NOT to refactor

- A bug-fix branch doesn't need surrounding name cleanup. Don't expand scope beyond the request.
- Three similar lines beat a premature abstraction. Wait for the second use before extracting a helper.
- Don't introduce a generic helper for a single caller.
- Don't ratchet linter thresholds back without a corresponding cleanup PR.
- Don't rename symbols the SPEC literally names unless you also update SPEC.md to match (SPEC is per-branch and ephemeral; the code name is durable — code wins).
- Don't refactor across a half-finished implementation. Finish it first.
