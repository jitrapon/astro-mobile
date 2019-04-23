package io.jitrapon.glom.base.ui.widget.recyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.PreferenceItemUiModel
import io.jitrapon.glom.base.util.getString
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.load
import io.jitrapon.glom.base.util.show

class DialogRecyclerViewAdapter(private val context: Context,
                                private val items: ArrayList<PreferenceItemUiModel>,
                                private val onItemClick: (Int) -> Unit) : RecyclerView.Adapter<DialogRecyclerViewAdapter.DialogListItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DialogListItemViewHolder {
        return DialogListItemViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.dialog_list_item_image_text, parent, false), onItemClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DialogListItemViewHolder, position: Int) {
        holder.textView.text = context.getString(items[position].title)
        holder.imageView.apply {
            if (items[position].leftImage == null) hide(invisible = true)
            else {
                show()
                load(context, items[position].leftImage)
            }
        }
    }

    inner class DialogListItemViewHolder(itemView: View, onItemClick: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {

        val textView: TextView = itemView.findViewById(R.id.dialog_list_item_text)
        val imageView: ImageView = itemView.findViewById(R.id.dialog_list_item_image)

        init {
            itemView.setOnClickListener {
                onItemClick(adapterPosition)
            }
        }
    }
}
