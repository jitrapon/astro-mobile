package io.jitrapon.glom.board

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.ui.widget.stickyheader.StickyHeaders
import io.jitrapon.glom.base.util.getString

/**
 * RecyclerView's Adapter for the board items
 *
 * Created by Jitrapon on 11/26/2017.
 */
class BoardItemAdapter(private val viewModel: BoardViewModel) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        StickyHeaders, StickyHeaders.ViewSetup {

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
                        title.text = item.title
                        if (item.dateTime == null) {
                            dateTimeIcon.visibility = View.GONE
                            dateTime.visibility = View.GONE
                        }
                        else {
                            dateTimeIcon.visibility = View.VISIBLE
                            dateTime.visibility = View.VISIBLE
                            dateTime.text = it.dateTime
                        }
                        if (item.location == null) {
                            locationIcon.visibility = View.GONE
                            location.visibility = View.GONE
                        }
                        else {
                            locationIcon.visibility = View.VISIBLE
                            location.visibility = View.VISIBLE
                            location.text = location.context.getString(item.location)
                        }
                    }
                }
                else -> {

                }
            }
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

    inner class HeaderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val text: TextView = itemView.findViewById(R.id.board_item_header_text)
    }

    inner class EventItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val title: TextView = itemView.findViewById(R.id.event_card_title)
        val dateTimeIcon: ImageView = itemView.findViewById(R.id.event_card_clock_icon)
        val dateTime: TextView = itemView.findViewById(R.id.event_card_date_time)
        val locationIcon: ImageView = itemView.findViewById(R.id.event_card_location_icon)
        val location: TextView = itemView.findViewById(R.id.event_card_location)
    }
}