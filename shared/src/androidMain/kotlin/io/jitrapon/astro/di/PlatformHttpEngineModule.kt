package io.jitrapon.astro.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android runs Ktor on OkHttp, the platform's established HTTP stack. */
internal actual val platformHttpEngineModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
}
