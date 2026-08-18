package io.jitrapon.astro.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/** iOS runs Ktor on Darwin, which is NSURLSession — the system's own networking stack. */
internal actual val platformHttpEngineModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
        // Closed here because nothing else will: an `HttpClient` built on an engine it was
        // handed treats that engine as externally owned, so closing the client leaves the engine
        // running. Without this, tearing the graph down and starting it again — which anything
        // that starts a graph more than once in a process must do — would strand the previous
        // engine's NSURLSession alive for the rest of the process.
        .withOptions { onClose { it?.close() } }
}
