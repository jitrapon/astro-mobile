package io.jitrapon.glom.base.data

import android.support.annotation.StringRes

/**
 * A wrapper around String resource retrieved via Strings.xml to be used for ViewModel
 * to pass String to the View without requiring Context object
 *
 * @author Jitrapon Tiachunpun
 */
class AndroidString(@StringRes val resId: Int? = null,
                    val formatArgs: Array<String>? = null,
                    val text: String? = null,
                    override val status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
