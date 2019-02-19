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
fun GoogleMap.setStyle(context: Context, @RawRes mapResId: Int,
                       showToolbar: Boolean = false) {
    try {
        setMapStyle(MapStyleOptions.loadRawResourceStyle(context, mapResId)).let {
            if (!it) AppLogger.w("Google Maps custom style parsing failed")
        }
        uiSettings.apply {
            isMapToolbarEnabled = showToolbar
        }
    }
    catch (ex: Exception) {
        AppLogger.w(ex)
    }
}

fun GoogleMap.showMap(latLng: LatLng, zoomLevel: Float, type: Int = GoogleMap.MAP_TYPE_NORMAL) {
    moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel))
    addMarker(MarkerOptions().position(latLng))
    if (mapType != type) mapType = type
}

fun GoogleMap.clearMap() {
    clear()
    if (mapType != GoogleMap.MAP_TYPE_NONE) mapType = GoogleMap.MAP_TYPE_NONE
}
