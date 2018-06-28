package io.jitrapon.glom.base.ui.widget

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import androidx.content.systemService
import io.jitrapon.glom.R

/**
 * A progress bar that can be added to an activity
 *
 * Created by Jitrapon
 */
class GlomProgressDialog {

    private var dialog: Dialog? = null

    fun show(context: Context, onCancelListener: DialogInterface.OnCancelListener? = null): Dialog {
        val view = context.systemService<LayoutInflater>().inflate(R.layout.progress_dialog, null)
        return Dialog(context, R.style.Theme_Glom_ProgressDialog).apply {
            setContentView(view)
            onCancelListener.let {
                if (it == null) {
                    setCancelable(true)
                    setOnCancelListener(onCancelListener)
                }
                else setCancelable(false)
            }
            setCanceledOnTouchOutside(false)
            show()
            dialog = this
        }
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
