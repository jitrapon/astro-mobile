package io.jitrapon.glom.base.domain.user.settings

import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.UiModel

data class LoginInfoUiModel(val userId: AndroidString,
                            val avatar: String?,
                            val loginButtonUiModel: ButtonUiModel,
                            override var status: UiModel.Status) : UiModel