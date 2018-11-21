package io.jitrapon.glom.auth

import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiModel

data class AuthUiModel(var showEmailExpandableLayout: Boolean,
                       var emailError: AndroidString? = null,
                       var passwordError: AndroidString? = null,
                       override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
