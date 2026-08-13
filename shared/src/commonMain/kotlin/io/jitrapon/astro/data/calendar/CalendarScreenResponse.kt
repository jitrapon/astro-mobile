package io.jitrapon.astro.data.calendar

import kotlinx.serialization.Serializable

/**
 * The one envelope schema version this client understands.
 *
 * `CalendarScreenApi` refuses any response whose `schemaVersion` is not exactly this value, and the
 * contract-parity test asserts the vendored contract declares it. Production decoding ignores
 * unknown keys so an additive BFF change cannot break a shipped client — which is precisely why the
 * version must be checked exactly: without it an incompatible response would deserialize into these
 * models with defaults and be reported as a success.
 */
const val SUPPORTED_SCHEMA_VERSION: String = "0.2.0"

/**
 * The top-level screen response: transport metadata, the active theme, and the screen itself.
 *
 * [theme] is required on every response and lives here rather than inside [ResolvedPreferences]
 * because it must be answerable on screens that carry no preferences block at all.
 */
@Serializable
data class CalendarScreenResponse(
    val schemaVersion: String,
    /**
     * Server-authoritative UTC instant. Clients derive "today" by converting it into [timeZone] and
     * truncating to a date, and seed the day-rollover timer from it — never from the device clock.
     */
    val serverTime: String,
    val locale: String,
    val timeZone: String,
    val theme: ThemeRef,
    /**
     * The active theme document, or `null` when the request's `knownTheme` already named the active
     * theme's `id@version` — the cache-hit path, and the common case in production.
     *
     * Its absence NEVER means "no theme": the active theme is always identified by [theme].
     * Treating this as required, or dereferencing it unconditionally, breaks every cache-hit
     * response.
     *
     * When present, its `id` and `version` MUST equal [theme]'s. A response pairing `dark@5` with a
     * valid document for `light@5` satisfies the schema, so the client verifies the pair before
     * applying and discards a mismatched document rather than painting the wrong tokens.
     */
    val themeDocument: ThemeDocument? = null,
    val screen: CalendarScreen,
)
