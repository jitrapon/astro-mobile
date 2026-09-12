package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.query.InMemoryScreenCache
import io.jitrapon.astro.data.query.SingleFlightRunner
import io.jitrapon.astro.data.query.StalenessWindowScreenCachePolicy
import io.jitrapon.astro.data.query.VirtualTimeTicker
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.JsonPrimitive

/**
 * A [CalendarScreenQuery] assembled over a stubbed backend and a virtual clock, which is what makes
 * both of the things this layer decides observable at all: [exchanges] counts the round trips it
 * actually caused, and the clock is driven by the test rather than by waiting.
 *
 * Everything below the query is the production part — the real cache, the real policy, the real
 * single-flight runner, the real API client over Ktor's mock engine. Substituting a double for any
 * of them would move the behaviour under test into the test.
 *
 * @param scope the scope the query hosts exchanges on. Pass `backgroundScope`: exchanges outlive
 *   the collectors that triggered them by design, so hanging them off the test's own job would make
 *   the test wait for work it has deliberately abandoned.
 * @param stalenessWindow how long a remembered screen stands. The default is long enough that a
 *   case which never advances the clock never crosses it by accident.
 * @param respondTo what the backend answers with. Suspending, so a case can hold an exchange open
 *   and inspect what is published while it is in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CalendarScreenQueryFixture(
    scope: CoroutineScope,
    scheduler: TestCoroutineScheduler,
    stalenessWindow: Duration = 5.minutes,
    respondTo: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respondWithMonthScreenFixture()
    },
) {

    private val backend = MockCalendarBackend(respondTo = respondTo)

    private val ticker = VirtualTimeTicker(scheduler)

    /** How many exchanges the query has caused — the count deduplication has to hold down. */
    val exchanges: Int
        get() = backend.requests.size

    val query: CalendarScreenQuery =
        CalendarScreenQuery(
            calendarScreenApi = backend.calendarScreenApi,
            screenCache =
                InMemoryScreenCache(
                    ticker = ticker,
                    storedUnderSchemaVersion = SUPPORTED_SCHEMA_VERSION,
                    maxEntries = MAX_REMEMBERED_SCREENS,
                ),
            screenCachePolicy =
                StalenessWindowScreenCachePolicy(
                    supportedSchemaVersion = SUPPORTED_SCHEMA_VERSION,
                    stalenessWindow = stalenessWindow,
                ),
            singleFlightRunner = SingleFlightRunner(scope),
            ticker = ticker,
            scope = scope,
        )

    /**
     * The repository fronting [query], assembled the way the graph assembles it.
     *
     * It lives here rather than beside the stubbed backend because a repository is only complete
     * once there is a query beneath it, and a query needs the scope and clock this fixture owns.
     */
    val calendarScreenRepository: CalendarScreenRepository =
        CalendarScreenRepository(
            calendarScreenApi = backend.calendarScreenApi,
            calendarScreenQuery = query,
        )

    private companion object {
        /** Comfortably more than any case observes, so capacity eviction never confounds one. */
        const val MAX_REMEMBERED_SCREENS = 8
    }
}

/**
 * An exchange held open until the test releases it.
 *
 * Holding one is the only way to observe what an observation publishes *while* the network is busy
 * — the stale-screen-plus-spinner state, a second observer joining a running exchange, a collector
 * cancelled mid-flight. A case that lets the backend answer immediately can only ever see the
 * outcome.
 */
internal class StalledExchange {

    private val gate = CompletableDeferred<Unit>()

    private val reached = CompletableDeferred<Unit>()

    /** Suspends the backend's answer until [release]; returns immediately once released. */
    suspend fun hold() {
        reached.complete(Unit)
        gate.await()
    }

    /**
     * Suspends until the backend has reached [hold] on an exchange, so a case can act while one is
     * provably in flight.
     *
     * What this buys is ordering the exchanges themselves, which no amount of draining the test
     * scheduler can: an exchange travels to the stub on a real dispatcher, so a case that acts
     * without waiting for this has not established that the exchange it means to overtake has even
     * started.
     */
    suspend fun awaitHeld() {
        reached.await()
    }

    /** Lets every held answer through, and every later one through without holding. */
    fun release() {
        gate.complete(Unit)
    }
}

/**
 * Every state one collector received, in order, alongside the handle that stops it collecting.
 *
 * Order is what most of these cases assert on: "painted the remembered screen, then the fresh one"
 * is a claim about a sequence, and the final state alone cannot tell it apart from having painted
 * nothing until the exchange landed.
 *
 * The recording is itself a [StateFlow] so a case can *wait* for a state rather than pump a
 * scheduler for it. That is not a convenience: an exchange travels through Ktor's engine on a real
 * dispatcher, so draining the test scheduler says nothing about whether the backend has answered,
 * and a case that asserted right afterwards would be asserting on a half-finished exchange.
 */
internal class StateRecording(
    private val recorded: StateFlow<List<CalendarScreenQueryState>>,
    private val collector: Job,
) {

    /** Every state delivered so far, none of them lost to conflation. */
    val states: List<CalendarScreenQueryState>
        get() = recorded.value

    /** The most recent state, failing the test when nothing has been published at all. */
    val latest: CalendarScreenQueryState
        get() = recorded.value.lastOrNull() ?: fail("The observation published nothing.")

    /**
     * Suspends until the most recent state satisfies [predicate], and returns it.
     *
     * Deliberately carries no deadline of its own: `runTest` already fails a test whose body stops
     * making progress, while a deadline expressed in virtual time would fire on a test scheduler
     * that is merely idle because a real dispatcher is doing the work.
     */
    suspend fun awaitLatest(
        predicate: (CalendarScreenQueryState) -> Boolean
    ): CalendarScreenQueryState = recorded.mapNotNull { it.lastOrNull() }.first(predicate)

    /** Ends the collection, the way a caller's scope ending would. */
    fun stopCollecting() {
        collector.cancel()
    }
}

/**
 * Collects [states] into a [StateRecording] on this scope.
 *
 * Collected on a scope that outlives the test body — `backgroundScope` — because an observation
 * never completes: collecting it from the test's own job would leave the test waiting forever for a
 * flow that is designed never to end.
 */
internal fun CoroutineScope.recordStates(states: Flow<CalendarScreenQueryState>): StateRecording {
    val recorded = MutableStateFlow<List<CalendarScreenQueryState>>(emptyList())
    val collector = launch { states.collect { state -> recorded.update { it + state } } }
    return StateRecording(recorded, collector)
}

/** The zone [monthScreenRequest] asks for, and so the one its screen must come back in. */
internal const val BANGKOK_TIME_ZONE: String = "Asia/Bangkok"

/**
 * A second zone, which makes a second request identity that must never share the first's screen.
 */
internal const val ZURICH_TIME_ZONE: String = "Europe/Zurich"

/**
 * A distinct server clock per exchange, so a case can name which exchange's screen it is looking at
 * rather than only that a screen arrived.
 */
internal fun serverTimeOfExchange(nth: Int): String = "2026-04-15T0$nth:00:00Z"

/**
 * Answers with the month-screen fixture, with [serverTime] substituted so responses are telling
 * apart.
 */
internal fun MockRequestHandleScope.respondWithServerTime(serverTime: String): HttpResponseData =
    respondJson(
        monthScreenFixtureJson().replacing("serverTime", JsonPrimitive(serverTime)).toString()
    )

/**
 * Answers with the month-screen fixture, echoing [zone] back into the envelope alongside
 * [serverTime].
 *
 * Both substitutions are needed together whenever a case runs more than one request: the zone says
 * which request a published screen belongs to, the clock says which exchange produced it, and
 * neither answers the other's question.
 */
internal fun MockRequestHandleScope.respondWithZoneAndServerTime(
    zone: String,
    serverTime: String,
): HttpResponseData =
    respondJson(
        monthScreenFixtureJson()
            .replacing("timeZone", JsonPrimitive(zone))
            .replacing("serverTime", JsonPrimitive(serverTime))
            .toString()
    )

/**
 * The screen [state] is showing, failing the test — naming what it held instead — when it is not
 * showing one.
 */
internal fun loadedResponse(state: CalendarScreenQueryState): CalendarScreenResponse =
    when (state) {
        is CalendarScreenQueryState.Loaded -> state.response
        is CalendarScreenQueryState.Pending -> fail("Expected a loaded screen; nothing had loaded.")
        is CalendarScreenQueryState.Failed ->
            fail("Expected a loaded screen, but the observation had failed.", state.error)
    }

/** The failure [state] reported, failing the test when it did not report one. */
internal fun reportedObservationFailure(
    state: CalendarScreenQueryState
): CalendarScreenQueryState.Failed =
    state as? CalendarScreenQueryState.Failed
        ?: fail("Expected the observation to report a failure, but it held $state.")
