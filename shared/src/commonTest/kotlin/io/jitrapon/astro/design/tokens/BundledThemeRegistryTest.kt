package io.jitrapon.astro.design.tokens

import io.jitrapon.astro.contract.EmbeddedContract
import io.jitrapon.astro.data.calendar.ColorScheme
import io.jitrapon.astro.data.calendar.ThemeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class BundledThemeRegistryTest {

    /** The theme each system scheme boots into, named directly rather than looked up by scheme. */
    private val bootDefaults =
        mapOf(ColorScheme.LIGHT to BundledThemes.light, ColorScheme.DARK to BundledThemes.dark)

    @Test
    fun anExactReferenceResolvesToItsThemeWhateverTheSystemScheme() {
        BundledThemes.all.forEach { theme ->
            ColorScheme.entries.forEach { systemScheme ->
                assertSame(
                    theme,
                    BundledThemeRegistry.resolve(ThemeRef(theme.id, theme.version), systemScheme),
                    "${theme.knownThemeReference} on a $systemScheme device",
                )
            }
        }
    }

    @Test
    fun theContractFixturesThemeResolvesToTheBundledLightTheme() {
        val fixture = Json.parseToJsonElement(EmbeddedContract.MONTH_SCREEN_FIXTURE_JSON).jsonObject
        val reference = Json.decodeFromJsonElement(ThemeRef.serializer(), fixture.getValue("theme"))

        ColorScheme.entries.forEach { systemScheme ->
            assertSame(
                BundledThemes.light,
                BundledThemeRegistry.resolve(reference, systemScheme),
                "fixture theme on a $systemScheme device",
            )
        }
    }

    @Test
    fun aKnownIdAtAnotherVersionFallsBackToTheSystemSchemesTheme() {
        BundledThemes.all.forEach { theme ->
            assertFallsBack(ThemeRef(theme.id, "${theme.version}-republished"), theme.id)
        }
    }

    @Test
    fun anUnknownIdFallsBackToTheSystemSchemesTheme() {
        val version = BundledThemes.light.version
        assertFallsBack(ThemeRef("user-sunset", version), "an unknown id")
    }

    @Test
    fun noReferenceResolvesToTheSystemSchemesTheme() {
        assertFallsBack(null, "no reference")
    }

    @Test
    fun everyKnownThemeReferenceMatchesTheContractsKnownThemePattern() {
        val knownTheme =
            EmbeddedContract.CALENDAR_SCREEN_PARAMETERS.single { it.name == "knownTheme" }
        val pattern = Regex(checkNotNull(knownTheme.pattern) { "knownTheme declares no pattern" })

        BundledThemes.all.forEach { theme ->
            assertTrue(
                pattern.containsMatchIn(theme.knownThemeReference),
                "${theme.knownThemeReference} against $pattern",
            )
        }
    }

    private fun assertFallsBack(reference: ThemeRef?, case: String) {
        bootDefaults.forEach { (systemScheme, expected) ->
            assertSame(
                expected,
                BundledThemeRegistry.resolve(reference, systemScheme),
                "$case on a $systemScheme device",
            )
        }
        assertEquals(ColorScheme.entries.toSet(), bootDefaults.keys, "a boot default per scheme")
    }
}
