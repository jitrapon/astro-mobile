package io.jitrapon.astro.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module

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
    startDependencyGraph(baseUrl)
}

/**
 * Starts the graph [initKoin] starts, plus [platformModules] — the bindings only one platform's app
 * consumes.
 *
 * Loaded in the same start rather than added afterwards, so there is no instant at which the graph
 * is running without them and a teardown closes them alongside everything else. Internal because
 * [Module] is Koin's type and must not reach the framework surface.
 */
internal fun startDependencyGraph(baseUrl: String, platformModules: List<Module> = emptyList()) {
    startKoin {
        modules(listOf(platformHttpEngineModule, dataLayerModule(baseUrl)) + platformModules)
    }
}
