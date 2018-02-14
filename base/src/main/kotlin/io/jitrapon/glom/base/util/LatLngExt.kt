package io.jitrapon.glom.base.util

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.MapStyle.styleEncodedUri

/**
 * Utility extension functions with LatLng
 *
 * @author Jitrapon Tiachunpun
 */

object MapStyle {

    /**
     * Encoded URL string that contains style defined in map_style.json
     */
    const val styleEncodedUri: String = "style=element:geometry%7Chue:0xff0000%7Cvisibility:simplified" +
            "&style=feature:administrative%7Cvisibility:simplified&style=feature:poi%7Cvisibility:off&style=" +
            "feature:road%7Celement:labels.icon%7Cvisibility:off&style=feature:transit.station%7Celement:labels.icon%7Cvisibility:on&" +
            "style=feature:water%7Cvisibility:on"
}

/**
 * Converts this LatLng location to a URL to generated static Google Maps bitmap
 * to be able to display on an ImageView
 *
 * Sample URL: https://maps.googleapis.com/maps/api/staticmap?zoom=15&size=600x400&markers=13.732756,100.643237&style=element:geometry%7Chue:0xff0000%7Cvisibility:simplified&style=feature:administrative%7Cvisibility:simplified&style=feature:poi%7Cvisibility:off&style=feature:road%7Celement:labels.icon%7Cvisibility:off&style=feature:transit.station%7Celement:labels.icon%7Cvisibility:on&style=feature:water%7Cvisibility:on
 */
fun LatLng.toUri(context: Context, width: Int, height: Int, zoomLevel: Int = 16): String {
    return StringBuilder().apply {
        append("https://maps.googleapis.com/maps/api/staticmap?")
        append("zoom=$zoomLevel&")
        append("size=${width}x$height&")
//        append("scale=2&")
        append("markers=$latitude,$longitude&")
        append("$styleEncodedUri&")
        append("key=${context.getString(R.string.google_geo_api_key)}")
    }.toString()
}
