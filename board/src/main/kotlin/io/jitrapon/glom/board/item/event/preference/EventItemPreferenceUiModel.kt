package io.jitrapon.glom.board.item.event.preference

import android.util.SparseBooleanArray
import androidx.recyclerview.widget.DiffUtil
import io.jitrapon.glom.base.model.PreferenceItemUiModel
import io.jitrapon.glom.base.model.UiModel

/**
 * UIModel class representing customization options for event items in a board
 *
 * Created by Jitrapon
 */
data class EventItemPreferenceUiModel(override var status: UiModel.Status = UiModel.Status.SUCCESS,
                                      var preferences: List<PreferenceItemUiModel> = listOf(),
                                      val expandStates: SparseBooleanArray = SparseBooleanArray(),
                                      var lastExpandHeaderIndex: Int? = null,
                                      var preferencesDiffResult: DiffUtil.DiffResult? = null) : UiModel
