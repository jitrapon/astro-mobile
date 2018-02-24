package io.jitrapon.glom.board.event

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.text.Selection
import android.view.WindowManager
import android.widget.AdapterView
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.DateTimePicker
import io.jitrapon.glom.base.ui.widget.GlomAutoCompleteTextView
import io.jitrapon.glom.base.util.clearFocusAndHideKeyboard
import io.jitrapon.glom.base.util.getString
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemActivity
import io.jitrapon.glom.board.BoardItemViewModelStore
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.event_item_activity.*

/**
 * Shows dialog-like UI for viewing and/or editing an event in a board.
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity() {

    private lateinit var viewModel: EventItemViewModel

    /* adapter for event autocomplete */
    private var autocompleteAdapter: EventAutoCompleteAdapter? = null

    private val dateTimePicker: DateTimePicker by lazy {
        DateTimePicker(this)
    }

    //region lifecycle

    override fun getLayout(): Int = R.layout.event_item_activity

    override fun onCreateViewModel() {
        viewModel = BoardItemViewModelStore.obtainViewModelForItem(EventItem::class.java) as EventItemViewModel
        viewModel.setPlaceProvider(placeProvider)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tag = "board_item_event"

        // setup all views
        event_item_title.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                event_item_title_til.apply {
                    setHelperTextEnabled(true)
                    setHelperText(getString(R.string.event_item_name_helper))
                }
            }
            else {
                event_item_title_til.apply {
                    viewModel.validateName(event_item_title.text.toString())
                }
            }
        }
        event_item_start_time.setOnClickListener {
            event_item_title.clearFocusAndHideKeyboard()
            viewModel.showDateTimePicker(true)
        }
        event_item_end_time.setOnClickListener {
            event_item_title.clearFocusAndHideKeyboard()
            viewModel.showDateTimePicker(false)
        }
        event_item_location.setOnClickListener {

        }

        viewModel.let {
            if (it.shouldShowNameAutocomplete()) {
                addAutocompleteCallbacks(event_item_title)
            }
            it.setItem(getBoardItemFromIntent())
            addLocationAutocompleteCallbacks(event_item_location)
        }
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        viewModel.apply {

            // observe on the name text change
            getObservableName().observe(this@EventItemActivity, Observer {
                it?.let { (query, filter) ->
                    when (query.status) {
                        UiModel.Status.SUCCESS -> {
                            event_item_title.apply {
                                setText(getString(query), filter)
                                Selection.setSelection(text, text.length)
                                if (filter) {
                                    delayRun(100L) {
                                        showDropDown()
                                    }
                                }
                            }
                        }
                        UiModel.Status.ERROR -> {
                            event_item_title_til.apply {
                                isErrorEnabled = true
                                error = getString(query)
                            }
                        }
                        UiModel.Status.EMPTY -> {
                            event_item_title_til.setHelperTextEnabled(false)
                        }
                        else -> { /* do nothing */ }
                    }
                }
            })

            // observe on the start date
            getObservableStartDate().observe(this@EventItemActivity, Observer {
                event_item_start_time.text = getString(it)
            })

            // observe on the end date
            getObservableEndDate().observe(this@EventItemActivity, Observer {
                event_item_end_time.text = getString(it)
            })

            // open a datetime picker
            getObservableDateTimePicker().observe(this@EventItemActivity, Observer {
                it?.let { (picker, isStartDate) ->
                    dateTimePicker.apply {
                        show(picker, onDateTimeSet = {
                            viewModel.setDate(it, isStartDate)
                        }, onCancel = {
                            viewModel.cancelSetDate()
                        })
                    }
                }
            })

            // observe on location text
            getObservableLocation().observe(this@EventItemActivity, Observer {
                it?.let {
                    event_item_location.setText(getString(it), false)
                }
            })
        }
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
            setAdapter(EventAutoCompleteAdapter(viewModel, this@EventItemActivity,
                    android.R.layout.simple_dropdown_item_1line).apply {
                autocompleteAdapter = this
            })

            // on item click listener
            onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _->
                viewModel.selectSuggestion(event_item_title.text, parent.getItemAtPosition(position) as Suggestion)
            }
        }
    }

    private fun addLocationAutocompleteCallbacks(textView: GlomAutoCompleteTextView) {
        textView.apply {

            // disable this textview to replace the entire text with autocomplete item
            shouldReplaceTextOnSelected = false

            // make suggestion dropdown to fit the screen while allowing keyboard to not overlap it
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            // start auto-suggesting from first character
            threshold = 1

            // update the auto-complete list on change callback
            setAdapter(EventAutoCompleteAdapter(viewModel, this@EventItemActivity,
                    android.R.layout.simple_dropdown_item_1line, true).apply {
                autocompleteAdapter = this
            })

            // on item click listener
            onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _->
                viewModel.selectPlace(parent.getItemAtPosition(position) as Suggestion)
            }
        }
    }

    //endregion
    //region other view callbacks

    override fun onSaveItem(callback: (BoardItem?) -> Unit) {
        viewModel.saveItem {
            callback(it)
        }
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
        event_item_time_separator.hide()
        event_item_end_time.hide()
        event_item_location_icon.hide()
        event_item_location.hide()
        event_item_location_separator.hide()
    }

    override fun onBeginTransitionAnimationEnd() {
        event_item_clock_icon.show(SHOW_ANIM_DELAY)
        event_item_start_time.show(SHOW_ANIM_DELAY)
        event_item_time_separator.show(SHOW_ANIM_DELAY)
        event_item_end_time.show(SHOW_ANIM_DELAY)
        event_item_location_icon.show(SHOW_ANIM_DELAY)
        event_item_location.show(SHOW_ANIM_DELAY)
        event_item_location_separator.show(SHOW_ANIM_DELAY)
    }

    override fun onFinishTransitionAnimationStart() {
        event_item_title.setText(viewModel.getPreviousName(), false)
        event_item_root_layout.requestFocus()
        event_item_clock_icon.hide()
        event_item_start_time.hide()
        event_item_time_separator.hide()
        event_item_end_time.hide()
        event_item_location_icon.hide()
        event_item_location.hide()
        event_item_location_separator.hide()
    }

    override fun onFinishTransitionAnimationEnd() {
        //nothing yet
    }

    //endregion
}
