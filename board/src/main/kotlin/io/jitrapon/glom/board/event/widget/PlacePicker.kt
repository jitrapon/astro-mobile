package io.jitrapon.glom.board.event.widget

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlacePicker

/**
 * Wrapper around an implementation of a PlacePicker widget whose job is to provide
 * search for a place in a map
 *
 * Created by Jitrapon
 */
class PlacePicker {

    fun launch(activity: Activity, requestCode: Int) {
        activity.startActivityForResult(PlacePicker.IntentBuilder().build(activity), requestCode)
    }

    fun getPlaceFromResult(activity: Activity, resultCode: Int, data: Intent?): Place? {
        data ?: return null
        return if (resultCode == RESULT_OK) PlacePicker.getPlace(activity, data) else null
    }
}