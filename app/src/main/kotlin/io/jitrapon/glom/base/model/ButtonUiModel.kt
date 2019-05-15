package io.jitrapon.glom.base.model

data class ButtonUiModel(var text: AndroidString?,
                         var drawable: AndroidImage? = null,
                         val tag: Any? = null,
                         override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
