package io.jitrapon.astro.data.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Identifies the active theme. Paired as `id@version` to form the `knownTheme` request parameter
 * and the screen-cache tag.
 *
 * [version] changes on ANY render- or validation-affecting field of the document, not only its
 * tokens.
 */
@Serializable data class ThemeRef(val id: String, val version: String)

/**
 * A complete theme — never a partial diff over a base theme.
 *
 * Values are typed and platform-neutral (six opaque hex digits plus a separate numeric alpha,
 * numeric shadow magnitudes, font ids) because the same documents are consumed by web and by
 * Compose, and Compose cannot parse CSS.
 */
@Serializable
data class ThemeDocument(
    val id: String,
    /** Display name for a settings UI. */
    val label: String? = null,
    val version: String,
    /**
     * Which era of the semantic vocabulary this document was authored against. Metadata only — it
     * drives the server's backfill sweep and diagnostics. Readers validate against their OWN
     * supported key set, filling what they require and ignoring what they do not recognize.
     */
    val tokenSetVersion: Int,
    val colorScheme: ColorScheme,
    val tokens: ThemeTokens,
)

/**
 * The theme's token maps, keyed by semantic role name.
 *
 * The key sets are open by design: a reader fills the roles it requires from its own supported set
 * and ignores the rest, so a document authored against a newer vocabulary still paints.
 */
@Serializable
data class ThemeTokens(
    val colors: Map<String, ColorValue>,
    val shadows: Map<String, ShadowValue>,
    /**
     * Role name to an id from the vendored font allowlist — never a URL, never a CSS font stack.
     */
    val fonts: Map<String, String>,
)

/**
 * Six opaque hex digits plus a separate numeric alpha.
 *
 * Eight-digit colors are forbidden by the contract: CSS reads `#RRGGBBAA` while Android's
 * conventional form is `#AARRGGBB`, so the same eight characters would denote different colors on
 * the two clients. Splitting alpha out makes that ambiguity unrepresentable.
 */
@Serializable data class ColorValue(val hex: String, val alpha: Double = 1.0)

/** A shadow with finite, bounded magnitudes. Density-independent: px on web, dp on Android. */
@Serializable
data class ShadowValue(
    val offsetX: Double,
    val offsetY: Double,
    val blur: Double,
    val spread: Double = 0.0,
    val color: ColorValue,
)

/**
 * Drives native control styling, and selects the canonical fallback used to fill roles a reader
 * requires and the document lacks.
 */
@Serializable
enum class ColorScheme {
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}
