package io.jitrapon.glom.board

import android.content.res.Configuration
import android.support.v4.app.Fragment
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.recyclerview.HorizontalSpaceItemDecoration
import io.jitrapon.glom.base.ui.widget.stickyheader.StickyHeaders
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.event.EventCardAttendeeAdapter
import io.jitrapon.glom.board.event.EventItem
import io.jitrapon.glom.board.event.EventItemUiModel
import io.jitrapon.glom.board.event.EventItemViewModel

/**
 * RecyclerView's Adapter for the Board screen, handling different item types (i.e. Events, Notes, etc.)
 *
 * Created by Jitrapon on 11/26/2017.
 */
class BoardItemAdapter(private val viewModel: BoardViewModel, private val fragment: Fragment, private val orientation: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        StickyHeaders, StickyHeaders.ViewSetup {

    /* https://medium.com/@mgn524/optimizing-nested-recyclerview-a9b7830a4ba7 */
    private val attendeesRecycledViewPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool()

    companion object {

        private const val CAMERA_ZOOM_LEVEL = 15f
        private const val VISIBLE_ATTENDEE_AVATARS = 3
        private val ANIM_ROTATION = RotateAnimation(0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
        }
    }

    //endregion
    //region adapter callbacks

    /**
     * Bind view holder without payloads containing individual payloads
     */
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

    /**
     * Sets header items (list seperator)
     */
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
                updateStatus(item)
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
                        EventItemUiModel.ATTENDSTATUS -> { updateAttendStatus(item) }
                        EventItemUiModel.STATUS -> { updateStatus(item) }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = viewModel.getBoardItemCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        return when (viewType) {
            BoardItemUiModel.TYPE_EVENT -> EventItemViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.board_item_event, parent, false), attendeesRecycledViewPool, viewModel::viewItemDetail)
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
                                    onEventItemClicked: (Int, List<Pair<View, String>>?) -> Unit) : RecyclerView.ViewHolder(itemView) {

        var itemId: String? = null
        private val title: TextView = itemView.findViewById(R.id.event_card_title)
        private val cardView: CardView = itemView.findViewById(R.id.event_card_root_view)
        private val dateTimeIcon: ImageView = itemView.findViewById(R.id.event_card_clock_icon)
        private val dateTime: TextView = itemView.findViewById(R.id.event_card_date_time)
        private val locationIcon: ImageView = itemView.findViewById(R.id.event_card_location_icon)
        private val location: TextView = itemView.findViewById(R.id.event_card_location)
        private val mapView: ImageView = itemView.findViewById(R.id.event_card_map)
        private val attendees: RecyclerView = itemView.findViewById(R.id.event_card_attendees)
        private val attendStatus: ImageButton = itemView.findViewById(R.id.event_card_join_status_button)
        private val syncStatus: ImageView = itemView.findViewById(R.id.event_card_sync_status)

        init {
            attendees.apply {
                recycledViewPool = attendeesPool
                (layoutManager as LinearLayoutManager).initialPrefetchItemCount = VISIBLE_ATTENDEE_AVATARS + 1
                adapter = EventCardAttendeeAdapter(fragment, visibleItemCount = VISIBLE_ATTENDEE_AVATARS)
                fragment.context?.let {
                    addItemDecoration(HorizontalSpaceItemDecoration(it.dimen(io.jitrapon.glom.R.dimen.avatar_spacing)))
                }
            }
            attendStatus.setOnClickListener {
                itemId?.let {
                    BoardItemViewModelStore.obtainViewModelForItem(EventItem::class.java)?.let {
                        val newStatus = getNewAttendStatus(attendStatus.tag as EventItemUiModel.AttendStatus)
                        (it as? EventItemViewModel)?.setEventAttendStatus(viewModel, adapterPosition, newStatus)
                    }
                }
            }
            itemView.setOnClickListener {
                onEventItemClicked(adapterPosition,
                        listOf(cardView as View to itemView.context.getString(R.string.event_card_background_transition))
                )
            }
            syncStatus.apply {
                loadFromResource(io.jitrapon.glom.R.drawable.ic_sync)
                hide()
            }
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
                    mapView.apply {
                        show()
                        val width = if (orientation == Configuration.ORIENTATION_PORTRAIT) 481 else 650
                        loadFromUrl(fragment, latLng.toUri(context, width, 153), transformation = Transformation.FIT_CENTER)
                    }
                }
                else {
                    mapView.apply {
                        hide()
                    }
                }
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

        fun updateStatus(item: EventItemUiModel) {
            item.status.let {
                when (it) {
                    UiModel.Status.LOADING -> {
                        syncStatus.apply {
                            show()
//                            startAnimation(ANIM_ROTATION)     // cause no shared element transition
                        }
                    }
                    UiModel.Status.SUCCESS -> {
                        syncStatus.apply {
                            hide(200L)
//                            animation?.let {
//                                it.reset()
//                                it.cancel()
//                            }
                        }
                    }
                    UiModel.Status.ERROR -> {
                        syncStatus.hide()
                    }
                    UiModel.Status.EMPTY -> {
                        //not applicable
                    }
                }
            }
        }
    }

    //endregion
}