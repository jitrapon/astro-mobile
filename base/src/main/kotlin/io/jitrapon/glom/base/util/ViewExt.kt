package io.jitrapon.glom.base.util

import android.support.design.widget.Snackbar
import android.view.View

/**
 * @author Jitrapon Tiachunpun
 */
/**
 * Show a snackbar message with optional action and callback
 */
fun View.showSnackbar(message: String?, actionMessage: String? = null, actionCallback: (() -> Unit)? = null) {
    if (!message.isNullOrBlank()) {
        if (actionMessage == null) {
            Snackbar.make(this, message!!, Snackbar.LENGTH_LONG).show()
        }
        else {
            Snackbar.make(this, message!!, Snackbar.LENGTH_INDEFINITE).setAction(actionMessage, {
                actionCallback?.invoke()
            }).show()
        }
    }
}