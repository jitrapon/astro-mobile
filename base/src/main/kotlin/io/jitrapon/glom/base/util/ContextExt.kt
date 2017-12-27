package io.jitrapon.glom.base.util

import android.content.Context
import android.support.annotation.ColorRes
import android.support.annotation.DimenRes
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.widget.Toast
import io.jitrapon.glom.base.model.AndroidString

/**
 * Contains various functions using the Context
 *
 * @author Jitrapon Tiachunpun
 */

/**
 * Show a simple toast message
 */
fun Context.showToast(message: String?) {
    if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/**
 * Show an alert dialog with optional buttons and callbacks
 */
fun Context.showAlertDialog(title: String?, message: String?, positiveOptionText: String? = null,
                            onPositiveOptionClicked: (() -> Unit)? = null, negativeOptionText: String? = null,
                            onNegativeOptionClicked: (() -> Unit)? = null, isCancelable: Boolean = true,
                            onCancel: (() -> Unit)? = null): AlertDialog {
    return AlertDialog.Builder(this).apply {
        if (!title.isNullOrBlank()) {
            setTitle(title)
        }
        setMessage(message)
        if (!positiveOptionText.isNullOrBlank()) {
            setPositiveButton(positiveOptionText, { _, _ ->
                onPositiveOptionClicked?.invoke()
            })
        }
        if (!negativeOptionText.isNullOrBlank()) {
            setNegativeButton(negativeOptionText, { _, _ ->
                onNegativeOptionClicked?.invoke()
            })
        }
        setOnCancelListener {
            onCancel?.invoke()
        }
    }.setCancelable(isCancelable).show()
}

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun Context.getString(string: AndroidString?): String? {
    string ?: return null
    return if (string.text != null) string.text else {
        if (string.resId != null) {
            return if (string.formatArgs != null) getString(string.resId, *string.formatArgs)
            else getString(string.resId)
        }
        return null
    }
}

fun Context.dimen(@DimenRes dimenId: Int): Int = resources.getDimensionPixelSize(dimenId)