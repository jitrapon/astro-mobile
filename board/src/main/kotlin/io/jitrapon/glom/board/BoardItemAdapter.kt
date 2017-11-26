package io.jitrapon.glom.board

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Created by Jitrapon on 11/26/2017.
 */
class BoardItemAdapter(private val viewModel: BoardViewModel) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        viewModel.getBoardItem(position)?.let {
            when (holder) {
                is EventItemViewHolder -> {
                    val item = it as EventItemUiModel
                    holder.apply {
                        title.text = item.name
                        startTime.text = item.startTime
                        endTime.text = item.endTime
                    }
                }
                else -> {}
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

        val title: TextView = itemView.findViewById(R.id.event_card_title_text)
        val startTime: TextView = itemView.findViewById(R.id.event_card_start_time_text)
        val endTime: TextView = itemView.findViewById(R.id.event_card_end_time_text)
    }
}