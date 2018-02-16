package io.jitrapon.glom.base.util

import android.text.TextUtils

/**
 * Small util class that validates input
 *
 * Created by Jitrapon
 */
object InputValidator {

    fun validateNotEmpty(input: String?): Boolean {
        input ?: return false
        return !TextUtils.isEmpty(input)
    }

    fun validateTextsEqual(input1: String?, input2: String?, ignoreCase: Boolean): Boolean {
        if (TextUtils.isEmpty(input1) || TextUtils.isEmpty(input2)) return false
        return input1.equals(input2, ignoreCase)
    }

    fun validateEmail(input: String): Boolean {
        return !(TextUtils.isEmpty(input) || !android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches())
    }
}