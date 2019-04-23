package io.jitrapon.glom.base.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.instantapps.InstantApps
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.model.Alert
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.EmptyLoading
import io.jitrapon.glom.base.model.Loading
import io.jitrapon.glom.base.model.Navigation
import io.jitrapon.glom.base.model.PresentChoices
import io.jitrapon.glom.base.model.ReloadData
import io.jitrapon.glom.base.model.RequestPermission
import io.jitrapon.glom.base.model.Snackbar
import io.jitrapon.glom.base.model.Toast
import io.jitrapon.glom.base.model.UiActionModel
import io.jitrapon.glom.base.ui.widget.GlomProgressDialog
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.getUngrantedPermissions
import io.jitrapon.glom.base.util.shouldShowRequestPermissionRationale
import io.jitrapon.glom.base.util.showAlertDialog
import io.jitrapon.glom.base.util.showChoiceDialog
import io.jitrapon.glom.base.util.showSnackbar
import io.jitrapon.glom.base.util.showToast
import javax.inject.Inject

const val REQUEST_PERMISSION_RESULT_CODE = 5000

/**
 * Wrapper around Android's AppCompatActivity. Contains convenience functions
 * relating to fragment transactions, activity transitions, Android's new runtime permission handling,
 * analytics, and more. All activities should extend from this class.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseActivity : AppCompatActivity() {

    /* this activity 's swipe refresh layout, if provided */
    private var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout? = null

    /* subclass should overwrite this variable for naming the activity */
    var tag: String = "base"

    /* indicates whether or not this Activity instance is Instant App */
    val isInstantApp: Boolean by lazy {
        InstantApps.isInstantApp(this)
    }

    /* shared handler object */
    val handler: Handler by lazy {
        Handler()
    }

    /* base theme full-screen loading progress dialog */
    val progressDialog: GlomProgressDialog by lazy {
        GlomProgressDialog()
    }

    /* snack bar that is used in this activity */
    var snackBar: com.google.android.material.snackbar.Snackbar? = null

    /* Alert dialog that is displayed */
    var dialog: AlertDialog? = null

    /* shared google place provider */
    @Inject
    lateinit var placeProvider: PlaceProvider

    /* callback for when permission request is granted */
    private var permissionsGrantCallback: ((Array<out String>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ObjectGraph.component.inject(this)

        onCreateViewModel()
        onSubscribeToObservables()

        // if we're being restored from a previous state,
        // then we don't need to do anything and should return or else
        // we could end up with overlapping fragments
        savedInstanceState ?: onCreateFragment(savedInstanceState)
    }

    /**
     * Common UI Action handlers for all child activities
     */
    private val viewActionHandler: Observer<UiActionModel> = Observer {
        it?.let {
            when (it) {
                is Toast -> showToast(it.message)
                is Snackbar -> if (it.shouldDismiss) snackBar?.dismiss() else showSnackbar(it.level, it.message, it.actionMessage, it.duration, it.actionCallback).apply {
                    snackBar = this
                }
                is Alert -> showAlertDialog(it.title, it.message, it.positiveOptionText, it.onPositiveOptionClicked,
                        it.negativeOptionText, it.onNegativeOptionClicked, it.isCancelable, it.onCancel)
                is Loading -> showLoading(it.show)
                is EmptyLoading -> showEmptyLoading(it.show)
                is Navigation -> navigate(it.action, it.payload)
                is ReloadData -> onRefresh(it.delay)
                is RequestPermission -> showRequestPermissionsDialog(it.rationaleMessage, it.permissions, it.onRequestPermission)
                is PresentChoices -> showChoiceDialog(it.title, it.items, it.onItemClick).apply {
                    dialog = this
                }
                else -> {
                    AppLogger.w("This ViewAction is is not yet supported by this handler")
                }
            }
        }
    }

    /**
     * Called when one or more instances of fragments have to be created.
     * Only called if savedInstanceState is NULL
     */
    open fun onCreateFragment(savedInstanceState: Bundle?) {}

    /**
     * Called when one or more ViewModel instances should be created
     */
    open fun onCreateViewModel() {}

    /**
     * Subscribe to LiveData and LiveEvent from the ViewModel
     */
    open fun onSubscribeToObservables() {}

    /**
     * Should be called by child class to handle all view action observables automatically
     */
    protected fun subscribeToViewActionObservables(observableViewAction: LiveData<UiActionModel>) {
        observableViewAction.observe(this, viewActionHandler)
    }

    /**
     * Called when a RefreshLayout has been triggered manually by the user. This is a good time
     * to call any necessary ViewModel's function to (re)-load the data
     */
    open fun onRefresh(delayBeforeRefresh: Long) {}

    /**
     * Indicates that the view has no data and should be showing the main loading progress bar.
     * Override this function to make it behave differently
     */
    open fun showEmptyLoading(show: Boolean) {
        getEmptyLoadingView()?.let {
            it.visibility = if (show) View.VISIBLE else View.GONE

            // if triggered manually by swipe refresh layout, hide it. We don't need to show
            // two loading icons
            if (show) showLoading(false)
        }
        // if somehow the refreshlayout is still loading, set it to hide
        if (!show) showLoading(false)
    }

    /**
     * Returns a loading progress bar shown when the view is empty and is about to load a data
     */
    open fun getEmptyLoadingView(): ProgressBar? = null

    /**
     * Indicates that the view is loading some data. Override this function to make it behave differently
     */
    open fun showLoading(show: Boolean) {
        refreshLayout?.let {
            if (show) {
                if (!it.isRefreshing) it.isRefreshing = true
            }
            else {
                if (it.isRefreshing) it.isRefreshing = false
            }
        }
    }

    /**
     * Overrides this function to allow handling of navigation events
     */
    open fun navigate(action: String, payload: Any?) {}

    /**
     * Overrides this function to change the behavior to show the permission dialog
     */
    open fun showRequestPermissionsDialog(
        rationaleMessage: AndroidString,
        permissions: Array<out String>,
        onPermissionsGranted: (ungrantedPermissions: Array<out String>) -> Unit) {
        // check to make sure that the permissions are actually not granted
        val ungrantedPermissions = getUngrantedPermissions(permissions)
        permissionsGrantCallback = onPermissionsGranted
        if (ungrantedPermissions.isNotEmpty()) {
            // not all permissions are granted
            // should we show an explanation?
            if (shouldShowRequestPermissionRationale(ungrantedPermissions)) {
                showAlertDialog(null, rationaleMessage, AndroidString(android.R.string.yes), {
                    showRequestPermissionsDialog(rationaleMessage, permissions, onPermissionsGranted)
                }, AndroidString(android.R.string.no), {
                    onPermissionsGranted(ungrantedPermissions)
                }, true, {
                    onPermissionsGranted(ungrantedPermissions)
                })
            }
            // no explanation needed
            else {
                ActivityCompat.requestPermissions(this, ungrantedPermissions, REQUEST_PERMISSION_RESULT_CODE)
            }
        }

        // all permissions have been granted
        else {
            onPermissionsGranted(ungrantedPermissions)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISSION_RESULT_CODE -> {
                val ungrantedPermissions = arrayListOf<String>()
                if (grantResults.isNotEmpty()) {
                    for (i in grantResults.indices) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            ungrantedPermissions.add(permissions[i])
                        }
                    }
                    permissionsGrantCallback?.invoke(ungrantedPermissions.toTypedArray())
                }
            }
        }
    }

    /**
     * Wrapper around Android's handler to delay run a Runnable on the main thread
     */
    fun delayRun(delay: Long, block: (Handler) -> Unit) {
        handler.postDelayed({
            block(handler)
        }, delay)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                supportFinishAfterTransition()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
