package io.jitrapon.glom.board.event

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.text.Selection
import android.view.WindowManager
import android.widget.AdapterView
import io.jitrapon.glom.base.ui.widget.GlomAutoCompleteTextView
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemActivity
import io.jitrapon.glom.board.BoardItemViewModelStore
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.event.autocomplete.EventAutoCompleteAdapter
import io.jitrapon.glom.board.event.autocomplete.Suggestion
import kotlinx.android.synthetic.main.event_item_activity.*

/**
 * Shows dialog-like UI for viewing and/or editing an event in a board.
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity() {

    private lateinit var viewModel: EventItemViewModel

    /* animation delay time in ms before content of this view appears */
    private val SHOW_ANIM_DELAY = 700L

    /* adapter for event autocomplete */
    private var autocompleteAdapter: EventAutoCompleteAdapter? = null

    //region lifecycle

    override fun getLayout(): Int = R.layout.event_item_activity

    override fun onCreateViewModel() {
        viewModel = BoardItemViewModelStore.obtainViewModelForItem(EventItem::class.java) as EventItemViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tag = "board_item_event"

        // setup all views
        viewModel.let {
            if (it.shouldShowNameAutocomplete()) {
                addAutocompleteCallbacks(event_item_title)
            }
            it.setItem(getBoardItemFromIntent())
        }
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        viewModel.getObservableName().observe(this, Observer {
            it?.let { (query, filter) ->
                event_item_title.apply {
                    //TODO differentiate between append vs replace
                    setText(query, filter)
                    Selection.setSelection(text, text.length)
                    if (filter) {
                        delayRun(100L) {
                            showDropDown()
                        }
                    }
                }
            }
        })
    }

    /*
     * TextWatcher for smart auto-complete suggestions when the user
     * begins to type event name
     */
    private fun addAutocompleteCallbacks(textView: GlomAutoCompleteTextView) {
        textView.apply {

            // disable this textview to replace the entire text with autocomplete item
            shouldReplaceTextOnSelected = false

            // make suggestion dropdown to fit the screen while allowing keyboard to not overlap it
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            // start auto-suggesting from first character
            threshold = 1

            // update the auto-complete list on change callback
            setAdapter(EventAutoCompleteAdapter(viewModel,this@EventItemActivity,
                    android.R.layout.simple_dropdown_item_1line).apply {
                autocompleteAdapter = this
            })

            // on item click listener
            onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _->
                viewModel.selectSuggestion(event_item_title.text.toString(), parent.getItemAtPosition(position) as Suggestion)
            }
        }
    }

    //endregion
    //region other view callbacks

    override fun onSaveItem(): BoardItem? {
        return viewModel.saveAndGetItem(event_item_title.text.toString())
    }

    /**
     * When this event item is about to expand, we want to show only the title as part
     * of the transition animation
     */
    override fun onBeginTransitionAnimationStart() {
        event_item_title_til.hint = " "         // hide the hint above the text
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
        event_item_title.setText(viewModel.getPreviousName(), false)
        event_item_root_layout.requestFocus()
        event_item_clock_icon.hide()
        event_item_start_time.hide()
        event_item_right_icon.hide()
        event_item_end_time.hide()
        event_card_location_icon.hide()
        event_card_location.hide()
    }

    override fun onFinishTransitionAnimationEnd() {
        //nothing yet
    }

    //endregion
}
