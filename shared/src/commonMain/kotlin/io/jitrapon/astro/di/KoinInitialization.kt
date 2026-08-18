package io.jitrapon.astro.di

import org.koin.core.context.startKoin

/**
 * Starts the shared dependency graph against the backend at [baseUrl]. Each platform calls this
 * once at app launch and then resolves what it needs; neither restates the graph, so a binding
 * added here reaches Android and iOS at the same time.
 *
 * Returns nothing on purpose. Handing back Koin's application object would put the DI library on
 * the shared framework's public surface, which is exactly what the iOS side is kept away from —
 * Swift initializes and resolves through this module's own facade, never through Koin itself.
 */
fun initKoin(baseUrl: String) {
    startKoin { modules(platformHttpEngineModule, dataLayerModule(baseUrl)) }
}
