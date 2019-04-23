package io.jitrapon.glom.base.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context

/**
 * Utitlity methods for getting information about the device and
 * performance
 *
 * @author Jitrapon Tiachunpun
 */

object DeviceUtils {

    private lateinit var application: Application

    fun init(application: Application) {
        this.application = application
    }

    /**
     * Gets the device's processor count
     */
    val processorCount: Int
        get() = Runtime.getRuntime().availableProcessors()

    /**
     * Returns true iff a device is considered low memory devices
     */
    val isLowRamDevice: Boolean
        get() = (application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice

    /**
     * Returns the total memory in MB that this app can run
     */
    val totalAppMemoryInMB: Int
        get() = (application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass

    /**
     * Returns true if this device is considered high-end
     */
    val isHighPerformingDevice: Boolean
        get() = !isLowRamDevice && processorCount >= 4 && totalAppMemoryInMB >= 128
}


