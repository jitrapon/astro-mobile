package io.jitrapon.glom.auth

import io.jitrapon.glom.base.model.UiActionModel

enum class AccountType {
    PASSWORD, GOOGLE, FACEBOOK
}

data class CredentialSaveUiModel(val email: CharArray?,
                                 val password: CharArray?,
                                 val accountType: AccountType,
                                 val name: String? = null,
                                 val profilePictureUri: String? = null,
                                 val shouldDelete: Boolean = false): UiActionModel {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CredentialSaveUiModel

        if (email != null) {
            if (other.email == null) return false
            if (!email.contentEquals(other.email)) return false
        } else if (other.email != null) return false
        if (password != null) {
            if (other.password == null) return false
            if (!password.contentEquals(other.password)) return false
        } else if (other.password != null) return false
        if (accountType != other.accountType) return false
        if (name != other.name) return false
        if (profilePictureUri != other.profilePictureUri) return false
        if (shouldDelete != other.shouldDelete) return false

        return true
    }

    override fun hashCode(): Int {
        var result = email?.contentHashCode() ?: 0
        result = 31 * result + (password?.contentHashCode() ?: 0)
        result = 31 * result + accountType.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (profilePictureUri?.hashCode() ?: 0)
        result = 31 * result + shouldDelete.hashCode()
        return result
    }
}
