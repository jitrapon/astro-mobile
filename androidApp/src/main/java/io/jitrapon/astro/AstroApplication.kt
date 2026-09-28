package io.jitrapon.astro

import android.app.Application
import io.jitrapon.astro.di.initKoin

/**
 * Starts the shared dependency graph once per process, before any component can resolve from it.
 *
 * The graph lives here rather than in [io.jitrapon.astro.ui.main.MainActivity] because it outlives
 * every activity: starting it from an activity would restart it on each configuration-change
 * recreation, discarding the HTTP client's connection pool along with it.
 *
 * The backend origin is the build type's `BACKEND_BASE_URL`. Every build type but one names the
 * development placeholder `http://10.0.2.2:8080/api` — the host machine's loopback as the Android
 * emulator reaches it, with the contract's operations under `/api`. The backend is not deployed
 * anywhere yet, so there is no real origin to name; replace the placeholder before a release ships.
 * It resolves to nothing off a developer's machine, and only the debug build's network-security
 * overlay permits the cleartext it needs — a release build sends cleartext nowhere.
 *
 * The exception is `releaseLoopback`, the shipping shrink pointed at `127.0.0.1`, where its
 * black-box instrumented run serves the contract fixture; its own overlay permits that one address.
 */
class AstroApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin(baseUrl = BuildConfig.BACKEND_BASE_URL)
    }
}
