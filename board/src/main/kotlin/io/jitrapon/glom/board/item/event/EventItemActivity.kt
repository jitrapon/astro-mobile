package io.jitrapon.glom.board.item.event

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Selection
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import androidx.lifecycle.Observer
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrencePickerDialog
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.GlomAutoCompleteTextView
import io.jitrapon.glom.base.ui.widget.recyclerview.HorizontalSpaceItemDecoration
import io.jitrapon.glom.base.util.applyState
import io.jitrapon.glom.base.util.clearFocusAndHideKeyboard
import io.jitrapon.glom.base.util.dimen
import io.jitrapon.glom.base.util.doOnTextChanged
import io.jitrapon.glom.base.util.findViewsWithContentDescription
import io.jitrapon.glom.base.util.getString
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.load
import io.jitrapon.glom.base.util.setStyle
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.base.util.showLiteMap
import io.jitrapon.glom.base.util.startActivity
import io.jitrapon.glom.board.Const
import io.jitrapon.glom.board.Const.NAVIGATE_TO_EVENT_PLAN
import io.jitrapon.glom.board.NavigationArguments
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.BoardItemActivity
import io.jitrapon.glom.board.item.BoardItemViewModelStore
import io.jitrapon.glom.board.item.SHOW_ANIM_DELAY
import io.jitrapon.glom.board.item.event.plan.EXTRA_FIRST_VISIBLE_PAGE
import io.jitrapon.glom.board.item.event.plan.PlanEventActivity
import io.jitrapon.glom.board.item.event.preference.EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL
import io.jitrapon.glom.board.item.event.widget.DateTimePicker
import io.jitrapon.glom.board.item.event.widget.STYLE_BOTTOM_SHEET
import io.jitrapon.glom.board.item.event.widget.placepicker.PlacePickerActivity
import io.jitrapon.glom.board.widget.recurrencepicker.RecurrencePicker
import io.jitrapon.glom.board.widget.recurrencepicker.toRepeatInfo
import kotlinx.android.synthetic.main.event_item_activity.event_item_attendees
import kotlinx.android.synthetic.main.event_item_activity.event_item_attendees_action_button_1
import kotlinx.android.synthetic.main.event_item_activity.event_item_attendees_action_button_2
import kotlinx.android.synthetic.main.event_item_activity.event_item_datetime_action_button_1
import kotlinx.android.synthetic.main.event_item_activity.event_item_datetime_action_button_2
import kotlinx.android.synthetic.main.event_item_activity.event_item_datetime_action_button_3
import kotlinx.android.synthetic.main.event_item_activity.event_item_end_time
import kotlinx.android.synthetic.main.event_item_activity.event_item_location_action_button_1
import kotlinx.android.synthetic.main.event_item_activity.event_item_location_action_button_2
import kotlinx.android.synthetic.main.event_item_activity.event_item_location_action_button_3
import kotlinx.android.synthetic.main.event_item_activity.event_item_location_action_button_4
import kotlinx.android.synthetic.main.event_item_activity.event_item_location_primary
import kotlinx.android.synthetic.main.event_item_activity.event_item_location_secondary
import kotlinx.android.synthetic.main.event_item_activity.event_item_location_separator
import kotlinx.android.synthetic.main.event_item_activity.event_item_map
import kotlinx.android.synthetic.main.event_item_activity.event_item_note
import kotlinx.android.synthetic.main.event_item_activity.event_item_root_layout
import kotlinx.android.synthetic.main.event_item_activity.event_item_source_icon
import kotlinx.android.synthetic.main.event_item_activity.event_item_source_text_view
import kotlinx.android.synthetic.main.event_item_activity.event_item_start_time
import kotlinx.android.synthetic.main.event_item_activity.event_item_title
import kotlinx.android.synthetic.main.event_item_activity.event_item_title_til

/**
 * Shows dialog-like UI for viewing and/or editing an event in a board.
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity(),
    OnMapReadyCallback,
    RecurrencePickerDialog.RecurrenceSelectedCallback {

    private lateinit var viewModel: EventItemViewModel

    /* adapter for event autocomplete */
    private var autocompleteAdapter: EventAutoCompleteAdapter? = null

    private val dateTimePicker: DateTimePicker by lazy {
        DateTimePicker(this)
    }

    /* views that will be hidden/shown when transition element is active */
    private var untransitionedViews: ArrayList<View>? = null

    /* TextWatcher for location */
    private var locationTextWatcher: TextWatcher? = null

    /* TextWatcher for note */
    private var noteTextWatcher: TextWatcher? = null

    /* Google Map object */
    private var map: GoogleMap? = null

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
                    isHelperTextEnabled = true
                    helperText = getString(R.string.event_item_name_helper)
                }
            }
            else {
                event_item_title_til.apply {
                    viewModel.validateName(event_item_title.text.toString())
                }
            }
        }
        event_item_start_time.apply {
            setOnClickListener {
                event_item_title.clearFocusAndHideKeyboard()
                viewModel.showDateTimePicker(true)
            }
            setOnDrawableClick {
                viewModel.clearDate(true)
            }
        }
        event_item_end_time.apply {
            setOnClickListener {
                event_item_title.clearFocusAndHideKeyboard()
                viewModel.showDateTimePicker(false)
            }
            setOnDrawableClick {
                viewModel.clearDate(false)
            }
        }
        locationTextWatcher = event_item_location_primary.doOnTextChanged { s, _, _, _ ->
            viewModel.onLocationTextChanged(s)
            event_item_location_primary.setDrawableVisible(s.isNotEmpty())
        }
        event_item_location_primary.apply {
            setOnDrawableClick {
                setText(null, true)
            }
        }
        event_item_attendees.apply {
            adapter = EventItemAttendeeAdapter(this@EventItemActivity, visibleItemCount = 20,
                    userLayoutId = R.layout.user_avatar_with_name, otherLayoutId = R.layout.remaining_indicator_large)
            context?.let {
                addItemDecoration(HorizontalSpaceItemDecoration(it.dimen(io.jitrapon.glom.R.dimen.avatar_spacing)))
            }
        }
        noteTextWatcher = event_item_note.doOnTextChanged { s, _, _, _ ->
            viewModel.onNoteTextChanged(s)
        }

        viewModel.let {
            if (it.shouldShowNameAutocomplete()) {
                addAutocompleteCallbacks(event_item_title)
            }
            it.setItem(getBoardItemFromIntent(), isNewItem())
            addLocationAutocompleteCallbacks(event_item_location_primary)
        }
        event_item_source_text_view.setOnClickListener {
            viewModel.showEventDetailSources()
        }
        event_item_datetime_action_button_1.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
        event_item_datetime_action_button_2.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
        event_item_datetime_action_button_3.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
        event_item_location_action_button_1.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
        event_item_location_action_button_2.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
        event_item_location_action_button_3.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
        event_item_location_action_button_4.setOnClickListener { viewModel.handleActionItem(it.tag,
                *getCommonViewActionArguments().toTypedArray()) }
        event_item_attendees_action_button_1.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
        event_item_attendees_action_button_2.setOnClickListener { viewModel.handleActionItem(it.tag,
            *getCommonViewActionArguments().toTypedArray()) }
    }

    override fun onResume() {
        super.onResume()

        map?.let {
            event_item_map.onResume()
        }
    }

    override fun onPause() {
        super.onPause()

        map?.let {
            event_item_map.onPause()
        }
    }

    override fun onStart() {
        super.onStart()

        map?.let {
            event_item_map.onStart()
        }
    }

    override fun onStop() {
        super.onStop()

        map?.let {
            event_item_map.onStop()
        }
    }

    override fun onDestroy() {
        map?.let {
            event_item_map.onDestroy()
        }

        viewModel.closeItem()
        super.onDestroy()
    }

    override fun onMapReady(map: GoogleMap?) {
        this.map = map ?: return
        with(map) {
            setStyle(this@EventItemActivity, io.jitrapon.glom.R.raw.map_style)
            setOnMapClickListener {
                viewModel.navigateToMap(false)
            }

            // if there is a tag previously, set the location now
            (event_item_map.tag as? LatLng)?.let {
                event_item_map.show()
                showLiteMap(it, EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL)
            }
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
                                isEnabled = true
                                Selection.setSelection(text, text.length)
                                if (filter) {
                                    delayRun(100L) {
                                        showDropDown()
                                    }
                                }
                                event_item_title_til.apply {
                                    isHelperTextEnabled = false
                                    helperText = null
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
                        UiModel.Status.NEGATIVE -> {
                            event_item_title.apply {
                                setText(getString(query), filter)
                                isEnabled = false
                                event_item_title_til.apply {
                                    isHelperTextEnabled = true
                                    helperText = getString(R.string.board_item_not_editable)
                                }
                            }
                        }
                        else -> { /* do nothing */ }
                    }
                }
            })

            // observe on the start date
            getObservableStartDate().observe(this@EventItemActivity, Observer {
                event_item_start_time.apply {
                    text = getString(it)
                    isEnabled = it?.status != UiModel.Status.NEGATIVE
                    it?.let {
                        event_item_start_time.setDrawableVisible(it.status != UiModel.Status.EMPTY && it.status != UiModel.Status.NEGATIVE)
                    }
                }
            })

            // observe on the end date
            getObservableEndDate().observe(this@EventItemActivity, Observer {
                event_item_end_time.apply {
                    text = getString(it)
                    isEnabled = it?.status != UiModel.Status.NEGATIVE
                    it?.let {
                        event_item_end_time.setDrawableVisible(it.status != UiModel.Status.EMPTY && it.status != UiModel.Status.NEGATIVE)
                    }
                }
            })

            // open a datetime picker
            getObservableDateTimePicker().observe(this@EventItemActivity, Observer {
                it?.let { picker ->
                    dateTimePicker.apply {
                        show(picker, onDateTimeSetListener = { startDate, endDate, isFullDay ->
                            viewModel.setDate(startDate, endDate, isFullDay)
                        }, onCancel = {
                            //do nothing
                        }, style = STYLE_BOTTOM_SHEET)
                    }
                }
            })

            // observe on location text
            getObservableLocation().observe(this@EventItemActivity, Observer {
                event_item_location_primary.apply {
                    locationTextWatcher?.let (::removeTextChangedListener)
                    setText(getString(it), false)
                    Selection.setSelection(text, text.length)
                    setDrawableVisible(text.isNotEmpty())
                    locationTextWatcher?.let (::addTextChangedListener)
                    if (it?.status == UiModel.Status.NEGATIVE) {
                        isEnabled = false
                        setDrawableVisible(false)
                    }
                    else {
                        isEnabled = true
                    }
                }
            })

            // observe on location description
            getObservableLocationDescription().observe(this@EventItemActivity, Observer {
                if (it == null) {
                    event_item_location_secondary.hide()
                }
                else {
                    event_item_location_secondary.apply {
                        show()
                        text = getString(it)
                    }
                }
            })

            // observe on location latLng
            getObservableLocationLatLng().observe(this@EventItemActivity, Observer {
                if (it == null) {
                    event_item_map.apply {
                        hide()
                        tag = null
                    }
                }
                else {
                    map.let { map ->
                        if (map != null) event_item_map.show()
                        map?.showLiteMap(it, EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL) ?: event_item_map.apply {
                            event_item_map.tag = it
                            onCreate(null)
                            getMapAsync(this@EventItemActivity)
                        }
                    }
                }
            })

            // observe on title showing number of attendees
            getObservableAttendeeTitle().observe(this@EventItemActivity, Observer {
                event_item_location_separator.setTitle(getString(it))
            })

            // observe on list of attendees
            getObservableAttendees().observe(this@EventItemActivity, Observer {
                (event_item_attendees.adapter as EventItemAttendeeAdapter).setItems(it)
            })

            // observe on event note
            getObservableNote().observe(this@EventItemActivity, Observer {
                event_item_note.apply {
                    isEnabled = it?.status != UiModel.Status.NEGATIVE
                    noteTextWatcher?.let (::removeTextChangedListener)
                    setText(context.getString(it))
                    noteTextWatcher?.let (::addTextChangedListener)
                }
            })

            // observe on navigation event
            getObservableNavigation().observe(this@EventItemActivity, Observer {
                if (it.action == NAVIGATE_TO_EVENT_PLAN) {
                    val (boardItem, isNewItem, firstVisiblePage) = it.payload as Triple<*, *, *>

                    startActivity(PlanEventActivity::class.java, Const.PLAN_EVENT_REQUEST_CODE, {
                        putExtra(Const.EXTRA_BOARD_ITEM, boardItem as EventItem)
                        putExtra(Const.EXTRA_IS_BOARD_ITEM_NEW, isNewItem as Boolean)
                        putExtra(EXTRA_FIRST_VISIBLE_PAGE, firstVisiblePage as Int)
                    }, animTransition = io.jitrapon.glom.R.anim.slide_up to 0)
                }
            })

            // observe on event source change
            getObservableSource().observe(this@EventItemActivity, Observer {
                dialog?.dismiss()

                event_item_source_text_view.apply {
                    text = getString(it?.sourceDescription)
                    isEnabled = it?.status != UiModel.Status.NEGATIVE
                }
                event_item_source_icon.load(this@EventItemActivity, it.sourceIcon)
            })

            // observe on action buttons
            getObservableDateTimeActions().observe(this@EventItemActivity, Observer {
                it.getOrNull(0).let(event_item_datetime_action_button_1::applyState)
                it.getOrNull(1).let(event_item_datetime_action_button_2::applyState)
                it.getOrNull(2).let(event_item_datetime_action_button_3::applyState)
            })
            getObservableLocationActions().observe(this@EventItemActivity, Observer {
                it.getOrNull(0).let(event_item_location_action_button_1::applyState)
                it.getOrNull(1).let(event_item_location_action_button_2::applyState)
                it.getOrNull(2).let(event_item_location_action_button_3::applyState)
                it.getOrNull(3).let(event_item_location_action_button_4::applyState)
            })
            getObservableAttendeesActions().observe(this@EventItemActivity, Observer {
                it.getOrNull(0).let(event_item_attendees_action_button_1::applyState)
                it.getOrNull(1).let(event_item_attendees_action_button_2::applyState)
            })

            // observe on recurrence picker
            getObservableRecurrencePicker().observe(this@EventItemActivity, Observer {
                it?.let {
                    RecurrencePicker().apply {
                        show(supportFragmentManager, it)
                    }
                }
            })
        }
    }

    override fun onRecurrencePickerSelected(r: Recurrence?) {
        viewModel.setEventDetailRecurrence(r.toRepeatInfo())
    }

    override fun onRecurrencePickerCancelled(r: Recurrence?) {
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

    private fun getCommonViewActionArguments(): List<String?> {
        return listOf(
            event_item_title.text?.toString(),
            event_item_location_primary.text?.toString(),
            event_item_note.text?.toString()
        )
    }

    //endregion
    //region other view callbacks

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == Const.PLAN_EVENT_REQUEST_CODE) {
            data?.getParcelableExtra<BoardItem?>(Const.EXTRA_BOARD_ITEM)?.let {
                viewModel.updateEventFromPlan()
            }
        }
        else if (requestCode == Const.PLACE_PICKER_RESULT_CODE) {
//            viewModel.selectPlace()
        }
    }

    override fun onSaveItem(callback: (Triple<BoardItem?, Boolean, Boolean>) -> Unit) {
        viewModel.saveItem {
            callback(it)
        }
    }

    override fun navigate(action: String, payload: Any?) {
        if (Const.NAVIGATE_TO_MAP_SEARCH == action) {
            (payload as? NavigationArguments).let {
                launchMap(it?.latLng, it?.query, it?.placeId, it?.withDirection ?: false)
            }
        }
        else if (Const.NAVIGATE_TO_PLACE_PICKER == action) {
            startActivity(PlacePickerActivity::class.java, Const.PLACE_PICKER_RESULT_CODE, {

            }, animTransition = io.jitrapon.glom.R.anim.slide_up to 0)
        }
    }

    private fun launchMap(latLng: LatLng?, query: String?, placeId: String?, withDirection: Boolean = false) {
        var url = Uri.Builder().apply {
            scheme("https")
            authority("www.google.com")
            appendPath("maps")

            if (withDirection) {
                appendPath("dir")
                appendQueryParameter("api", "1")
                if (latLng != null) {
                    appendQueryParameter("destination", "${latLng.latitude},${latLng.longitude}")
                }
                else if (!query.isNullOrEmpty()){
                    appendQueryParameter("destination", query)
                }
                if (!TextUtils.isEmpty(placeId)) {
                    appendQueryParameter("destination_place_id", placeId)
                }
//                appendQueryParameter("dir_action", "navigate")
            }
            else {
                appendPath("search")
                appendQueryParameter("api", "1")
                if (latLng != null) {
                    appendQueryParameter("query", "${latLng.latitude},${latLng.longitude}")
                }
                else if (!query.isNullOrEmpty()){
                    appendQueryParameter("query", query)
                }
                if (!TextUtils.isEmpty(placeId)) {
                    appendQueryParameter("query_place_id", placeId)
                }
            }
        }.toString()
        val indexOfQueryStart = url.indexOf('?')
        url = StringBuilder(url).insert(indexOfQueryStart, "/").toString()
        startActivity(url)
    }

    /**
     * When this event item is about to expand, we want to show only the title as part
     * of the transition animation
     */
    override fun onBeginTransitionAnimationStart() {
        event_item_title_til.hint = " "         // hide the hint above the text
        event_item_title.clearFocus()
        event_item_root_layout.findViewsWithContentDescription(getString(R.string.event_item_transition_view)) {
            forEach {
                it.hide(null, true)
            }
            untransitionedViews = this
        }
    }

    override fun onBeginTransitionAnimationEnd() {
        untransitionedViews?.forEach {
            it.show(SHOW_ANIM_DELAY)
        }
    }

    override fun onFinishTransitionAnimationStart() {
        event_item_title.setText(viewModel.getPreviousName(), false)
        event_item_root_layout.requestFocus()
        untransitionedViews?.forEach {
            it.hide()
        }
    }

    override fun onFinishTransitionAnimationEnd() {
        //nothing yet
    }

    //endregion
}
