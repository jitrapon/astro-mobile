package io.jitrapon.astro.ui.component

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.jitrapon.astro.data.calendar.CalendarColor

// Resolves an event's calendar colours for the renderers that draw it.

/** The three colours a chip draws with, resolved from the event's calendar. */
internal data class ChipColors(val accent: Color, val background: Color, val foreground: Color)

/**
 * This calendar's colour triple as Compose colours. An event whose calendar the body does not
 * carry, or a colour that does not parse, takes the theme's neutral colours instead — one bad
 * reference must not fail the whole body.
 */
@Composable
internal fun CalendarColor?.toChipColors(): ChipColors {
    val neutral =
        ChipColors(
            accent = MaterialTheme.colors.primary,
            background = MaterialTheme.colors.surface,
            foreground = MaterialTheme.colors.onSurface,
        )
    if (this == null) return neutral
    return ChipColors(
        accent = accentColor.toColorOrNull() ?: neutral.accent,
        background = backgroundColor.toColorOrNull() ?: neutral.background,
        foreground = foregroundColor.toColorOrNull() ?: neutral.foreground,
    )
}

/** A `#rrggbb` (or `#aarrggbb`) string as a colour, or `null` when it does not parse. */
private fun String.toColorOrNull(): Color? =
    try {
        Color(android.graphics.Color.parseColor(this))
    } catch (_: IllegalArgumentException) {
        null
    }
