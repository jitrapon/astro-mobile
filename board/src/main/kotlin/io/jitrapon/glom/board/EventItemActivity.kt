package io.jitrapon.glom.board

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import io.jitrapon.glom.base.component.GooglePlaceProvider
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.base.util.show
import kotlinx.android.synthetic.main.event_item_activity.*

/**
 * Container activity for expanded board items
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity() {

    /* this activity's viewmodel */
    private lateinit var viewModel: EventItemViewModel

    /* animation delay time in ms before content of this view appears */
    private val SHOW_ANIM_DELAY = 700L

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
            it.setItem(getBoardItemFromIntent())
        }
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        // subscribe to changes in this event
        // when an observable event changes, the whole layout is invalidated
        viewModel.getObservableEvent().observe(this, Observer {
            it?.let {
                event_item_title.setText(it.title)
            }
        })
    }

    private fun addAutocompleteCallbacks(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {
                viewModel.onNameChanged(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    //endregion
    //region other view callbacks

    override fun getCurrentBoardItem(): BoardItem? {
        return viewModel.getCurrentBoardItem()
    }

    override fun onBeginTransitionAnimationStart() {
        event_item_title_til.hint = " "
        event_item_title.clearFocus()
        event_item_clock_icon.hide()
        event_item_start_time.hide()
        event_item_right_icon.hide()
        event_item_end_time.hide()
        event_card_location_icon.hide()
        event_card_location.hide()
    }

    override fun onBeginTransitionAnimationEnd() {
        event_item_clock_icon.show(SHOW_ANIM_DELAY)
        event_item_start_time.show(SHOW_ANIM_DELAY)
        event_item_right_icon.show(SHOW_ANIM_DELAY)
        event_item_end_time.show(SHOW_ANIM_DELAY)
        event_card_location_icon.show(SHOW_ANIM_DELAY)
        event_card_location.show(SHOW_ANIM_DELAY)
    }

    override fun onFinishTransitionAnimationStart() {
        event_item_root_layout.requestFocus()
        event_item_clock_icon.hide()
        event_item_start_time.hide()
        event_item_right_icon.hide()
        event_item_end_time.hide()
        event_card_location_icon.hide()
        event_card_location.hide()
    }

    override fun onFinishTransitionAnimationEnd() {

    }

    //endregion
}
