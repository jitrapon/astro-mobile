package io.jitrapon.glom.base.model

/**
 * A UiActionModel class represents UI action events (e.g. snackbar, toast, navigation, alert, etc.).
 * Classes that implements this interface are usually wrapped as LiveEvent.
 *
 * @author Jitrapon Tiachunpun
 */
interface UiActionModel

class Toast(val message: AndroidString) : UiActionModel

class Snackbar(val message: AndroidString, val actionMessage: AndroidString? = null, val actionCallback: (() -> Unit)? = null) : UiActionModel

class Alert(val title: AndroidString? = null, val message: AndroidString, val positiveOptionText: AndroidString? = null,
            val onPositiveOptionClicked: (() -> Unit)? = null, val negativeOptionText: AndroidString? = null,
            val onNegativeOptionClicked: (() -> Unit)? = null, val isCancelable: Boolean,
            val onCancel: (() -> Unit)? = null) : UiActionModel

class Loading(val show: Boolean): UiActionModel

class EmptyLoading(val show: Boolean): UiActionModel