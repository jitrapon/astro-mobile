package io.jitrapon.glom.base.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.support.design.widget.Snackbar
import android.text.TextUtils
import android.view.View
import io.jitrapon.glom.base.model.AndroidString

/**
 * @author Jitrapon Tiachunpun
 */
/**
 * Show a snackbar message with optional action and callback
 */
fun View.showSnackbar(message: AndroidString, actionMessage: AndroidString? = null,
                      duration: Int = Snackbar.LENGTH_LONG, actionCallback: (() -> Unit)? = null) {
    val messageText = context.getString(message)
    if (!TextUtils.isEmpty(messageText)) {
        Snackbar.make(this, messageText!!, duration).apply {
            val actionMessageText = context.getString(actionMessage)
            if (!TextUtils.isEmpty(actionMessageText)) setAction(actionMessageText,  {
                actionCallback?.invoke()
            })
            show()
        }
    }
}

/**
 * Toggles the visibility of the view to be VISIBLE, with optional parameter to allow fade-in animation
 *
 * @param animateDuration The duration in milliseconds until this view fades in completely
 */
fun View.show(animateDuration: Long? = null) {
    animateDuration.let {
        if (visibility != View.VISIBLE) visibility = View.VISIBLE
        if (it == null) {
            if (alpha != 1.0f) alpha = 1.0f else {}
        }
        else {
            animate().alpha(1.0f)
                    .setDuration(it)
        }
    }
}

/**
 * Toggles the visibility of the view to be GONE, with optional parameter to allow fade-out animation
 *
 * @param animateDuration The duration in milliseconds until this view fades out completely, after which its visibility
 * will be set to GONE
 */
fun View.hide(animateDuration: Long? = null) {
    animateDuration.let {
        if (it == null) {
            if (visibility != View.GONE) visibility = View.GONE else {}
        }
        else {
            animate().alpha(0.0f)
                    .setDuration(it)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            visibility = View.GONE
                        }}
                    )
        }
    }
}