package io.jitrapon.glom.base.ui

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.setStyle
import io.jitrapon.glom.base.util.showMap

const val MAP_CAMERA_ZOOM_LEVEL = 15f
const val DEFAULT_LATITUDE = 13.75398
const val DEFAULT_LONGITUDE = 100.50144

/**
 * Base class for all Activities that displays a map in its layout
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseMapActivity :
        BaseActivity(),
        OnMapReadyCallback {

    private var googleMap: GoogleMap? = null

    /**
     * Returns the ID of the fragment that represents the SupportMapFragment
     */
    @IdRes
    abstract fun getMapFragmentId(): Int

    @LayoutRes
    abstract fun getLayoutId(): Int

    /**
     * Called when map has marker and camera initialized to its default state
     */
    open fun onMapInitialized() {}

    /**
     * By default, allow user location to be displayed on the map, and a button
     * to quickly pinpoint the location
     */
    open fun isUserLocationEnabled(): Boolean = true

    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())

        ((supportFragmentManager.findFragmentById(getMapFragmentId())
                as? SupportMapFragment)?.getMapAsync(this))
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        googleMap ?: return
        this.googleMap = googleMap
        googleMap.apply {
            setStyle(this@BaseMapActivity, R.raw.map_style)
            showMap(this@BaseMapActivity,
                LatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE),
                MAP_CAMERA_ZOOM_LEVEL,
                GoogleMap.MAP_TYPE_NORMAL,
                animateCamera = false,
                showToolbar = true,
                showMyLocationButton = isUserLocationEnabled(),
                showUserLocation = isUserLocationEnabled()
            )
        }

        onMapInitialized()
    }

    //endregion
}
