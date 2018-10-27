package io.jitrapon.glom.base.model

import androidx.annotation.DrawableRes

data class ImageButtonUiModel(var imageUrl: String?,
                              @DrawableRes val errorPlaceHolder: Int,
                              @DrawableRes val placeHolder: Int,
                              override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
