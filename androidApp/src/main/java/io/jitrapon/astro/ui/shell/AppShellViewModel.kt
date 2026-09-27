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
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.presentation.calendar.CalendarViewModel
import io.jitrapon.astro.presentation.calendar.ViewSwitcherOptionUiState
import io.jitrapon.astro.presentation.shell.AppShellState
import io.jitrapon.astro.presentation.shell.AppShellTab
import io.jitrapon.astro.presentation.shell.toAppShellState
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.context.GlobalContext

/**
 * Holds the app shell's state across configuration changes: it observes the current month's
 * calendar screen, publishes that screen and the shell it implies, and forwards what the person
 * does on the screen back to it.
 *
 * It derives nothing itself. The shell is the shared [CalendarViewModel]'s state mapped through
 * [toAppShellState], the one projection both apps use, so Android and iOS cannot disagree about
 * which destinations become tabs or when the bar is shown.
 *
 * The screen is opened through [openCalendarScreen] on this view model's own scope, so it lives
 * exactly as long as the shell does. Production opens a [CalendarViewModel]; see [Factory].
 */
class AppShellViewModel(openCalendarScreen: (CoroutineScope) -> CalendarScreenHandle) :
    ViewModel() {

    private val calendarScreen = openCalendarScreen(viewModelScope)

    /**
     * The calendar screen as it stands now — its title, body and view switcher. Collecting it is
     * what starts the observation, and it stops when the last collector leaves, exactly as
     * [CalendarViewModel.state] does.
     */
    val calendarState: StateFlow<CalendarUiState> = calendarScreen.state

    /** The shell as it stands now, derived from [calendarState] and started by collecting it. */
    val shellState: StateFlow<AppShellState> =
        calendarScreen.state
            .map { it.toAppShellState() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                calendarScreen.state.value.toAppShellState(),
            )

    /**
     * Switches the calendar to [option]'s view. The screen consumes the switch itself — it
     * re-points the request it observes — so there is no effect for the platform to carry out.
     */
    fun selectCalendarView(option: ViewSwitcherOptionUiState) {
        calendarScreen.dispatch(option.action)
    }

    /**
     * Carries out [tab]'s action as far as the screen can, and returns the effect the shell must
     * carry out for the rest — or `null` when the screen consumed it, as a view switch is.
     */
    fun selectTab(tab: AppShellTab): ActionEffect? = calendarScreen.dispatch(tab.action)

    companion object {
        /**
         * Builds the view model over a [CalendarViewModel] observing the month the device is in
         * now, through the repository resolved from the graph [AstroApplication] started.
         *
         * Resolution happens here, at the composition edge, so the class itself takes a plain
         * function and a test can hand it any screen.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val calendarScreenRepository: CalendarScreenRepository = GlobalContext.get().get()
                val request = currentMonthRequest()
                AppShellViewModel { scope ->
                    CalendarViewModel(calendarScreenRepository, request, scope)
                        .toCalendarScreenHandle()
                }
            }
        }
    }
}

/**
 * The month-view request for the month the device is in now, in the device's zone and locale.
 *
 * The window ends on the month's own last day rather than a fixed count, so a screen promising the
 * current month is not missing its final days.
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
