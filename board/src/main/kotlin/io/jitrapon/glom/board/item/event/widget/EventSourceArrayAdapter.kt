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

    private val items: MutableList<EventSourceUiModel> = ArrayList()

    inner class EventSourceViewHolder(view: View) {

        internal val icon: ImageView = view.findViewById(R.id.list_item_left_icon)
        internal val text: TextView = view.findViewById(R.id.list_item_title)

        init {
            view.findViewById<CheckBox>(R.id.list_item_checkbox).hide()
            view.findViewById<ImageView>(R.id.list_item_right_icon).hide()
        }
    }

    fun setItems(newItems: MutableList<EventSourceUiModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private val layoutInflater: LayoutInflater by lazy {
        LayoutInflater.from(context)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val vh: EventSourceViewHolder
        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.list_item_no_icon, parent, false)
            vh = EventSourceViewHolder(view)
            view?.tag = vh
        }
        else {
            view = convertView
            vh = view.tag as EventSourceViewHolder
        }
        vh.icon.load(context, items[position].sourceIcon)
        vh.text.text = context.getString(items[position].sourceDescription)
        return view
    }

    override fun getItem(position: Int): Any? = null

    override fun getItemId(position: Int): Long = 0

    override fun getCount(): Int = items.size
}
