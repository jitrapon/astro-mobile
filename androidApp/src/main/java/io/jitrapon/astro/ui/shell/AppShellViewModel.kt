package io.jitrapon.astro.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.jitrapon.astro.data.calendar.CalendarDate
import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.jitrapon.astro.data.calendar.CalendarScreenRequest
import io.jitrapon.astro.data.calendar.RequestedCalendarView
import io.jitrapon.astro.presentation.calendar.CalendarViewModel
import io.jitrapon.astro.presentation.shell.AppShellState
import io.jitrapon.astro.presentation.shell.toAppShellState
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.context.GlobalContext

/**
 * Holds the app shell's state across configuration changes: it observes the current month's
 * calendar screen and publishes the shell that screen implies.
 *
 * It derives nothing itself. The shell is the shared [CalendarViewModel]'s state mapped through
 * [toAppShellState], the one projection both apps use, so Android and iOS cannot disagree about
 * which destinations become tabs or when the bar is shown.
 *
 * The request is fixed at construction. A shell that outlives a month boundary keeps observing the
 * month it started in, which is acceptable while the shell renders only destinations — they do not
 * depend on the month asked for.
 */
class AppShellViewModel(
    calendarScreenRepository: CalendarScreenRepository,
    request: CalendarScreenRequest,
) : ViewModel() {

    private val calendarViewModel =
        CalendarViewModel(calendarScreenRepository, request, viewModelScope)

    /**
     * The shell as it stands now. Collecting it is what starts the observation, and it stops when
     * the last collector leaves, exactly as [CalendarViewModel.state] does.
     */
    val shellState: StateFlow<AppShellState> =
        calendarViewModel.state
            .map { it.toAppShellState() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                calendarViewModel.state.value.toAppShellState(),
            )

    companion object {
        /**
         * Builds the view model with the repository resolved from the graph [AstroApplication]
         * started and the request for the month the device is in now.
         *
         * Resolution happens here, at the composition edge, so the class itself takes plain
         * constructor arguments and a test can hand it any repository.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppShellViewModel(
                    calendarScreenRepository = GlobalContext.get().get(),
                    request = currentMonthRequest(),
                )
            }
        }
    }
}

/**
 * The month-view request for the month the device is in now, in the device's zone and locale.
 *
 * The window ends on the month's own last day rather than a fixed count, so a screen promising the
 * current month is not missing its final days. Built with [GregorianCalendar] rather than
 * `java.time`, which is unavailable below API 26 without core-library desugaring.
 */
private fun currentMonthRequest(): CalendarScreenRequest {
    val timeZone = TimeZone.getDefault()
    val now = GregorianCalendar(timeZone)
    val year = now.get(Calendar.YEAR)
    // Calendar months are zero-based; the contract's are one-based.
    val month = now.get(Calendar.MONTH) + 1
    return CalendarScreenRequest(
        view = RequestedCalendarView.Month,
        start = CalendarDate(year = year, month = month, dayOfMonth = 1),
        end =
            CalendarDate(
                year = year,
                month = month,
                dayOfMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH),
            ),
        timeZone = timeZone.id,
        locale = Locale.getDefault().toLanguageTag(),
    )
}
