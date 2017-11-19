package io.jitrapon.glom.base.data

/**
 * A UiActionModel class represents UI action events (e.g. snackbar, toast, navigation, alert, etc.).
 * Classes that implements this interface are usually wrapped as LiveEvent.
 *
 * @author Jitrapon Tiachunpun
 */
interface UiActionModel

class Toast(val message: String?) : UiActionModel

class Snackbar(val message: String?, val actionMessage: String? = null, val actionCallback: (() -> Unit)?) : UiActionModel

class Alert(val title: String?, val message: String?, val positiveOptionText: String? = null,
            val onPositiveOptionClicked: (() -> Unit)? = null, val negativeOptionText: String? = null,
            val onNegativeOptionClicked: (() -> Unit)? = null, val isCancelable: Boolean = true,
            val onCancel: (() -> Unit)? = null) : UiActionModel

class Loading(val show: Boolean): UiActionModel