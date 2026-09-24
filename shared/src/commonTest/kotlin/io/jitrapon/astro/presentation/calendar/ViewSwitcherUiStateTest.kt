package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.AgendaViewSelection
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.calendar.ViewSwitcher
import io.jitrapon.astro.data.calendar.YearViewSelection
import io.jitrapon.astro.data.calendar.decodeMonthScreenFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins how the delivered view switcher becomes the state a platform draws, starting from the
 * vendored contract fixture so the options are shaped the way the server actually sends them.
 */
class ViewSwitcherUiStateTest {

    @Test
    fun exactlyOneFixtureOptionIsActiveAndItCarriesTheDeliveredActiveSelection() {
        val switcher = fixtureSwitcher()

        val active = switcher.toViewSwitcherUiState().options.filter { it.isActive }

        assertEquals(1, active.size)
        assertEquals(SwitchCalendarViewAction(switcher.activeSelection), active.single().action)
    }

    @Test
    fun optionsKeepContractOrderIdsLabelsAndTheSelectionEachChooses() {
        val switcher = fixtureSwitcher()

        val options = switcher.toViewSwitcherUiState().options

        assertEquals(switcher.options.map { it.id }, options.map { it.id })
        assertEquals(switcher.options.map { it.label }, options.map { it.label })
        assertEquals(
            switcher.options.map { SwitchCalendarViewAction(it.selection) },
            options.map { it.action },
        )
    }

    @Test
    fun twoOptionsOfferingTheActiveSelectionMarkOnlyTheFirstActive() {
        val switcher = fixtureSwitcher().copy(activeSelection = AgendaViewSelection)
        val agenda = switcher.options.first { it.selection == AgendaViewSelection }
        val duplicated =
            switcher.copy(options = switcher.options + agenda.copy(id = "agenda-again"))

        val options = duplicated.toViewSwitcherUiState().options

        assertEquals(listOf(agenda.id), options.filter { it.isActive }.map { it.id })
    }

    @Test
    fun anActiveSelectionNoOptionOffersMarksNothingActive() {
        val switcher = fixtureSwitcher()
        val withoutYear =
            switcher.copy(
                activeSelection = YearViewSelection,
                options = switcher.options.filterNot { it.selection == YearViewSelection },
            )

        val options = withoutYear.toViewSwitcherUiState().options

        assertTrue(options.isNotEmpty())
        assertTrue(options.none { it.isActive })
    }
}

private fun fixtureSwitcher(): ViewSwitcher = decodeMonthScreenFixture().screen.viewSwitcher
