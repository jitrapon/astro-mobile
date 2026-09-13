package io.jitrapon.astro.di

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import org.koin.mp.KoinPlatformTools

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
    fun handsEveryScreenTheSameCalendarScreenObserver() {
        DependencyGraph.start(baseUrl = UNREACHABLE_BASE_URL)

        // One observer for the whole app: it holds the delivery scope every subscription is a child
        // of, so a second instance would be a second set of subscriptions teardown could not reach.
        assertSame(
            DependencyGraph.calendarScreenObserver(),
            DependencyGraph.calendarScreenObserver(),
        )
    }

    @Test
    fun stoppingTheGraphEndsEverySubscriptionsDeliveryScope() {
        DependencyGraph.start(baseUrl = UNREACHABLE_BASE_URL)
        DependencyGraph.calendarScreenObserver()
        val deliveryScope =
            KoinPlatformTools.defaultContext().get().get<CoroutineScope>(MAIN_THREAD_DELIVERY_SCOPE)

        DependencyGraph.stop()

        // A subscription Swift forgot to cancel must still stop when the graph that served it goes
        // away; left running, it would keep delivering screens from a data layer that is closed.
        assertFalse(deliveryScope.isActive, "The delivery scope outlived the graph.")
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
