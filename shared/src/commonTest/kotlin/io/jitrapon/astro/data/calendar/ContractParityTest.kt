package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.contract.EmbeddedContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Holds the vendored contract, the example fixture, and these hand-written models to one version of
 * one shape.
 *
 * The decode below is **strict** where production is lenient. Production ignores unknown keys so
 * that a field the backend adds cannot break a client already in users' hands; the cost is that an
 * unmodelled field is dropped in silence. Rejecting unknown keys here is what converts that silence
 * into a failing test: a contract field these models do not cover fails here, at the point the
 * fixture is refreshed, rather than reaching a user as a blank region of a screen.
 */
class ContractParityTest {

    @Test
    fun decodesTheVendoredFixtureThroughTheProductionModels() {
        val response = decodeMonthScreenFixture()

        assertEquals(SUPPORTED_SCHEMA_VERSION, response.schemaVersion)
        assertEquals("0.2.0", SUPPORTED_SCHEMA_VERSION)
    }

    @Test
    fun agreesWithTheVersionTheVendoredContractDeclares() {
        // The contract, the constant the client enforces, and the fixture the models decode are
        // three artifacts that drift independently; this is the assertion that ties them together.
        assertEquals(SUPPORTED_SCHEMA_VERSION, EmbeddedContract.DECLARED_CONTRACT_VERSION)
    }

    @Test
    fun deliversAThemeDocumentMatchingTheEnvelopeThemeReference() {
        assertThemeDocumentMatchesReference(decodeMonthScreenFixture())
    }
}

/**
 * Asserts the delivered theme document describes the theme the envelope names.
 *
 * A response pairing one theme's reference with another theme's document satisfies the schema, so
 * the pair is checked rather than assumed. Both halves matter: a matching id with a stale version
 * paints tokens the reference no longer describes.
 */
internal fun assertThemeDocumentMatchesReference(response: CalendarScreenResponse) {
    val themeDocument =
        assertNotNull(
            response.themeDocument,
            "The vendored fixture is the full-document response; a null document here means it " +
                "was regenerated from a knownTheme cache hit and no longer exercises the document " +
                "at all.",
        )
    assertEquals(response.theme.id, themeDocument.id)
    assertEquals(response.theme.version, themeDocument.version)
}

/** The strict codec these tests decode through — see [ContractParityTest]. */
internal val strictContractJson: Json = Json { ignoreUnknownKeys = false }

/**
 * The vendored month-screen fixture, ready to decode.
 *
 * The fixture carries `_`-prefixed keys that annotate it for a human reader and are no part of the
 * contract, so they are stripped before the strict decode would reject them. Stripping recurses: an
 * annotation nested inside the screen body would otherwise fail a decode that has nothing wrong
 * with it.
 */
internal fun monthScreenFixtureJson(): JsonObject =
    withoutAnnotationKeys(
        strictContractJson.decodeFromString<JsonObject>(EmbeddedContract.MONTH_SCREEN_FIXTURE_JSON)
    )

/** Decodes [monthScreenFixtureJson] through the production models with the strict codec. */
internal fun decodeMonthScreenFixture(): CalendarScreenResponse =
    decodeCalendarScreenResponse(monthScreenFixtureJson())

/**
 * Decodes an arbitrary screen envelope through the production models with the strict codec.
 *
 * Taking the envelope as a parameter is what lets a deliberately perturbed copy of the fixture run
 * through the very same decode path the parity checks use, so a mutation proves those checks bite
 * rather than proving something about a parallel decode.
 */
internal fun decodeCalendarScreenResponse(envelope: JsonObject): CalendarScreenResponse =
    strictContractJson.decodeFromJsonElement(CalendarScreenResponse.serializer(), envelope)

/** Returns a copy of this object with [field] removed, whether or not it was present. */
internal fun JsonObject.without(field: String): JsonObject = JsonObject(minus(field))

/** Returns a copy of this object with [field] set to [value], added or overwritten. */
internal fun JsonObject.replacing(field: String, value: JsonElement): JsonObject =
    JsonObject(plus(field to value))

private fun withoutAnnotationKeys(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject ->
            buildJsonObject {
                element.forEach { (key, value) ->
                    if (!key.startsWith(ANNOTATION_KEY_PREFIX))
                        put(key, withoutAnnotationKeys(value))
                }
            }
        is JsonArray -> buildJsonArray { element.forEach { add(withoutAnnotationKeys(it)) } }
        else -> element
    }

private fun withoutAnnotationKeys(element: JsonObject): JsonObject =
    withoutAnnotationKeys(element as JsonElement) as JsonObject

private const val ANNOTATION_KEY_PREFIX = "_"
