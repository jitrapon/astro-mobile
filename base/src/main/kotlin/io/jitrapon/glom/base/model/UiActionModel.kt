package io.jitrapon.glom.base.model

import com.google.android.material.snackbar.Snackbar

/**
 * A UiActionModel class represents UI action events (e.g. snackbar, toast, navigation, alert, etc.).
 * Classes that implements this interface are usually wrapped as LiveEvent.
 *
 * @author Jitrapon Tiachunpun
 */
interface UiActionModel

class Toast(val message: AndroidString) : UiActionModel

class Snackbar(val message: AndroidString, val actionMessage: AndroidString? = null,
               val actionCallback: (() -> Unit)? = null, val level: Int = MessageLevel.INFO,
               val duration: Int = Snackbar.LENGTH_LONG, val shouldDismiss: Boolean = false) : UiActionModel

class Alert(val title: AndroidString? = null, val message: AndroidString, val positiveOptionText: AndroidString? = null,
            val onPositiveOptionClicked: (() -> Unit)? = null, val negativeOptionText: AndroidString? = null,
            val onNegativeOptionClicked: (() -> Unit)? = null, val isCancelable: Boolean,
            val onCancel: (() -> Unit)? = null) : UiActionModel

class Loading(val show: Boolean): UiActionModel

class EmptyLoading(val show: Boolean): UiActionModel

class Navigation(val action: String, val payload: Any? = null) : UiActionModel

class ReloadData(val delay: Long) : UiActionModel

object MessageLevel {

    const val INFO = 0
    const val SUCCESS = 1
    const val WARNING = 2
    const val ERROR = 3
}
