package io.jitrapon.glom.base.util

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
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
                                                     sharedElements: List<Pair<View, String>>? = null, animTransition: Pair<Int, Int>? = null) {
    activity?.let {
        resultCode.let { resultCode ->
            if (resultCode == null) {
                startActivity(Intent(it, clazz).apply {
                    action?.invoke(this)
                }, sharedElements.let { elements ->
                    if (elements.isNullOrEmpty()) null
                    else ActivityOptionsCompat.makeSceneTransitionAnimation(it, *(sharedElements!!.map {
                        androidx.core.util.Pair.create(it.first, it.second)
                    }.toTypedArray())).toBundle()
                })
            }
            else {
                startActivityForResult(Intent(it, clazz).apply {
                    action?.invoke(this)
                }, resultCode, sharedElements.let { elements ->
                    if (elements.isNullOrEmpty()) null
                    else ActivityOptionsCompat.makeSceneTransitionAnimation(it, *(sharedElements!!.map {
                        androidx.core.util.Pair.create(it.first, it.second)
                    }.toTypedArray())).toBundle()
                })
            }
            animTransition?.let {
                activity!!.overridePendingTransition(it.first, it.second)
            }
        }
    }
}

/**
 * Start an activity that can handle view intent with a specified URL
 */
fun Fragment.startActivity(url: String) {
    activity?.let {
        startActivity(Intent(ACTION_VIEW).apply {
            data = Uri.parse(url)
        })
    }
}