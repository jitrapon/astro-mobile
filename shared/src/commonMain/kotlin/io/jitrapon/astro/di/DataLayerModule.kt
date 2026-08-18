package io.jitrapon.astro.di

import io.jitrapon.astro.data.calendar.CalendarScreenApi
import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.jitrapon.astro.data.network.createBackendHttpClient
import io.jitrapon.astro.data.network.createLenientBackendJson
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * Declares the data layer's bindings against [baseUrl] — the JSON codec, the HTTP client, the
 * calendar screen API client, and the repository above it.
 *
 * Every binding is a `single`: the codec compiles a serializers cache and the client owns a
 * connection pool, so rebuilding either per request would pay that cost on every call. The client
 * is closed when the graph is torn down, since nothing else holds a reference that could release
 * its pool afterwards.
 *
 * [baseUrl] is captured here rather than read from a constant inside the API client because origin
 * and scheme belong to the environment the app was built for; a default baked into common code
 * would ship one environment's host to all of them.
 *
 * The engine this client runs on is not declared here — it comes from [platformHttpEngineModule],
 * which is the only part of the graph that differs per platform.
 */
internal fun dataLayerModule(baseUrl: String): Module = module {
    single<Json> { createLenientBackendJson() }
    single<HttpClient> { createBackendHttpClient(engine = get(), json = get()) }
        .withOptions { onClose { it?.close() } }
    single { CalendarScreenApi(httpClient = get(), baseUrl = baseUrl) }
    single { CalendarScreenRepository(calendarScreenApi = get()) }
}
