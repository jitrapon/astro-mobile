package io.jitrapon.glom.base.component

import android.app.Activity
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import com.google.android.gms.location.places.GeoDataClient
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.Places
import io.reactivex.Single

/**
 * Lifecycle-aware components that abstracts away logic to retrieve Place information
 * using the Google Place API.
 *
 * This class should be initialized in one of the Android's view classes
 * (i.e. Activity or Fragment), and be passed onto the domain classes afterwards. This is
 * because those classes contain the Lifecycle and context necessary to construct this class.
 *
 * Created by Jitrapon on 11/30/2017.
 */
class GooglePlaceProvider(lifeCycle: Lifecycle, context: Context? = null, activity: Activity? = null) : PlaceProvider {

    //region constructors

    /**
     * Initializes the life cycle object from the constructor to be notified of any change in the
     * state in the life cycle
     */
    private var lifeCycle: Lifecycle = lifeCycle.apply {
        addObserver(this@GooglePlaceProvider)
    }

    /**
     * GeoDataClient instance to talk to a Google server
     */
    private val client: GeoDataClient by lazy {
        if (context == null && activity == null) throw IllegalArgumentException("Context and Activity must not be null")
        if (context != null) Places.getGeoDataClient(context, null)
        else Places.getGeoDataClient(activity!!, null)
    }

    //endregion
    //region lifecycle

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun cleanup() {
        lifeCycle.removeObserver(this)
    }

    override fun retrievePlaces(placeIds: Array<String>): Single<Array<Place>> {
        return Single.create { single ->
            if (lifeCycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (placeIds.isEmpty()) {
                    single.onSuccess(emptyArray())
                }
                else {
                    client.getPlaceById(*placeIds).let {
                        it.addOnCompleteListener {
                            if (it.isSuccessful) {
                                val places = it.result
                                val result = ArrayList<Place>()
                                places
                                        .filter { it.isDataValid }
                                        .forEach { result.add(it.freeze()) }
                                places.release()
                                single.onSuccess(result.toTypedArray())
                            }
                            else single.onError(Exception("Failed to retrieve places with exception ${it.exception}"))
                        }
                        it.addOnFailureListener {
                            // api exception is thrown automatically, we don't need to call onError
                        }
                    }
                }
            }
            else single.onError(Exception("Attempting to retrieve place data while lifecycle is not at least STARTED"))
        }
    }



    //endregion
}
