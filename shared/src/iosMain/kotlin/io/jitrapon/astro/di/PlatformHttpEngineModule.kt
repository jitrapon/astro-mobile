package io.jitrapon.astro.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS runs Ktor on Darwin, which is NSURLSession — the system's own networking stack. */
internal actual val platformHttpEngineModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
}
