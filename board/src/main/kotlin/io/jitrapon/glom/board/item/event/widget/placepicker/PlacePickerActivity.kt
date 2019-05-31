package io.jitrapon.glom.board.item.event.widget.placepicker

import android.os.Bundle
import androidx.lifecycle.Observer
import io.jitrapon.glom.base.ui.BaseMapActivity
import io.jitrapon.glom.base.util.hasLocationPermission
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.board.R


/**
 * Activity that allows users to select a location on the map
 *
 * Created by Jitrapon
 */
class PlacePickerActivity : BaseMapActivity() {

    private lateinit var viewModel: PlacePickerViewModel

    //region lifecycle

    override fun getMapFragmentId(): Int = R.id.place_picker_map

    override fun getLayoutId(): Int = R.layout.place_picker_activity

    override fun isUserLocationEnabled(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tag = "place_picker"
    }

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(PlacePickerViewModel::class.java)
    }

    override fun onMapInitialized() {
        delayRun(200L) {
            viewModel.onMapInitialized(hasLocationPermission())
        }
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.observableViewAction)

        viewModel.getObservableUserLocation().observe(this, Observer {

        })
    }

    //endregion

}
