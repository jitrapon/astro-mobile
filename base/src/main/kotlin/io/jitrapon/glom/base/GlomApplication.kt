package io.jitrapon.glom.base

import android.app.Application
import android.support.v7.app.AppCompatDelegate
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

        // allow support for older pre-lollipop devices to use vector graphics
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // initialize logging behaviors
        AppLogger.initialize(this)

        // initialize Google Play Services
        MapsInitializer.initialize(this)
    }
}
