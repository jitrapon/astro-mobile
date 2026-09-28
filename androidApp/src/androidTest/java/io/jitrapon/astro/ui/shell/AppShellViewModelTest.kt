package io.jitrapon.astro.ui.shell

import io.jitrapon.astro.data.calendar.CalendarDate
import io.jitrapon.astro.data.calendar.CalendarScreenRequest
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins that the shell brings its calendar screen up to what it would ask for now — the month the
 * device is in, from its first day to its last, in the device's zone and language — when asked to.
 */
class AppShellViewModelTest {

    @Test
    fun showingTheCurrentMonthHandsTheScreenTodaysRequest() {
        val updates = mutableListOf<CalendarScreenRequest>()
        val shellViewModel = AppShellViewModel {
            CalendarScreenHandle(
                state = MutableStateFlow(CalendarUiState(null, isLoading = true, failure = null)),
                dispatch = { null },
                updateRequest = { updates += it },
            )
        }

        shellViewModel.showCurrentMonth()

        val now = GregorianCalendar(TimeZone.getDefault())
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val lastDay = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val update = updates.single()
        assertEquals(CalendarDate(year, month, 1), update.start)
        assertEquals(CalendarDate(year, month, lastDay), update.end)
        assertEquals(TimeZone.getDefault().id, update.timeZone)
        assertEquals(Locale.getDefault().toLanguageTag(), update.locale)
    }
}
