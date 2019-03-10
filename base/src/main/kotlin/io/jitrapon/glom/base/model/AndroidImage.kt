package io.jitrapon.glom.base.model

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.chip.Chip
import io.jitrapon.glom.R
import io.jitrapon.glom.base.component.GlideApp
import io.jitrapon.glom.base.util.Transformation
import io.jitrapon.glom.base.util.color

/**
 * A wrapper around Drawable resource to be used for ViewModel
 *
 * Created by Jitrapon
 */
data class AndroidImage(@DrawableRes val resId: Int? = null,
                        @ColorInt val colorInt: Int? = null,
                        @ColorRes val colorRes: Int? = null,
                        @ColorInt val tint: Int? = null,
                        val imageUrl: String? = null,
                        @DrawableRes val placeHolder: Int? = null,
                        @DrawableRes val errorPlaceHolder: Int? = null,
                        @DrawableRes val fallback: Int? = null,
                        val transformation: Transformation? = null,
                        override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel

fun Chip.setChipIcon(activity: Activity, image: AndroidImage?) {
    if (image == null) {
        isChipIconVisible = false
    }
    else {
        isChipIconVisible = true
        if (image.resId != null) setChipIconResource(image.resId)
        else if (image.colorInt != null) chipIcon = ColorDrawable(image.colorInt)
        else if (image.colorRes != null) chipIcon = ColorDrawable(context.color(image.colorRes))
        else if (!image.imageUrl.isNullOrEmpty()) GlideApp.with(activity)
                .load(image.imageUrl)
                .apply(RequestOptions()
                        .placeholder(image.placeHolder ?: R.drawable.bg_solid_circle)
                        .error(image.errorPlaceHolder ?: R.drawable.bg_solid_circle)
                        .fallback(image.fallback ?: R.drawable.bg_solid_circle)
                        .circleCrop())
                .addListener( object : RequestListener<Drawable> {
                    override fun onResourceReady(resource: Drawable, model: Any,
                                                 target: Target<Drawable>, dataSource: DataSource,
                                                 isFirstResource: Boolean): Boolean {
                        chipIcon = resource
                        return true
                    }

                    override fun onLoadFailed(e: GlideException?, model: Any,
                                      target: Target<Drawable>,
                                      isFirstResource: Boolean): Boolean {
                        return false
                    }
        })
        if (image.tint != null) chipIconTint = ColorStateList.valueOf(image.tint)
        else chipIconTint = null
    }
}