package io.jitrapon.glom.board.event

import android.arch.lifecycle.Observer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Selection
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.GlomAutoCompleteTextView
import io.jitrapon.glom.base.ui.widget.recyclerview.HorizontalSpaceItemDecoration
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.*
import io.jitrapon.glom.board.Const.NAVIGATE_TO_EVENT_PLAN
import io.jitrapon.glom.board.event.widget.DateTimePicker
import kotlinx.android.synthetic.main.event_item_activity.*

/**
 * Shows dialog-like UI for viewing and/or editing an event in a board.
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity(), OnMapReadyCallback {

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

    companion object {

        private const val CAMERA_ZOOM_LEVEL = 15f

        /**
         * Displays a LatLng location on a
         * {@link com.google.android.gms.maps.GoogleMap}.
         * Adds a marker and centers the camera on the location with the normal map type.
         */
        private fun showMap(mapView: MapView, map: GoogleMap, data: LatLng) {
            mapView.show()

            // Add a marker for this item and set the camera
            map.apply {
                // Add a marker for this item and set the camera
                moveCamera(CameraUpdateFactory.newLatLngZoom(data, CAMERA_ZOOM_LEVEL))
                addMarker(MarkerOptions().position(data))

                // Set the map type back to normal.
                mapType = GoogleMap.MAP_TYPE_NORMAL
            }
        }

        private fun clearMap(mapView: MapView, map: GoogleMap?) {
            // clear the map and free up resources by changing the map type to none
            mapView.hide()
            map?.apply {
                // clear the map and free up resources by changing the map type to none
                clear()
                mapType = GoogleMap.MAP_TYPE_NONE
            }
        }
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
        event_item_start_time.apply {
            setOnClickListener {
                event_item_title.clearFocusAndHideKeyboard()
                viewModel.showDateTimePicker(true)
            }
            setOnDrawableClick {
                viewModel.setDate(null, true)
            }
        }
        event_item_end_time.apply {
            setOnClickListener {
                event_item_title.clearFocusAndHideKeyboard()
                viewModel.showDateTimePicker(false)
            }
            setOnDrawableClick {
                viewModel.setDate(null, false)
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
        event_item_join_button.setOnClickListener {
            viewModel.setEventDetailAttendStatus()
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
        event_item_plan_button.setOnClickListener {
            viewModel.showEventDetailPlan(event_item_title.text.toString())
        }
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

        super.onDestroy()
    }

    override fun onMapReady(map: GoogleMap) {
        this.map = map
        this.map?.let {
            try {
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)).let {
                    if (!it) AppLogger.w("Google Maps custom style parsing failed")
                }
            }
            catch (ex: Exception) {
                AppLogger.w(ex)
            }

            // disable ui
            map.uiSettings.isMapToolbarEnabled = false

            // on click listener
            it.setOnMapClickListener {
                viewModel.navigateToMap()
            }

            // if there is a tag previously, set the location now
            event_item_map.tag?.let {
                showMap(event_item_map, map, it as LatLng)
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
                it?.let {
                    event_item_start_time.setDrawableVisible(it.status != UiModel.Status.EMPTY)
                }
            })

            // observe on the end date
            getObservableEndDate().observe(this@EventItemActivity, Observer {
                event_item_end_time.text = getString(it)
                it?.let {
                    event_item_end_time.setDrawableVisible(it.status != UiModel.Status.EMPTY)
                }
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
                    event_item_location_primary.apply {
                        locationTextWatcher?.let (::removeTextChangedListener)
                        setText(getString(it), false)
                        Selection.setSelection(text, text.length)
                        setDrawableVisible(text.isNotEmpty())
                        locationTextWatcher?.let (::addTextChangedListener)
                    }
                }
            })

            // observe on location description
            getObservableLocationDescription().observe(this@EventItemActivity, Observer {
                it.let {
                    if (it == null) {
                        event_item_location_secondary.hide()
                    }
                    else {
                        event_item_location_secondary.apply {
                            show()
                            text = getString(it)
                        }
                    }
                }
            })

            // observe on location latlng
            getObservableLocationLatLng().observe(this@EventItemActivity, Observer {
                it.let { latlng ->
                    if (latlng == null) {
                        clearMap(event_item_map, map)
                    }
                    else {
                        if (map == null) {
                            event_item_map.apply {
                                onCreate(null)
                                getMapAsync(this@EventItemActivity)
                            }
                        }
                        map.let {
                            // if map is not ready, set the tag, and onMapReady() will receive the latlng in the tag
                            if (it == null) {
                                event_item_map.tag = latlng
                            }
                            else {
                                showMap(event_item_map, it, latlng)
                            }
                        }
                    }
                }
            })

            // observe on title showing number of attendees
            getObservableAttendeeTitle().observe(this@EventItemActivity, Observer {
                it?.let {
                    event_item_location_separator.setTitle(getString(it))
                }
            })

            // observe on list of attendees
            getObservableAttendees().observe(this@EventItemActivity, Observer {
                it?.let {
                    (event_item_attendees.adapter as EventItemAttendeeAdapter).setItems(it)
                }
            })

            // observe on plan status
            getObservablePlanStatus().observe(this@EventItemActivity, Observer {
                it?.let(event_item_plan_button::applyState)
            })

            // observe on attend status
            getObservableAttendStatus().observe(this@EventItemActivity, Observer {
                it?.let(event_item_join_button::applyState)
            })

            // observe on event note
            getObservableNote().observe(this@EventItemActivity, Observer {
                event_item_note.apply {
                    noteTextWatcher?.let (::removeTextChangedListener)
                    setText(context.getString(it))
                    noteTextWatcher?.let (::addTextChangedListener)
                }
            })

            // observe on navigation event
            getObservableNavigation().observe(this@EventItemActivity, Observer {
                it?.let {
                    if (it.action == NAVIGATE_TO_EVENT_PLAN) {
                        val (boardItem, isNewItem) = it.payload as Pair<*, *>

                        startActivity(PlanEventActivity::class.java, Const.PLAN_EVENT_RESULT_CODE, {
                            putExtra(Const.EXTRA_BOARD_ITEM, boardItem as EventItem)
                            putExtra(Const.EXTRA_IS_BOARD_ITEM_NEW, isNewItem as Boolean)
                        }, animTransition = io.jitrapon.glom.R.anim.slide_up to 0)
                    }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Const.PLAN_EVENT_RESULT_CODE) {
            data?.getParcelableExtra<BoardItem?>(Const.EXTRA_BOARD_ITEM)?.let {
                viewModel.apply {
                    updateEventDetailAttendStatus()
                    updateEventDetailDate()
                }
            }
        }
    }

    override fun onSaveItem(callback: (Triple<BoardItem?, Boolean, Boolean>) -> Unit) {
        viewModel.saveItem {
            callback(it)
        }
    }

    override fun navigate(action: String, payload: Any?) {
        if (Const.NAVIGATE_TO_MAP_SEARCH == action) {
            (payload as? Triple<*,*,*>).let {
                launchMap(it?.first as? LatLng, it?.second as? String, it?.third as? String)
            }
        }
    }

    private fun launchMap(latLng: LatLng?, query: String?, placeId: String?) {
        var url = Uri.Builder().apply {
            scheme("https")
            authority("www.google.com")
            appendPath("maps")
            appendPath("search")
            appendQueryParameter("api", "1")
            if (latLng != null) {
                appendQueryParameter("query", "${latLng.latitude},${latLng.longitude}")
            }
            else {
                appendQueryParameter("query", if (TextUtils.isEmpty(query)) "place" else query)
            }
            if (!TextUtils.isEmpty(placeId)) {
                appendQueryParameter("query_place_id", placeId)
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
                it.hide()
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
