package io.jitrapon.glom.base.util

import android.location.Address
import com.google.android.gms.maps.model.LatLng

val Address.fullAddress: String?
    get() = with(this) {
        (0..maxAddressLineIndex).map { getAddressLine(it) }
    }.joinToString(separator = "\n")

val Address.latLng: LatLng?
    get() = with(this) {
        if (hasLatitude()) LatLng(latitude, longitude)
        else null
    }
