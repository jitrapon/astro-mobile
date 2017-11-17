package io.jitrapon.glom.base.util

import android.support.v4.app.Fragment

/**
 * Various extension functions for Fragment and wrapper around ContextExt to handle nullable
 * Context object
 *
 * @author Jitrapon Tiachunpun
 */

fun Fragment.finish() {
    activity?.finish()
}

fun Fragment.showToast(message: String?) {
    activity?.showToast(message)
}

fun Fragment.showSnackbar(message: String?, actionMessage: String? = null, actionCallback: (() -> Unit)? = null) {
    activity?.let {
        view?.showSnackbar(message, actionMessage, actionCallback)
    }
}

fun Fragment.showAlertDialog(title: String?, message: String?, positiveOptionText: String? = null,
                             onPositiveOptionClicked: (() -> Unit)? = null, negativeOptionText: String? = null,
                             onNegativeOptionClicked: (() -> Unit)? = null, isCancelable: Boolean = true,
                             onCancel: (() -> Unit)? = null) {
    activity?.showAlertDialog(title, message, positiveOptionText, onPositiveOptionClicked, negativeOptionText, onNegativeOptionClicked,
            isCancelable, onCancel)
}
