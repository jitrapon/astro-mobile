package io.jitrapon.glom.base.model

import androidx.annotation.DrawableRes

/**
 * Base UiModel class for all preference item
 *
 * Created by Jitrapon
 */
open class PreferenceItemUiModel(override var status: UiModel.Status = UiModel.Status.SUCCESS,
                                 val isHeader: Boolean,
                                 val title: String,
                                 val subtitle: String?,
                                 val isToggled: Boolean?,
                                 @DrawableRes val icon: Int?) : UiModel