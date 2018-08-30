package io.jitrapon.glom.base.di

import android.graphics.Bitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.component.PlaceProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Named


private const val PLACE_ID_PREFIX = "place:"
private const val PLACE_PHOTO_SIZE = 256

@Module(includes = [GoogleModule::class])
class GlideModule {

    @Provides
    @Named("place")
    fun provideModelLoaderFactory(placeProvider: PlaceProvider): ModelLoaderFactory<String, InputStream> = GlidePlaceModelLoaderFactory(placeProvider)
}

class GlidePlaceModelLoaderFactory(val placeProvider: PlaceProvider) : ModelLoaderFactory<String, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> = GlidePlaceModelLoader(placeProvider)

    override fun teardown() {
        //do nothing
    }
}

/**
 * Loads a InputStream representing a bitmap retrieved via a Google place ID string.
 *
 * @author Jitrapon Tiachunpun
 */
class GlidePlaceModelLoader(private val placeProvider: PlaceProvider) : ModelLoader<String, InputStream> {

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream> =
            ModelLoader.LoadData(ObjectKey(model.toPlaceId()), GlidePlaceDataFetcher(placeProvider, PLACE_PHOTO_SIZE, PLACE_PHOTO_SIZE, model.toPlaceId()))

    override fun handles(model: String) = model.isValidModel()
}

/**
 * Handles the work to fetch Google place photo data asynchronously
 */
class GlidePlaceDataFetcher(private val placeProvider: PlaceProvider, private val width: Int, private val height: Int, private val placeId: String) : DataFetcher<InputStream> {

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun cleanup() {
        //no I/O or InputStream to close
    }

    override fun getDataSource(): DataSource = DataSource.REMOTE

    override fun cancel() {
        //not applicable
    }

    /**
     * Called on one of Glide's background threads. A given DataFetcher will only be used on a single background thread at a time, so it doesn’t need to be thread safe.
     * However, multiple DataFetchers may be run in parallel, so any shared resources accessed by DataFetchers should be thread safe.
     */
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        placeProvider.getPlacePhoto(placeId, width, height, {
            callback.onDataReady(it.bitmap.toInputStream())
        }, {
            callback.onLoadFailed(it)
        })
    }

    private fun Bitmap.toInputStream(): InputStream {
        return ByteArrayOutputStream().let {
            compress(Bitmap.CompressFormat.PNG, 100, it)
            ByteArrayInputStream(it.toByteArray())
        }
    }
}

//region ext functions

/**
 * Prepends a Google place ID string with a special string
 * so that the model loader identifies the string as a place ID
 */
fun String.toGlidePlaceId(): String = PLACE_ID_PREFIX + this

/**
 * Converts a glide place ID into a normal place ID without the prefix
 */
fun String.toPlaceId(): String = removePrefix(PLACE_ID_PREFIX)

/**
 * Whether or not this string contains a valid place ID prefix
 */
private fun String.isValidModel(): Boolean = startsWith(PLACE_ID_PREFIX)

//endregion

