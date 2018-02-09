package io.jitrapon.glom.board.event.autocomplete

import android.text.TextUtils
import android.util.SparseArray
import androidx.util.set
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.repository.UserRepository
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * A client-side component that extracts information such as event name, date/time, location, and/or participants
 * from a string
 *
 * Created by Jitrapon
 */
class EventAutocompleter(private val callback: Callbacks, private val placeProvider: PlaceProvider) {

    /* saved event fields */
    private val fields = SparseArray<Any>(4)

    /* current locale supported */
    private var locale = Locale.ENGLISH

    /* locale-specific words to search for */
    private val timeConjunctions = listOf("on")
    private val placeConjunctions = listOf("at", "from", "to")
    private val peopleConjunctions = listOf("with", "for")
    private val nameSuggestions = listOf("Haircut", "Lunch", "Email", "Dinner", "Party", "Pick up package",
            "Pick up", "Pick up prescription", " Pick up dry cleaning", "Pick up cake", "Pick up kids")

    /* All the field types */
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

            // attempt to extract the last word in this text, see if it matches any of the conjunctions
            // in this locale
            val lastWord = getLastWord(text, ' ')

            // if the last word matches any of the conjunction, show suggestions based on that conjunction
            val suggestions = ArrayList<Suggestion>()
            timeConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                suggestions.addAll(listOf(
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)))
                ))
            }
            placeConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
//                placeProvider.retrievePlaces()
                return
            }
            peopleConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                UserRepository.getAll()?.let {
                    suggestions.addAll(it.map { Suggestion(it) })
                }
            }

            // suggest for event names if it's not yet saved
            val emptyFields = getIncompleteFields()
            val names = ArrayList<Any>().apply {
                if (suggestions.isEmpty()) {
                    val trimmed = text.trim()

                    // if the entire text so far matches any of the name suggestions, we can proceed to suggest
                    // other fields to enter
                    if (nameSuggestions.any { it.equals(trimmed, ignoreCase = true) }) {
                        if (emptyFields.any { it == DATE }) add(Suggestion("on", "When..?"))
                        if (emptyFields.any { it == PLACE }) add(Suggestion("at", "Where..?"))
                        if (emptyFields.any { it == INVITEES }) add(Suggestion("with", "With..?"))
                        fields[Field.NAME] = trimmed
                    }

                    // otherwise, add all the name suggestions if any of them matches the text so far
                    if (emptyFields.any { it == NAME }) {
                        addAll(
                                nameSuggestions.filter {
                                    it.startsWith(text, ignoreCase = true) && !it.equals(text, ignoreCase = true)
                                }.map {
                                    Suggestion(it)
                                })
                    }
                }
            }

//            AppLogger.i("NAME = ${fields[Field.NAME]}, " +
//                    "DATE = ${fields[Field.DATE]}, " +
//                    "PLACE = ${fields[Field.PLACE]}, " +
//                    "INVITEES = ${fields[Field.INVITEES]}")

            callback.onSuggestionsAvailable(ArrayList<Any>().apply {
                addAll(names)
                addAll(suggestions)
            })
        }
    }
    
    private fun getLastWord(text: String, delimiter: Char): String {
        return if (text.last() == delimiter) {
            text.dropLast(1).let {
                val lastSpaceIndex = it.lastIndexOf(delimiter)
                if (lastSpaceIndex != -1) it.substring(lastSpaceIndex, it.length).trim() else it
            }
        }
        else {
            val lastSpaceIndex = text.lastIndexOf(delimiter)
            if (lastSpaceIndex != -1) text.substring(lastSpaceIndex, text.length).trim() else text
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
    private fun getIncompleteFields(): List<Int> {
        return ArrayList<Int>().apply {
            (0 until 4)
                    .filter { fields.valueAt(it) == null }
                    .forEach { add(it) }
        }
    }
}
