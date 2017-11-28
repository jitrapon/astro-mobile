package io.jitrapon.glom.board

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.util.getString

/**
 * RecyclerView's Adapter for the board items
 *
 * Created by Jitrapon on 11/26/2017.
 */
class BoardItemAdapter(private val viewModel: BoardViewModel) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        viewModel.getBoardItemUiModel(position)?.let {
            when (holder) {
                is EventItemViewHolder -> {
                    val item = it as EventItemUiModel
                    holder.apply {
                        if (item.upcomingTime == null) {
                            upcomingTime.visibility = View.GONE
                        }
                        else {
                            upcomingTime.text = item.upcomingTime
                        }
                        title.text = item.title
                        if (item.dateTime == null) {
                            dateTimeIcon.visibility = View.GONE
                            dateTime.visibility = View.GONE
                        }
                        else {
                            dateTime.text = it.dateTime
                        }
                        if (item.location == null) {
                            locationIcon.visibility = View.GONE
                            location.visibility = View.GONE
                        }
                        else {
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
            else -> null
        }
    }

    override fun getItemViewType(position: Int): Int = viewModel.getBoardItemType(position)

    class EventItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val title: TextView = itemView.findViewById(R.id.event_card_title)
        val upcomingTime: TextView = itemView.findViewById(R.id.event_card_time_highlight)
        val dateTimeIcon: ImageView = itemView.findViewById(R.id.event_card_clock_icon)
        val dateTime: TextView = itemView.findViewById(R.id.event_card_date_time)
        val locationIcon: ImageView = itemView.findViewById(R.id.event_card_location_icon)
        val location: TextView = itemView.findViewById(R.id.event_card_location)
    }
}