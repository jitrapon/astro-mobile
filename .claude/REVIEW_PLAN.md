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

### Round 2 — verdict `needs-attention` (3 findings: 2 high, 1 medium)

> Codex summary: "No-ship. The round-1 request/version/nullability work is now planned, but the plan still permits response-contract drift, an absent repository layer, and unverified platform startup behavior to pass all gates."

Round 1's four findings were confirmed closed and not re-raised.

**1. [high] Response-schema drift can still pass the parity gate — PARTIAL**

The valid kernel: the strict decode covers one month fixture, and there is no agenda fixture upstream (confirmed — `astro-docs` ships only `calendar-month-screen.v0.example.json`), so the agenda models §3 explicitly requires would have had **no** mechanical gate at all. That is precisely the "dead code with no caller" the issue set out to avoid. Agreed and closed by extending the generator (item 9) to embed each modelled response schema's `required:` list and enums — following the discriminator mapping to reach `AgendaBody`, not just the inline month schema — and asserting the models against them in item 21.

Rejected: the recommendation's full scope — "a contract-derived response conformance corpus covering every declared screen branch, required field, discriminator, and enum … include negative schema mutations". That is an OpenAPI schema validator hand-written in Kotlin, and it duplicates what `./gradlew openApiGenerate` provides for free at M-5, which §3 explicitly places out of scope. astro-web reached the same conclusion from the same position and recorded it in its own parity suite: the other body components "are contract-valid but have no fixture in this repo, and a branch nothing exercises is an untested claim dressed as coverage." Requiredness plus enum conformance is the proportionate gate at this stage; whole-schema validation arrives with codegen.

**2. [high] The required repository graph is never planned or tested — AGREE**

Factually correct and a genuine miss against user prose: §3 says "wire the HTTP client, API client, and repository graph through it", and CLAUDE.md documents the repository-wraps-a-data-source pattern as a project convention, yet the plan bound only `HttpClient`, `Json`, and the API client. Left unaddressed, M-2 would either reach past the data layer into the API client or need a graph change to introduce one.

Addressed by: new §4 item 15 adds `CalendarScreenRepository` wrapping the API client and owning the last-fetched screen as cached state — deliberately thin, inventing no caching policy or refresh scheduler, since those need M-2's view model; item 16 binds it in the Koin graph; item 17 has the iOS facade resolve the repository rather than the API client; item 22 adds repository behaviour tests (successful fetch populates cached state, failed fetch surfaces `Result.Error` without discarding it) and extends the graph smoke test to resolve it; item 23 refreshes CLAUDE.md's Repository-pattern entry, whose example is the `LoginRepository` this branch deletes.

**3. [medium] App-launch graph resolution unverified on both entry paths — PARTIAL**

Agreed on the gap: Android received only an `assembleDebug` check, so an `Application` that throws inside `startKoin` would crash at launch with every listed check green, and the iOS runtime loop confirmed only "did not crash" rather than "Swift resolved through the facade". Closed by making **both** runtime loops mandatory in §5 (neither substitutes for the other, with the failure mode each one catches stated), and by requiring item 18's iOS view to render state derived from the resolved repository rather than a static string — so the simulator screenshot is evidence of resolution.

Rejected: the recommendation to add "Android instrumentation launch plus graph resolution" and an "iOS UI/integration assertion". `androidApp` has no `androidTest` source set and CI runs no emulator, so this means standing up an instrumentation harness — its own task, and the `testing-setup` skill's domain — as a rider on a data-layer branch. The repo's documented `android-device-debug` / `ios-device-debug` loops are the established mechanism for exactly this verification and are now mandatory rather than optional. The §5 entry records that the omission is deliberate so it reads as a decision, not an oversight.

**Net effect on the plan:** 23 → 25 implementation items (item 8 split into generator mechanism + contract-fact extraction; new repository item 15), §5 regrouped to match. No finding required editing §§1–3 or §7.

### Round 3 — verdict `needs-attention` (1 finding: 1 high)

> Codex summary: "No-ship: the new repository cache can return the wrong calendar screen after a range/view/timezone/theme change, and the planned tests do not detect it."

Rounds 1 and 2 were confirmed closed; nothing previously rebutted was re-raised.

**1. [high] Repository cache has no request identity or ordering contract — AGREE**

Valid, and it caught a contradiction inside round 2's own fix: item 15 said the repository "owns the last successfully-fetched screen as cached state and the decision of when to re-fetch" *and* that it "does not invent a caching policy" — both at once. A cache keyed on nothing, while the request varies by view, start, end, timezone, locale, `dayCount`, and `knownTheme`, hands the next caller a screen belonging to a different request; item 22's single success/failure pair would not have noticed.

Taken Codex's first option — make the repository a stateless forwarding boundary until M-2 defines caching — rather than its second (specify cache identity and generation ordering now). Defining ordering semantics with no consumer to define them for is guesswork, and it re-creates the unexercised-dead-code problem this branch exists to avoid; the whole class of stale-data bugs disappears instead at zero cost. Item 15 now states the statelessness, names the request-identity and out-of-order hazards as the reason, and requires the KDoc to record it as a decision. Item 22's repository test becomes: forwards `Result` unchanged on both paths, and two successive calls with different parameters each return their own response — the assertion that fails if a keyless cache is added later.

**Net effect on the plan:** no item count change (25 items). No finding required editing §§1–3 or §7.

### Round 4 — verdict `needs-attention` (1 finding: 1 high)

> Codex summary: "No-ship: the stateless repository change closes round 3, but request conformance can still pass while serializing calendar ranges in a BFF-incompatible form."

Round 3 was confirmed closed; nothing previously rebutted was re-raised.

**1. [high] Request conformance omits query-value format and conditional encoding — AGREE**

Correct on the facts. Item 9 extracted parameter *names*, the `view` enum, and the path — no types or formats — and item 21 compared name sets and enum membership while item 22 asserted only on responses. Nothing in the plan looked at the query string the client actually emits. A client serializing `start` as an instant, sending `knownTheme=` empty instead of omitting it, or attaching `dayCount` to a month request would pass every planned check and then 400 against the real BFF. The contract does declare what is required to catch this: `start` / `end` are `type: string, format: date` and `dayCount` is an integer bounded 1–7.

In scope rather than gold-plating: §2 names the API client for the range-based endpoint as part of the objective, and §3 specifies the parameters it is built on — the wire format is that client's core correctness, not an extra.

Addressed by: item 9 now extracts each parameter's declared type and format alongside its name; item 21 asserts over a captured `MockEngine` request that `start` / `end` are date-only (no time, offset, or `Z`), that `dayCount` appears only for the time-grid view and within 1–7, and that an absent optional parameter is omitted from the query string rather than sent empty or `null`; item 22 asserts on both halves of each exchange — the emitted request as well as the returned `Result`. §5 item 9 additionally asserts the extraction captured `format: date`, since extracting it as empty would make the wire-format assertions pass vacuously, and §5 item 19–22 adds the negative mutation: switching the client to a date-time encoding must fail the conformance test on the encoding, not on the name.

**Net effect on the plan:** no item count change (25 items). No finding required editing §§1–3 or §7.
