package io.jitrapon.glom.base.util

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.support.annotation.IdRes
import android.support.annotation.Size
import android.support.v4.app.*
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import io.jitrapon.glom.base.model.AndroidString

/**
 * Various extension functions for AppCompatActivity
 *
 * @author Jitrapon Tiachunpun
 */

fun AppCompatActivity.setupActionBar(toolbar: Toolbar, action: ActionBar.() -> Unit) {
    setSupportActionBar(toolbar)
    supportActionBar?.run {
        action()
    }
}

/**
 * Adds a fragment into the container ViewGroup in this activity, specify toBackStack = true
 * to indicate that this transaction will be remembered, and back behavior is applied
 */
fun AppCompatActivity.addFragment(@IdRes container: Int, fragment: Fragment, fragmentTag: String? = null,
                                  toBackStack: Boolean = false, stateName: String? = null, @Size(4) transitionAnim: Array<Int>? = null) {
    supportFragmentManager.transact {
        transitionAnim?.let {
            setCustomAnimations(it[0], it[1], it[2], it[3])
        }
        replace(container, fragment, fragmentTag)
        if (toBackStack) addToBackStack(stateName)
    }
}

/**
 * Convenient function for retrieving the ViewModel based on its class name
 */
fun <T : ViewModel> AppCompatActivity.obtainViewModel(viewModelClass: Class<T>) =
        ViewModelProviders.of(this, ViewModelProvider.NewInstanceFactory()).get(viewModelClass)

/**
 * Runs a FragmentTransaction, then calls commit().
 */
private inline fun FragmentManager.transact(action: FragmentTransaction.() -> Unit) {
    beginTransaction().apply {
        action()
    }.commit()
}

/**
 * Shows a toast message
 */
fun AppCompatActivity.showToast(message: AndroidString) {
    application?.showToast(message)
}

/**
 * Show a snackbar message with optional action and callback
 */
fun AppCompatActivity.showSnackbar(level: Int, message: AndroidString, actionMessage: AndroidString? = null,
                                   actionCallback: (() -> Unit)? = null) {
    window.decorView.rootView.showSnackbar(level, message, actionMessage, actionCallback = actionCallback)
}


/**
 * Start an activity that can handle view intent with a specified URL
 */
fun AppCompatActivity.startActivity(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
    })
}

fun <T> AppCompatActivity.startActivity(clazz: Class<T>, resultCode: Int?, action: (Intent.() -> Unit)? = null,
                                        sharedElements: List<Pair<View, String>>? = null, animTransition: Pair<Int, Int>? = null) {
    resultCode.let { rs ->
        if (rs == null) {
            startActivity(Intent(this, clazz).apply {
                action?.invoke(this)
            }, sharedElements.let { elements ->
                if (elements.isNullOrEmpty()) null
                else ActivityOptionsCompat.makeSceneTransitionAnimation(this, *(sharedElements!!.map {
                    android.support.v4.util.Pair.create(it.first, it.second)
                }.toTypedArray())).toBundle()
            })
        }
        else {
            ActivityCompat.startActivityForResult(this, Intent(this, clazz).apply {
                action?.invoke(this)
            }, rs, sharedElements.let { elements ->
                if (elements.isNullOrEmpty()) null
                else ActivityOptionsCompat.makeSceneTransitionAnimation(this, *(sharedElements!!.map {
                    android.support.v4.util.Pair.create(it.first, it.second)
                }.toTypedArray())).toBundle()
            })
        }
        animTransition?.let {
            overridePendingTransition(it.first, it.second)
        }
    }
}