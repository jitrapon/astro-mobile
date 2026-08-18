package io.jitrapon.astro.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/** Android runs Ktor on OkHttp, the platform's established HTTP stack. */
internal actual val platformHttpEngineModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
        // Closed here because nothing else will: an `HttpClient` built on an engine it was
        // handed treats that engine as externally owned, so closing the client leaves the engine
        // running. Without this, tearing the graph down and starting it again — which anything
        // that starts a graph more than once in a process must do — would strand the previous
        // engine's dispatcher and connection pool alive for the rest of the process.
        .withOptions { onClose { it?.close() } }
}
