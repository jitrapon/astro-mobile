package io.jitrapon.glom.base.component

import android.text.TextUtils
import android.util.SparseArray
import io.jitrapon.glom.base.util.AppLogger
import java.util.*

/**
 * A client-side component that extracts information such as event name, date/time, location, and/or participants
 * from a string
 *
 * Created by Jitrapon
 */
class LocalEventNameAutocompleter(private val callback: Callbacks, private val placeProvider: PlaceProvider) {

    /* cached event fields */
    private val fields = SparseArray<Any>(4)

    /* text user has entered so far */
    private val inputText: StringBuilder = StringBuilder()

    /* currently active field to search for */
    private var currentField: Int? = null

    /* current locale supported */
    private var locale = Locale.ENGLISH

    /* locale-specific words to search for */
    private val keywords = listOf("with", "on", "at", "to", "from", "for")

    /* suggestion of names */
    private val nameSuggestions = listOf("Haircut", "Lunch", "Email", "Dinner", "Party", "Pick up package",
            "Pick up", "Pick up prescription", " Pick up dry cleaning", "Pick up cake", "Pick up kids")

    private val dateFieldMissingText = "When?"
    private val placeFieldMissingText = "Where?"
    private val inviteesFieldMissingText = "Invite Anyone?"

    /* locale-specific word delimiter */
    private val delimiter = ' '

    companion object Field {

        const val NAME = 0
        const val DATE = 1
        const val PLACE = 2
        const val INVITEES = 3
    }

    /**
     * Update current input text to be parsed
     */
    fun updateText(text: String) {
        if (TextUtils.isEmpty(text)) return

        if (locale == Locale.ENGLISH) {
            // attempt to extract the last word in this text, see if it matches any of the keywords
            // in this locale
            val lastWord = if (text.last() == delimiter) {
                text.dropLast(1).let {
                    val lastSpaceIndex = it.lastIndexOf(delimiter)
                    if (lastSpaceIndex != -1) it.substring(lastSpaceIndex, it.length).trim() else it
                }
            }
            else {
                val lastSpaceIndex = text.lastIndexOf(delimiter)
                if (lastSpaceIndex != -1) text.substring(lastSpaceIndex, text.length).trim() else text
            }

            // if the last word matches any of the keywords, show suggestions based on that category
            AppLogger.i("Last word entered is \"$lastWord\"")
            val categories = ArrayList<Any>()
            keywords.find { it.equals(lastWord, ignoreCase = true) }?.let {
                AppLogger.i("Keyword $it found")
                categories.addAll(listOf("Tomorrow", "Today", "Some place", "Other place", "Jitrapon"))
            }

            // suggest for event names if it's not yet saved
            val emptyFields = getIncompleteFields()
            val names = ArrayList<String>().apply {
                if (categories.isEmpty()) {
                    val trimmed = text.trim()

                    // if the entire text so far matches any of the name suggestions, we can proceed to suggest
                    // other fields to enter
                    if (nameSuggestions.any { it.equals(trimmed, ignoreCase = true) }) {
                        if (emptyFields.any { it == Field.DATE}) add(dateFieldMissingText)
                        if (emptyFields.any { it == Field.PLACE}) add(placeFieldMissingText)
                        if (emptyFields.any { it == Field.INVITEES}) add(inviteesFieldMissingText)
                    }
                    if (emptyFields.any { it == Field.NAME }) {
                        addAll(nameSuggestions.filter { it.startsWith(text, ignoreCase = true) && !it.equals(text, ignoreCase = true) })
                    }
                }
            }

            AppLogger.i("NAME = ${fields[Field.NAME]}, " +
                    "DATE = ${fields[Field.DATE]}, " +
                    "PLACE = ${fields[Field.PLACE]}, " +
                    "INVITEES = ${fields[Field.INVITEES]}")

            callback.onSuggestionsAvailable(ArrayList<Any>().apply {
                addAll(names)
                addAll(categories)
            })
        }
    }

    /**
     * Callbacks for listener class to implement to listen for specific events
     * when certain features are extracted
     */
    interface Callbacks {

        /**
         * Called when a list of suggestion is available to be displayed
         * The types available to show are:
         * 1. Field indicator (i.e. Date/time, Place, Invitee)
         * 2. Name suggestion (i.e. Exercise, Haircut, Pick up) [String]
         * 3. Contact suggestion (i.e. John Doe) [Contact]
         * 4. Date time suggestion (i.e. Tomorrow 7 PM) [java.util.Date]
         * 5. Place suggestion (i.e. Emporium) [Place]
         */
        fun onSuggestionsAvailable(results: List<Any>)
    }

    /**
     * Returns fields the user has not completely entered based on the input string so far
     */
    fun getIncompleteFields(): List<Int> {
        return ArrayList<Int>().apply {
            (0 until 4)
                    .filter { fields.valueAt(it) == null }
                    .forEach { add(it) }
        }
    }
}
