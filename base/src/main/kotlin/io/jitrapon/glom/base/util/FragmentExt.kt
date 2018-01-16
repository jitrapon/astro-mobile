package io.jitrapon.glom.base.util

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.support.annotation.ColorRes
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.util.Pair
import android.view.View
import io.jitrapon.glom.base.model.AndroidString

/**
 * Various extension functions for Fragment and wrapper around ContextExt to handle nullable
 * Context object
 *
 * @author Jitrapon Tiachunpun
 */

fun Fragment.finish() {
    activity?.finish()
}

fun Fragment.showToast(message: AndroidString) {
    activity?.application?.showToast(message)
}

fun Fragment.showSnackbar(level: Int, message: AndroidString, actionMessage: AndroidString?, actionCallback: (() -> Unit)?) {
    activity?.let {
        view?.showSnackbar(level, message, actionMessage, actionCallback = actionCallback)
    }
}

fun Fragment.showAlertDialog(title: AndroidString?, message: AndroidString, positiveOptionText: AndroidString?,
                             onPositiveOptionClicked: (() -> Unit)?, negativeOptionText: AndroidString?,
                             onNegativeOptionClicked: (() -> Unit)?, isCancelable: Boolean,
                             onCancel: (() -> Unit)?) {
    activity?.showAlertDialog(title, message, positiveOptionText, onPositiveOptionClicked, negativeOptionText, onNegativeOptionClicked,
            isCancelable, onCancel)
}

/**
 * Convenient function for retrieving the ViewModel based on its class name
 */
fun <T : ViewModel> Fragment.obtainViewModel(viewModelClass: Class<T>, activity: FragmentActivity? = null): T {
    return if (activity != null) ViewModelProviders.of(activity, ViewModelProvider.NewInstanceFactory()).get(viewModelClass)
    else ViewModelProviders.of(this, ViewModelProvider.NewInstanceFactory()).get(viewModelClass)
}

fun Fragment.color(@ColorRes colorRes: Int) = context?.color(colorRes)

/**
 * Start an activity specifying destination class and optional block of code to run
 */
fun <T> Fragment.startActivity(clazz: Class<T>, resultCode: Int?, action: (Intent.() -> Unit)? = null,
                               sharedElements: List<Pair<View, String>>? = null) {
    activity?.let {
        resultCode.let { resultCode ->
            if (resultCode == null) {
                startActivity(Intent(it, clazz).apply {
                    action?.invoke(this)
                }, sharedElements.let { elements ->
                    if (elements.isNullOrEmpty()) null
                    else ActivityOptionsCompat.makeSceneTransitionAnimation(it, *(sharedElements!!.toTypedArray())).toBundle()
                })
            }
            else {
                startActivityForResult(Intent(it, clazz).apply {
                    action?.invoke(this)
                }, resultCode, sharedElements.let { elements ->
                    if (elements.isNullOrEmpty()) null
                    else ActivityOptionsCompat.makeSceneTransitionAnimation(it, *(sharedElements!!.toTypedArray())).toBundle()
                })
            }
        }
    }
}