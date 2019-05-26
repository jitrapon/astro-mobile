package io.jitrapon.glom.board.item.event.placepicker

import android.os.Bundle
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.board.R

/**
 * Activity that allows users to select a location on the map
 *
 * Created by Jitrapon
 */
class PlacePickerActivity : BaseActivity() {

//    private lateinit var viewModel: PlacePickerViewModel
    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.place_picker_activity)

        tag = "place_picker"
    }
}
