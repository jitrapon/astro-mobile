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
        if (context != null) Places.getGeoDataClient(context, null)
        if (activity != null) Places.getGeoDataClient(activity, null)
        throw IllegalArgumentException("Must provide either non-null Context or Activity!")
    }

    //endregion
    //region lifecycle

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun cleanup() {
        lifeCycle.removeObserver(this)
    }

    override fun retrievePlaces(map: HashMap<String, Place>): Single<HashMap<String, Place>> {
        return Single.create { single ->
            if (lifeCycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                client.getPlaceById(*map.keys.toTypedArray()).let {
                    it.addOnCompleteListener {
                        if (it.isSuccessful) {
                            val places = it.result
                            places
                                    .filter { it.isDataValid }
                                    .forEach { map.put(it.id, it.freeze()) }
                            places.release()
                            single.onSuccess(map)
                        }
                        else single.onError(Exception("Failed to retrieve places with exception ${it.exception}"))
                    }
                    it.addOnFailureListener {
                        single.onError(it)
                    }
                }
            }
            single.onError(Exception("Attempting to retrieve place data while lifecycle is not at least STARTED"))
        }
    }

    //endregion
}
