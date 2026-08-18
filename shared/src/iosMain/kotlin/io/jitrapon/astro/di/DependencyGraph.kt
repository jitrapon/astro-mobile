package io.jitrapon.astro.di

import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatformTools

/**
 * The iOS app's entire view of the shared dependency graph: it starts the graph here and asks this
 * object for what it needs.
 *
 * Every function below takes and returns types the shared module owns, so neither Koin nor Ktor
 * appears in the generated framework header and no Swift call site can bind to either. Swapping the
 * DI container or the HTTP engine then stays a change inside this module rather than an iOS-app
 * refactor.
 *
 * Android needs no counterpart — it calls [initKoin] from its `Application` and resolves in Kotlin,
 * where nothing crosses a framework boundary in the first place.
 */
object DependencyGraph {

    /**
     * Starts the graph against the backend at [baseUrl]. Call once, at app launch, before anything
     * resolves from it.
     */
    fun start(baseUrl: String) {
        initKoin(baseUrl)
    }

    /** Resolves the calendar screen's data boundary from the running graph. */
    fun calendarScreenRepository(): CalendarScreenRepository =
        KoinPlatformTools.defaultContext().get().get()

    /**
     * Tears the graph down, closing the HTTP client and its connection pool with it.
     *
     * The app itself never calls this — the process ends first — but anything that starts a graph
     * more than once in a single process must, because Koin's context is global and [start] fails
     * against an already-running container.
     */
    fun stop() {
        stopKoin()
    }
}
