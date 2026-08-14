package io.jitrapon.astro.di

import io.jitrapon.astro.data.calendar.CalendarScreenApi
import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
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
        assertSame(graph.get<CalendarScreenRepository>(), graph.get<CalendarScreenRepository>())
    }
}

/**
 * Nothing here sends a request, so the origin only has to be a well-formed absolute URL. `.invalid`
 * is reserved by RFC 2606 and never resolves, so a binding that started making calls would fail
 * loudly instead of reaching some real host.
 */
private const val UNREACHABLE_BASE_URL = "https://backend.invalid/api"
