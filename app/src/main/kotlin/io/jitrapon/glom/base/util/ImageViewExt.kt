package io.jitrapon.glom.base.util

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.jitrapon.glom.base.component.GlideApp
import io.jitrapon.glom.base.di.toGlidePlaceId
import io.jitrapon.glom.base.model.AndroidImage


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
fun ImageView.loadFromUrl(fragment: androidx.fragment.app.Fragment, url: String?, @DrawableRes placeholder: Int? = null,
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
fun ImageView.loadFromUrl(context: Context, url: String?, @DrawableRes placeholder: Int? = null,
                          @DrawableRes error: Int? = null, fallback: Drawable = ColorDrawable(Color.BLACK), transformation: Transformation = Transformation.NONE) {
    GlideApp.with(context)
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

fun ImageView.loadFromPlaceId(context: Context, placeId: String?, @DrawableRes placeholder: Int? = null,
                              @DrawableRes error: Int? = null, fallback: Drawable = ColorDrawable(Color.BLACK), transformation: Transformation = Transformation.NONE,
                              crossFade: Int? = null) {
    GlideApp.with(context)
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
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        e?.let {
                            it.causes?.forEach { cause ->
                                AppLogger.e(cause)
                            }
                            it.rootCauses?.forEach { rootCause ->
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

fun ImageView.loadFromRaw(fragment: androidx.fragment.app.Fragment, @RawRes resId: Int, @DrawableRes placeholder: Int? = null,
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
fun ImageView.clear(fragment: androidx.fragment.app.Fragment) {
    GlideApp.with(fragment).clear(this)
}

/**
 * Tints this image view to a specific color
 */
fun ImageView.tint(@ColorInt color: Int?) {
    ImageViewCompat.setImageTintList(this, if (color == null) null else ColorStateList.valueOf(color))
}


/**
 * Loads an image based an an AndroidImage object
 */
fun ImageView.load(context: Context, image: AndroidImage?) {
    image ?: return
    if (image.resId != null) loadFromResource(image.resId)
    else if (image.colorInt != null) setBackgroundColor(image.colorInt)
    else if (image.colorRes != null) setBackgroundResource(image.colorRes)
    else if (!image.imageUrl.isNullOrEmpty()) loadFromUrl(context, image.imageUrl,
            image.placeHolder, image.errorPlaceHolder, image.fallback.let {
        if (it == null) ColorDrawable(Color.BLACK)
        else context.drawable(it) ?: ColorDrawable(Color.BLACK)
    }, image.transformation ?: Transformation.NONE)
    if (image.tint != null) tint(image.tint)
    else tint(null)
}

