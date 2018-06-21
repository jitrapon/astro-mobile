package io.jitrapon.glom.board.event

import android.app.Activity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.ui.widget.recyclerview.PartialRecyclerViewAdapter
import io.jitrapon.glom.base.util.Transformation
import io.jitrapon.glom.base.util.loadFromUrl
import io.jitrapon.glom.board.R

/**
 * Recyclerview Adapter that displays the list of event attendees in the event detail
 *
 * Created by Jitrapon
 */
class EventItemAttendeeAdapter(private val activity: Activity,
                               private val visibleItemCount: Int = 3,
                               private val userLayoutId: Int,
                               private val otherLayoutId: Int) : PartialRecyclerViewAdapter<RecyclerView.ViewHolder>() {

    private var users: List<UserUiModel> = ArrayList()

    companion object {

        private const val TYPE_AVATAR = 0
        private const val TYPE_MORE = 1
    }

    fun setItems(attendees: List<UserUiModel>) {
        users = attendees
        notifyDataSetChanged()
    }

    override fun getMoreItemViewType(): Int = TYPE_MORE

    override fun getVisibleItemCount(): Int = visibleItemCount

    override fun getAllItemCount(): Int = users.let {
        if (it.isEmpty()) 0 else it.size
    }

    override fun onCreateMoreViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
            MoreItemViewHolder(LayoutInflater.from(parent.context).inflate(otherLayoutId, parent, false))

    override fun onCreateOtherViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            AvatarViewHolder(LayoutInflater.from(parent.context).inflate(userLayoutId, parent, false))

    override fun getOtherItemViewType(position: Int): Int = TYPE_AVATAR

    override fun onBindMoreItemViewHolder(holder: RecyclerView.ViewHolder, remainingItemCount: Int) {
        (holder as MoreItemViewHolder).updateCount(remainingItemCount)
    }

    override fun onBindOtherItemViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as AvatarViewHolder).setUser(users[position])
    }

    inner class MoreItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val text: TextView = itemView.findViewById(R.id.remaining_count_text)

        fun updateCount(count: Int) {
            activity.let {
                text.text = String.format(it.getString(R.string.event_attendee_remaining_count), count)
            }
        }
    }

    inner class AvatarViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        val image: ImageView = itemView.findViewById(R.id.user_avatar_image)
        val username: TextView = itemView.findViewById(R.id.user_avatar_text)

        fun setUser(user: UserUiModel) {
            image.loadFromUrl(activity, user.avatar, transformation = Transformation.CIRCLE_CROP)
            username.text = user.username
        }
    }
}