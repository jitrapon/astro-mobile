package io.jitrapon.glom.board.item.event.placepicker

import android.os.Bundle
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.util.setStyle
import io.jitrapon.glom.base.util.showMap
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.event.preference.EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL


/**
 * Activity that allows users to select a location on the map
 *
 * Created by Jitrapon
 */
class PlacePickerActivity :
        BaseActivity(),
        OnMapReadyCallback {

//    private lateinit var viewModel: PlacePickerViewModel
    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.place_picker_activity)

        tag = "place_picker"

        ((supportFragmentManager.findFragmentById(R.id.place_picker_map)
                as? SupportMapFragment)?.getMapAsync(this))
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        googleMap ?: return

        googleMap.apply {
            setStyle(this@PlacePickerActivity, R.raw.map_style, true)
            showMap(LatLng(13.732345, 100.6487077), EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL)
        }
    }
}
