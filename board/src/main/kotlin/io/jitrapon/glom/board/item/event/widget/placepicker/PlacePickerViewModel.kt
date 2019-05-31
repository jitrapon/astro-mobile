package io.jitrapon.glom.board.item.event.widget.placepicker

import android.location.LocationProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.MessageLevel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.board.BoardInjector
import io.jitrapon.glom.board.R
import javax.inject.Inject

class PlacePickerViewModel : BaseViewModel() {

//    @Inject
//    lateinit var locationProvider: LocationProvider

    private val observableUserLocation = MutableLiveData<LatLng>()

    init {
        BoardInjector.getComponent().inject(this)
    }

    /**
     * Called when the map is fully initialized
     */
    fun onMapInitialized(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) {
            showNoLocationPermissionMessage()
        }
        else {
            retrieveUserLocation()
        }
    }

    private fun requestLocationPermissions() {
        showGrantLocationPermissionDialog { ungrantedPermissions ->
            if (ungrantedPermissions.isEmpty()) {
                retrieveUserLocation()
            }
            else {
                showNoLocationPermissionMessage()
            }
        }
    }

    private fun showNoLocationPermissionMessage() {
        observableViewAction.value = io.jitrapon.glom.base.model.Snackbar(
            AndroidString(R.string.error_no_location_permission),
            AndroidString(io.jitrapon.glom.R.string.permission_grant_action),
            ::requestLocationPermissions,
            duration = Snackbar.LENGTH_INDEFINITE
        )
    }

    private fun retrieveUserLocation() {

    }

    fun getObservableUserLocation(): LiveData<LatLng> = observableUserLocation
}
