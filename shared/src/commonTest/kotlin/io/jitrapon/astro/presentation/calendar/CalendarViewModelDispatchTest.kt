package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.AgendaViewSelection
import io.jitrapon.astro.data.calendar.CalendarDate
import io.jitrapon.astro.data.calendar.CalendarScreenQueryFixture
import io.jitrapon.astro.data.calendar.CalendarScreenQueryState
import io.jitrapon.astro.data.calendar.MonthViewSelection
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.data.calendar.OpenEventDetailAction
import io.jitrapon.astro.data.calendar.OpenUrlAction
import io.jitrapon.astro.data.calendar.PresentModalAction
import io.jitrapon.astro.data.calendar.RequestedCalendarView
import io.jitrapon.astro.data.calendar.StalledExchange
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.calendar.TimeGridViewSelection
import io.jitrapon.astro.data.calendar.monthScreenFixtureJson
import io.jitrapon.astro.data.calendar.monthScreenRequest
import io.jitrapon.astro.data.calendar.replacing
import io.jitrapon.astro.data.calendar.respondJson
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.recordStates
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pins what dispatching an action does to a view model: which actions come back as an effect for
 * the platform, and how a view switch re-points the observed screen — including what is painted
 * while the switched-to view loads, fails, or is itself superseded.
 *
 * Every case runs the real stack over a stubbed backend that stamps each screen with the view it
 * answered, so "the previous screen is still painted" is an assertion about which response is
 * showing rather than about whether anything is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelDispatchTest {

    @Test
    fun everyActionButAViewSwitchComesBackAsTheEffectNamingIt() = runTest {
        val backend = ViewStampingBackend()
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }

        assertEquals(
            ActionEffect.ShowScreen("settings"),
            viewModel.dispatch(NavigateAction("settings")),
        )
        assertEquals(
            ActionEffect.OpenExternalUrl("https://astro.test/help"),
            viewModel.dispatch(OpenUrlAction("https://astro.test/help")),
        )
        assertEquals(
            ActionEffect.ShowEventDetail("event-1"),
            viewModel.dispatch(OpenEventDetailAction("event-1")),
        )
        assertEquals(
            ActionEffect.ShowEvents(listOf("event-2", "event-1")),
            viewModel.dispatch(PresentModalAction(listOf("event-2", "event-1"))),
        )
        advanceUntilIdle()

        assertEquals(listOf(MONTH), backend.requestedViews)
        assertEquals(MONTH, painted.latest.stampedView())
    }

    @Test
    fun aViewSwitchRePointsOnlyTheRequestsViewAndReturnsNoEffect() = runTest {
        val backend = ViewStampingBackend()
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }

        assertNull(viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection)))

        painted.awaitLatest { it.stampedView() == AGENDA }
        assertEquals(listOf(MONTH, AGENDA), backend.requestedViews)
        val month = backend.requests.first().url.parameters
        val agenda = backend.requests.last().url.parameters
        for (unchanged in listOf("start", "end", "tz", "locale")) {
            assertEquals(month[unchanged], agenda[unchanged], "The switch moved `$unchanged`.")
        }
    }

    @Test
    fun whileTheSwitchedToViewLoadsThePreviousScreenStaysPainted() = runTest {
        val stalled = StalledExchange()
        val backend = ViewStampingBackend(beforeAgendaAnswers = { stalled.hold() })
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }
        val monthArrivedAt = painted.states.size - 1

        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        stalled.awaitHeld()
        val loading = painted.awaitLatest { it.isLoading }

        assertEquals(MONTH, loading.stampedView())
        stalled.release()
        painted.awaitLatest { it.stampedView() == AGENDA }
        assertTrue(
            painted.states.drop(monthArrivedAt).all { it.content != null },
            "The switch blanked the screen on its way: ${painted.states.map { it.stampedView() }}",
        )
    }

    @Test
    fun aFailedSwitchPaintsThePreviousScreenBesideTheFailure() = runTest {
        val backend = ViewStampingBackend(agendaFails = { true })
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        val month = painted.awaitLatest { it.stampedView() == MONTH }

        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        val failed = painted.awaitLatest { it.failure != null }

        assertEquals(MONTH, failed.stampedView())
        assertEquals(false, failed.isLoading)
        assertNotNull(month.viewSwitcher)
        assertEquals(month.title, failed.title)
        assertEquals(month.body, failed.body)
        assertEquals(
            month.viewSwitcher,
            failed.viewSwitcher,
            "The failed switch left nothing to switch back with.",
        )
    }

    @Test
    fun selectingAFailedViewAgainRetriesItAndSelectingALoadedOneDoesNot() = runTest {
        var agendaAnswers = 0
        val backend = ViewStampingBackend(agendaFails = { ++agendaAnswers == 1 })
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }
        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        painted.awaitLatest { it.failure != null && !it.isLoading }

        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))

        val agenda = painted.awaitLatest { it.stampedView() == AGENDA }
        assertNull(agenda.failure)
        assertEquals(listOf(MONTH, AGENDA, AGENDA), backend.requestedViews)
        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        advanceUntilIdle()
        assertEquals(
            listOf(MONTH, AGENDA, AGENDA),
            backend.requestedViews,
            "Selecting the view already showing exchanged again.",
        )
    }

    @Test
    fun aFailedSwitchKeepsThePreviousScreenAcrossACollectionRestart() = runTest {
        val backend = ViewStampingBackend(agendaFails = { true })
        val viewModel = backend.monthViewModel(this)
        val beforeBackground = backgroundScope.recordStates(viewModel.state)
        beforeBackground.awaitLatest { it.stampedView() == MONTH }
        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        beforeBackground.awaitLatest { it.failure != null && !it.isLoading }

        // Every collector leaves, as an Android screen's does in the background. The pause lets the
        // view model's sharing — a background task, which `advanceUntilIdle` never waits for —
        // actually stop observing before a collector returns, so the return restarts it.
        beforeBackground.stopCollecting()
        delay(COLLECTION_STOP_PAUSE_MILLIS)
        val afterReturn = backgroundScope.recordStates(viewModel.state)
        afterReturn.awaitLatest { it.isLoading }
        afterReturn.awaitLatest { it.failure != null && !it.isLoading }

        assertEquals(listOf(MONTH, AGENDA, AGENDA), backend.requestedViews)
        assertTrue(
            afterReturn.states.all { it.stampedView() == MONTH },
            "The return lost the screen to switch back with: ${afterReturn.states}",
        )
    }

    @Test
    fun anUpdatedRequestTakesTheFreshDatesZoneAndLocaleInTheViewShowing() = runTest {
        val backend = ViewStampingBackend()
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }
        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        painted.awaitLatest { it.stampedView() == AGENDA }

        viewModel.updateRequest(NEXT_MONTH_ELSEWHERE)

        // The agenda screen already showing satisfies "agenda, settled"; the updated request is
        // only on its way once the screen reports it loading.
        painted.awaitLatest { it.isLoading }
        painted.awaitLatest { it.stampedView() == AGENDA && !it.isLoading }
        assertEquals(listOf(MONTH, AGENDA, AGENDA), backend.requestedViews)
        val updated = backend.requests.last().url.parameters
        assertEquals(NEXT_MONTH_ELSEWHERE.start.toIsoDate(), updated["start"])
        assertEquals(NEXT_MONTH_ELSEWHERE.end.toIsoDate(), updated["end"])
        assertEquals(NEXT_MONTH_ELSEWHERE.timeZone, updated["tz"])
        assertEquals(NEXT_MONTH_ELSEWHERE.locale, updated["locale"])
    }

    @Test
    fun anUnchangedRequestAfterAFailedUpdateRetriesIt() = runTest {
        var agendaAnswers = 0
        val backend = ViewStampingBackend(agendaFails = { ++agendaAnswers == 2 })
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }
        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        painted.awaitLatest { it.stampedView() == AGENDA }
        viewModel.updateRequest(NEXT_MONTH_ELSEWHERE)
        painted.awaitLatest { it.failure != null && !it.isLoading }

        viewModel.updateRequest(NEXT_MONTH_ELSEWHERE)

        painted.awaitLatest { it.failure == null && !it.isLoading }
        assertEquals(listOf(MONTH, AGENDA, AGENDA, AGENDA), backend.requestedViews)
    }

    @Test
    fun aSupersededSwitchIsCancelledRatherThanReportedOrPainted() = runTest {
        val stalled = StalledExchange()
        val backend = ViewStampingBackend(beforeAgendaAnswers = { stalled.hold() })
        val fixture = backend.fixture(this)
        val viewModel = backend.monthViewModel(this, fixture)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }

        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        stalled.awaitHeld()
        viewModel.dispatch(SwitchCalendarViewAction(MonthViewSelection))
        painted.awaitLatest { it.stampedView() == MONTH && !it.isLoading }
        stalled.release()

        // The abandoned exchange still lands — exchanges outlive the waits that started them — so
        // waiting for it is what makes "never painted" evidence rather than a race it won early.
        backgroundScope
            .recordStates(
                fixture.calendarScreenRepository.observeCalendarScreen(
                    monthScreenRequest(view = RequestedCalendarView.Agenda)
                )
            )
            .awaitLatest { it is CalendarScreenQueryState.Loaded }
        advanceUntilIdle()

        assertTrue(
            painted.states.none { it.failure != null || it.stampedView() == AGENDA },
            "The superseded switch reached the screen: ${painted.states}",
        )
    }

    @Test
    fun aRequestWhoseOwnScreenWasDisownedIsNotPaintedWithThePreviousOne() = runTest {
        var agendaAnswers = 0
        val backend = ViewStampingBackend(agendaFails = { ++agendaAnswers > 1 })
        val fixture = backend.fixture(this)
        val viewModel = backend.monthViewModel(this, fixture)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }
        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        painted.awaitLatest { it.stampedView() == AGENDA }

        fixture.calendarScreenRepository.invalidateCalendarScreens {
            it.view == RequestedCalendarView.Agenda
        }
        val failed = painted.awaitLatest { it.failure != null }

        assertNull(
            failed.content,
            "An invalidated request was painted with the screen that preceded it.",
        )
    }

    @Test
    fun aTimeGridDayCountTheContractRejectsLeavesTheViewWhereItIs() = runTest {
        val backend = ViewStampingBackend()
        val viewModel = backend.monthViewModel(this)
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.stampedView() == MONTH }

        assertNull(
            viewModel.dispatch(
                SwitchCalendarViewAction(TimeGridViewSelection(DAY_COUNT_BELOW_CONTRACT))
            )
        )
        assertNull(
            viewModel.dispatch(
                SwitchCalendarViewAction(TimeGridViewSelection(DAY_COUNT_ABOVE_CONTRACT))
            )
        )

        // A later switch that does exchange puts any request the rejected ones caused on record
        // ahead of its own, so the sequence below is evidence that they caused none.
        viewModel.dispatch(SwitchCalendarViewAction(AgendaViewSelection))
        painted.awaitLatest { it.stampedView() == AGENDA }
        assertEquals(listOf(MONTH, AGENDA), backend.requestedViews)
    }
}

/** The fixture request as a presenter would ask for it a month on, from another zone and locale. */
private val NEXT_MONTH_ELSEWHERE =
    monthScreenRequest()
        .copy(
            start = CalendarDate(year = 2026, month = 5, dayOfMonth = 1),
            end = CalendarDate(year = 2026, month = 5, dayOfMonth = 31),
            timeZone = "Europe/London",
            locale = "en-GB",
        )

/** Long enough, in virtual time, for a collection that lost its last collector to stop. */
private const val COLLECTION_STOP_PAUSE_MILLIS = 1L

private const val MONTH = "month"
private const val AGENDA = "agenda"

/** Time-grid day counts either side of the contract's 1–7, which decoding accepts all the same. */
private const val DAY_COUNT_BELOW_CONTRACT = 0
private const val DAY_COUNT_ABOVE_CONTRACT = 8

/**
 * A backend answering every view with the contract fixture stamped with the requested view in its
 * `serverTime`, so a painted state names which request's screen it is.
 *
 * @param beforeAgendaAnswers suspends an agenda answer, to hold a switch in flight.
 * @param agendaFails decides, per agenda request, whether to answer it with a server error instead.
 */
private class ViewStampingBackend(
    private val beforeAgendaAnswers: suspend () -> Unit = {},
    private val agendaFails: () -> Boolean = { false },
) {

    private val recorded = mutableListOf<HttpRequestData>()

    val requests: List<HttpRequestData>
        get() = recorded

    val requestedViews: List<String?>
        get() = recorded.map { it.url.parameters["view"] }

    fun fixture(scope: TestScope): CalendarScreenQueryFixture =
        CalendarScreenQueryFixture(scope.backgroundScope, scope.testScheduler) { request ->
            recorded += request
            answer(request)
        }

    fun monthViewModel(
        scope: TestScope,
        fixture: CalendarScreenQueryFixture = fixture(scope),
    ): CalendarViewModel =
        CalendarViewModel(
            calendarScreenRepository = fixture.calendarScreenRepository,
            request = monthScreenRequest(),
            scope = scope.presentationScope(),
        )

    private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val view = request.url.parameters["view"].orEmpty()
        if (view == AGENDA) {
            beforeAgendaAnswers()
            if (agendaFails()) return respondJson("{}", status = HttpStatusCode.ServiceUnavailable)
        }
        return respondJson(
            monthScreenFixtureJson().replacing("serverTime", JsonPrimitive(view)).toString()
        )
    }
}

/** The view the painted screen was answered for, or `null` when no screen is painted. */
private fun CalendarUiState.stampedView(): String? = content?.serverTime
