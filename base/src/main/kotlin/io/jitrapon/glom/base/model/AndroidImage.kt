package io.jitrapon.glom.base.model

import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import io.jitrapon.glom.base.util.Transformation

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
