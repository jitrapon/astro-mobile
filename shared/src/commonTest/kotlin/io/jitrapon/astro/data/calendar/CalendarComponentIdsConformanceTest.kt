package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.contract.EmbeddedContract
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the component ids each platform registry keys on to the ones the contract declares.
 *
 * Both registries enumerate [CalendarComponentIds.BODY_IDS] and
 * [CalendarComponentIds.EVENT_PRESENTATION_IDS]; this holds those sets to the contract's own
 * `component` discriminators, so a component the contract adds or renames fails here, on a
 * host-portable runner, rather than surfacing as a fallback on a device.
 *
 * The contract also declares bodies this client deliberately does not model yet. They are named
 * below rather than filtered by rule, so modelling one is a visible edit to this list.
 */
class CalendarComponentIdsConformanceTest {

    @Test
    fun bodyIdsAreExactlyTheModelledBodiesTheContractDeclares() {
        val declared = discriminatorsOf("CalendarBody")

        assertEquals(declared - UNMODELLED_BODY_IDS, CalendarComponentIds.BODY_IDS)
        assertEquals(
            UNMODELLED_BODY_IDS,
            UNMODELLED_BODY_IDS intersect declared,
            "A body listed as unmodelled is no longer one the contract declares.",
        )
    }

    @Test
    fun eventPresentationIdsAreExactlyTheOnesTheContractDeclares() {
        assertEquals(
            discriminatorsOf("EventPresentation"),
            CalendarComponentIds.EVENT_PRESENTATION_IDS,
        )
    }

    private fun discriminatorsOf(schemaName: String): Set<String> {
        val schema =
            checkNotNull(EmbeddedContract.RESPONSE_SCHEMAS[schemaName]) {
                "The contract declares no $schemaName schema."
            }
        return schema.discriminatorMapping.keys
    }

    private companion object {
        /** Bodies the contract declares that fail to decode until their views are built. */
        val UNMODELLED_BODY_IDS = setOf("calendar.timeGrid.v1", "calendar.year.v1")
    }
}
