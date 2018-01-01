package io.jitrapon.glom.base.component

import android.support.annotation.DrawableRes
import android.support.v4.app.Fragment
import android.widget.ImageView

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
                          @DrawableRes error: Int? = null, transformation: Transformation = Transformation.NONE) {
    GlideApp.with(fragment)
            .load(url)
            .apply {
                placeholder?.let (this::placeholder)
                error?.let (this::error)
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



