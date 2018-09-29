package io.jitrapon.glom.base.ui.widget.recyclerview

import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup

/**
 * Adapter of the Recyclerview that shows partial number of items from a list.
 *
 * Created by Jitrapon
*/
abstract class PartialRecyclerViewAdapter<T> : androidx.recyclerview.widget.RecyclerView.Adapter<T>() where T : androidx.recyclerview.widget.RecyclerView.ViewHolder {

    /**
     * Child class should return the view type integer to represent the more item type
     */
    abstract fun getMoreItemViewType(): Int
    
    /**
     * Child class should return the number of items to be visible
     */
    abstract fun getVisibleItemCount(): Int

    /**
     * Child class should return the number of items originally in the list
     */
    abstract fun getAllItemCount(): Int

    /**
     * Creates a viewholder class that shows remaining number of items
     */
    abstract fun onCreateMoreViewHolder(parent: ViewGroup): T

    /**
     * Creates one or more types of viewholder classes that show the visible items
     */
    abstract fun onCreateOtherViewHolder(parent: ViewGroup, viewType: Int): T

    /**
     * Returns the item view type of the visible list
     */
    abstract fun getOtherItemViewType(position: Int): Int

    /**
     * Child class should override to bind more item
     */
    abstract fun onBindMoreItemViewHolder(holder: T, remainingItemCount: Int)

    /**
     * Child class should override to bind other visible item
     */
    abstract fun onBindOtherItemViewHolder(holder: T, position: Int)
    
    override fun getItemViewType(position: Int): Int {
        return if (position == itemCount - 1 && getVisibleItemCount() < getAllItemCount()) getMoreItemViewType()
        else getOtherItemViewType(position)
    }
    
    override fun getItemCount(): Int {
        val allCount = getAllItemCount()
        val visibleCount = getVisibleItemCount()
        return if (allCount == 0) 0 else Math.min(allCount, visibleCount + 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T {
        return when (viewType) {
            getMoreItemViewType() -> onCreateMoreViewHolder(parent)
            else -> onCreateOtherViewHolder(parent, viewType)
        }
    }

    override fun onBindViewHolder(holder: T, position: Int) {
        if (getItemViewType(position) == getMoreItemViewType()) {
            onBindMoreItemViewHolder(holder, getAllItemCount() - getVisibleItemCount())
        }
        else {
            onBindOtherItemViewHolder(holder, position)
        }
    }
}