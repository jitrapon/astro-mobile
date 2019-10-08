package io.jitrapon.glom.board.item.event.widget

import android.text.TextUtils
import android.util.SparseArray
import androidx.core.util.set
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.datastructure.LimitedBooleanArray
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.model.PlaceInfo
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.addDay
import io.jitrapon.glom.base.util.addMinute
import io.jitrapon.glom.base.util.isToday
import io.jitrapon.glom.base.util.roundToNextHalfHour
import io.jitrapon.glom.base.util.setTime
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventItemDataSource
import io.jitrapon.glom.board.item.event.Suggestion
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Created by Jitrapon
 */
class EventNameAutocompleteInteractor(val circleInteractor: CircleInteractor, val placeProvider: PlaceProvider,
                                      val eventItemDataSource: EventItemDataSource) {

    companion object {
        private const val FIELD_COUNT = 5 // this changes according to how many fields we have below
        private const val NAME = 0 // stores object of type String
        private const val START_DAY =
            1 // stores object of type Triple<Calendar.DAY_OF_MONTH, Boolean(true), Date>
        private const val START_TIME =
            2 // stores object of type Triple<Calendar.HOUR_OF_DAY, Boolean(true), Date>
        private const val LOCATION = 3 // stores object of type EventLocation
        private const val INVITEES = 4 // stores object of type List<User>
    }

    /* saved event fields */
    private val fields = SparseArray<Any?>(FIELD_COUNT)

    /* current locale supported */
    private var locale = Locale.ENGLISH

    /* locale-specific words to search for */
    private val timeConjunctions = listOf("on")
    private val placeConjunctions = listOf("at", "from", "to")
    private val peopleConjunctions = listOf("with", "for")
    private val nameSuggestions = listOf(
        "Haircut", "Lunch", "Email", "Dinner", "Party", "Pick up package",
        "Pick up", "Pick up prescription", " Pick up dry cleaning", "Pick up cake", "Pick up kids"
    )

    private var ignoreElements = ArrayList<String>()
    private val filterConditions = LimitedBooleanArray(FIELD_COUNT, 1)
    private var lastModifiedField: Int = -1
    private var subQueryStartIndex: Int = -1

    /* convenient event item instance */
    val event: EventItem
        get() = eventItemDataSource.getItem().blockingFirst()

    /**
     * Returns fields the user has not completely entered based on the input string so far
     */
    private fun getIncompleteFields(): List<Int> {
        return ArrayList<Int>().apply {
            (0 until FIELD_COUNT)
                .filter { fields[it] == null }
                .forEach { add(it) }
        }
    }

    /**
     * Process the query string and return the list of place suggestions based on the query. This function
     * should be called in a background thread
     */
    fun filterLocationSuggestions(text: String): List<Suggestion> {
        return getPlaceSuggestions(text)
    }

    fun getSubQueryStartIndex(): Int = subQueryStartIndex

    private fun getDaySuggestions(query: String): List<Suggestion> {
        val now = Date()
        return ArrayList<Suggestion>().apply {
            for (dayOffset in 0..10) {
                add(
                    Suggestion(
                        Triple(
                            Calendar.DAY_OF_MONTH, true, now.addDay(dayOffset)
                        )
                    )
                )
            }
        }
    }

    private fun getTimeSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            val startDay = fields[START_DAY] as Date
            val startingTime: Date =
                if (startDay.isToday()) startDay.roundToNextHalfHour() else startDay.setTime(7, 0)
            when {
                fields[START_TIME] == null -> (0..300 step 30).mapTo(this) {
                    Suggestion(
                        Triple(
                            Calendar.HOUR_OF_DAY, true, startingTime.addMinute(it)
                        )
                    )
                }
            }
        }
    }

    private fun getPlaceSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            val showAllCustomPlaces = TextUtils.isEmpty(query) || placeConjunctions.contains(query)

            // add all places saved in this circle
            circleInteractor.getActiveCirclePlaces().let {
                if (it.isNotEmpty()) {
                    val places = if (showAllCustomPlaces) it
                    else it.filter {
                        !TextUtils.isEmpty(it.name) && it.name!!.startsWith(query, true)
                    }
                    addAll(places.map { Suggestion(it) })
                }
            }

            // add places from Google Place API
            if (!showAllCustomPlaces) {
                placeProvider?.getAutocompletePrediction(query)?.blockingGet()?.let {
                    it.map {
                        Suggestion(
                            PlaceInfo(
                                it.getPrimaryText(null)?.toString(),
                                null,
                                it.getSecondaryText(null)?.toString(),
                                null,
                                null,
                                null,
                                it.placeId,
                                null
                            )
                        )
                    }.let { addAll(it) }
                }
            }
        }
    }

    private fun getInviteeSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            circleInteractor.getActiveUsersInCircle().let {
                val users = if (TextUtils.isEmpty(query) || peopleConjunctions.contains(query)) it
                else it.filter {
                    !TextUtils.isEmpty(it.userName) && it.userName.startsWith(query, true)
                }
                addAll(users.map { Suggestion(it) })
            }
        }
    }

    fun getSelectedDate(): Date? {
        return fields[START_DAY]?.let {
            Calendar.getInstance().run {
                time = it as Date
                if (fields[START_TIME] != null) {
                    val cal = Calendar.getInstance()
                    cal.time = (fields[START_TIME] as Date)
                    set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                }
                time
            }
        }
    }

    /**
     * Apply the current suggestion and update field
     */
    fun applySuggestion(
        currentText: String,
        selected: Suggestion,
        displayText: String,
        delimiter: String
    ) {
        selected.selectData.let {
            when (it) {
                is Triple<*, *, *> -> {
                    if (it.first == Calendar.DAY_OF_MONTH) {
                        if (it.second == true) {
                            fields[START_DAY] = it.third
                            filterConditions[START_DAY] = false
                        }
                    }
                    else {
                        if (it.second == true) {
                            fields[START_TIME] = it.third
                            filterConditions[START_TIME] = false
                        }
                    }

                    var newText = currentText
                    ignoreElements.forEach {
                        newText = newText.replace(it, "", true)
                    }
                    timeConjunctions.forEach {
                        newText = newText.replaceLast(it + delimiter, "", true)
                        ignoreElements.add(it + delimiter)
                    }
                    ignoreElements.add(displayText)
                    eventItemDataSource.setName(newText.trim())
                }
                is PlaceInfo -> {
                    fields[LOCATION] = it
                    filterConditions[LOCATION] = false

                    var newText = currentText
                    ignoreElements.forEach {
                        newText = newText.replace(it, "", true)
                    }
                    placeConjunctions.forEach {
                        newText = newText.replaceLast(it + delimiter, "", true)
                        ignoreElements.add(it + delimiter)
                    }
                    ignoreElements.add(displayText)
                    eventItemDataSource.setName(newText.trim())
                }
                else -> { /* do nothing */
                }
            }
        }
        debugLog()
    }

    private fun String.replaceLast(
        toReplace: String,
        replacement: String,
        ignoreCase: Boolean = false
    ): String {
        val start = lastIndexOf(toReplace, ignoreCase = ignoreCase)
        if (start == -1) return this
        return StringBuilder().let {
            it.append(substring(0, start))
            it.append(replacement)
            it.append(substring(start + toReplace.length))
            it.toString()
        }
    }

    fun removeSuggestion(removed: Suggestion) {
        debugLog()
    }

    private fun clearSuggestionCache() {
        fields.clear()
        ignoreElements.clear()
        filterConditions.clear()
        lastModifiedField = -1
        subQueryStartIndex = -1
    }

    private fun debugLog() {
        AppLogger.i(
            "name=${event.itemInfo.eventName}, " +
                    "startDay=${fields[START_DAY]}, " +
                    "startHour=${fields[START_TIME]}, " +
                    "place=${fields[LOCATION]}, " +
                    "invitees=${fields[INVITEES]}"
        )
    }
}