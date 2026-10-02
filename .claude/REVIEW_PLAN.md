Status: clear

# Plan Review

- Date: 2026-10-02
- Base ref: main
- Focus sent to Codex: Review the plan in .claude/SPEC.md sections 4 (implementation) and 5 (testing) against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot actually verify them, scope drift beyond §§1–3, and any way a regression could land without the plan catching it. Do NOT review code — the diff is doc-only (SPEC.md) plus a merge of main (the Bouncy Castle constraints, already reviewed and merged as #166); ignore that merged content.

## Round 1 — 2026-10-02

Verdict: needs-attention ("Do not execute the plan unchanged"), treated as blocking.

### Codex output (verbatim)

Target: branch diff against main
Verdict: needs-attention

Do not execute the plan unchanged. The working-tree SPEC leaves generated test sources ambiguously wired and does not prove builds work without the private submodule.

Findings:
- [medium] Separate production and test output wiring explicitly (.claude/SPEC.md:124-128)
  The generator declares two output directories but attaches the task provider directly to commonMain and never specifies commonTest wiring. As written, the inferred outcome is that both task outputs become production sources, including EmbeddedDesignArtifacts and its JSON payloads. Compilation and the existing location checks could pass while test fixtures ship in the framework.
  Recommendation: Expose separate output-directory properties and wire each lazily to its matching source set while preserving the producer dependency. Add a check that EmbeddedDesignArtifacts is available to commonTest but absent from production compilation outputs.
- [medium] Verify submodule independence with a clean, submodule-free build (.claude/SPEC.md:250-253)
  Item 9 runs after checks that require the submodule and after generation has already populated build outputs. A successful xcodebuild in that environment cannot establish the claimed absence of submodule reads: an accidental dependency or cached generated sources could hide the failure. This leaves the explicit fresh-clone and iOS-job requirement unverified.
  Recommendation: Add a clean checkout without submodule initialization or generated outputs, disable build-cache reuse, and run the platform compilation and simulator tests there. Keep parity-gate validation in the checkout that contains the submodule.

Next steps:
- Correct source-set wiring and add an isolated submodule-free build check before implementing the plan.

## Round 2 — 2026-10-02

Verdict: needs-attention. Round 1's two findings are confirmed closed; one new medium.

### Codex output (verbatim)

Target: branch diff against main
Verdict: needs-attention

Both round-one findings are closed at the plan level. One registry regression remains undetectable by the proposed tests.

Findings:
- [medium] Test exact-reference resolution for both bundled themes (.claude/SPEC.md:245-252)
  The only successful reference lookup tested is the fixture's light theme. A resolver that searches only BundledThemes.light and otherwise falls back by system scheme would pass every listed check. It would incorrectly resolve a valid dark theme reference to light on a light-scheme device, violating §3's reference-resolution requirement.
  Recommendation: For every bundled theme, construct its exact id/version reference and assert that resolve returns that theme under both system schemes. Retain the fixture integration check and mismatch fallback cases.

Next steps:
- Extend §5 item 6 with the complete bundled-theme lookup matrix before implementing.

## Round 3 — 2026-10-02

Verdict: approve. All three prior findings confirmed closed; no new findings.

### Codex output (verbatim)

Target: branch diff against main
Verdict: approve

The plan is ready for implementation. All three prior findings are closed at the plan level: separate production/test generators with output checks, fresh submodule-free builds with cache disabled, and exact-reference resolution for every bundled theme under both schemes. No new material blocker is supported by the doc-only diff. This does not approve the eventual implementation.

No material findings.

Next steps:
- Record iteration 3 as clear in REVIEW_PLAN.md.
- Execute the plan and capture the required validation evidence.

## Resolution log

### Round 1 dispositions

1. **Separate production and test output wiring explicitly: AGREE.** Verified: the precedent wires
   `kotlin.srcDir(generateEmbeddedContractSource)` (the task provider), which adds every output
   directory of the task. One task with two outputs would therefore compile `EmbeddedDesignArtifacts`
   into commonMain. §4 item 3 now uses two tasks, `GenerateDesignTokenSource` → commonMain and
   `GenerateEmbeddedDesignArtifactSource` → commonTest, each wired only to its own source set. §5
   item 3 adds a class-file check that the embed is absent from the Android main compilation and
   present in tests.
2. **Verify submodule independence with a clean, submodule-free build: AGREE.** Every check ran in a
   checkout with the submodule and warm outputs. §5 item 3 adds a fresh-worktree compile right after
   the generator lands (early detection). §5 item 9 adds the full clean-worktree run: host and
   simulator tests, the `xcodebuild` app build with `--no-build-cache`, and the parity gate failing
   there as the only submodule reader. §4 item 9 is renamed to match.

### Round 2 dispositions

1. **Test exact-reference resolution for both bundled themes: AGREE.** Valid: the only exact match
   tested was the fixture's light reference, so a resolver that checked only `light` and otherwise
   fell back by scheme would pass. §5 item 6 now requires every theme in `BundledThemes.all` to
   resolve from its own exact reference under both system schemes. The fixture and mismatch cases
   stay.

### Round 3 dispositions

No findings. The plan cleared after three rounds: 3 medium findings, all AGREE and folded into
§§4–5, with none rebutted or deferred.
