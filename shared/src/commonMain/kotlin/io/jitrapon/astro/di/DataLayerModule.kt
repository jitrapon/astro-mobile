package io.jitrapon.astro.di

import io.jitrapon.astro.data.Result
import io.jitrapon.astro.data.calendar.CalendarScreenApi
import io.jitrapon.astro.data.calendar.CalendarScreenExchangeKey
import io.jitrapon.astro.data.calendar.CalendarScreenQuery
import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.jitrapon.astro.data.calendar.CalendarScreenRequest
import io.jitrapon.astro.data.calendar.CalendarScreenResponse
import io.jitrapon.astro.data.calendar.SUPPORTED_SCHEMA_VERSION
import io.jitrapon.astro.data.network.createBackendHttpClient
import io.jitrapon.astro.data.network.createLenientBackendJson
import io.jitrapon.astro.data.query.InMemoryScreenCache
import io.jitrapon.astro.data.query.MonotonicTicker
import io.jitrapon.astro.data.query.ScreenCache
import io.jitrapon.astro.data.query.ScreenCachePolicy
import io.jitrapon.astro.data.query.SingleFlightRunner
import io.jitrapon.astro.data.query.StalenessWindowScreenCachePolicy
import io.jitrapon.astro.data.query.Ticker
import io.ktor.client.HttpClient
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * How long a remembered calendar screen still answers for its request without an exchange.
 *
 * Short enough that a screen left open and returned to does not show yesterday's agenda, long
 * enough that moving between views and back — the navigation a calendar gets most of — is answered
 * from memory rather than from the network. It bounds only how long a screen is served *without*
 * asking; past it the screen is still painted, now under an in-flight flag, so crossing the window
 * costs a round trip and never a blank screen.
 */
private const val REMEMBERED_SCREEN_STALENESS_MINUTES: Int = 5

/**
 * How many screens are remembered before the least recently written one is dropped.
 *
 * A session moves between a handful of ranges and views, and each entry holds one decoded response
 * — so the bound exists to stop unbounded growth over a long session, not to ration a scarce
 * resource. Age never evicts: an aged entry is exactly what lets a refresh paint something.
 */
private const val MAX_REMEMBERED_SCREENS: Int = 32

/**
 * Declares the data layer's bindings against [baseUrl] — the JSON codec, the HTTP client, the
 * calendar screen API client, the observation machinery above it, and the repository that fronts
 * the whole thing.
 *
 * Every binding is a `single`: the codec compiles a serializers cache and the client owns a
 * connection pool, so rebuilding either per request would pay that cost on every call, and the
 * cache, the in-flight table and the observed states are shared state whose whole purpose is
 * defeated by a second copy. The client is closed and the layer's scope cancelled when the graph is
 * torn down, since nothing else holds a reference that could release them afterwards.
 *
 * [baseUrl] is captured here rather than read from a constant inside the API client because origin
 * and scheme belong to the environment the app was built for; a default baked into common code
 * would ship one environment's host to all of them.
 *
 * The engine this client runs on is not declared here — it comes from [platformHttpEngineModule],
 * which is the only part of the graph that differs per platform.
 *
 * This is the one file allowed to name a concrete dispatcher. Everything below takes one as a
 * dependency so a test can substitute a `TestDispatcher` and settle under virtual time; naming
 * `Dispatchers.Default` anywhere but a composition root is what makes that impossible.
 */
internal fun dataLayerModule(baseUrl: String): Module = module {
    single<Json> { createLenientBackendJson() }
    single<HttpClient> { createBackendHttpClient(engine = get(), json = get()) }
        .withOptions { onClose { it?.close() } }
    single { CalendarScreenApi(httpClient = get(), baseUrl = baseUrl) }
    // `Default` rather than a single-threaded dispatcher: the work hosted on the scope below is a
    // round trip plus a few map operations already serialised by the layer's own mutexes, and
    // `Dispatchers.IO` does not exist on Kotlin/Native.
    single<CoroutineDispatcher> { Dispatchers.Default }
    // The scope every exchange runs on. A `SupervisorJob` so one request's failed exchange does not
    // tear down another's, and cancelled on teardown because an exchange deliberately outlives the
    // collector that triggered it — nothing else would ever stop one.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }
        .withOptions { onClose { it?.cancel() } }
    single<Ticker> { MonotonicTicker() }
    single<ScreenCache<CalendarScreenRequest, CalendarScreenResponse>> {
        InMemoryScreenCache(
            ticker = get(),
            storedUnderSchemaVersion = SUPPORTED_SCHEMA_VERSION,
            maxEntries = MAX_REMEMBERED_SCREENS,
        )
    }
    single<ScreenCachePolicy> {
        StalenessWindowScreenCachePolicy(
            supportedSchemaVersion = SUPPORTED_SCHEMA_VERSION,
            stalenessWindow = REMEMBERED_SCREEN_STALENESS_MINUTES.minutes,
        )
    }
    single { SingleFlightRunner<CalendarScreenExchangeKey, Result<CalendarScreenResponse>>(get()) }
    single {
        CalendarScreenQuery(
            calendarScreenApi = get(),
            screenCache = get(),
            screenCachePolicy = get(),
            singleFlightRunner = get(),
            ticker = get(),
            scope = get(),
        )
    }
    single { CalendarScreenRepository(calendarScreenApi = get(), calendarScreenQuery = get()) }
}
