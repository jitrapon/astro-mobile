package io.jitrapon.astro.data.network

import kotlinx.serialization.json.Json

/**
 * The JSON codec every production decode of a backend response goes through.
 *
 * Unknown keys are **ignored**: the backend may add a field to a response at any time, and a
 * shipped client that has already left the store must keep working when it does. The cost of that
 * leniency is that an unmodelled field is dropped silently rather than reported, which is why two
 * other mechanisms sit beside it — `CalendarScreenApi` rejects any envelope whose `schemaVersion`
 * is not the one these models were written against, and the contract tests decode the vendored
 * example fixture through a **strict** codec of their own so an unmodelled field fails a test
 * instead of reaching production unnoticed.
 *
 * Returned as a fresh instance rather than a shared singleton so the DI graph owns its lifetime; a
 * caller wanting one instance should register this once, not call it per request — building a
 * [Json] compiles a serializers cache that is wasteful to rebuild.
 */
fun createLenientBackendJson(): Json = Json { ignoreUnknownKeys = true }
