package io.jitrapon.glom.base

import android.app.Application
import com.google.android.gms.maps.MapsInitializer
import io.jitrapon.glom.base.util.AppLogger

/**
 * Application class where all the dependencies are initialized if appropriate
 *
 * Created by Jitrapon on 12/11/2017.
 */
class GlomApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // initialize logging behaviors
        AppLogger.initialize(this)

        // initialize Google Play Services
        MapsInitializer.initialize(this)
    }
}
