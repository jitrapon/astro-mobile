package io.jitrapon.astro.di

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Drives the facade Swift calls, on the target Swift calls it from.
 *
 * The iOS half of the graph is otherwise only exercised by launching the app: the Darwin engine
 * `actual` and every binding above it are resolved for the first time at runtime, and Koin
 * validates nothing at compile time. Running the same start / resolve / stop sequence here puts
 * that path under a test CI already runs.
 */
class DependencyGraphTest {

    @AfterTest
    fun stopGraph() {
        DependencyGraph.stop()
    }

    @Test
    fun resolvesTheCalendarScreenRepositoryFromTheStartedGraph() {
        DependencyGraph.start(baseUrl = UNREACHABLE_BASE_URL)

        val repository = DependencyGraph.calendarScreenRepository()

        // Resolving twice must hand back the one instance. The repository sits above an HTTP client
        // that owns a connection pool, so a second construction here would mean every caller on iOS
        // silently gets a pool of its own.
        assertSame(repository, DependencyGraph.calendarScreenRepository())
    }

    @Test
    fun startsAgainAfterBeingStopped() {
        DependencyGraph.start(baseUrl = UNREACHABLE_BASE_URL)
        DependencyGraph.stop()

        DependencyGraph.start(baseUrl = UNREACHABLE_BASE_URL)

        // A stop that left Koin's global context populated would make this second start throw, and
        // the failure would surface as an unrelated test's crash rather than here.
        DependencyGraph.calendarScreenRepository()
    }

    private companion object {
        /**
         * Nothing here sends a request, so the origin only has to be a well-formed absolute URL.
         * `.invalid` is reserved by RFC 2606 and never resolves, so a binding that started making
         * calls would fail loudly instead of reaching some real host.
         */
        const val UNREACHABLE_BASE_URL = "https://backend.invalid/api"
    }
}
