package io.jitrapon.glom.base.component

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

/**
 * Application module for Glide library to initialize settings for all image loading requests.
 *
 * Created by Jitrapon on 12/20/2017.
 */
@GlideModule
class GlomGlideModule : AppGlideModule() {

    companion object {
        private const val DISK_CACHE_SIZE: Long = 1024 * 1024 * 250 // 250 MB
        private const val DISK_CACHE_NAME: String = "image_cache"
    }

    override fun isManifestParsingEnabled() = false

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(RequestOptions().format(DecodeFormat.PREFER_ARGB_8888)
                .disallowHardwareConfig())
                .setDiskCache(InternalCacheDiskCacheFactory(context, DISK_CACHE_NAME, DISK_CACHE_SIZE))
                .setLogLevel(Log.ERROR)
    }

    override fun registerComponents(context: Context?, glide: Glide?, registry: Registry?) {
        TODO()
    }
}