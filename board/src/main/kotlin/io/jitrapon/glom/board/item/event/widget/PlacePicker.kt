package io.jitrapon.glom.board.item.event.widget

import android.app.Activity
import android.content.Intent
import com.google.android.libraries.places.api.model.Place

/**
 * Wrapper around an implementation of a PlacePicker widget whose job is to provide
 * search for a place in a map
 *
 * Created by Jitrapon
 */
class PlacePicker {

    fun launch(activity: Activity, requestCode: Int) {

    }

    fun getPlaceFromResult(activity: Activity, resultCode: Int, data: Intent?): Place? {
        return null
    }
}
