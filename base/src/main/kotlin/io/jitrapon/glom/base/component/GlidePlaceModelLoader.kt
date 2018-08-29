package io.jitrapon.glom.base.component

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import java.nio.ByteBuffer

/**
 * Loads a {@link java.nio.ByteBuffer} representing a bitmap retrieved via a Google place ID string.
 *
 * @author Jitrapon Tiachunpun
 */
sealed class GlidePlaceModelLoader : ModelLoader<String, ByteBuffer> {

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteBuffer>? {
        return null
    }

    override fun handles(model: String): Boolean {
        return model.startsWith("place:")
    }
}
