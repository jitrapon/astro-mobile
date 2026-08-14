package io.jitrapon.astro.di

import org.koin.core.module.Module

/**
 * The one binding the shared graph cannot declare in common code: the Ktor `HttpClientEngine` that
 * each platform's own networking stack provides, which `createBackendHttpClient` then runs on.
 *
 * Kept `internal` on purpose. Koin types must not reach the iOS framework's public surface — Swift
 * initializes the graph through the shared module's own facade, so a call site there can never bind
 * itself to the DI library and turn a container swap into an iOS-app refactor.
 */
internal expect val platformHttpEngineModule: Module
