package io.jitrapon.glom.board.event

import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
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
class AttendeeAdapter(private val fragment: Fragment,
                      private var attendees: List<String?>? = null,
                      private val visibleItemCount: Int = 3) : PartialRecyclerViewAdapter<RecyclerView.ViewHolder>() {

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

    override fun onCreateMoreViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
            MoreItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.avatar_remaining_item, parent, false))

    override fun onCreateOtherViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            AvatarViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.avatar_item, parent, false))

    override fun getOtherItemViewType(position: Int): Int = TYPE_AVATAR

    override fun onBindMoreItemViewHolder(holder: RecyclerView.ViewHolder, remainingItemCount: Int) {
        (holder as MoreItemViewHolder).updateCount(remainingItemCount)
    }

    override fun onBindOtherItemViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        attendees?.let {
            (holder as AvatarViewHolder).setAvatar(it[position])
        }
    }

    inner class MoreItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val text: TextView = itemView.findViewById(R.id.remaining_count_text)

        fun updateCount(count: Int) {
            fragment.context?.let {
                text.text = String.format(it.getString(R.string.event_attendee_remaining_count), count)
            }
        }
    }

    inner class AvatarViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        val image: ImageView = itemView.findViewById(R.id.avatar_image)

        fun setAvatar(imageUrl: String?) {
            image.loadFromUrl(fragment, imageUrl, transformation = Transformation.CIRCLE_CROP)
        }
    }
}