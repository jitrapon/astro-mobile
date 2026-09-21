Status: blocking

# Plan Review

> Per-branch working file owned by the `spec-development` skill's plan-review
> loop. Captures the adversarial review of the SPEC §§4–6 plan _before_
> implementation; this file in `main` is an empty skeleton.

- Date: 2026-09-21
- Base ref: main
- Focus sent to Codex: Review the plan in .claude/SPEC.md sections 4 (implementation) and 5 (testing) against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot actually verify them, scope drift beyond §§1–3, and any way a regression could land without the plan catching it. In particular challenge whether the plan can really establish that -Xpartial-linkage-loglevel=ERROR reaches every iOS framework LINK task (not only klib compilation) on Kotlin 2.4.20, whether the scratch prove-it-fails perturbation is a sound and achievable test, and whether the enforcement could later be removed or bypassed without any gate noticing. Do NOT review code — the diff is doc-only (SPEC.md).

## Iteration 1 — Codex output (verbatim)

### Codex Adversarial Review

Target: branch diff against main
Verdict: needs-attention

Do not ship this plan yet. Reviewed the working-tree SPEC, which contains §§4–5 absent from the supplied commit diff. It lacks a durable regression gate, leaves framework-link variants unverified, and depends on an unproven negative-test recipe.

Findings:
- [high] Enforcement can disappear without any gate failing (.claude/SPEC.md:120-128)
  All enforcement-specific checks are manual, and the negative test is explicitly never committed. After removing the flag or moving it to compilation-only options, a compatible dependency graph still builds green—the exact blind spot §3 describes. Neither the comment nor the existing positive builds detects that regression.
  Recommendation: Add a §4 item and paired §5 check for a persistent gate covering effective framework-link arguments. Require mutation checks showing that removal, compilation-only placement, and a per-binary override fail the gate; wire it into the appropriate local and CI checks.
- [medium] The link-argument matrix does not cover every framework LINK task (.claude/SPEC.md:114-119)
  The checklist inspects debug links for three targets but release only for IosArm64. It therefore cannot establish the requested every-framework-LINK invariant: release simulator links could lack enforcement while every listed check passes. Finding the flag somewhere in an argument list also does not establish that a conflicting setting cannot override it.
  Recommendation: Enumerate all configured iOS framework binaries and their link tasks, including both simulator release variants. For Kotlin 2.4.20, require evidence of each executed link's effective arguments, reject conflicting partial-linkage settings, and specify how skipped tasks or unavailable argument logging will be handled.
- [medium] The mandatory negative test has no reproducible construction (.claude/SPEC.md:120-128)
  The proposed dependency downgrade names neither versions nor a missing symbol. It may produce dependency-resolution or klib-compilation errors, or no linkage defect at all. The fail/green comparison is a useful criterion, but the plan explicitly ends in 'stop and ask' if exploratory downgrades fail, leaving a mandatory acceptance check without an achievable, bounded implementation path.
  Recommendation: Split fixture preparation from enforcement implementation. Specify a verified dependency/version/symbol recipe or a deterministic two-version klib fixture: compile a consumer against an API, replace its provider with a version missing that API, and retain the consumer call in the framework. Require actual link execution in both runs, identical inputs except the flag, and a partial-linkage diagnostic rather than an unrelated failure.

Next steps:
- Add a persistent enforcement gate with mutation coverage.
- Expand verification to every configured iOS framework link.
- Replace exploratory downgrading with a reproducible negative-test fixture.


## Resolution log

### Iteration 1 → dispositions

Verified against the configured build with a read-only init-script probe before deciding: `:shared`
has nine `KotlinNativeLink` tasks (six framework, three test), each exposing a readable
`toolOptions.freeCompilerArgs`, all empty today.

- **[high] Enforcement can disappear without any gate failing — AGREE.** Valid and in scope: §3
  itself warns that a setting which misses the link "would leave the defect in place while looking
  enforced", and a Kotlin Gradle plugin upgrade changing argument propagation would produce exactly
  that with the flag still in the file. The probe showed the gate is cheap to build. Added §4 item 2
  (`verifyNativeLinksFailOnPartialLinkage`: enumerates every link task, requires the flag, rejects
  any conflicting `-Xpartial-linkage…` argument, fails on zero link tasks, classified into
  `verifyIos` in the same commit) and two §5 checks (gate-vs-executed-arguments agreement; mutation
  checks for removal, a non-reaching attachment form, and a per-binary override).
- **[medium] The link-argument matrix does not cover every framework LINK task — AGREE.** Codex is
  right on the facts: six framework links exist and the draft named four, omitting both release
  simulator variants. §5 now executes all six, refuses `UP-TO-DATE` / `FROM-CACHE` / `SKIPPED` as
  evidence, and names the fallback if `--info` does not print linker arguments. The gate closes the
  class of error by enumerating tasks instead of listing them.
- **[medium] The mandatory negative test has no reproducible construction — PARTIAL.** Adopted: the
  rigor criteria (both runs execute the link, identical inputs but for the flag, a partial-linkage
  diagnostic rather than an unrelated failure) and a bounded three-rung ladder ending in a
  throwaway two-version library built outside the repo. Rejected: a *committed* deterministic klib
  fixture. It would add a module and a publishing step to a build whose entire change is one
  compiler flag, and the persistent gate — not the negative test — is what guards against
  regression; the negative test's job is a one-time demonstration that the flag does what the
  issue claims. "Stop and ask" stays as the terminal rung, since an item that cannot be evidenced
  must not be ticked.
