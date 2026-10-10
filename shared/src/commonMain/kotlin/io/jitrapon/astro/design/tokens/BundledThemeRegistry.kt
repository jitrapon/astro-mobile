package io.jitrapon.astro.design.tokens

import io.jitrapon.astro.data.calendar.ColorScheme
import io.jitrapon.astro.data.calendar.ThemeRef

/**
 * Resolves the theme to paint from the themes compiled into the app.
 *
 * Each bundled theme's [BundledTheme.knownThemeReference] is the `id@version` a screen request
 * names as its `knownTheme`, so the server can omit a theme document the app already holds. A
 * response's [ThemeRef] resolves here only on an exact id **and** version match: a bundled theme
 * whose tokens were republished upstream keeps its id under a new version, and painting the stale
 * copy would disagree with the colors the server resolved against the current one.
 *
 * Everything else — no response yet at cold start, an unknown id, or a known id at another version
 * — falls back to the bundled theme for the system's color scheme at the moment of the call. That
 * is a boot default, not follow-system: nothing here observes the system scheme, and a later change
 * to it repaints nothing on its own.
 */
object BundledThemeRegistry {

    /**
     * The bundled theme [reference] names, or the one for [systemColorScheme] when [reference] is
     * `null` or names no bundled theme.
     */
    fun resolve(reference: ThemeRef?, systemColorScheme: ColorScheme): BundledTheme =
        reference?.let(::findExact) ?: themeFor(systemColorScheme)

    private fun findExact(reference: ThemeRef): BundledTheme? =
        BundledThemes.all.firstOrNull { it.id == reference.id && it.version == reference.version }

    // Total by construction: the token generator fails the build unless the bundled themes number
    // exactly one per color scheme.
    private fun themeFor(colorScheme: ColorScheme): BundledTheme =
        BundledThemes.all.first { it.colorScheme == colorScheme }
}
