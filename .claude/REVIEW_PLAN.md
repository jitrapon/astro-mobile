Status: blocking

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
