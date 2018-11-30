package io.jitrapon.glom.auth

import io.jitrapon.glom.base.model.UiModel

data class CredentialPickerUiModel(val showCancelButton: Boolean,
                                   val isEmailAddressIdentifierSupported: Boolean,
                                   override var status: UiModel.Status = UiModel.Status.SUCCESS): UiModel
