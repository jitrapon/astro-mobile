package io.jitrapon.glom.base.component

import android.content.Context
import android.graphics.Bitmap
import android.location.Address
import android.location.Geocoder
import android.text.TextUtils
import androidx.annotation.WorkerThread
import androidx.collection.ArrayMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.internal.it
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.get
import io.reactivex.Single
import java.util.Arrays
import java.util.Locale

/**
 * Lifecycle-aware components that abstracts away logic to retrieve Place information
 * using the Google Place API.
 *
 * Created by Jitrapon on 11/30/2017.
 */
class GooglePlaceProvider(context: Context) : PlaceProvider {

    private enum class SKUType {
        BASIC, CONTACT, ATMOSPHERE
    }

    //region constructors

    /**
     * Place API client
     */
    private val client: PlacesClient by lazy {
        Places.initialize(context.applicationContext, context.getString(R.string.google_geo_api_key))
        Places.createClient(context)
    }

    /**
     * Geocoder API client
     */
    private val geocoder: Geocoder by lazy {
        Geocoder(context.applicationContext, Locale.getDefault())
    }

    /**
     * Token for autocomplete session and fetch place
     */
    private var sessionToken: AutocompleteSessionToken? = null

    /**
     * Search LatLng boundary
     */
    private val bounds: RectangularBounds = RectangularBounds.newInstance(LatLng(13.7244409, 100.3522243), LatLng(13.736717, 100.523186))

    //endregion
    //region lifecycle

    /**
     * Synchronously retrieve a Place information
     */
    @WorkerThread
    private fun getPlaceDetails(placeId: String, vararg skuTypes: SKUType): Place {
        val fields = ArrayList<Place.Field>().apply {
            for (type in skuTypes) {
                when (type) {
                    SKUType.BASIC -> addAll(arrayListOf(
                        Place.Field.ADDRESS,
                        Place.Field.ID,
                        Place.Field.LAT_LNG,
                        Place.Field.NAME,
                        Place.Field.OPENING_HOURS,
                        Place.Field.PHOTO_METADATAS,
                        Place.Field.PLUS_CODE,
                        Place.Field.TYPES,
                        Place.Field.VIEWPORT)
                    )
                    SKUType.CONTACT -> addAll(arrayListOf(
                        Place.Field.PHONE_NUMBER,
                        Place.Field.WEBSITE_URI)
                    )
                    SKUType.ATMOSPHERE -> addAll(arrayListOf(
                        Place.Field.PRICE_LEVEL,
                        Place.Field.RATING,
                        Place.Field.USER_RATINGS_TOTAL
                    ))
                }
            }
        }
        AppLogger.d("Fetching place for placeID=$placeId, sessionToken=$sessionToken, fields=$fields...")

        return client.fetchPlace(FetchPlaceRequest.builder(placeId, fields)
            .setSessionToken(sessionToken)
            .build()
        ).let {
            Tasks.await(it).place
        }
    }

    override fun getPlaces(placeIds: Array<String>): Single<Array<Place>> {
        return Single.create { single ->
            if (placeIds.isEmpty()) {
                single.onSuccess(emptyArray())
            }
            else {
                val result = ArrayList<Place>()
                for (placeId in placeIds) {
                    try {
                        val place = getPlaceDetails(placeId, SKUType.BASIC, SKUType.CONTACT)
                        result.add(place)

                        AppLogger.d("Found place for placeID=$placeId, name=${place.name}")
                    }
                    catch (ex: Exception) {
                        sessionToken = null

                        AppLogger.e(ex)
                        single.onError(ex)
                        return@create
                    }
                }
                sessionToken = null

                single.onSuccess(result.toTypedArray())
            }
        }
    }

    override fun getPlacePhoto(placeId: String, width: Int, height: Int,
                               onSuccess: (Bitmap) -> Unit, onError: (Exception) -> Unit) {
        client.fetchPlace(FetchPlaceRequest.builder(placeId, Arrays.asList(Place.Field.PHOTO_METADATAS)).build())
            .addOnSuccessListener {
                try {
                    val photoRequest = FetchPhotoRequest.builder(it.place.photoMetadatas!![0])
                        .setMaxWidth(width)
                        .setMaxHeight(height)
                        .build()
                    client.fetchPhoto(photoRequest)
                        .addOnSuccessListener { photoResponse ->
                            onSuccess(photoResponse.bitmap)
                        }
                        .addOnFailureListener { ex ->
                            onError(ex)
                        }
                }
                catch (ex: Exception) {
                    onError(ex)
                }
            }
            .addOnFailureListener {
                onError(it)
            }
    }

    override fun getAutocompletePrediction(query: String): Single<Array<AutocompletePrediction>> {
        return Single.create { single ->
            if (TextUtils.isEmpty(query)) {
                single.onSuccess(emptyArray())
            }
            else {
                // this session token is used until a Fetch Place request is called
                // or clearSession() is manually called
                sessionToken = sessionToken ?: AutocompleteSessionToken.newInstance()
                val request = FindAutocompletePredictionsRequest.builder()
                    .setLocationBias(bounds)
                    .setCountry("th")
                    .setSessionToken(sessionToken)
                    .setQuery(query)
                    .build()
                AppLogger.d("Finding place autocomplete predictions from query=$query, sessionToken=$sessionToken")
                client.findAutocompletePredictions(request).addOnSuccessListener { response ->
                    for (prediction in response.autocompletePredictions) {
                       val name = prediction.getPrimaryText(null)
                       AppLogger.d("Place predicted to be placeID=${prediction.placeId}, name=$name")
                    }
                    single.onSuccess(response.autocompletePredictions.toTypedArray())
                }
                .addOnFailureListener {
                    single.onError(it)
                }
            }
        }
    }

    override fun clearSession() {
        AppLogger.d("Clearing session token")

        sessionToken = null
    }

    override fun geocode(queries: List<String>): Single<Map<String, Address?>> {
        return Single.create { single ->
            if (!Geocoder.isPresent()) {
                AppLogger.w("Geocoder is not present")
                single.onSuccess(emptyMap())
            }
            else {
                try {
                    AppLogger.d("Geocoding from ${queries.size} queries")
                    val result = ArrayMap<String, Address?>()
                    for (query in queries) {
                        val address = geocoder.getFromLocationName(query, 1).get(0, null)
                        result[query] = address

                        AppLogger.d("Found geocoding result for query=$query")
                    }
                    single.onSuccess(result)
                }
                catch (ex: Exception) {
                    AppLogger.e(ex)

                    single.onError(ex)
                }
            }
        }
    }

    //endregion
}
