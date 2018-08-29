package io.jitrapon.glom.base.component

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
sealed class GlidePlaceModelLoader : ModelLoader<String, ByteBuffer> {

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteBuffer>? =
            ModelLoader.LoadData(ObjectKey(model), GlidePlaceDataFetcher(model.toPlaceId()))

    override fun handles(model: String) = model.isValidModel()
}

/**
 * Handles the work to fetch Google place photo data asynchronously
 */
class GlidePlaceDataFetcher(private val placeId: String) : DataFetcher<ByteBuffer> {

    override fun getDataClass(): Class<ByteBuffer> = ByteBuffer::class.java

    override fun cleanup() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDataSource(): DataSource = DataSource.REMOTE

    override fun cancel() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun loadData(priority: Priority?, callback: DataFetcher.DataCallback<in ByteBuffer>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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