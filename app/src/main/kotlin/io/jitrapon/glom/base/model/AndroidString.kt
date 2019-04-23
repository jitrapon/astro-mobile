package io.jitrapon.glom.base.model

import androidx.annotation.StringRes
import java.util.*

/**
 * A wrapper around String resource retrieved via Strings.xml to be used for ViewModel
 * to pass String to the View without requiring Context object
 *
 * @author Jitrapon Tiachunpun
 */
data class AndroidString(@StringRes val resId: Int? = null,
                    val formatArgs: Array<out CharSequence>? = null,
                    val text: CharSequence? = null,
                    override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AndroidString

        if (resId != other.resId) return false
        if (!Arrays.equals(formatArgs, other.formatArgs)) return false
        if (text != other.text) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resId ?: 0
        result = 31 * result + (formatArgs?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        return result
    }
}
