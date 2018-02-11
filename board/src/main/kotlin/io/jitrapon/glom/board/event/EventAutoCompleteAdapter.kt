package io.jitrapon.glom.board.event

import android.content.Context
import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import java.util.*

/**
 * Adapter for event name autocomplete. Supports suggesting names, dates, places, and contact names
 * to be part of the event name as user types
 *
 * @author Jitrapon Tiachunpun
 */
class EventAutoCompleteAdapter(private val viewModel: EventItemViewModel, context: Context, @LayoutRes resId: Int) :
        ArrayAdapter<Suggestion>(context, resId), Filterable {

    /* results object payloads to display */
    private var suggestions = ArrayList<Suggestion>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        val textView: TextView = view.findViewById(android.R.id.text1)
        textView.text = viewModel.getSuggestionText(suggestions[position])
        return view
    }

    override fun getItem(position: Int): Suggestion = suggestions[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = suggestions.size

    override fun getFilter(): Filter {
        return object : Filter() {
            private val lock = Any()
            private val lockTwo = Any()

            /**
             * Invoked in a worker thread.
             */
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    constraint.let {
                        if (it != null && it.isNotEmpty()) {
                            viewModel.filterSuggestions(it.toString()).let {
                                values = it
                                count = it.size
                            }
                        }
                        else {
                            synchronized(lock) {
                                values = ArrayList<Suggestion>()
                                count = 0
                            }
                        }
                    }
                }
            }

            /**
             * Published on UI thread
             */
            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                results.let {
                    suggestions = results.values.let {
                        if (it != null && it is ArrayList<*>) it as ArrayList<Suggestion>
                        else ArrayList()
                    }
                    if (it.count > 0) notifyDataSetChanged()
                    else notifyDataSetInvalidated()
                }
            }
        }
    }
}
