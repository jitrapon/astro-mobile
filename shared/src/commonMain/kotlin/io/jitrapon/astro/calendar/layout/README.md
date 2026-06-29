# Calendar layout engine — reserved seam (`io.jitrapon.astro.calendar.layout`)

**Reserved on M-1, implemented on M-2+. No engine code lives here yet.**

This package is the mobile home of the shared calendar layout engine described in
astro-plans `ADR-calendar-layout-engine-sharing-strategy` (decision D-15) and gated by
the `W-S1` KMP-viability spike. The engine is the time-grid column-pack and all-day /
multi-day week-segmentation math — the spec-heavy part of the calendar that must behave
identically on web and mobile.

## Why the seam is reserved here

Under the ADR's default (Option 1), the engine lives **once** in shared Kotlin
`commonMain`, compiled to a JS artifact for web and consumed natively on mobile —
parity by construction, not by test. A Kotlin implementation is committed for mobile
regardless of which web option the spike selects, so `commonMain` is its home either way.

The engine is **pure layout math and UI-agnostic** — it has no dependency on Jetpack
Compose, SwiftUI, or Compose Multiplatform. The Android (Compose) and iOS (SwiftUI) UIs
each consume its plain output. This is why it belongs in `:shared` and not in a UI module.

## Interop contract (must hold so the same source can compile to JS for web)

When the engine lands, its public seam takes plain, JSON-like input and returns plain,
JSON-like output. The following must **not** cross the seam boundary (they do not survive
the Kotlin→JS adapter the web side uses):

- `kotlinx.datetime` types, `Flow` / coroutine primitives, Kotlin exceptions
- Kotlin sealed-class objects, Kotlin collection types as part of the public shape
- generated API/OpenAPI classes or the app's `Result<T>`

Inputs and outputs are plain value types with primitive/`data class` fields. Adapters at
each call site convert from app models into the layout input and back.

## Golden-vector corpus

The cross-platform parity / regression gate is a JSON golden-vector corpus, reserved at
`shared/src/commonTest/resources/calendar-layout-golden/`. It is the executable spec for
the fiddly cases (column packing, multi-day segmentation) and is authored alongside the
engine on M-2+.
