Status: blocking

# Plan Review — M-1 part 2 shared data layer

- **Date:** 2026-08-13
- **Base ref:** `main`
- **Target:** `.claude/SPEC.md` §§4–5 (doc-only diff)
- **Focus sent:** Review the plan in `.claude/SPEC.md` sections 4 (implementation) and 5 (testing) against sections 1, 2, 3, and 7. Flag: steps missing to satisfy the stated objective, wrong ordering, items too coarse to finish in one resume pass, items whose paired §5 check cannot actually verify them, scope drift beyond §§1–3, and any way a regression could land without the plan catching it. Do NOT review code — the diff is doc-only (SPEC.md).

## Resolution log

### Round 1 — verdict `needs-attention` (4 findings: 2 high, 2 medium)

> Codex summary: "No-ship: the plan leaves contract drift and required iOS/runtime paths able to regress while all listed checks pass."

**1. [high] Request-side OpenAPI drift is not mechanically checked — AGREE**

The vendoring guard byte-compares the spec files and the parity test is entirely response-side, so the client's path and query parameters were pinned only by a hand-copied list in the plan. Verified against the vendored contract and the finding is not merely theoretical — the plan's own parameter list was **already wrong**: `/screens/calendar` also declares an optional `dayCount` (time-grid column count) the plan omitted, and `knownTheme` reaches the operation through `$ref: "#/components/parameters/KnownTheme"`, so any naive slice of the inline `parameters:` block would silently drop it. Also surfaced a related trap now recorded in the plan: the request's `view` enum is lower-case (`timegrid`) while the response's view-selection discriminator is camel-case (`timeGrid`).

Addressed by: §4 item 8 now extracts and embeds the contract-derived request facts (path, required and optional parameter names, `view` enum) including the `$ref` resolution; new §4 item 19 asserts the client against those embedded facts; §4 item 12 now carries the full parameter surface and the casing note; §5 item 8 asserts the extraction resolved the `$ref` and classified `dayCount` as optional; §5 item 17–20 requires temporarily renaming a contract parameter to prove the test is genuinely contract-derived.

**2. [high] No runtime schema-version rejection gate — AGREE**

Item 12 makes production decoding lenient (`ignoreUnknownKeys`) while nothing rejected an envelope whose `schemaVersion` is not 0.2.0, so a future incompatible response would deserialize into these models with defaults and return as `Result.Success`. Confirmed this is cross-repo parity rather than gold-plating: astro-web already guards its own fetch boundary with `assertSupportedSchemaVersion` (exact equality, dedicated `UnsupportedSchemaVersionError`).

Addressed by: new §4 item 13 requires `CalendarScreenApi` to return `Result.Error` — never `Success` — on any `schemaVersion` mismatch, carrying both received and supported versions, mirroring astro-web's semantics and differing only in surfacing through `Result<T>` instead of throwing; §4 item 20 adds `MockEngine` cases above and below the supported version; §5 item 13 states explicitly that a green build is not evidence for this item.

**3. [medium] The required iOS facade can ship without ever running — AGREE**

§3 requires Swift to initialize and resolve through the shared framework's public surface, but §5 only compiled and linked the iOS app and allowed the runtime loop to be satisfied on Android ("at least one of the two"). A broken Swift call site or framework export compiles and links cleanly.

Addressed by: §4 item 15 now also requires an `iosTest` driving the same facade (init → resolve → tear down), so the iOS half of the graph is exercised by a test already in the CI aggregate; §5 item 14–15 makes the iOS simulator runtime loop mandatory and states that an Android launch does not substitute for it.

**4. [medium] A valid `knownTheme` response without `themeDocument` is untested — AGREE**

`themeDocument` is optional by contract and is absent precisely on the `knownTheme` cache hit — the common production case — yet the only fixture carries one, so the models could make it required or dereference it unconditionally and every listed check would pass.

Addressed by: §4 item 9 now states the nullability and why it is load-bearing; §4 item 18 adds the inverse parity case (fixture with `themeDocument` removed must still decode with its `theme` reference intact); §4 item 20 adds the matching `MockEngine` success case.

**Net effect on the plan:** 21 → 23 implementation items (new items 13 and 19; subsequent items renumbered), and §5 regrouped to match. No finding required editing §§1–3 or §7.
