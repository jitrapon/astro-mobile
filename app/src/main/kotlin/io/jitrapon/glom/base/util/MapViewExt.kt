package io.jitrapon.glom.base.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RawRes
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions

@SuppressLint("MissingPermission")
fun GoogleMap.setStyle(context: Context, @RawRes mapResId: Int) {
    try {
        setMapStyle(MapStyleOptions.loadRawResourceStyle(context, mapResId)).let {
            if (!it) AppLogger.w("Google Maps custom style parsing failed")
        }
    }
    catch (ex: Exception) {
        AppLogger.w(ex)
    }
}

fun GoogleMap.showLiteMap(latLng: LatLng,
                          zoomLevel: Float,
                          type: Int = GoogleMap.MAP_TYPE_NORMAL) {
    moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel))
    addMarker(MarkerOptions().position(latLng))
    if (mapType != type) mapType = type
    uiSettings.apply {
        isMapToolbarEnabled = false
    }
}

@SuppressLint("MissingPermission")
fun GoogleMap.showMap(context: Context,
                      latLng: LatLng,
                      zoomLevel: Float,
                      type: Int = GoogleMap.MAP_TYPE_NORMAL,
                      animateCamera: Boolean = false,
                      showToolbar: Boolean = false,
                      showMyLocationButton: Boolean = false,
                      showUserLocation: Boolean = false) {
    if (animateCamera) animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel))
    else moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel))
    addMarker(MarkerOptions().position(latLng))
    if (mapType != type) mapType = type

    val hasLocationPermission = context.hasLocationPermission()
    uiSettings.apply {
        isMapToolbarEnabled = showToolbar
        isMyLocationButtonEnabled = showMyLocationButton
        if (hasLocationPermission) {
            isMyLocationButtonEnabled = showMyLocationButton
            isMyLocationEnabled = showUserLocation
        }
        else {
            isMyLocationButtonEnabled = false
            isMyLocationEnabled = false
        }
    }
}

fun GoogleMap.clearMap() {
    clear()
    if (mapType != GoogleMap.MAP_TYPE_NONE) mapType = GoogleMap.MAP_TYPE_NONE
}
