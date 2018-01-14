package io.jitrapon.glom.board

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import io.jitrapon.glom.base.component.GooglePlaceProvider
import io.jitrapon.glom.base.util.obtainViewModel
import kotlinx.android.synthetic.main.event_item_activity.*

/**
 * Container activity for expanded board items
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity() {

    /* this activity's viewmodel */
    private lateinit var viewModel: EventItemViewModel

    //region lifecycle

    override fun getLayout(): Int = R.layout.event_item_activity

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(EventItemViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tag = "board_item_event"

        // setup all views
        viewModel.let {
            if (it.shouldShowNameAutocomplete()) {
                it.init(GooglePlaceProvider(lifecycle, activity = this))
                addAutocompleteCallbacks(event_item_title)
            }
        }
    }

    private fun addAutocompleteCallbacks(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {
                viewModel.updateName(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    //endregion
    //region other view callbacks

    override fun onActivityWillFinish() {

    }

    override fun onBeginTransitionAnimationStart() {

    }

    override fun onBeginTransitionAnimationEnd() {

    }

    override fun onFinishTransitionAnimationStart() {

    }

    override fun onFinishTransitionAnimationEnd() {

    }

    //endregion
}
