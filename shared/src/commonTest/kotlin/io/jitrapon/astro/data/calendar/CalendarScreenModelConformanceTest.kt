package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.contract.ContractSchema
import io.jitrapon.astro.contract.EmbeddedContract
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * Holds the response models to the schemas the vendored contract declares.
 *
 * The fixture-decoding parity test can only see what the one canonical example happens to contain:
 * a field the contract requires but that example omits, an enum value it never uses, and every
 * agenda schema — for which no fixture exists upstream at all — are invisible to it. This test
 * reads the contract's own `required:` lists, enums, and discriminator mappings out of the embedded
 * facts and compares them against the models' serial descriptors, so those gaps are covered without
 * a fixture per branch.
 *
 * Full structural validation of every declared screen branch is deliberately not attempted here.
 * That arrives for free with generated models; hand-writing it would produce a large body of
 * assertions over branches nothing exercises — an untested claim dressed as coverage.
 */
class CalendarScreenModelConformanceTest {

    @Test
    fun modelsRequireExactlyTheFieldsTheContractRequires() {
        MODELLED_SCHEMAS.forEach { modelled ->
            val declared = contractSchema(modelled.contractSchema).requiredProperties.toSet()
            // A polymorphic branch's discriminator is written by the encoder rather than declared
            // as a property, so it is absent from the descriptor and added back here — the contract
            // requires it on the wire either way.
            val modelledFields =
                modelled.descriptor.requiredElementNames() +
                    setOfNotNull(modelled.discriminator) +
                    modelled.pinnedProperties

            assertEquals(
                declared,
                modelledFields,
                "${modelled.contractSchema} and ${modelled.descriptor.serialName} disagree on " +
                    "which fields are required.",
            )
        }
    }

    @Test
    fun modelledEnumsCarryExactlyTheValuesTheContractEnumerates() {
        MODELLED_ENUMS.forEach { modelled ->
            val declared =
                modelled.contractSites
                    .flatMap { (schemaName, property) ->
                        assertNotNull(
                            contractSchema(schemaName).enumsByProperty[property],
                            "$schemaName.$property no longer enumerates its values.",
                        )
                    }
                    .toSet()

            assertEquals(
                declared,
                modelled.descriptor.elementNames(),
                "${modelled.descriptor.serialName} and the contract disagree on the values of " +
                    "${modelled.contractSites.joinToString { (schema, property) ->
                        "$schema.$property" }}.",
            )
        }
    }

    /**
     * The pinned region on each presentation is the one the contract fixes for that component.
     *
     * [modelsRequireExactlyTheFieldsTheContractRequires] only knows `region` is accounted for, and
     * [modelledEnumsCarryExactlyTheValuesTheContractEnumerates] only knows the five branches
     * enumerate the five regions between them — neither can see whether a branch pins the *wrong*
     * one. Since the value is supplied rather than decoded, nothing on the wire would correct it: a
     * branch pinned to a region the contract gives to a different component would route every event
     * of that component to a renderer that cannot draw it, silently.
     */
    @Test
    fun pinsEachPresentationToTheSingleRegionTheContractFixesForIt() {
        PINNED_PRESENTATION_REGIONS.forEach { (schemaName, presentation) ->
            val declared =
                assertNotNull(
                    contractSchema(schemaName).enumsByProperty["region"],
                    "$schemaName no longer fixes a single region.",
                )

            assertEquals(
                declared.toSet(),
                setOf(presentation.region.wireName()),
                "$schemaName pins a region the contract gives to another component.",
            )
        }
    }

    @Test
    fun modelsEveryUnionBranchTheContractDeclaresOrNamesTheOnesItSkips() {
        MODELLED_UNIONS.forEach { union ->
            val declared = contractSchema(union.contractSchema).discriminatorMapping.keys
            val modelled = union.branches.map { it.serialName }.toSet()

            assertEquals(
                declared,
                modelled + union.unmodelledBranches,
                "${union.contractSchema}'s branches drifted from the contract's mapping.",
            )
        }
    }

    @Test
    fun encodesEachUnionUnderTheDiscriminatorPropertyTheContractDeclares() {
        val response = decodeMonthScreenFixture()
        val body = assertIs<MonthBody>(response.screen.body)
        val event = body.props.events.first()

        // Asserted over encoded output rather than over an annotation: what the contract constrains
        // is the key on the wire, and only encoding shows which key that is.
        assertDiscriminatedAsContractDeclares<CalendarBody>("CalendarBody", body)
        assertDiscriminatedAsContractDeclares<CalendarEvent>("CalendarEvent", event.props)
        assertDiscriminatedAsContractDeclares<EventPresentation>(
            "EventPresentation",
            event.presentation,
        )
        assertDiscriminatedAsContractDeclares<CalendarViewSelection>(
            "CalendarViewSelection",
            response.screen.viewSwitcher.activeSelection,
        )
        assertDiscriminatedAsContractDeclares<Action>(
            "Action",
            response.screen.navigation.destinations.first().action,
        )
    }

    @Test
    fun accountsForEverySchemaTheContractDeclares() {
        val accountedFor =
            MODELLED_SCHEMAS.map { it.contractSchema }.toSet() +
                MODELLED_UNIONS.map { it.contractSchema } +
                UNMODELLED_SCHEMAS

        // Equality in both directions: a schema added upstream lands here as an unaccounted name
        // rather than as silence, and a name that disappears upstream fails rather than lingering
        // in the lists above as a check that no longer checks anything.
        assertEquals(EmbeddedContract.RESPONSE_SCHEMAS.keys, accountedFor)
    }
}

/** A contract schema and the model that carries it. */
private class ModelledSchema(
    val contractSchema: String,
    val descriptor: SerialDescriptor,
    /** The polymorphic discriminator this branch is encoded under, if it is a union branch. */
    val discriminator: String? = null,
    /**
     * Properties the contract requires on the wire that the model supplies from a constant rather
     * than decoding into a property. They are absent from the descriptor for the same reason a
     * discriminator is — the model fixes the value instead of reading it — so they are named here
     * to keep that a deliberate registration rather than a silently missing field.
     */
    val pinnedProperties: Set<String> = emptySet(),
)

/** A modelled enum and every place the contract enumerates the same values. */
private class ModelledEnum(
    val descriptor: SerialDescriptor,
    val contractSites: List<Pair<String, String>>,
)

/** A discriminated union and the branches modelled for it. */
private class ModelledUnion(
    val contractSchema: String,
    val branches: List<SerialDescriptor>,
    /** Declared branches deliberately left unmodelled, keyed by their discriminator value. */
    val unmodelledBranches: Set<String> = emptySet(),
)

private const val TYPE_DISCRIMINATOR = "type"

private const val COMPONENT_DISCRIMINATOR = "component"

/** Every presentation branch supplies its own region; see [EventPresentation]. */
private val PINNED_REGION = setOf("region")

private const val KIND_DISCRIMINATOR = "kind"

private val MODELLED_SCHEMAS =
    listOf(
        ModelledSchema("CalendarScreenResponse", descriptorOf<CalendarScreenResponse>()),
        ModelledSchema("CalendarScreen", descriptorOf<CalendarScreen>()),
        ModelledSchema("Navigation", descriptorOf<Navigation>()),
        ModelledSchema("NavDestination", descriptorOf<NavDestination>()),
        ModelledSchema("ViewSwitcher", descriptorOf<ViewSwitcher>()),
        ModelledSchema("ViewSwitcherOption", descriptorOf<ViewSwitcherOption>()),
        ModelledSchema("ResolvedPreferences", descriptorOf<ResolvedPreferences>()),
        ModelledSchema("ResolvedPreferences.chipDensity", descriptorOf<ChipDensity>()),
        ModelledSchema("ThemeRef", descriptorOf<ThemeRef>()),
        ModelledSchema("ThemeDocument", descriptorOf<ThemeDocument>()),
        ModelledSchema("ThemeDocument.tokens", descriptorOf<ThemeTokens>()),
        ModelledSchema("ColorValue", descriptorOf<ColorValue>()),
        ModelledSchema("ShadowValue", descriptorOf<ShadowValue>()),
        ModelledSchema("Calendar", descriptorOf<Calendar>()),
        ModelledSchema("CalendarColor", descriptorOf<CalendarColor>()),
        ModelledSchema("EventPermissions", descriptorOf<EventPermissions>()),
        ModelledSchema("PresentationLine", descriptorOf<PresentationLine>()),
        ModelledSchema("PresentedCalendarEvent", descriptorOf<PresentedCalendarEvent>()),
        ModelledSchema("CalendarRange", descriptorOf<CalendarRange>()),
        ModelledSchema("CalendarMonthViewModel", descriptorOf<CalendarMonthViewModel>()),
        // The agenda view-model and its day sections have no fixture upstream, so these three rows
        // are the only mechanical check they get.
        ModelledSchema("CalendarAgendaViewModel", descriptorOf<CalendarAgendaViewModel>()),
        ModelledSchema("CalendarAgendaViewModel.days[]", descriptorOf<AgendaDay>()),
        ModelledSchema("AgendaBody", descriptorOf<AgendaBody>(), COMPONENT_DISCRIMINATOR),
        ModelledSchema("MonthBody", descriptorOf<MonthBody>(), COMPONENT_DISCRIMINATOR),
        ModelledSchema("TimedEvent", descriptorOf<TimedEvent>(), KIND_DISCRIMINATOR),
        ModelledSchema("AllDayEvent", descriptorOf<AllDayEvent>(), KIND_DISCRIMINATOR),
        ModelledSchema(
            "MonthAllDayBarPresentation",
            descriptorOf<MonthAllDayBarPresentation>(),
            COMPONENT_DISCRIMINATOR,
            PINNED_REGION,
        ),
        ModelledSchema(
            "MonthTimedMarkerPresentation",
            descriptorOf<MonthTimedMarkerPresentation>(),
            COMPONENT_DISCRIMINATOR,
            PINNED_REGION,
        ),
        ModelledSchema(
            "TimeGridAllDayBarPresentation",
            descriptorOf<TimeGridAllDayBarPresentation>(),
            COMPONENT_DISCRIMINATOR,
            PINNED_REGION,
        ),
        ModelledSchema(
            "EventBlockPresentation",
            descriptorOf<EventBlockPresentation>(),
            COMPONENT_DISCRIMINATOR,
            PINNED_REGION,
        ),
        ModelledSchema(
            "EventCardPresentation",
            descriptorOf<EventCardPresentation>(),
            COMPONENT_DISCRIMINATOR,
            PINNED_REGION,
        ),
        ModelledSchema(
            "AgendaViewSelection",
            descriptorOf<AgendaViewSelection>(),
            TYPE_DISCRIMINATOR,
        ),
        ModelledSchema(
            "TimeGridViewSelection",
            descriptorOf<TimeGridViewSelection>(),
            TYPE_DISCRIMINATOR,
        ),
        ModelledSchema(
            "MonthViewSelection",
            descriptorOf<MonthViewSelection>(),
            TYPE_DISCRIMINATOR,
        ),
        ModelledSchema("YearViewSelection", descriptorOf<YearViewSelection>(), TYPE_DISCRIMINATOR),
        ModelledSchema("NavigateAction", descriptorOf<NavigateAction>(), TYPE_DISCRIMINATOR),
        ModelledSchema("OpenUrlAction", descriptorOf<OpenUrlAction>(), TYPE_DISCRIMINATOR),
        ModelledSchema(
            "SwitchCalendarViewAction",
            descriptorOf<SwitchCalendarViewAction>(),
            TYPE_DISCRIMINATOR,
        ),
        ModelledSchema(
            "OpenEventDetailAction",
            descriptorOf<OpenEventDetailAction>(),
            TYPE_DISCRIMINATOR,
        ),
        ModelledSchema(
            "PresentModalAction",
            descriptorOf<PresentModalAction>(),
            TYPE_DISCRIMINATOR,
        ),
    )

private val MODELLED_ENUMS =
    listOf(
        ModelledEnum(descriptorOf<WeekStart>(), listOf("ResolvedPreferences" to "weekStart")),
        ModelledEnum(descriptorOf<ChipStyle>(), listOf("ResolvedPreferences" to "chipStyle")),
        ModelledEnum(
            descriptorOf<ChipDensityLevel>(),
            listOf("ResolvedPreferences.chipDensity" to "level"),
        ),
        ModelledEnum(descriptorOf<ColorScheme>(), listOf("ThemeDocument" to "colorScheme")),
        ModelledEnum(
            descriptorOf<CalendarItemType>(),
            listOf("PresentedCalendarEvent" to "itemType"),
        ),
        // One enum on this side, one single-valued `region` const per presentation branch on the
        // contract's — so the whole set is what the branches enumerate between them.
        ModelledEnum(
            descriptorOf<EventRegion>(),
            listOf(
                "MonthAllDayBarPresentation" to "region",
                "MonthTimedMarkerPresentation" to "region",
                "TimeGridAllDayBarPresentation" to "region",
                "EventBlockPresentation" to "region",
                "EventCardPresentation" to "region",
            ),
        ),
    )

private val MODELLED_UNIONS =
    listOf(
        ModelledUnion(
            contractSchema = "CalendarBody",
            branches = listOf(descriptorOf<MonthBody>(), descriptorOf<AgendaBody>()),
            // The time-grid and year views are not built. A response carrying either fails to
            // decode, which is the intended signal until they are.
            unmodelledBranches = setOf("calendar.timeGrid.v1", "calendar.year.v1"),
        ),
        ModelledUnion(
            contractSchema = "CalendarEvent",
            branches = listOf(descriptorOf<TimedEvent>(), descriptorOf<AllDayEvent>()),
        ),
        ModelledUnion(
            contractSchema = "EventPresentation",
            branches =
                listOf(
                    descriptorOf<MonthAllDayBarPresentation>(),
                    descriptorOf<MonthTimedMarkerPresentation>(),
                    descriptorOf<TimeGridAllDayBarPresentation>(),
                    descriptorOf<EventBlockPresentation>(),
                    descriptorOf<EventCardPresentation>(),
                ),
        ),
        ModelledUnion(
            contractSchema = "CalendarViewSelection",
            branches =
                listOf(
                    descriptorOf<AgendaViewSelection>(),
                    descriptorOf<TimeGridViewSelection>(),
                    descriptorOf<MonthViewSelection>(),
                    descriptorOf<YearViewSelection>(),
                ),
        ),
        ModelledUnion(
            contractSchema = "Action",
            branches =
                listOf(
                    descriptorOf<NavigateAction>(),
                    descriptorOf<OpenUrlAction>(),
                    descriptorOf<SwitchCalendarViewAction>(),
                    descriptorOf<OpenEventDetailAction>(),
                    descriptorOf<PresentModalAction>(),
                ),
        ),
    )

/**
 * Schemas the models deliberately do not carry, each for a reason that would otherwise have to be
 * rediscovered from the absence of a row above.
 */
private val UNMODELLED_SCHEMAS =
    setOf(
        // Error bodies. Non-2xx responses become a Result.Error carrying the status; the client
        // never decodes the problem document.
        "ProblemDetails",
        // The time-grid and year views, and the bodies that carry them.
        "TimeGridBody",
        "YearBody",
        "CalendarTimeGridViewModel",
        "CalendarDayColumn",
        "CalendarYearViewModel",
        "CalendarYearViewModel.days[]",
        // An open map of calendars, carried as a Map rather than a type of its own.
        "CalendarMap",
        // `allOf` composition bases. The generator folds their fields into every branch that
        // composes them, so each branch's row above already covers what they declare.
        "EventBase",
        "PresentationContentBase",
    )

/**
 * One instance of every presentation branch, paired with the contract schema that fixes its region.
 *
 * Instances rather than descriptors because a pinned region has no descriptor element to read — the
 * only way to see the value is to hold a branch and ask it. Content is irrelevant here; every field
 * but the region is left at whatever the branch needs to construct.
 */
private val PINNED_PRESENTATION_REGIONS: List<Pair<String, EventPresentation>> =
    listOf(
        "MonthAllDayBarPresentation" to MonthAllDayBarPresentation(title = ""),
        "MonthTimedMarkerPresentation" to
            MonthTimedMarkerPresentation(line = PresentationLine(text = "")),
        "TimeGridAllDayBarPresentation" to TimeGridAllDayBarPresentation(title = ""),
        "EventBlockPresentation" to EventBlockPresentation(title = ""),
        "EventCardPresentation" to EventCardPresentation(title = ""),
    )

private inline fun <reified T> descriptorOf(): SerialDescriptor = serializer<T>().descriptor

/** This region as the contract spells it, rather than as Kotlin names the constant. */
private fun EventRegion.wireName(): String = descriptorOf<EventRegion>().getElementName(ordinal)

private fun contractSchema(name: String): ContractSchema =
    assertNotNull(
        EmbeddedContract.RESPONSE_SCHEMAS[name],
        "The contract declares no `$name` schema.",
    )

/** The names of the elements a decode of this descriptor cannot fill from a default. */
private fun SerialDescriptor.requiredElementNames(): Set<String> =
    (0 until elementsCount).filterNot { isElementOptional(it) }.map { getElementName(it) }.toSet()

private fun SerialDescriptor.elementNames(): Set<String> =
    (0 until elementsCount).map { getElementName(it) }.toSet()

private inline fun <reified T> assertDiscriminatedAsContractDeclares(schemaName: String, value: T) {
    val schema = contractSchema(schemaName)
    val discriminatorProperty =
        assertNotNull(schema.discriminatorProperty, "$schemaName declares no discriminator.")

    val encoded = strictContractJson.encodeToJsonElement(value).jsonObject
    val discriminatorValue =
        assertNotNull(
                encoded[discriminatorProperty],
                "$schemaName is encoded without the `$discriminatorProperty` key the contract " +
                    "discriminates it on.",
            )
            .jsonPrimitive
            .content

    assertContains(schema.discriminatorMapping.keys, discriminatorValue)
}
