package io.jitrapon.glom.base.component

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.MemoryCategory
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.util.DeviceUtils
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Named

/**
 * Application module for Glide library to initialize settings for all image loading requests.
 *
 * Created by Jitrapon on 12/20/2017.
 */
@GlideModule
class GlomGlideModule : AppGlideModule() {

    @field:[Inject Named("place")]
    lateinit var placeModelLoaderFactory: ModelLoaderFactory<String, InputStream>

    init {
        ObjectGraph.component.inject(this)
    }

    companion object {
        private const val DISK_CACHE_SIZE: Long = 1024 * 1024 * 250 // 250 MB
        private const val DISK_CACHE_NAME: String = "image_cache"
    }

    override fun isManifestParsingEnabled() = false

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(RequestOptions().format(
                if (DeviceUtils.isHighPerformingDevice) DecodeFormat.PREFER_ARGB_8888 else DecodeFormat.PREFER_RGB_565)
                .disallowHardwareConfig())
                .setDiskCache(InternalCacheDiskCacheFactory(context, DISK_CACHE_NAME, DISK_CACHE_SIZE))
                .setLogLevel(Log.DEBUG)
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.prepend(String::class.java, InputStream::class.java, placeModelLoaderFactory)

        if (DeviceUtils.isHighPerformingDevice) {
            glide.setMemoryCategory(MemoryCategory.NORMAL)
        }
        else {
            glide.setMemoryCategory(MemoryCategory.LOW)
        }
    }
}