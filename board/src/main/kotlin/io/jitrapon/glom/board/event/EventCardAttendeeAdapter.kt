package io.jitrapon.glom.board.event

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.ui.widget.recyclerview.PartialRecyclerViewAdapter
import io.jitrapon.glom.base.util.Transformation
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.base.util.loadFromUrl
import io.jitrapon.glom.board.R

/**
 * Recyclerview Adapter that displays the list of event attendees
 *
 * Created by Jitrapon
 */
class EventCardAttendeeAdapter(private val fragment: androidx.fragment.app.Fragment,
                               private var attendees: List<String?>? = null,
                               private val visibleItemCount: Int = 3) : PartialRecyclerViewAdapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {

    companion object {

        private const val TYPE_AVATAR = 0
        private const val TYPE_MORE = 1
    }

    fun setItems(attendees: List<String?>?) {
        this.attendees = attendees
        notifyDataSetChanged()
    }

    override fun getMoreItemViewType(): Int = TYPE_MORE

    override fun getVisibleItemCount(): Int = visibleItemCount

    override fun getAllItemCount(): Int = attendees.let {
        if (it.isNullOrEmpty()) 0 else it!!.size
    }

    override fun onCreateMoreViewHolder(parent: ViewGroup): androidx.recyclerview.widget.RecyclerView.ViewHolder =
            MoreItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.remaining_indicator_small, parent, false))

    override fun onCreateOtherViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder =
            AvatarViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.user_avatar_small, parent, false))

    override fun getOtherItemViewType(position: Int): Int = TYPE_AVATAR

    override fun onBindMoreItemViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, remainingItemCount: Int) {
        (holder as MoreItemViewHolder).updateCount(remainingItemCount)
    }

    override fun onBindOtherItemViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        attendees?.let {
            (holder as AvatarViewHolder).setAvatar(it[position])
        }
    }

    inner class MoreItemViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

        val text: TextView = itemView.findViewById(R.id.remaining_count_text)

        fun updateCount(count: Int) {
            fragment.context?.let {
                text.text = String.format(it.getString(R.string.event_attendee_remaining_count), count)
            }
        }
    }

    inner class AvatarViewHolder(itemView: View): androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

        val image: ImageView = itemView.findViewById(R.id.avatar_image)

        fun setAvatar(imageUrl: String?) {
            image.loadFromUrl(fragment, imageUrl, transformation = Transformation.CIRCLE_CROP)
        }
    }
}