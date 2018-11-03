package io.jitrapon.glom.base.ui.widget

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.util.color
import io.jitrapon.glom.base.util.colorPrimary
import io.jitrapon.glom.base.util.getString

/**
 * Base button for using throughout the app. Allow additional operations such as
 * disabling upon clicked.
 *
 * Created by Jitrapon
 */
class GlomButton : AppCompatButton {

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    fun applyState(uiModel: ButtonUiModel) {
        isEnabled = uiModel.status != UiModel.Status.LOADING
        uiModel.text?.let {
            text = context.getString(it)
        }
    }

    fun setPositiveTheme() {
        background.setColorFilter(context.colorPrimary(), PorterDuff.Mode.MULTIPLY)
        setTextColor(context.color(io.jitrapon.glom.R.color.white))
    }

    fun setNegativeTheme() {
        background.setColorFilter(context.color(R.color.white), PorterDuff.Mode.MULTIPLY)
        setTextColor(context.colorPrimary())
    }
}