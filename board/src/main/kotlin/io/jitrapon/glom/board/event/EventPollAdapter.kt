package io.jitrapon.glom.board.event

import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.GlomButton
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.R

const val TYPE_DATE_POLL = 0
const val TYPE_PLACE_POLL = 1
const val TYPE_PLACE_CARD = 2
const val TYPE_ADD_PLACE_POLL = 3

class EventPollAdapter(private val viewModel: PlanEventViewModel, private val itemType: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int = if (itemType == TYPE_PLACE_POLL) {
        if (viewModel.isAddPlacePollButton(position)) TYPE_ADD_PLACE_POLL else TYPE_PLACE_POLL
    } else itemType

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_POLL -> EventDatePollViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.plan_event_date_poll, parent, false), viewModel::toggleDatePoll)
            TYPE_PLACE_POLL -> EventPlacePollViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.plan_event_place_poll, parent, false), viewModel::togglePlacePoll, viewModel::viewPlaceDetails)
            TYPE_ADD_PLACE_POLL -> EventAddPlacePollViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.plan_event_add_place_poll, parent, false), viewModel::showPlacePicker)
            TYPE_PLACE_CARD -> EventPlaceCardViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.plan_event_place_card, parent, false))
            else -> { null!! }
        }
    }

    override fun getItemCount(): Int = when (itemType) {
        TYPE_DATE_POLL -> viewModel.getDatePollCount()
        TYPE_PLACE_POLL -> viewModel.getPlacePollCount()
        TYPE_PLACE_CARD -> viewModel.getPlaceCardCount()
        else -> 0
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EventDatePollViewHolder -> viewModel.getDatePollItem(position).let {
                holder.date.apply {
                    text = context.getString(it.date)
                }
                holder.time.apply {
                    val time = context.getString(it.time)
                    if (TextUtils.isEmpty(time)) {
                        hide()
                    }
                    else {
                        text = time
                    }
                }
                holder.selectIcon.tint(
                        when (it.status) {
                            UiModel.Status.POSITIVE -> holder.selectIcon.context.colorPrimary()
                            UiModel.Status.SUCCESS -> holder.selectIcon.context.color(io.jitrapon.glom.R.color.calm_blue)
                            else -> holder.selectIcon.context.color(io.jitrapon.glom.R.color.warm_grey)
                        })
                holder.count.text = it.count.toString()
            }
            is EventPlacePollViewHolder -> viewModel.getPlacePollItem(position).let {
                holder.title.apply {
                    text = context.getString(it.name)
                }
                holder.subtitle.apply {
                    val subtitle = context.getString(it.address)
                    if (TextUtils.isEmpty(subtitle)) {
                        hide()
                    }
                    else {
                        show()
                        text = subtitle
                    }
                }
                holder.selectIcon.tint(
                        when (it.status) {
                            UiModel.Status.POSITIVE -> holder.selectIcon.context.colorPrimary()
                            UiModel.Status.SUCCESS -> holder.selectIcon.context.color(io.jitrapon.glom.R.color.calm_blue)
                            else -> holder.selectIcon.context.color(io.jitrapon.glom.R.color.warm_grey)
                        })
                holder.count.text = it.count.toString()
            }
            is EventPlaceCardViewHolder -> viewModel.getPlaceCardItem(position).let {
                it.avatar?.let {
                    if (it.startsWith("http")) {
                        holder.image.loadFromUrl(holder.image.context, it, transformation = Transformation.CENTER_CROP)
                    }
                    else {
                        holder.image.loadFromPlaceId(holder.image.context, it, transformation = Transformation.CENTER_CROP)
                    }
                }
                holder.name.apply {
                    text = context.getString(it.name)
                }
                holder.address.apply {
                    text = context.getString(it.address)
                }
                holder.button.applyState(it.actionButton)
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

    inner class EventPlacePollViewHolder(itemView: View, onItemClicked: (Int) -> Unit, onItemInfoClicked: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {

        val title: TextView = itemView.findViewById(R.id.event_plan_place_poll_name)
        val subtitle: TextView = itemView.findViewById(R.id.event_plan_place_poll_subtitle)
        val selectIcon: ImageView = itemView.findViewById(R.id.event_plan_place_poll_thumb_up)
        val count: TextView = itemView.findViewById(R.id.event_plan_place_poll_count)
        val info: ImageView = itemView.findViewById(R.id.event_plan_place_info_button)

        init {
            itemView.setOnClickListener {
                onItemClicked(adapterPosition)
            }
            info.setOnClickListener {
                onItemInfoClicked(adapterPosition)
            }
        }
    }

    inner class EventPlaceCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val image: ImageView = itemView.findViewById(R.id.event_plan_place_card_image)
        val name: TextView = itemView.findViewById(R.id.event_plan_place_card_name)
        val address: TextView = itemView.findViewById(R.id.event_place_plan_card_address)
        val button: GlomButton = itemView.findViewById(R.id.event_plan_place_card_button)
    }

    inner class EventAddPlacePollViewHolder(itemView: View, onItemClicked: () -> Unit) : RecyclerView.ViewHolder(itemView) {

        init {
            itemView.setOnClickListener {
                onItemClicked()
            }
        }
    }
}
