package io.jitrapon.glom.board.item.event

import io.jitrapon.glom.base.model.AndroidImage
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiModel

data class EventSourceUiModel(val sourceIcon: AndroidImage?,
                              val sourceDescription: AndroidString?,
                              override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel

data class EventSourceChoiceUiModel(val selectedIndex: Int,
                                    val items: MutableList<EventSourceUiModel>,
                                    override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
