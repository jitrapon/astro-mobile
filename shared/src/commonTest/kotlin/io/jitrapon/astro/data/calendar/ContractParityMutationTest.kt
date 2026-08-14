package io.jitrapon.astro.data.calendar

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Perturbs the vendored fixture one way at a time and asserts the contract-parity checks fail.
 *
 * [ContractParityTest] on its own passes against an untouched fixture, which is no evidence it
 * would notice drift — a decode that silently dropped every field it does not model would pass it
 * just as well. Each case below reintroduces one specific divergence the strict decode and the
 * theme-pairing check claim to catch, and asserts on the *reason* reported rather than merely that
 * something failed, so a check that starts failing for an unrelated reason is not mistaken for
 * coverage.
 *
 * The last case is the inverse: the response shape that must keep decoding. `themeDocument` is
 * absent on every `knownTheme` cache hit — the common case in production — so a change that made it
 * effectively required would break the majority of real responses while leaving every mutation case
 * above green.
 */
class ContractParityMutationTest {

    @Test
    fun rejectsAnEnvelopeMissingItsRequiredThemeReference() {
        val withoutThemeReference = monthScreenFixtureJson().without("theme")

        val failure =
            assertFailsWith<SerializationException> {
                decodeCalendarScreenResponse(withoutThemeReference)
            }

        assertContains(failure.message.orEmpty(), "'theme'")
    }

    @Test
    fun rejectsAThemeDocumentWhoseVersionDivergesFromTheEnvelopeReference() {
        // A document that still carries the right id but a stale version decodes cleanly — the
        // schema has nothing to say about the pair — so only the pairing assertion can catch it.
        val staleThemeDocument =
            monthScreenFixtureJson().editingObjectAt(listOf("themeDocument")) { themeDocument ->
                themeDocument.replacing("version", JsonPrimitive(DIVERGENT_THEME_VERSION))
            }

        val failure =
            assertFailsWith<AssertionError> {
                assertThemeDocumentMatchesReference(
                    decodeCalendarScreenResponse(staleThemeDocument)
                )
            }

        assertContains(failure.message.orEmpty(), DIVERGENT_THEME_VERSION)
    }

    @Test
    fun rejectsAnEventWhoseKindDisagreesWithItsTemporalFields() {
        // Relabelling an all-day event `timed` without swapping its floating dates for instants is
        // the drift the two-branch sealed hierarchy exists to make undecodable.
        val mislabelledEvent =
            monthScreenFixtureJson().editingFirstEventProps { event ->
                event.replacing("kind", JsonPrimitive("timed"))
            }

        val failure =
            assertFailsWith<SerializationException> {
                decodeCalendarScreenResponse(mislabelledEvent)
            }

        assertContains(failure.message.orEmpty(), "startDate")
    }

    @Test
    fun rejectsBodyPropsCarryingAnUnmodelledField() {
        val unmodelledField =
            monthScreenFixtureJson().editingObjectAt(MONTH_BODY_PROPS_PATH) { props ->
                props.replacing(UNMODELLED_PROPERTY, JsonPrimitive("stacked"))
            }

        val failure =
            assertFailsWith<SerializationException> {
                decodeCalendarScreenResponse(unmodelledField)
            }

        assertContains(failure.message.orEmpty(), UNMODELLED_PROPERTY)
    }

    @Test
    fun rejectsAPreferenceValueOutsideItsEnumeratedSet() {
        val unknownWeekStart =
            monthScreenFixtureJson().editingObjectAt(RESOLVED_PREFERENCES_PATH) { preferences ->
                preferences.replacing("weekStart", JsonPrimitive(UNENUMERATED_WEEK_START))
            }

        val failure =
            assertFailsWith<SerializationException> {
                decodeCalendarScreenResponse(unknownWeekStart)
            }

        assertContains(failure.message.orEmpty(), UNENUMERATED_WEEK_START)
    }

    @Test
    fun decodesACacheHitResponseCarryingNoThemeDocument() {
        val cacheHitResponse = monthScreenFixtureJson().without("themeDocument")

        val decoded = decodeCalendarScreenResponse(cacheHitResponse)

        assertNull(decoded.themeDocument)
        // The theme is still fully identified: absence of the document never means "no theme".
        assertEquals(decodeMonthScreenFixture().theme, decoded.theme)
    }
}

private val MONTH_BODY_PROPS_PATH = listOf("screen", "body", "props")

private val RESOLVED_PREFERENCES_PATH = listOf("screen", "resolvedPreferences")

private const val DIVERGENT_THEME_VERSION = "divergent-theme-version"

private const val UNMODELLED_PROPERTY = "lanePacking"

private const val UNENUMERATED_WEEK_START = "tuesday"

private fun JsonObject.without(field: String): JsonObject = JsonObject(minus(field))

private fun JsonObject.replacing(field: String, value: JsonElement): JsonObject =
    JsonObject(plus(field to value))

/** Returns a copy of the whole envelope with [edit] applied to the object reached by [path]. */
private fun JsonObject.editingObjectAt(
    path: List<String>,
    edit: (JsonObject) -> JsonObject,
): JsonObject {
    val field = path.firstOrNull() ?: return edit(this)
    return replacing(field, getValue(field).jsonObject.editingObjectAt(path.drop(1), edit))
}

/** Returns a copy of the whole envelope with [edit] applied to the first month event's props. */
private fun JsonObject.editingFirstEventProps(edit: (JsonObject) -> JsonObject): JsonObject =
    editingObjectAt(MONTH_BODY_PROPS_PATH) { props ->
        val events = props.getValue("events").jsonArray
        val edited = events.mapIndexed { index, event ->
            if (index == 0) event.jsonObject.editingObjectAt(listOf("props"), edit) else event
        }
        props.replacing("events", JsonArray(edited))
    }
