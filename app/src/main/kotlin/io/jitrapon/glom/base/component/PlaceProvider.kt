package io.jitrapon.glom.base.component

import android.graphics.Bitmap
import android.location.Address
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import io.reactivex.Single

/**
 * Lifecycle-aware components that abstracts away logic to retrieve Place information
 * using the Google Place API, returning reactive streams as responses.
 *
 * Created by Jitrapon on 11/30/2017.
 */
interface PlaceProvider {

    /**
     * Given an array of place IDs, return an array of place details
     */
    fun getPlaces(placeIds: Array<String>): Single<Array<Place>>

    /**
     * Given a place ID, return the Bitmap associated with the Place
     */
    fun getPlacePhoto(placeId: String, width: Int, height: Int, onSuccess: (Bitmap) -> Unit, onError: (Exception) -> Unit)

    /**
     * Given a query, return an array auto-complete prediction objects
     */
    fun getAutocompletePrediction(query: String): Single<Array<AutocompletePrediction>>

    /**
     * Forces cleaning up of session data
     */
    fun clearSession()

    /**
     * Converts a list of addresses into geographic coordinates (LatLng)
     */
    fun geocode(queries: List<String>): Single<Map<String, Address?>>
}
