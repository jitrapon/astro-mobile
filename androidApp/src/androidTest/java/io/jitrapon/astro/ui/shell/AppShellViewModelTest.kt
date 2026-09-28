package io.jitrapon.astro.ui.shell

import io.jitrapon.astro.data.calendar.CalendarDate
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins that the shell moves its calendar screen to the month the device is in now — the window a
 * month-view request covers, from its first day to its last — when asked to.
 */
class AppShellViewModelTest {

    @Test
    fun showingTheCurrentMonthMovesTheScreenToItsFirstAndLastDay() {
        val moves = mutableListOf<Pair<CalendarDate, CalendarDate>>()
        val shellViewModel = AppShellViewModel {
            CalendarScreenHandle(
                state = MutableStateFlow(CalendarUiState(null, isLoading = true, failure = null)),
                dispatch = { null },
                moveWindow = { start, end -> moves += start to end },
            )
        }

        shellViewModel.showCurrentMonth()

        val now = GregorianCalendar(TimeZone.getDefault())
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val lastDay = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        assertEquals(
            listOf(CalendarDate(year, month, 1) to CalendarDate(year, month, lastDay)),
            moves,
        )
    }
}
