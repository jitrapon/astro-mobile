package io.jitrapon.astro.ui.component

import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.jitrapon.astro.data.calendar.CalendarComponentIds
import io.jitrapon.astro.data.calendar.WeekStart
import io.jitrapon.astro.presentation.calendar.AgendaBodyUiState
import io.jitrapon.astro.presentation.calendar.AgendaDayUiState
import io.jitrapon.astro.presentation.calendar.CalendarBodyUiState
import io.jitrapon.astro.presentation.calendar.EventChipUiState
import io.jitrapon.astro.presentation.calendar.MonthBodyUiState
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.main.theme.AstroTheme
import org.junit.Rule
import org.junit.Test

/**
 * Pins that every calendar component `:shared` models has an Android renderer, and that an id with
 * none is drawn by the visible fallback rather than dropped.
 *
 * The ids come from [CalendarComponentIds]' own sets, not a list restated here, so a component
 * added to `:shared` without a renderer fails this test instead of shipping as a fallback.
 */
class CalendarComponentRegistryTest {

    // Hosted in the app's own activity for the same reason as the shell tests: the bare activity
    // `createComposeRule()` uses resolves only through a debug-only manifest, and instrumented
    // tests
    // run against the minified release build.
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyModelledBodyComponentIsDrawnByItsRegisteredRenderer() {
        val bodies = CalendarComponentIds.BODY_IDS.map { bodyStateFor(it) }

        show { bodies.forEach { CalendarBodyComponent(it) } }

        bodies.forEach { body ->
            composeRule
                .onNodeWithTag(CalendarComponentTestTags.registered(body.componentId))
                .assertIsDisplayed()
            composeRule
                .onNodeWithTag(CalendarComponentTestTags.unregistered(body.componentId))
                .assertDoesNotExist()
        }
    }

    @Test
    fun everyModelledEventPresentationIsDrawnByItsRegisteredRenderer() {
        val events = CalendarComponentIds.EVENT_PRESENTATION_IDS.map { eventWith(componentId = it) }

        show { events.forEach { CalendarEventComponent(it) } }

        events.forEach { event ->
            composeRule
                .onNodeWithTag(CalendarComponentTestTags.registered(event.componentId))
                .assertIsDisplayed()
            composeRule
                .onNodeWithTag(CalendarComponentTestTags.unregistered(event.componentId))
                .assertDoesNotExist()
        }
    }

    @Test
    fun anUnregisteredComponentIsDrawnByTheFallback() {
        val body = monthBody(componentId = UNREGISTERED_BODY_ID)
        val event = eventWith(componentId = UNREGISTERED_EVENT_ID)

        show {
            CalendarBodyComponent(body)
            CalendarEventComponent(event)
        }

        listOf(UNREGISTERED_BODY_ID, UNREGISTERED_EVENT_ID).forEach { id ->
            composeRule
                .onNodeWithTag(CalendarComponentTestTags.unregistered(id))
                .assertIsDisplayed()
            composeRule.onNodeWithTag(CalendarComponentTestTags.registered(id)).assertDoesNotExist()
        }
    }

    @Test
    fun aBodyStateThatDoesNotMatchItsIdIsDrawnByTheFallbackRatherThanFailing() {
        // An agenda state claiming the month id — only a hand-built state can pair them this way,
        // and the registry must neither cast it to a month nor throw mid-frame.
        val mismatched =
            AgendaBodyUiState(componentId = CalendarComponentIds.MONTH_BODY, days = emptyList())

        show { CalendarBodyComponent(mismatched) }

        composeRule
            .onNodeWithTag(CalendarComponentTestTags.unregistered(CalendarComponentIds.MONTH_BODY))
            .assertIsDisplayed()
    }

    private fun show(content: @Composable () -> Unit) {
        // MainActivity composes the live shell in `onCreate`, and the rule refuses to set content
        // over an activity that already has some. Detach it first so only the states below render.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        }
        composeRule.setContent { AstroTheme { Column { content() } } }
    }

    private companion object {
        const val UNREGISTERED_BODY_ID = "calendar.unmodelled.v1"
        const val UNREGISTERED_EVENT_ID = "calendar.event.unmodelled.v1"

        fun bodyStateFor(componentId: String): CalendarBodyUiState =
            when (componentId) {
                CalendarComponentIds.MONTH_BODY -> monthBody(componentId)
                CalendarComponentIds.AGENDA_BODY ->
                    AgendaBodyUiState(
                        componentId = componentId,
                        days =
                            listOf(
                                AgendaDayUiState(
                                    date = "2026-04-16",
                                    headerLabel = "16 เมษายน",
                                    events = emptyList(),
                                )
                            ),
                    )
                else -> error("No test state for body component $componentId — add one here.")
            }

        fun monthBody(componentId: String): MonthBodyUiState =
            MonthBodyUiState(
                componentId = componentId,
                headerLabel = "เมษายน 2569",
                monthAnchor = "2026-04-01",
                weekStart = WeekStart.MONDAY,
                events = emptyList(),
            )

        fun eventWith(componentId: String): EventChipUiState =
            EventChipUiState(
                eventId = "event-$componentId",
                componentId = componentId,
                title = "Standup",
                leadingIconToken = null,
                subtitleLines = emptyList(),
                chipStyle = null,
                calendarColor = null,
                accessibilityLabel = null,
            )
    }
}
