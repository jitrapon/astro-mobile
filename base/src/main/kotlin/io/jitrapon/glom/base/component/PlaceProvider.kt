package io.jitrapon.glom.base.component

import android.arch.lifecycle.LifecycleObserver
import com.google.android.gms.location.places.AutocompletePrediction
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.PlacePhotoResponse
import io.reactivex.Maybe
import io.reactivex.Single

/**
 * Lifecycle-aware components that abstracts away logic to retrieve Place information
 * using the Google Place API, returning reactive streams as responses.
 *
 * Created by Jitrapon on 11/30/2017.
 */
interface PlaceProvider : LifecycleObserver {

    /**
     * Given an array of place IDs, return an array of place details
     */
    fun getPlaces(placeIds: Array<String>): Single<Array<Place>>

    /**
     * Given a place ID, return the first image of a place
     */
    fun getPlacePhoto(placeId: String): Maybe<PlacePhotoResponse>

    /**
     * Given a query, return an array auto-complete prediction objects
     */
    fun getAutocompletePrediction(query: String): Single<Array<AutocompletePrediction>>
}