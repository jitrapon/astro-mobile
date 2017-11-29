package io.jitrapon.glom.util

import android.support.design.widget.Snackbar
import android.view.View

/**
 * @author Jitrapon Tiachunpun
 */
/**
 * Show a snackbar message with optional action and callback
 */
fun View.showSnackbar(message: String?, resId: Int?, actionMessage: String? = null, actionCallback: (() -> Unit)? = null) {
    val text = if (!message.isNullOrBlank()) message!! else if (resId != null) context.getString(resId) else return
    if (actionMessage == null) {
        Snackbar.make(this, text, Snackbar.LENGTH_LONG).show()
    }
    else {
        Snackbar.make(this, text, Snackbar.LENGTH_INDEFINITE).setAction(actionMessage, {
            actionCallback?.invoke()
        }).show()
    }
}

/**
 * Toggles the visibility of the view to be VISIBLE
 */
fun View.show() {
    visibility = View.VISIBLE
}

/**
 * Toggles the visibility of the view to be GONE
 */
fun View.hide() {
    visibility = View.GONE
}