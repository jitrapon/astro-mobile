package io.jitrapon.glom.base.util

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.support.annotation.IdRes
import android.support.annotation.Size
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
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
fun AppCompatActivity.showSnackbar(message: AndroidString, actionMessage: AndroidString? = null,
                                   actionCallback: (() -> Unit)? = null) {
    window.decorView.rootView.showSnackbar(message, actionMessage, actionCallback = actionCallback)
}