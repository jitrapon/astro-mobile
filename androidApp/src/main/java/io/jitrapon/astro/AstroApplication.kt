package io.jitrapon.astro

import android.app.Application
import io.jitrapon.astro.di.initKoin

/**
 * Starts the shared dependency graph once per process, before any component can resolve from it.
 *
 * The graph lives here rather than in [io.jitrapon.astro.ui.main.MainActivity] because it outlives
 * every activity: starting it from an activity would restart it on each configuration-change
 * recreation, discarding the HTTP client's connection pool along with it.
 */
class AstroApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin(baseUrl = DEVELOPMENT_BACKEND_BASE_URL)
    }

    private companion object {
        /**
         * The backend as an Android emulator reaches it: `10.0.2.2` is the host machine's loopback
         * seen from inside the emulator, and the contract declares its operations under `/api`.
         *
         * A development placeholder — the backend is not deployed anywhere yet, so there is no
         * environment to read a real origin from. Replace it with a build-type-specific value
         * before a release build ships; this one resolves to nothing off a developer's machine.
         */
        const val DEVELOPMENT_BACKEND_BASE_URL = "http://10.0.2.2:8080/api"
    }
}
