package io.jitrapon.glom.base

import android.app.Application
import android.support.v7.app.AppCompatDelegate
import com.google.android.gms.maps.MapsInitializer
import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.DeviceUtils

/**
 * Application class where all the dependencies are initialized if appropriate
 *
 * Created by Jitrapon on 12/11/2017.
 */
class GlomApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // initialize object dependency graph
        ObjectGraph.init(this)

        // allow support for older pre-lollipop devices to use vector graphics
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // initialize logging behaviors
        AppLogger.initialize(this)

        // set rx global plugin
        RxPlugins.init()

        // initialize Google Play Services
        MapsInitializer.initialize(this)

        // initialize device utility classes
        DeviceUtils.init(this)
    }
}
