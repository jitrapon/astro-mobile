package io.jitrapon.glom.base.model

data class ButtonUiModel(var text: AndroidString?, override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
