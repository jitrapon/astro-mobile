package io.jitrapon.glom.base.ui.widget

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.AutoCompleteTextView

/**
 * TextView that will show autocomplete suggestions as user is typing
 *
 * @author Jitrapon Tiachunpun
 */
class GlomAutoCompleteTextView : AutoCompleteTextView {

    var shouldReplaceTextOnSelected: Boolean = true

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    @TargetApi(Build.VERSION_CODES.O)
    override fun getAutofillType() = View.AUTOFILL_TYPE_NONE

    /**
     * Called when an autocomplete suggestion is selected
     */
    override fun replaceText(text: CharSequence?) {
        if (shouldReplaceTextOnSelected) super.replaceText(text)
    }
}