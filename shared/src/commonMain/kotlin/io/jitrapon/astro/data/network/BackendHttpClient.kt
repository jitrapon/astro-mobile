package io.jitrapon.astro.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the HTTP client every backend call travels over, on the platform's own networking stack.
 *
 * [engine] is a parameter rather than a choice made here: the engine is the one piece of the
 * transport that cannot be common code, so each platform contributes its own (OkHttp on Android,
 * NSURLSession through Darwin on iOS) and everything about the client's behaviour stays shared.
 *
 * [json] is likewise passed in so that the codec the content-negotiation plugin decodes through is
 * the same instance the rest of the data layer uses, rather than a second one configured by
 * accident with different leniency.
 *
 * Non-2xx responses are deliberately left to the caller: Ktor's `expectSuccess` stays off so a 404
 * or a 500 arrives as an ordinary response that `CalendarScreenApi` turns into a `Result.Error`
 * carrying its status, instead of a transport-level throw that loses which status came back.
 *
 * [deadlines] bounds a request that stalls, here rather than in the engine. Both engines impose
 * deadlines of their own, but different ones, so leaving them unset would not mean "no timeout" —
 * it would mean the same stalled backend gives up after seconds on one platform and around a minute
 * on the other, which is exactly the kind of divergence sharing this layer exists to remove. An
 * abandoned request fails, and `CalendarScreenApi` reports it like any other transport failure. It
 * is a parameter only so a test can stall a backend without waiting out the real deadline; nothing
 * in production passes anything but the default.
 *
 * `internal` because the dependency graph is the only thing that should ever build one, and because
 * a public factory taking Ktor types would put them on the iOS framework's generated surface, where
 * a Swift call site could bind to them.
 */
internal fun createBackendHttpClient(
    engine: HttpClientEngine,
    json: Json,
    deadlines: BackendRequestDeadlines = BackendRequestDeadlines(),
): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = deadlines.requestMillis
            connectTimeoutMillis = deadlines.connectMillis
            socketTimeoutMillis = deadlines.socketMillis
        }
    }

/**
 * How long a backend call may take before it is abandoned, in each of the three ways it can stall.
 *
 * The defaults are the shipped policy; they are generous on purpose. These bound a hung exchange so
 * a screen cannot load forever — they do not enforce a latency target, and a first load over a slow
 * cellular link legitimately takes many seconds. Revisit them when there is a real target to
 * enforce: a screen's loading and retry behaviour belongs to whatever presents it, and nothing
 * presents one yet.
 */
internal data class BackendRequestDeadlines(
    /** The whole exchange, from request to decoded response. */
    val requestMillis: Long = 30_000L,
    /**
     * Establishing the connection alone. Shorter than [requestMillis] because failing to reach a
     * host at all is answered quickly — an unreachable backend or a captive portal should surface
     * as an error rather than consume the whole request budget getting there.
     */
    val connectMillis: Long = 10_000L,
    /**
     * A single silence between bytes. Bounds the case [requestMillis] alone would not catch
     * cheaply: a connection that stays open and trickles nothing.
     */
    val socketMillis: Long = 30_000L,
)
