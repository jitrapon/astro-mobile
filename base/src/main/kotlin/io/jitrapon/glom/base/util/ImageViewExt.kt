package io.jitrapon.glom.base.util

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.annotation.RawRes
import android.support.v4.app.Fragment
import android.support.v4.widget.ImageViewCompat
import android.widget.ImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.jitrapon.glom.base.component.GlideApp
import io.jitrapon.glom.base.di.toGlidePlaceId


/**
 * Wrapper and extension functions around image loading to allow Android's ImageView
 * to conveniently load image
 *
 * Created by Jitrapon Tiachunpun
 */

/* All transformation supported */
enum class Transformation {

    NONE, FIT_CENTER, CENTER_INSIDE, CENTER_CROP, CIRCLE_CROP
}

/**
 * Loads an image in a fragment from a URL, with an optional placeholder and applies a transformation
 */
fun ImageView.loadFromUrl(fragment: Fragment, url: String?, @DrawableRes placeholder: Int? = null,
                          @DrawableRes error: Int? = null, fallback: Drawable = ColorDrawable(Color.BLACK), transformation: Transformation = Transformation.NONE) {
    GlideApp.with(fragment)
            .load(url)
            .apply {
                placeholder?.let (this::placeholder)
                error?.let (this::error)
                fallback.let (this::fallback)
                when (transformation) {
                    Transformation.FIT_CENTER -> fitCenter()
                    Transformation.CENTER_INSIDE -> centerInside()
                    Transformation.CENTER_CROP -> centerCrop()
                    Transformation.CIRCLE_CROP -> circleCrop()
                    else -> { /* do nothing */ }
                }
            }
            .into(this)
}

/**
 * Loads an image in a fragment from a URL, with an optional placeholder and applies a transformation
 */
fun ImageView.loadFromUrl(activity: Activity, url: String?, @DrawableRes placeholder: Int? = null,
                          @DrawableRes error: Int? = null, fallback: Drawable = ColorDrawable(Color.BLACK), transformation: Transformation = Transformation.NONE,
                          crossFade: Int? = null) {
    GlideApp.with(activity)
            .load(url)
            .apply {
                placeholder?.let (this::placeholder)
                error?.let (this::error)
                fallback.let (this::fallback)
                when (transformation) {
                    Transformation.FIT_CENTER -> fitCenter()
                    Transformation.CENTER_INSIDE -> centerInside()
                    Transformation.CENTER_CROP -> centerCrop()
                    Transformation.CIRCLE_CROP -> circleCrop()
                    else -> { /* do nothing */ }
                }
                crossFade?.let {
                    this.transition(DrawableTransitionOptions.withCrossFade(it))
                }
            }
            .into(this)
}

fun ImageView.loadFromPlaceId(fragment: Fragment, placeId: String?, @DrawableRes placeholder: Int? = null,
                              @DrawableRes error: Int? = null, fallback: Drawable = ColorDrawable(Color.BLACK), transformation: Transformation = Transformation.NONE,
                              crossFade: Int? = null) {
    GlideApp.with(fragment)
            .load(placeId?.toGlidePlaceId())
            .apply {
                placeholder?.let (this::placeholder)
                error?.let (this::error)
                fallback.let (this::fallback)
                when (transformation) {
                    Transformation.FIT_CENTER -> fitCenter()
                    Transformation.CENTER_INSIDE -> centerInside()
                    Transformation.CENTER_CROP -> centerCrop()
                    Transformation.CIRCLE_CROP -> circleCrop()
                    else -> { /* do nothing */ }
                }
                listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        e?.let {
                            it.causes.forEach { cause ->
                                AppLogger.e(cause)
                            }
                            it.rootCauses.forEach { rootCause ->
                                AppLogger.e(rootCause)
                            }
                        }
                        return false // allow calling onLoadFailed on the Target.
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        return false // allow calling onResourceReady on the Target.
                    }
                })
                crossFade?.let {
                    this.transition(DrawableTransitionOptions.withCrossFade(it))
                }
            }
            .into(this)
}

fun ImageView.loadFromRaw(fragment: Fragment, @RawRes resId: Int, @DrawableRes placeholder: Int? = null,
                          @DrawableRes error: Int? = null, fallback: Drawable = ColorDrawable(Color.BLACK), transformation: Transformation = Transformation.NONE) {
    GlideApp.with(fragment)
            .load(resId)
            .apply {
                placeholder?.let (this::placeholder)
                error?.let (this::error)
                fallback.let (this::fallback)
                when (transformation) {
                    Transformation.FIT_CENTER -> fitCenter()
                    Transformation.CENTER_INSIDE -> centerInside()
                    Transformation.CENTER_CROP -> centerCrop()
                    Transformation.CIRCLE_CROP -> circleCrop()
                    else -> { /* do nothing */ }
                }
            }
            .into(this)
}

/**
 * Compatible function that loads image from a Vector Drawable
 */
fun ImageView.loadFromResource(@DrawableRes resId: Int) {
    setImageResource(resId)
}

/**
 * Recycles any resources manually
 */
fun ImageView.clear(fragment: Fragment) {
    GlideApp.with(fragment).clear(this)
}

/**
 * Tints this image view to a specific color
 */
fun ImageView.tint(@ColorInt color: Int) {
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
}

