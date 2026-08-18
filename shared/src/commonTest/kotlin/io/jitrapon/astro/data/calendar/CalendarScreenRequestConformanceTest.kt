package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.contract.ContractParameter
import io.jitrapon.astro.contract.EmbeddedContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Holds the request `CalendarScreenApi` puts on the wire to the operation the contract declares.
 *
 * Every expectation below is read out of the vendored contract at build time rather than copied
 * into this file, so a renamed parameter or a changed bound upstream fails here instead of at the
 * first call against a real backend. Assertions are made over the request Ktor actually emitted,
 * captured by [MockCalendarBackend] — reading the client's source proves what it was written to
 * send, not what a plugin, a default, or an encoder did with it afterwards.
 *
 * Names alone are not enough: a parameter spelled correctly and encoded wrongly passes every
 * name-level check and then fails against the backend. So the values are asserted too — the
 * date-only window boundaries, the day count's presence and bounds, and the absence of an unset
 * optional from the query string rather than its presence as an empty value.
 */
class CalendarScreenRequestConformanceTest {

    @Test
    fun requestsTheOperationPathTheContractDeclares() = runTest {
        val emitted = emittedRequestFor(monthScreenRequest())

        assertEquals(EmbeddedContract.CALENDAR_SCREEN_PATH, emitted.url.encodedPath)
    }

    @Test
    fun alwaysSendsExactlyTheParametersTheContractRequires() = runTest {
        // The narrowest request this client can express: no day count, no known theme.
        val emitted = emittedRequestFor(monthScreenRequest())

        assertEquals(requiredContractParameterNames(), emitted.url.parameters.names())
    }

    @Test
    fun sendsNoConditionalParameterOutsideTheContractsOptionalSet() = runTest {
        // The widest one: the only view carrying a day count, plus a theme the caller already
        // holds.
        val emitted =
            emittedRequestFor(
                monthScreenRequest(
                    view = RequestedCalendarView.TimeGrid(dayCount = 3),
                    knownTheme = "light@8a95f0d5c35fdec5eab641a121ae068f",
                )
            )

        val conditional = emitted.url.parameters.names() - requiredContractParameterNames()
        assertEquals(optionalContractParameterNames(), conditional)
    }

    @Test
    fun emitsExactlyTheViewValuesTheContractEnumerates() = runTest {
        val declared = contractParameter("view").enumValues.toSet()
        assertTrue(declared.isNotEmpty(), "The contract declares no `view` enum to check against.")

        val emitted =
            RequestedCalendarView.ALL.map { view ->
                assertNotNull(
                    emittedRequestFor(monthScreenRequest(view = view)).url.parameters["view"]
                )
            }

        // Equality in both directions: a value this client can emit that the contract does not
        // enumerate is a 400, and one the contract enumerates that it cannot emit is a view the app
        // has no way to ask for.
        assertEquals(declared, emitted.toSet())
    }

    @Test
    fun serializesTheWindowBoundariesInTheDateOnlyFormatTheContractDeclares() = runTest {
        // Guard first: with no declared format the value assertions below would pass vacuously
        // against a contract that had started asking for instants.
        listOf("start", "end").forEach { boundary ->
            assertEquals(
                DATE_ONLY_FORMAT,
                contractParameter(boundary).format,
                "The contract no longer declares `$boundary` as `format: $DATE_ONLY_FORMAT`.",
            )
        }

        val emitted = emittedRequestFor(monthScreenRequest())

        // Exact values, not a shape: no time component, no zone offset, no trailing `Z`.
        assertEquals("2026-04-01", emitted.url.parameters["start"])
        assertEquals("2026-04-30", emitted.url.parameters["end"])
    }

    @Test
    fun sendsTheDayCountForTheTimeGridViewAlone() = runTest {
        RequestedCalendarView.ALL.filterNot { it is RequestedCalendarView.TimeGrid }
            .forEach { view ->
                val emitted = emittedRequestFor(monthScreenRequest(view = view))
                assertNull(
                    emitted.url.parameters[DAY_COUNT],
                    "`$DAY_COUNT` means nothing to the ${view.wireValue} view and must not be sent.",
                )
            }

        val timeGrid =
            emittedRequestFor(
                monthScreenRequest(view = RequestedCalendarView.TimeGrid(dayCount = 3))
            )

        assertEquals("3", timeGrid.url.parameters[DAY_COUNT])
    }

    @Test
    fun refusesADayCountOutsideTheBoundsTheContractDeclares() {
        val dayCount = contractParameter(DAY_COUNT)
        val minimum =
            assertNotNull(dayCount.minimum, "The contract declares no `$DAY_COUNT` floor.")
        val maximum =
            assertNotNull(dayCount.maximum, "The contract declares no `$DAY_COUNT` ceiling.")

        // The bounds are enforced where the value is constructed, so an out-of-range day count
        // cannot reach the wire at all — the request that carries it is unrepresentable.
        RequestedCalendarView.TimeGrid(dayCount = minimum)
        RequestedCalendarView.TimeGrid(dayCount = maximum)
        assertFailsWith<IllegalArgumentException> {
            RequestedCalendarView.TimeGrid(dayCount = minimum - 1)
        }
        assertFailsWith<IllegalArgumentException> {
            RequestedCalendarView.TimeGrid(dayCount = maximum + 1)
        }
    }

    @Test
    fun omitsAnUnsetOptionalParameterFromTheQueryStringEntirely() = runTest {
        val emitted = emittedRequestFor(monthScreenRequest())

        optionalContractParameterNames().forEach { name ->
            assertFalse(emitted.url.parameters.contains(name), "`$name` was sent unset.")
            // Absent, not empty: `?knownTheme=` is a supplied value as far as the backend's
            // validation is concerned, and it would fail the parameter's pattern rather than being
            // read as "the caller holds no theme".
            assertFalse(
                emitted.url.encodedQuery.contains(name),
                "`$name` appears in the query string `${emitted.url.encodedQuery}` despite being " +
                    "unset — an empty value is a supplied value, not an absent one.",
            )
        }
    }
}

private const val DAY_COUNT = "dayCount"

private const val DATE_ONLY_FORMAT = "date"

private fun contractParameter(name: String): ContractParameter =
    assertNotNull(
        EmbeddedContract.CALENDAR_SCREEN_PARAMETERS.firstOrNull { it.name == name },
        "The contract's calendar screen operation declares no `$name` parameter.",
    )

private fun requiredContractParameterNames(): Set<String> =
    EmbeddedContract.CALENDAR_SCREEN_PARAMETERS.filter { it.required }.map { it.name }.toSet()

private fun optionalContractParameterNames(): Set<String> =
    EmbeddedContract.CALENDAR_SCREEN_PARAMETERS.filterNot { it.required }.map { it.name }.toSet()
