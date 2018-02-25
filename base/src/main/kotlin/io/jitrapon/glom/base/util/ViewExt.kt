package io.jitrapon.glom.base.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.support.annotation.ColorRes
import android.support.design.widget.Snackbar
import android.text.TextUtils
import android.view.View
import android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION
import android.view.ViewPropertyAnimator
import android.view.inputmethod.InputMethodManager
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.MessageLevel

/**
 * @author Jitrapon Tiachunpun
 */
/**
 * Show a snackbar message with optional action and callback
 */
fun View.showSnackbar(level: Int, message: AndroidString, actionMessage: AndroidString? = null,
                      duration: Int = Snackbar.LENGTH_LONG, actionCallback: (() -> Unit)? = null) {
    val colors = when (level) {
        MessageLevel.INFO -> R.color.calm_blue to R.color.reddish
        MessageLevel.WARNING -> R.color.warning_orange to R.color.reddish
        MessageLevel.ERROR -> R.color.bright_red to R.color.blueish_purple
        MessageLevel.SUCCESS -> R.color.success_green to R.color.white
        else -> null to null
    }
    showStyledSnackbar(message, actionMessage, duration, actionCallback, colors.first, colors.second)
}

private fun View.showStyledSnackbar(message: AndroidString, actionMessage: AndroidString? = null,
                                    duration: Int = Snackbar.LENGTH_LONG, actionCallback: (() -> Unit)? = null,
                                    @ColorRes backgroundColorId: Int? = null, @ColorRes actionColorId: Int? = null) {
    val messageText = context.getString(message)
    if (!TextUtils.isEmpty(messageText)) {
        Snackbar.make(this, messageText!!, duration).apply {
            val actionMessageText = context.getString(actionMessage)
            if (!TextUtils.isEmpty(actionMessageText)) setAction(actionMessageText,  {
                actionCallback?.invoke()
            })
            backgroundColorId?.let {
                view.setBackgroundColor(context.color(it))
            }
            actionColorId?.let {
                setActionTextColor(context.color(it))
            }
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
            alpha = 0.0f
            animate().alpha(1.0f)
                    .setDuration(it)
        }
    }
}

inline fun View.animate(duration: Long, block:(ViewPropertyAnimator.() -> Unit), noinline onAnimationEnd: ((View, Animator) -> Unit)? = null) {
    animate().apply {
        block()
        this.duration = duration
        onAnimationEnd?.let {
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    it.invoke(this@animate, animation)
                }
            })
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

/**
 * Clears the focus on this View and hides the keyboard if shown
 */
fun View.clearFocusAndHideKeyboard() {
    clearFocus()
    val imm: InputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

/**
 * Finds all views in this View that contains the matching content description
 */
inline fun View.findViewsWithContentDescription(description: CharSequence, applyFunction: ArrayList<View>.() -> Unit) {
    ArrayList<View>().apply {
        findViewsWithText(this, description, FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        applyFunction(this)
    }
}