package io.jitrapon.glom.board.item

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.recyclerview.HorizontalSpaceItemDecoration
import io.jitrapon.glom.base.ui.widget.recyclerview.ItemTouchHelperCallback
import io.jitrapon.glom.base.ui.widget.stickyheader.StickyHeaders
import io.jitrapon.glom.base.util.Transformation
import io.jitrapon.glom.base.util.clearMap
import io.jitrapon.glom.base.util.dimen
import io.jitrapon.glom.base.util.getString
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.base.util.load
import io.jitrapon.glom.base.util.loadFromResource
import io.jitrapon.glom.base.util.loadFromUrl
import io.jitrapon.glom.base.util.setStyle
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.base.util.showLiteMap
import io.jitrapon.glom.base.util.tint
import io.jitrapon.glom.base.util.toUri
import io.jitrapon.glom.board.BoardViewModel
import io.jitrapon.glom.board.HeaderItemUiModel
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.event.ATTENDEES
import io.jitrapon.glom.board.item.event.ATTENDSTATUS
import io.jitrapon.glom.board.item.event.DATETIME
import io.jitrapon.glom.board.item.event.EventCardAttendeeAdapter
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventItemUiModel
import io.jitrapon.glom.board.item.event.EventItemViewModel
import io.jitrapon.glom.board.item.event.LOCATION
import io.jitrapon.glom.board.item.event.MAPLATLNG
import io.jitrapon.glom.board.item.event.PLAN
import io.jitrapon.glom.board.item.event.SOURCE
import io.jitrapon.glom.board.item.event.SYNCSTATUS
import io.jitrapon.glom.board.item.event.TITLE
import io.jitrapon.glom.board.item.event.preference.EVENT_ITEM_ATTENDEE_MAXIMUM_COUNT
import io.jitrapon.glom.board.item.event.preference.EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL
import io.jitrapon.glom.board.item.event.preference.EVENT_ITEM_MAP_USE_GOOGLE_MAP

/**
 * RecyclerView's Adapter for the Board screen, handling different item types (i.e. Events, Notes, etc.)
 *
 * Created by Jitrapon on 11/26/2017.
 */
class BoardItemAdapter(
    private val viewModel: BoardViewModel,
    private val fragment: Fragment,
    private val orientation: Int
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    StickyHeaders,
    StickyHeaders.ViewSetup,
    ItemTouchHelperCallback.OnItemChangedListener {

    /* https://medium.com/@mgn524/optimizing-nested-recyclerview-a9b7830a4ba7 */
    private val attendeesRecycledViewPool: RecyclerView.RecycledViewPool =
        RecyclerView.RecycledViewPool()

    @Suppress("PrivatePropertyName", "unused")
    private val ANIM_ROTATION = RotateAnimation(
        0f, 360f,
        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
        0.5f
    ).apply {
        duration = 1000
        repeatCount = Animation.INFINITE
    }

    private lateinit var recyclerView: RecyclerView

    //endregion
    //region adapter callbacks

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    /**
     * Bind view holder without payloads containing individual payloads
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        viewModel.getBoardItemUiModel(position)?.let {
            when (holder) {
                is HeaderItemViewHolder -> bindHeaderItem(it as HeaderItemUiModel, holder)
                is EventItemViewHolder -> bindEventItem(it as EventItemUiModel, holder)
                else -> Unit
            }
        }
    }

    /**
     * @param payloads A non-null list of merged payloads. Can be empty list if requires full
     *                 update.
     */
    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        viewModel.getBoardItemUiModel(position)?.let {
            when (holder) {
                is HeaderItemViewHolder -> bindHeaderItem(it as HeaderItemUiModel, holder)
                is EventItemViewHolder -> bindEventItem(it as EventItemUiModel, holder, payloads)
                else -> Unit
            }
        }
    }

    /**
     * Sets header items (list seperator)
     */
    private fun bindHeaderItem(item: HeaderItemUiModel, holder: HeaderItemViewHolder) {
        holder.apply {
            text.text = text.context.getString(item.text)
        }
    }

    private fun bindEventItem(
        item: EventItemUiModel,
        holder: EventItemViewHolder,
        payloads: List<Any>? = null
    ) {
        holder.apply {
            itemId = item.itemId
            if (payloads.isNullOrEmpty()) {
                updateTitle(item)
                updateDateTime(item)
                updateLocation(item)
                updateMap(item)
                updateAttendees(item)
                updateAttendStatus(item)
                updateStatus(item, false)
                updateIsPlanning(item)
                updateSource(item)
            }
            else {
                val fields = payloads!!.first() as List<*>
                for (field in fields) {
                    when (field as Int) {
                        TITLE -> updateTitle(item)
                        DATETIME -> updateDateTime(item)
                        LOCATION -> updateLocation(item)
                        MAPLATLNG -> updateMap(item)
                        ATTENDEES -> updateAttendees(item)
                        ATTENDSTATUS -> updateAttendStatus(item)
                        SYNCSTATUS -> updateStatus(item, false)
                        PLAN -> updateIsPlanning(item)
                        SOURCE -> updateSource(item)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = viewModel.getBoardItemCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            BoardItemUiModel.TYPE_EVENT -> EventItemViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.board_item_event, parent, false),
                attendeesRecycledViewPool,
                viewModel::viewItemDetail
            )
            else -> HeaderItemViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.board_item_header, parent, false)
            )
        }
    }

    override fun getItemViewType(position: Int): Int = viewModel.getBoardItemType(position)

    override fun isStickyHeader(position: Int): Boolean {
        return viewModel.getBoardItemType(position) == BoardItemUiModel.TYPE_HEADER
    }

    override fun setupStickyHeaderView(stickyHeader: View) {
        stickyHeader.translationZ =
            stickyHeader.context.resources.getDimension(R.dimen.card_header_item_stuck_elevation)
    }

    override fun teardownStickyHeaderView(stickyHeader: View) {
        stickyHeader.translationZ = 0f
    }

    override fun onMove(fromPosition: Int, toPosition: Int) {
        //not applicable
    }

    override fun onSwipe(position: Int, isStartDir: Boolean) {
        viewModel.deleteItem(position)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? BoardItemViewHolder)?.recycle()
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
    inner class EventItemViewHolder(
        itemView: View, attendeesPool: RecyclerView.RecycledViewPool,
        onEventItemClicked: (Int, List<Pair<View, String>>?) -> Unit
    ) :
        BoardItemViewHolder(itemView),
        OnMapReadyCallback {

        var itemId: String? = null
        private val title: TextView = itemView.findViewById(R.id.event_card_title)
        private val cardView: androidx.cardview.widget.CardView =
            itemView.findViewById(R.id.event_card_root_view)
        private val dateTimeIcon: ImageView = itemView.findViewById(R.id.event_card_clock_icon)
        private val dateTime: TextView = itemView.findViewById(R.id.event_card_date_time)
        private val locationIcon: ImageView = itemView.findViewById(R.id.event_card_location_icon)
        private val location: TextView = itemView.findViewById(R.id.event_card_location)
        private val mapImage: ImageView = itemView.findViewById(R.id.event_card_map_image)
        private val attendees: RecyclerView = itemView.findViewById(R.id.event_card_attendees)
        private val attendStatus: ImageButton =
            itemView.findViewById(R.id.event_card_join_status_button)
        private val syncStatus: ImageView = itemView.findViewById(R.id.event_card_sync_status)
        private val planStatus: ImageButton = itemView.findViewById(R.id.event_card_plan_button)
        private val mapView: MapView = itemView.findViewById(R.id.event_card_map)
        private var map: GoogleMap? = null
        private val sourceIcon: ImageView = itemView.findViewById(R.id.event_card_source_image)
        private val sourceDescription: TextView =
            itemView.findViewById(R.id.event_card_source_description)

        init {
            attendees.apply {
                setRecycledViewPool(attendeesPool)
                (layoutManager as androidx.recyclerview.widget.LinearLayoutManager).initialPrefetchItemCount =
                    EVENT_ITEM_ATTENDEE_MAXIMUM_COUNT + 1
                adapter = EventCardAttendeeAdapter(
                    fragment,
                    visibleItemCount = EVENT_ITEM_ATTENDEE_MAXIMUM_COUNT
                )
                fragment.context?.let {
                    addItemDecoration(HorizontalSpaceItemDecoration(it.dimen(io.jitrapon.glom.R.dimen.avatar_spacing)))
                }
            }
            attendStatus.setOnClickListener {
                itemId?.let {
                    BoardItemViewModelStore.obtainViewModelForItem(EventItem::class.java)?.let {
                        val newStatus =
                            getNewAttendStatus(attendStatus.tag as EventItemUiModel.AttendStatus)
                        (it as? EventItemViewModel)?.setEventAttendStatus(
                            viewModel,
                            adapterPosition,
                            newStatus
                        )
                    }
                }
            }
            itemView.setOnClickListener {
                onEventItemClicked(
                    adapterPosition,
                    listOf(cardView as View to itemView.context.getString(R.string.event_card_background_transition))
                )
            }
            syncStatus.apply {
                loadFromResource(io.jitrapon.glom.R.drawable.ic_sync)
                hide()
            }
            planStatus.setOnClickListener {
                BoardItemViewModelStore.obtainViewModelForItem(EventItem::class.java)?.let {
                    (it as? EventItemViewModel)?.showEventPlan(viewModel, adapterPosition)
                }
            }
            syncStatus.setOnClickListener {
                itemId?.let(viewModel::syncItem)
            }

            // initialize the MapView
            if (EVENT_ITEM_MAP_USE_GOOGLE_MAP) {
                with(mapView) {
                    onCreate(null)
                    getMapAsync(this@EventItemViewHolder)
                }
                mapImage.hide()
            }
            else {
                mapView.hide()
            }
        }

        override fun onMapReady(googleMap: GoogleMap?) {
            MapsInitializer.initialize(fragment.context!!.applicationContext)
            map = googleMap ?: return
            map!!.setStyle(fragment.context!!, io.jitrapon.glom.R.raw.map_style)
            setMapLocation()
        }

        fun updateTitle(item: EventItemUiModel) {
            title.text = title.context.getString(item.title)
        }

        fun updateDateTime(item: EventItemUiModel) {
            if (item.dateTime == null) {
                dateTimeIcon.hide()
                dateTime.hide()
            }
            else {
                dateTimeIcon.show()
                dateTime.show()
                dateTime.text = title.context.getString((item.dateTime))
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
            item.mapLatLng.let { latLng ->
                if (latLng != null) {
                    if (EVENT_ITEM_MAP_USE_GOOGLE_MAP) {
                        mapView.tag = latLng
                        setMapLocation()
                    }
                    else {
                        mapImage.apply {
                            show()
                            val width =
                                if (orientation == Configuration.ORIENTATION_PORTRAIT) 481 else 650
                            loadFromUrl(
                                fragment,
                                latLng.toUri(context, width, 153),
                                transformation = Transformation.FIT_CENTER
                            )
                        }
                    }
                }
                else {
                    if (EVENT_ITEM_MAP_USE_GOOGLE_MAP) mapView.hide()
                    else mapImage.hide()
                }
            }
        }

        private fun setMapLocation() {
            (mapView.tag as? LatLng)?.let {
                mapView.show()
                map?.showLiteMap(it, EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL)
            }
        }

        fun updateAttendees(item: EventItemUiModel) {
            item.attendeesAvatars.let {
                attendees.apply {
                    if (it.isNullOrEmpty()) {
                        hide(invisible = true)
                    }
                    else {
                        show()
                        adapter?.let {
                            (it as EventCardAttendeeAdapter).setItems(item.attendeesAvatars)
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

        fun updateStatus(item: EventItemUiModel, shouldAnimateVisibility: Boolean) {
            val duration = if (shouldAnimateVisibility) 200L else null
            when (item.status) {
                UiModel.Status.LOADING -> {
                    syncStatus.apply {
                        loadFromResource(io.jitrapon.glom.R.drawable.ic_sync)
                        show(duration)
                        isEnabled = false
                    }
                }
                UiModel.Status.SUCCESS -> {
                    syncStatus.apply {
                        hide(duration)
                        isEnabled = false
                    }
                }
                UiModel.Status.ERROR -> {
                    syncStatus.apply {
                        loadFromResource(io.jitrapon.glom.R.drawable.ic_sync_failed)
                        show(duration)
                        isEnabled = true
                    }
                }
                UiModel.Status.POSITIVE -> {
                    syncStatus.apply {
                        loadFromResource(io.jitrapon.glom.R.drawable.ic_sync_offline)
                        show(duration)
                        isEnabled = true
                    }
                }
                else -> Unit
            }
        }

        fun updateIsPlanning(item: EventItemUiModel) {
            if (item.isPlanning) planStatus.show() else planStatus.hide()
        }

        fun updateSource(item: EventItemUiModel) {
            item.sourceIcon.let {
                when {
                    it == null -> {
                        sourceIcon.hide()
                    }
                    it.colorInt != null -> sourceIcon.apply {
                        show()
                        loadFromResource(io.jitrapon.glom.R.drawable.ic_checkbox_blank_circle)
                        tint(it.colorInt)
                    }
                    else -> sourceIcon.apply {
                        show()
                        load(fragment.context!!, it)
                    }
                }
            }
            item.sourceDescription.let {
                if (it == null) {
                    sourceDescription.hide()
                }
                else {
                    sourceDescription.apply {
                        show()
                        text = context.getString(it)
                    }
                }
            }
        }

        override fun recycle() {
            if (EVENT_ITEM_MAP_USE_GOOGLE_MAP) {
                map?.clearMap()
            }
        }
    }

    //endregion
}

abstract class BoardItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    abstract fun recycle()
}
