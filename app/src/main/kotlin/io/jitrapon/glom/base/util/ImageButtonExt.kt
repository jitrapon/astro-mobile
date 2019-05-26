package io.jitrapon.glom.base.util

import android.view.View
import android.widget.ImageButton
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.UiModel

fun ImageButton.applyState(uiModel: ButtonUiModel?) {
    if (uiModel == null) {
        visibility = View.GONE
    }
    else {
        visibility = View.VISIBLE
        isEnabled = uiModel.status != UiModel.Status.LOADING
        uiModel.drawable?.resId?.let {
            loadFromResource(it)
            tint(if (uiModel.status == UiModel.Status.POSITIVE) context.colorPrimary() else context.color(R.color.warm_grey))
        }
        tag = uiModel.tag
    }
}