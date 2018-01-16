package io.jitrapon.glom.board

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.support.v4.app.Fragment
import android.support.v4.util.Pair
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import io.jitrapon.glom.base.component.loadFromResource
import io.jitrapon.glom.base.ui.widget.recyclerview.HorizontalSpaceItemDecoration
import io.jitrapon.glom.base.ui.widget.stickyheader.StickyHeaders
import io.jitrapon.glom.base.util.*

/**
 * RecyclerView's Adapter for the board items
 *
 * Created by Jitrapon on 11/26/2017.
 */
class BoardItemAdapter(private val viewModel: BoardViewModel, private val fragment: Fragment) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        StickyHeaders, StickyHeaders.ViewSetup, LifecycleObserver {

    /* https://medium.com/@mgn524/optimizing-nested-recyclerview-a9b7830a4ba7 */
    private val attendeesRecycledViewPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool()

    companion object {

        private const val CAMERA_ZOOM_LEVEL = 15f
        private const val VISIBLE_ATTENDEE_AVATARS = 3

        /**
         * Displays a LatLng location on a
         * {@link com.google.android.gms.maps.GoogleMap}.
         * Adds a marker and centers the camera on the location with the normal map type.
         */
        private fun setMapLocation(map: GoogleMap, data: LatLng) {
            // Add a marker for this item and set the camera
            map.apply {
                // Add a marker for this item and set the camera
                moveCamera(CameraUpdateFactory.newLatLngZoom(data, CAMERA_ZOOM_LEVEL))
                addMarker(MarkerOptions().position(data))

                // Set the map type back to normal.
                mapType = GoogleMap.MAP_TYPE_NORMAL
            }
        }
    }

    /**
     * Set containing MapView objects that are created programmatically
     * in the ViewHolder instances
     */
    private val mapViews: HashSet<MapView> = HashSet()

    /**
     * Initializes the life cycle object from the constructor to be notified of any change in the
     * state in the life cycle
     */
    private var lifeCycle: Lifecycle = fragment.lifecycle.apply {
        addObserver(this@BoardItemAdapter)
    }

    //region lifecycle

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onViewResumed() {
        mapViews.forEach {
            it.onResume()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onViewPaused() {
        mapViews.forEach {
            it.onPause()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onViewDestroyed() {
        mapViews.forEach {
            it.onDestroy()
        }
        mapViews.clear()
        lifeCycle.removeObserver(this)
    }

    //endregion
    //region adapter callbacks

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        viewModel.getBoardItemUiModel(position)?.let {
            when (holder) {
                is HeaderItemViewHolder -> bindHeaderItem(it as HeaderItemUiModel, holder)
                is EventItemViewHolder -> bindEventItem(it as EventItemUiModel, holder)
                else -> {}
            }
        }
    }

    /**
     * @param payloads A non-null list of merged payloads. Can be empty list if requires full
     *                 update.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int, payloads: List<Any>) {
        viewModel.getBoardItemUiModel(position)?.let {
            when (holder) {
                is HeaderItemViewHolder -> bindHeaderItem(it as HeaderItemUiModel, holder)
                is EventItemViewHolder -> bindEventItem(it as EventItemUiModel, holder, payloads)
                else -> {}
            }
        }
    }

    private fun bindHeaderItem(item: HeaderItemUiModel, holder: HeaderItemViewHolder) {
        holder.apply {
            text.text = text.context.getString(item.text)
        }
    }

    private fun bindEventItem(item: EventItemUiModel, holder: EventItemViewHolder, payloads: List<Any>? = null) {
        holder.apply {
            itemId = item.itemId
            if (payloads.isNullOrEmpty()) {
                updateTitle(item)
                updateDateTime(item)
                updateLocation(item)
                updateMap(item)
                updateAttendees(item)
                updateAttendStatus(item)
            }
            else {
                val fields = payloads!!.first() as List<*>
                for (field in fields) {
                    when (field as Int) {
                        EventItemUiModel.TITLE -> { updateTitle(item) }
                        EventItemUiModel.DATETIME -> { updateDateTime(item) }
                        EventItemUiModel.LOCATION -> { updateLocation(item) }
                        EventItemUiModel.MAPLATLNG -> { updateMap(item) }
                        EventItemUiModel.ATTENDEES -> { updateAttendees(item) }
                        EventItemUiModel.ATTENDSTATUS-> { updateAttendStatus(item) }
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is EventItemViewHolder) {
            holder.apply {
                clearMapView()
            }
        }
    }

    override fun getItemCount(): Int = viewModel.getBoardItemCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        return when (viewType) {
            BoardItemUiModel.TYPE_EVENT -> EventItemViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.board_item_event, parent, false), attendeesRecycledViewPool, viewModel::selectItem)
            BoardItemUiModel.TYPE_HEADER -> HeaderItemViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.board_item_header, parent, false))
            else -> null
        }
    }

    override fun getItemViewType(position: Int): Int = viewModel.getBoardItemType(position)

    override fun isStickyHeader(position: Int): Boolean {
        return viewModel.getBoardItemType(position) == BoardItemUiModel.TYPE_HEADER
    }

    override fun setupStickyHeaderView(stickyHeader: View) {
        stickyHeader.translationZ = stickyHeader.context.resources.getDimension(R.dimen.card_header_item_stuck_elevation)
    }

    override fun teardownStickyHeaderView(stickyHeader: View) {
        stickyHeader.translationZ = 0f
    }

    //endregion
    //region view holder classes

    inner class HeaderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val text: TextView = itemView.findViewById(R.id.board_item_header_text)
    }

    /**
     * On event item that has a map, the map will be initialized and onMapReady called.
     * The map is then initialized with LatLng that is stored as the tag of the MapView.
     * This ensures that the map is initialised with the latest data that it should display.
     */
    inner class EventItemViewHolder(itemView: View, attendeesPool: RecyclerView.RecycledViewPool,
                                    onEventItemClicked: (Int, List<Pair<View, String>>?) -> Unit) : RecyclerView.ViewHolder(itemView), OnMapReadyCallback {

        var itemId: String? = null
        val title: TextView = itemView.findViewById(R.id.event_card_title)
        val cardView: CardView = itemView.findViewById(R.id.event_card_root_view)
        val dateTimeIcon: ImageView = itemView.findViewById(R.id.event_card_clock_icon)
        val dateTime: TextView = itemView.findViewById(R.id.event_card_date_time)
        val locationIcon: ImageView = itemView.findViewById(R.id.event_card_location_icon)
        val location: TextView = itemView.findViewById(R.id.event_card_location)
        val mapView: MapView = itemView.findViewById(R.id.event_card_map)
        var map: GoogleMap? = null
        val attendees: RecyclerView = itemView.findViewById(R.id.event_card_attendees)
        val attendStatus: ImageButton = itemView.findViewById(R.id.event_card_join_status_button)

        init {
            // to avoid stutter when scrolling, we shouldn't be initializing the map in onBindViewHolder().
            // rather, we should do it as soon as this ViewHolder instance is created.
            initializeMap()
            mapViews.add(mapView)
            attendees.apply {
                recycledViewPool = attendeesPool
                (layoutManager as LinearLayoutManager).initialPrefetchItemCount = VISIBLE_ATTENDEE_AVATARS + 1
                adapter = AttendeeAdapter(fragment, visibleItemCount = VISIBLE_ATTENDEE_AVATARS)
                fragment.context?.let {
                    addItemDecoration(HorizontalSpaceItemDecoration(it.dimen(R.dimen.avatar_spacing)))
                }
            }
            attendStatus.setOnClickListener {
                itemId?.let {
                    val newStatus = getNewAttendStatus(attendStatus.tag as EventItemUiModel.AttendStatus)
                    viewModel.setEventAttendStatus(adapterPosition, newStatus)
                }
            }
            itemView.setOnClickListener {
                onEventItemClicked(adapterPosition,
                        listOf(Pair.create(cardView as View, itemView.context.getString(R.string.event_card_background_transition))
                ))
            }
        }

        override fun onMapReady(googleMap: GoogleMap) {
            map = googleMap
            map?.let { map ->
                try {
                    map.setMapStyle(MapStyleOptions.loadRawResourceStyle(fragment.context, R.raw.map_style)).let {
                        if (!it) AppLogger.w("Google Maps custom style parsing failed")
                    }
                }
                catch (ex: Exception) {
                    AppLogger.w(ex)
                }

                map.uiSettings.isMapToolbarEnabled = false

                // this view tag will be set if onBindViewHolder() is called before onMapReady called
                mapView.tag?.let {
                    setMapLocation(map, it as LatLng)
                }
            }
        }

        private fun initializeMap() {
            if (map == null) {
                mapView.apply {
                    hide()
                    onCreate(null)          // it is mandatory to call onCreate(), otherwise no map will appear
                    getMapAsync(this@EventItemViewHolder)
                }
            }
        }

        private fun showMapView(data: LatLng) {
            mapView.show()
            map?.let { it ->
                setMapLocation(it, data)
            }
        }

        fun clearMapView() {
            mapView.hide()
            map?.apply {
                // clear the map and free up resources by changing the map type to none
                clear()
                mapType = GoogleMap.MAP_TYPE_NONE
            }
        }

        fun updateTitle(item: EventItemUiModel) {
            title.text = item.title
        }

        fun updateDateTime(item: EventItemUiModel) {
            if (item.dateTime == null) {
                dateTimeIcon.hide()
                dateTime.hide()
            }
            else {
                dateTimeIcon.show()
                dateTime.show()
                dateTime.text = item.dateTime
            }
        }

        fun updateLocation(item: EventItemUiModel) {
            if (item.location == null) {
                locationIcon.hide()
                location.hide()
            }
            else {
                locationIcon.show()
                location.show()
                location.text = location.context.getString(item.location)
            }
        }

        fun updateMap(item: EventItemUiModel) {
            item.mapLatLng.let {
                mapView.tag = it
                if (it == null) clearMapView()
                else showMapView(it)
            }
        }

        fun updateAttendees(item: EventItemUiModel) {
            item.attendeesAvatars.let {
                attendees.apply {
                    if (it.isNullOrEmpty()) {
                        hide()
                    }
                    else {
                        show()
                        adapter?.let {
                            (it as AttendeeAdapter).setItems(item.attendeesAvatars)
                        }
                    }
                }
            }
        }

        fun updateAttendStatus(item: EventItemUiModel) {
            item.attendStatus.let {
                when (it) {
                    EventItemUiModel.AttendStatus.GOING -> {
                        attendStatus.loadFromResource(R.drawable.ic_emoticon_excited)
                    }
                    EventItemUiModel.AttendStatus.MAYBE -> {
                        attendStatus.loadFromResource(R.drawable.ic_emoticon_neutral)
                    }
                    EventItemUiModel.AttendStatus.DECLINED -> {
                        attendStatus.hide()
                    }
                }
                attendStatus.tag = it
            }
        }

        private fun getNewAttendStatus(status: EventItemUiModel.AttendStatus): EventItemUiModel.AttendStatus {
            return when (status) {
                EventItemUiModel.AttendStatus.GOING -> EventItemUiModel.AttendStatus.MAYBE
                else -> EventItemUiModel.AttendStatus.GOING
            }
        }
    }

    //endregion
}