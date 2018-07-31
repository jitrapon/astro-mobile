package io.jitrapon.glom.board.event

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.util.color
import io.jitrapon.glom.base.util.colorPrimary
import io.jitrapon.glom.base.util.getString
import io.jitrapon.glom.base.util.tint
import io.jitrapon.glom.board.R

class EventPollAdapter(private val viewModel: PlanEventViewModel, private val isDatePoll: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {

        private const val TYPE_DATE_POLL = 0
        private const val TYPE_PLACE_POLL = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (isDatePoll) TYPE_DATE_POLL else TYPE_PLACE_POLL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_POLL -> EventDatePollViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.plan_event_date_poll, parent, false), viewModel::toggleDatePoll)
            TYPE_PLACE_POLL -> EventPlacePollViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.plan_event_place_poll, parent, false), viewModel::togglePlacePoll)
            else -> { null!! }
        }
    }

    override fun getItemCount(): Int = viewModel.getDatePollCount()

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is EventDatePollViewHolder) {
            viewModel.getDatePollItem(position).let {
                holder.date.apply {
                    text = context.getString(it.date)
                }
                holder.time.apply {
                    text = context.getString(it.time)
                }
                holder.selectIcon.tint(
                        when (it.status) {
                            UiModel.Status.POSITIVE -> holder.selectIcon.context.colorPrimary()
                            UiModel.Status.SUCCESS -> holder.selectIcon.context.color(R.color.calm_blue)
                            else -> holder.selectIcon.context.color(R.color.warm_grey)
                        })
                holder.count.text = it.count.toString()
            }
        }
    }

    inner class EventDatePollViewHolder(itemView: View, onItemClicked: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {

        val date: TextView = itemView.findViewById(R.id.event_plan_date_poll_date)
        val time: TextView = itemView.findViewById(R.id.event_plan_date_poll_time)
        val selectIcon: ImageView = itemView.findViewById(R.id.event_plan_date_poll_thumb_up)
        val count: TextView = itemView.findViewById(R.id.event_plan_date_poll_count)

        init {
            itemView.setOnClickListener {
                onItemClicked(adapterPosition)
            }
        }
    }

    inner class EventPlacePollViewHolder(itemView: View, onItemClicked: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {

    }
}
