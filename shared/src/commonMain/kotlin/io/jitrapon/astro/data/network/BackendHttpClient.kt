package io.jitrapon.astro.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
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
 * `internal` because the dependency graph is the only thing that should ever build one, and because
 * a public factory taking Ktor types would put them on the iOS framework's generated surface, where
 * a Swift call site could bind to them.
 */
internal fun createBackendHttpClient(engine: HttpClientEngine, json: Json): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(json) } }
