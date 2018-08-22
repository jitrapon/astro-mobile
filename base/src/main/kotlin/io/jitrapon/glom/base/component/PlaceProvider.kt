package io.jitrapon.glom.base.component

import android.arch.lifecycle.LifecycleObserver
import com.google.android.gms.location.places.AutocompletePrediction
import com.google.android.gms.location.places.Place
import io.reactivex.Single

/**
 * Lifecycle-aware components that abstracts away logic to retrieve Place information
 * using the Google Place API, returning reactive streams as responses.
 *
 * Created by Jitrapon on 11/30/2017.
 */
interface PlaceProvider : LifecycleObserver {

    fun getPlaces(placeIds: Array<String>): Single<Array<Place>>

    fun getPlacePhotos(placeId: String): Single<String>

    fun getAutocompletePrediction(query: String): Single<Array<AutocompletePrediction>>
}