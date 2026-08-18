package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.contract.EmbeddedContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

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

    /**
     * Every component-to-region pairing the fixture declares is the one the models pin for it.
     *
     * The models supply a presentation's region rather than decoding it, so
     * `monthScreenFixtureJson` strips that key before the strict decode. This is what keeps the
     * strip from hiding a disagreement: real upstream data says which region belongs to which
     * component, and a model pinned to a different one fails here rather than quietly re-labelling
     * every event of that component.
     */
    @Test
    fun pinsTheRegionTheFixtureDeclaresForEveryPresentationItCarries() {
        val declared = declaredComponentRegionPairs(unstrippedMonthScreenFixtureJson())
        assertTrue(
            declared.isNotEmpty(),
            "The fixture carries no presentation with a region, so this proves nothing.",
        )

        val body = assertIs<MonthBody>(decodeMonthScreenFixture().screen.body)
        val pinned =
            body.props.events
                .map { event ->
                    event.presentation.componentName() to event.presentation.regionName()
                }
                .toSet()

        assertEquals(
            declared,
            pinned,
            "The models pin a region the fixture assigns to a different component.",
        )
    }
}

/**
 * Every `component`-to-`region` pairing this envelope declares, read before any key is stripped.
 */
private fun declaredComponentRegionPairs(element: JsonElement): Set<Pair<String, String>> =
    when (element) {
        is JsonObject -> {
            val pair =
                element["component"]?.jsonPrimitive?.content?.let { component ->
                    element["region"]?.jsonPrimitive?.content?.let { component to it }
                }
            setOfNotNull(pair) + element.values.flatMap { declaredComponentRegionPairs(it) }
        }
        is JsonArray -> element.flatMap { declaredComponentRegionPairs(it) }.toSet()
        else -> emptySet()
    }

/** The discriminator this presentation encodes under — the only place its component name exists. */
private fun EventPresentation.componentName(): String =
    strictContractJson
        .encodeToJsonElement(serializer<EventPresentation>(), this)
        .jsonObject
        .getValue("component")
        .jsonPrimitive
        .content

/** This presentation's pinned region as the contract spells it. */
private fun EventPresentation.regionName(): String =
    serializer<EventRegion>().descriptor.getElementName(region.ordinal)

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
 *
 * A presentation's `region` is stripped for a different reason: the models pin it per component
 * rather than decoding it (see [EventPresentation]), so the strict decode would reject a field the
 * models cover deliberately. What the fixture declares there is not discarded —
 * [ContractParityTest.pinsTheRegionTheFixtureDeclaresForEveryPresentationItCarries] reads it off
 * the unstripped fixture and holds the models to it.
 */
internal fun monthScreenFixtureJson(): JsonObject =
    withoutPinnedPresentationRegions(withoutAnnotationKeys(unstrippedMonthScreenFixtureJson()))

/** The fixture exactly as vendored, before any key is stripped from it. */
internal fun unstrippedMonthScreenFixtureJson(): JsonObject =
    strictContractJson.decodeFromString<JsonObject>(EmbeddedContract.MONTH_SCREEN_FIXTURE_JSON)

/**
 * Returns [element] with the `region` removed from every presentation it carries.
 *
 * Keyed on the presence of the `component` discriminator, so it reaches presentations wherever a
 * view-model nests them and touches nothing else — the other objects carrying a `component` declare
 * no `region` for this to remove.
 */
private fun withoutPinnedPresentationRegions(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject ->
            buildJsonObject {
                val isPresentation = element.containsKey(PRESENTATION_DISCRIMINATOR)
                element.forEach { (key, value) ->
                    if (!(isPresentation && key == PINNED_REGION_KEY))
                        put(key, withoutPinnedPresentationRegions(value))
                }
            }
        is JsonArray ->
            buildJsonArray { element.forEach { add(withoutPinnedPresentationRegions(it)) } }
        else -> element
    }

private fun withoutPinnedPresentationRegions(element: JsonObject): JsonObject =
    withoutPinnedPresentationRegions(element as JsonElement) as JsonObject

private const val PRESENTATION_DISCRIMINATOR = "component"

private const val PINNED_REGION_KEY = "region"

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
