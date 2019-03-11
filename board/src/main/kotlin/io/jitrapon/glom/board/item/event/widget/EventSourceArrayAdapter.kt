package io.jitrapon.glom.board.item.event.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import io.jitrapon.glom.base.util.getString
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.load
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.event.EventItemViewModel
import io.jitrapon.glom.board.item.event.EventSourceUiModel

class EventSourceArrayAdapter(private val context: Context, private val viewModel: EventItemViewModel) : BaseAdapter() {

    var items: MutableList<EventSourceUiModel> = ArrayList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class EventSourceViewHolder(view: View) {

        internal val icon: ImageView = view.findViewById(R.id.list_item_left_icon)
        internal val text: TextView = view.findViewById(R.id.list_item_title)

        init {
            view.findViewById<CheckBox>(R.id.list_item_checkbox).hide()
        }
    }

    private val layoutInflater: LayoutInflater by lazy {
        LayoutInflater.from(context)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return layoutInflater.inflate(R.layout.list_item_no_icon, parent, false).apply {
            tag = EventSourceViewHolder(this)
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return if (convertView == null) {
            layoutInflater.inflate(R.layout.list_item_no_icon, parent, false).apply {
                tag = EventSourceViewHolder(this)
            }
        } else {
            (convertView.tag as EventSourceViewHolder).run {
                icon.load(context, items[position].sourceIcon)
                text.text = context.getString(items[position].sourceDescription)
                convertView
            }!!
        }
    }

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = items.size
}
