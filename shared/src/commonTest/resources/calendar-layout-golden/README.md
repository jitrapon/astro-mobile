# Calendar layout golden-vector corpus — reserved (empty on M-1)

Home for the JSON golden-vector corpus that gates the shared calendar layout engine
(`io.jitrapon.astro.calendar.layout`). Reserved on M-1; vectors are authored with the
engine on M-2+.

Each vector pairs a layout **input** (events on a grid) with its expected layout
**output** (placed columns / segments). The corpus is the cross-platform parity contract
with astro-web and the regression gate for the shared implementation. See
`../../../commonMain/kotlin/io/jitrapon/astro/calendar/layout/README.md` and astro-plans
`ADR-calendar-layout-engine-sharing-strategy`.
