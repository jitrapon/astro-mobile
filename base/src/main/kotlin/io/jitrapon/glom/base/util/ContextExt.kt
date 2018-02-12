package io.jitrapon.glom.base.util

import android.content.Context
import android.support.annotation.ColorRes
import android.support.annotation.DimenRes
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.text.TextUtils
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
fun Context.showToast(message: AndroidString) {
    val text = getString(message)
    if (!TextUtils.isEmpty(text)) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}

/**
 * Show an alert dialog with optional buttons and callbacks
 */
fun Context.showAlertDialog(title: AndroidString?, message: AndroidString, positiveOptionText: AndroidString? = null,
                            onPositiveOptionClicked: (() -> Unit)? = null, negativeOptionText: AndroidString? = null,
                            onNegativeOptionClicked: (() -> Unit)? = null, isCancelable: Boolean = true,
                            onCancel: (() -> Unit)? = null): AlertDialog {
    return AlertDialog.Builder(this).apply {
        val titleText = getString(title)
        val messageText = getString(message)
        val positive = getString(positiveOptionText)
        val negative = getString(negativeOptionText)
        if (!TextUtils.isEmpty(titleText)) setTitle(titleText)
        if (!TextUtils.isEmpty(messageText)) setMessage(messageText)
        if (!TextUtils.isEmpty(positive)) {
            setPositiveButton(positive, { _, _ ->
                onPositiveOptionClicked?.invoke()
            })
        }
        if (!TextUtils.isEmpty(negative)) {
            setNegativeButton(negative, { _, _ ->
                onNegativeOptionClicked?.invoke()
            })
        }
        setOnCancelListener {
            onCancel?.invoke()
        }
    }.setCancelable(isCancelable).show()
}

/**
 * Convenience wrapper to retrieve @ColorInt integer from Color ID
 */
fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

/**
 * Converts an AndroidString object into a CharSequence, ready for display
 */
fun Context.getString(string: AndroidString?): CharSequence? {
    string ?: return null
    return if (!TextUtils.isEmpty(string.text)) string.text else {
        if (string.resId != null) {
            return if (string.formatArgs != null) getString(string.resId, *string.formatArgs)
            else getString(string.resId)
        }
        return null
    }
}

/**
 * Convenience wrapper to retrieve Integer in Pixel from @DimenRes
 */
fun Context.dimen(@DimenRes dimenId: Int): Int = resources.getDimensionPixelSize(dimenId)