---
name: kotlin-data-kmp-data-layer
description: Use when implementing or reviewing KMP data layers, including repositories, data sources, source-of-truth design, API exposure, conflict resolution, error handling, and main-safe data operations.
license: Apache-2.0
metadata:
  author: Mariano Miani
  version: "1.1.0"
---

# Kotlin Multiplatform Data Layer

> **Provenance & vetting (astro-mobile).** Imported from
> [`mmiani/kotlin-kmp-claude-agent-skills`](https://github.com/mmiani/kotlin-kmp-claude-agent-skills)
> (`skills/kotlin-data-kmp-data-layer/SKILL.md`), commit
> `b73b7c1f8bad9c3068a619aa69d383d40809c248`, Apache-2.0. Cherry-picked and vetted against the
> repo's locked conventions before landing — `checked` = upstream already complied, `adapted` =
> conflicting guidance was reworded to comply:
>
> - **ktfmt is the sole Kotlin formatter:** checked — no conflict (no competing formatter recommended).
> - **No Detekt suppression baseline file and no source-level suppression annotations to silence findings:** checked — no conflict.
> - **Sealed `Result<T>` (`Success<T>` / `Error`) for error handling:** adapted — §8 "Error handling model" upstream allowed thrown exceptions as an acceptable default; reworded to mandate returning `Result<T>` across the data-layer boundary.
> - **Package layout: base package `io.jitrapon.astro`, organized by layer (`data/`, `ui/`):** checked — no conflict.
> - **UI stays out of the `:shared` module — platform UI is never unified into shared Kotlin:** checked — no conflict.

Use this skill when designing, implementing, or reviewing the data layer in a Kotlin Multiplatform project.

This skill is intentionally strict. Its purpose is to keep repositories meaningful, data ownership explicit, source-of-truth decisions clear, and upper layers insulated from transport and persistence details.

## Primary goals

The data-layer design should optimize for:

- clear ownership of application data
- a single source of truth per repository
- repositories as the entry points to data access
- clean separation between repositories and data sources
- conflict resolution across multiple sources in one place
- API shapes that fit Kotlin best practices
- immutable exposed data
- main-safe repository and data-source APIs
- predictable failure handling
- testable mapping and coordination logic

---

## Official defaults to prefer

Unless the project has a strong reason not to, prefer these defaults:

- the data layer contains application data and business logic
- repositories expose data to the rest of the app
- repositories centralize changes to data
- repositories resolve conflicts between multiple data sources
- repositories abstract data sources from the rest of the app
- repositories act as the entry points to the data layer
- each data source is responsible for one source only
- each repository defines a single source of truth
- one-shot operations use `suspend` functions
- updates over time use `Flow`
- exposed data is immutable
- repository and data-source APIs are main-safe

---

## Review and implementation dimensions

### 1. Data-layer responsibility

The data layer is not only a transport layer.

Check whether:
- the data layer owns application data concerns
- business logic that belongs with data ownership is located here
- upper layers are protected from storage and transport details

Flag as a concern when:
- the data layer is treated as only an HTTP wrapper
- business rules around creation, storage, reconciliation, or change tracking leak upward
- repositories are too thin to add meaningful ownership or coordination

### 2. Repository responsibility

Repositories should be meaningful architectural boundaries.

Check whether each repository is responsible for:
- exposing data to the rest of the app
- centralizing changes to that data
- resolving conflicts between multiple sources
- abstracting data sources from callers
- containing business logic related to data ownership and coordination

Flag as a concern when:
- repositories merely mirror endpoint methods
- repositories expose transport-layer details directly
- upper layers coordinate local and remote sources themselves
- conflict resolution is duplicated outside the repository

### 3. Data-source responsibility

Each data source should work with exactly one source of data.

Examples:
- a network source
- a database source
- a file source
- an in-memory source

Check whether:
- each data source has a narrow responsibility
- repositories depend on data sources
- other layers do not depend on data sources directly

Flag as a concern when:
- ViewModels, presenters, use cases, or UI call data sources directly
- one data source mixes several unrelated storage/transport concerns
- repositories do not meaningfully sit between callers and sources

### 4. Source of truth

Each repository should define a single source of truth.

Check whether:
- the source of truth is explicit
- the data exposed from the repository comes from that source of truth
- the repository updates that source of truth consistently
- multi-source reconciliation feeds back into the source of truth

Strong candidates:
- local database
- in-memory cache for specific bounded cases

For offline-first behavior, prefer a local data source such as a database as the source of truth.

Flag as a concern when:
- multiple sources are treated as simultaneously authoritative
- UI consumes remote responses directly while local state is supposed to be canonical
- the source-of-truth choice is implicit or unstable

### 5. API shape

The data layer should expose APIs based on the kind of operation.

Prefer:
- `suspend` functions for one-shot CRUD-style operations
- `Flow` for observing changes over time

Check whether:
- one-shot work is modeled as one-shot APIs
- long-lived observation uses `Flow`
- callers receive stable APIs that match how the data behaves

Flag as a concern when:
- streaming data is exposed as repeated polling by upper layers without reason
- one-shot operations are modeled as long-lived observable streams unnecessarily
- API style is inconsistent across similar repositories

### 6. Immutability of exposed data

The data exposed by the data layer should be immutable.

Check whether:
- repositories expose immutable models or collections
- callers cannot mutate shared state directly
- data crossing layer boundaries is safe to use concurrently

Flag as a concern when:
- mutable state is exposed directly from repositories
- collections are shared in mutable form across layers
- upper layers can tamper with repository-owned state

### 7. Main-safety and threading

Repository and data-source APIs should be safe to call from the main thread.

That means these classes are responsible for shifting blocking or expensive work to the proper thread when needed.

On **Android/JVM targets**, this typically means dispatching to `Dispatchers.IO` or a similar dispatcher inside repositories and data sources.

On **Kotlin/Native targets** (iOS, macOS, etc.), the threading model changed significantly in Kotlin 1.7.20+ with the new memory model. The old frozen-object restrictions have been removed. Coroutines on Kotlin/Native now behave much more like on JVM. However, some platform SDKs (UIKit, certain iOS APIs) still require main-thread access, so threading discipline remains relevant at the platform boundary.

Check whether:
- file, database, network, or expensive filtering work is not forced onto callers
- repositories and data sources encapsulate threading concerns appropriately
- upper layers are not made responsible for knowing threading details of the data layer
- platform-specific threading requirements (e.g., main-thread-only iOS APIs) are respected at the edge, not inside shared business logic

Flag as a concern when:
- callers must manually move work off the main thread for normal repository usage
- repositories perform blocking work without appropriate dispatching
- threading policy is inconsistent or undocumented across targets
- Kotlin/Native-specific threading behavior is assumed to be the same as the old frozen-object model

### 8. Error handling model

In this project, repository and data-source APIs surface failures through the sealed `Result<T>` type (`Success<T>` / `Error`), not by throwing across the data-layer boundary. Exceptions are caught at the data source and translated into an `Error` so callers handle outcomes by pattern-matching rather than `try/catch`. (The upstream skill treats thrown exceptions as an allowed default; this project deliberately chooses the result-wrapper API shape.)

Check whether:
- the data layer consistently returns `Result<T>` instead of leaking thrown exceptions
- failures are surfaced deliberately and carry enough context to distinguish meaningful cases
- the `Error` arm preserves the domain-specific failure meaning when it matters
- the wrapper is applied uniformly, not mixed with ad-hoc throwing in some repositories

Flag as a concern when:
- errors are swallowed
- every repository invents a different failure contract
- strings are used as the primary error model
- upper layers cannot distinguish meaningful failure cases when they need to

### 9. Naming and ownership clarity

Prefer repository names based on the data they own.

Prefer data-source names based on:
- the data they handle
- and the type of source

Check whether:
- repository names describe owned data, not transport details
- data-source names describe role and source clearly
- implementation details do not leak into architecture unnecessarily

Flag as a concern when:
- naming obscures ownership
- class names couple callers to a storage technology without reason
- repository/data-source boundaries are hard to infer from names

### 10. Upper-layer insulation

The data layer should shield the rest of the app from implementation detail.

Check whether:
- UI/state-holder/domain code depends on repository contracts rather than raw network/database details
- storage/transport migrations would stay localized inside the data layer
- data-source technology choices do not leak upward

Flag as a concern when:
- UI models mirror backend payloads directly
- persistence schemas leak into presentation
- changing a storage or network implementation would force changes across many layers

### 11. Testability

The data layer should be testable in isolation.

Check whether:
- repository coordination logic can be tested
- mappers are pure and testable
- source-of-truth behavior can be validated
- conflict-resolution logic can be exercised with fakes
- error paths are testable

Flag as a concern when:
- repository logic depends on hidden globals
- source-of-truth behavior is implicit and hard to verify
- coordination logic only works in large integration tests

---

## Required output format

When using this skill, respond with:

1. **Data-layer summary**
   - repository structure
   - data-source structure
   - source-of-truth design
   - API shape

2. **What is structurally sound**
   - concrete strengths only

3. **Issues by dimension**
   - data-layer responsibility
   - repository responsibility
   - data-source responsibility
   - source of truth
   - API shape
   - immutability
   - main-safety
   - error handling
   - naming/ownership clarity
   - upper-layer insulation
   - testability

4. **Severity for each issue**
   - high / medium / low

5. **Concrete recommendations**
   - exact repository/data-source restructuring steps
   - source-of-truth corrections
   - API-shape corrections
   - threading/error-model corrections

6. **Suggested target structure**
   - proposed repository/data-source layout if useful

7. **Open risks**
   - migration cost
   - compatibility concerns
   - remaining architectural ambiguity

---

## Anti-patterns to flag aggressively

- repositories that only mirror endpoints
- upper layers depending directly on data sources
- no explicit single source of truth
- multiple writable authorities for the same data
- mutable data exposed from repositories
- blocking or expensive work forced onto callers
- transport or persistence details leaking into UI/state holders
- silent error swallowing
- inconsistent API shapes for similar operations

---

## References

- Android data layer: https://developer.android.com/topic/architecture/data-layer
- Android architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Kotlin Multiplatform project structure: https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html
- Kotlin coroutines: https://kotlinlang.org/docs/coroutines-overview.html