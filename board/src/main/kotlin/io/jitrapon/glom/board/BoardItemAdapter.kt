package io.jitrapon.glom.board

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import io.jitrapon.glom.base.ui.widget.stickyheader.StickyHeaders
import io.jitrapon.glom.base.util.getString

/**
 * RecyclerView's Adapter for the board items
 *
 * Created by Jitrapon on 11/26/2017.
 */
class BoardItemAdapter(private val viewModel: BoardViewModel, lifeCycle: Lifecycle) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        StickyHeaders, StickyHeaders.ViewSetup, LifecycleObserver {

    companion object {

        private const val CAMERA_ZOOM_LEVEL = 13f

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
    private var lifeCycle: Lifecycle = lifeCycle.apply {
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
                is HeaderItemViewHolder -> {
                    val item = it as HeaderItemUiModel
                    holder.apply {
                        text.text = text.context.getString(item.text)
                    }
                }
                is EventItemViewHolder -> {
                    val item = it as EventItemUiModel
                    holder.apply {

                        // set up title
                        title.text = item.title

                        // set up event's date time
                        if (item.dateTime == null) {
                            dateTimeIcon.visibility = View.GONE
                            dateTime.visibility = View.GONE
                        }
                        else {
                            dateTimeIcon.visibility = View.VISIBLE
                            dateTime.visibility = View.VISIBLE
                            dateTime.text = it.dateTime
                        }

                        // set up event's location text
                        if (item.location == null) {
                            locationIcon.visibility = View.GONE
                            location.visibility = View.GONE
                        }
                        else {
                            locationIcon.visibility = View.VISIBLE
                            location.visibility = View.VISIBLE
                            location.text = location.context.getString(item.location)
                        }

                        // set up event's map, if available
                        item.mapLatLng.let {
                            mapView.tag = it
                            if (it == null) clearMapView()
                            else showMapView(it)
                        }
                    }
                }
                else -> {

                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is EventItemViewHolder) {
            holder.clearMapView()
        }
    }

    override fun getItemCount(): Int = viewModel.getBoardItemCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        return when (viewType) {
            BoardItemUiModel.TYPE_EVENT -> EventItemViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.board_item_event, parent, false))
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
    inner class EventItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), OnMapReadyCallback {

        val title: TextView = itemView.findViewById(R.id.event_card_title)
        val dateTimeIcon: ImageView = itemView.findViewById(R.id.event_card_clock_icon)
        val dateTime: TextView = itemView.findViewById(R.id.event_card_date_time)
        val locationIcon: ImageView = itemView.findViewById(R.id.event_card_location_icon)
        val location: TextView = itemView.findViewById(R.id.event_card_location)
        val mapView: MapView = itemView.findViewById(R.id.event_card_map)
        var map: GoogleMap? = null

        init {
            // to avoid stutter when scrolling, we shouldn't be initializing the map in onBindViewHolder().
            // rather, we should do it as soon as this ViewHolder instance is created.
            initializeMapView()
            mapViews.add(mapView)
        }

        override fun onMapReady(googleMap: GoogleMap) {
            MapsInitializer.initialize(itemView.context)
            map = googleMap
            map?.let { map ->
                map.uiSettings.isMapToolbarEnabled = false

                // this view tag will be set if onBindViewHolder() is called before onMapReady called
                mapView.tag?.let {
                    setMapLocation(map, it as LatLng)
                }
            }
        }

        fun initializeMapView() {
            mapView.apply {
                visibility = View.GONE
                onCreate(null)          // it is mandatory to call onCreate(), otherwise no map will appear
                getMapAsync(this@EventItemViewHolder)
            }
        }

        fun showMapView(data: LatLng) {
            mapView.visibility = View.VISIBLE
            map?.let { it ->
                setMapLocation(it, data)
            }
        }

        fun clearMapView() {
            mapView.visibility = View.GONE
            map?.apply {
                // clear the map and free up resources by changing the map type to none
                clear()
                mapType = GoogleMap.MAP_TYPE_NONE
            }
        }
    }

    //endregion
}