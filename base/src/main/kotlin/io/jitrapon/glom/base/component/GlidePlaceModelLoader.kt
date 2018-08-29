package io.jitrapon.glom.base.component

import android.graphics.Bitmap
import android.support.v4.graphics.BitmapCompat
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import java.nio.ByteBuffer

private const val PLACE_ID_PREFIX = "place:"

/**
 * Loads a {@link java.nio.ByteBuffer} representing a bitmap retrieved via a Google place ID string.
 *
 * @author Jitrapon Tiachunpun
 */
sealed class GlidePlaceModelLoader(private val placeProvider: PlaceProvider, width: Int, height: Int) : ModelLoader<String, ByteBuffer> {

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteBuffer> =
            ModelLoader.LoadData(ObjectKey(model), GlidePlaceDataFetcher(placeProvider, width, height, model.toPlaceId()))

    override fun handles(model: String) = model.isValidModel()
}

/**
 * Handles the work to fetch Google place photo data asynchronously
 */
class GlidePlaceDataFetcher(private val placeProvider: PlaceProvider, private val width: Int, private val height: Int, private val placeId: String) : DataFetcher<ByteBuffer> {

    override fun getDataClass(): Class<ByteBuffer> = ByteBuffer::class.java

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
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {
        placeProvider.getPlacePhoto(placeId, width, height, {
            it.bitmap.let {
                val buffer = it.toByteBuffer()
                it.recycle()
                callback.onDataReady(buffer)
            }
        }, {
            callback.onLoadFailed(it)
        })
    }

    private fun Bitmap.toByteBuffer(): ByteBuffer {
        val bytes = BitmapCompat.getAllocationByteCount(this)
        return ByteBuffer.allocate(bytes).let {
            copyPixelsToBuffer(it)
            it.rewind()
            it
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