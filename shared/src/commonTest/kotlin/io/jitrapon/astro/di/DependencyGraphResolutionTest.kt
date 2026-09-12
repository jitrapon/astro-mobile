package io.jitrapon.astro.di

import io.jitrapon.astro.data.Result
import io.jitrapon.astro.data.calendar.CalendarScreenApi
import io.jitrapon.astro.data.calendar.CalendarScreenExchangeKey
import io.jitrapon.astro.data.calendar.CalendarScreenQuery
import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.jitrapon.astro.data.calendar.CalendarScreenRequest
import io.jitrapon.astro.data.calendar.CalendarScreenResponse
import io.jitrapon.astro.data.query.ScreenCache
import io.jitrapon.astro.data.query.ScreenCachePolicy
import io.jitrapon.astro.data.query.SingleFlightRunner
import io.jitrapon.astro.data.query.Ticker
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatformTools

/**
 * Resolves every binding the shared graph declares, on each platform the graph runs on.
 *
 * This is the price of a container that resolves at runtime: nothing fails at compile time when a
 * binding is missing or its dependency is unsatisfiable, so a graph nobody exercises is a graph
 * whose first failure is an app that dies at launch. Resolving each declaration here — including
 * the platform engine, which differs per target — moves that failure into a test both CI halves
 * run.
 *
 * Each binding is resolved twice and required to hand back the one instance. The client owns a
 * connection pool and the codec compiles a serializer cache, so a declaration that stopped being a
 * singleton would keep every test passing while giving each caller in production a pool of its own.
 */
class DependencyGraphResolutionTest {

    @AfterTest
    fun stopGraph() {
        stopKoin()
    }

    @Test
    fun resolvesEveryDeclarationToASingleInstance() {
        initKoin(baseUrl = UNREACHABLE_BASE_URL)
        // The running container, reached the way the iOS facade reaches it — the accessor that
        // resolves on every target, rather than one that compiles only on the JVM.
        val graph = KoinPlatformTools.defaultContext().get()

        assertSame(graph.get<Json>(), graph.get<Json>())
        assertSame(graph.get<HttpClient>(), graph.get<HttpClient>())
        assertSame(graph.get<CalendarScreenApi>(), graph.get<CalendarScreenApi>())
        assertSame(graph.get<CoroutineDispatcher>(), graph.get<CoroutineDispatcher>())
        assertSame(graph.get<CoroutineScope>(), graph.get<CoroutineScope>())
        assertSame(graph.get<Ticker>(), graph.get<Ticker>())
        // Singletons here are not a cost decision but a correctness one: a second cache, in-flight
        // table or observation map would remember screens nothing else can read and deduplicate
        // exchanges against a table no other caller shares.
        assertSame(
            graph.get<ScreenCache<CalendarScreenRequest, CalendarScreenResponse>>(),
            graph.get<ScreenCache<CalendarScreenRequest, CalendarScreenResponse>>(),
        )
        assertSame(graph.get<ScreenCachePolicy>(), graph.get<ScreenCachePolicy>())
        assertSame(
            graph.get<
                SingleFlightRunner<CalendarScreenExchangeKey, Result<CalendarScreenResponse>>
            >(),
            graph.get<
                SingleFlightRunner<CalendarScreenExchangeKey, Result<CalendarScreenResponse>>
            >(),
        )
        assertSame(graph.get<CalendarScreenQuery>(), graph.get<CalendarScreenQuery>())
        assertSame(graph.get<CalendarScreenRepository>(), graph.get<CalendarScreenRepository>())
    }

    /**
     * A graph that is torn down must stop the scope its exchanges run on.
     *
     * Nothing else can. An exchange is hosted on the data layer's own scope precisely so it
     * outlives the collector that triggered it — a collector going away abandons its wait and
     * cancels nothing — which leaves the scope's own cancellation as the only thing that ever stops
     * one. A graph that dropped its scope without cancelling it would leak a live `SupervisorJob`,
     * and with it any exchange still in flight, per start/stop cycle.
     */
    @Test
    fun cancelsTheDataLayerScopeWhenTheGraphIsTornDown() {
        initKoin(baseUrl = UNREACHABLE_BASE_URL)
        val graph = KoinPlatformTools.defaultContext().get()
        val scope = graph.get<CoroutineScope>()

        stopKoin()

        assertFalse(scope.isActive, "the graph left its data-layer scope running after teardown")
    }

    /**
     * A graph that is torn down must release the transport it was holding, on both platforms.
     *
     * The engine needs its own close callback and cannot inherit one from the client above it: an
     * `HttpClient` built on an engine it was handed treats that engine as externally owned, so
     * closing the client leaves the engine — and the dispatcher, connection pool, or NSURLSession
     * underneath it — running. That costs nothing in an app, which stops the graph by ending the
     * process, and leaks one engine per cycle in anything that starts a graph repeatedly.
     *
     * The engine's coroutine scope is what makes the release observable: an engine is a
     * `CoroutineScope` whose job is completed by `close`, and neither engine exposes a closed flag.
     */
    @Test
    fun releasesThePlatformEngineWhenTheGraphIsTornDown() {
        initKoin(baseUrl = UNREACHABLE_BASE_URL)
        val graph = KoinPlatformTools.defaultContext().get()
        val engine = graph.get<HttpClientEngine>()
        // Built here so teardown has to release an engine the client is actually running on, which
        // is the arrangement that makes the engine externally owned in the first place.
        graph.get<HttpClient>()

        stopKoin()

        assertFalse(engine.isActive, "the graph left its HTTP engine running after teardown")
    }
}

/**
 * Nothing here sends a request, so the origin only has to be a well-formed absolute URL. `.invalid`
 * is reserved by RFC 2606 and never resolves, so a binding that started making calls would fail
 * loudly instead of reaching some real host.
 */
private const val UNREACHABLE_BASE_URL = "https://backend.invalid/api"
