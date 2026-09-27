package io.jitrapon.astro

import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.platform.app.InstrumentationRegistry
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.AgendaViewSelection
import io.jitrapon.astro.data.calendar.CalendarScreenResponse
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.data.calendar.OpenEventDetailAction
import io.jitrapon.astro.data.calendar.OpenUrlAction
import io.jitrapon.astro.data.calendar.PresentModalAction
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.network.createLenientBackendJson
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.action.toActionEffect
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.presentation.calendar.MonthBodyUiState
import io.jitrapon.astro.ui.component.CalendarBodyComponent
import io.jitrapon.astro.ui.component.CalendarComponentTestTags
import io.jitrapon.astro.ui.component.CalendarEventComponent
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.main.theme.AstroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pins that the shrunk app can still decode a contract response and act on what it carries — the
 * polymorphic serialization the component registry and the action model depend on, which R8 strips
 * when nothing keeps it and which no unshrunk build exercises.
 *
 * It decodes through the app's own lenient codec with a reified `decodeFromString<T>()` — the same
 * reflective `Companion.serializer()` lookup the app's HTTP client performs — so the serializers
 * under test are the app's shrunk ones. The keep rules generated for instrumented tests drop every
 * rule that would keep a serializer: referencing the model types from here keeps their names, but
 * nothing this test does keeps a serializer, so one R8 removed fails the decode below rather than
 * being rescued by the test's own presence.
 *
 * What it cannot vouch for is the shipping build itself. Every contract model it names is kept
 * whole in this APK — name, members and annotations, with R8's merging and inlining of those
 * classes switched off — where `release` leaves them to the optimizer.
 * `ReleaseContractRenderingTest` in `:androidAppReleaseTest` closes that gap by running black-box
 * against `release`'s own shrink.
 *
 * Kept apart from [MinifiedAppSmokeTest], which stays black-box so it proves the app starts with
 * nothing kept on its behalf.
 */
class MinifiedContractDecodingTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val json = createLenientBackendJson()

    @Test
    fun theDecodedFixturesBodyResolvesToItsRegisteredRenderer() {
        val body = checkNotNull(loaded(decodeFixture()).body) { "The fixture projected no body." }

        show { CalendarBodyComponent(body, onAction = {}) }

        composeRule
            .onAllNodesWithTag(CalendarComponentTestTags.registered(body.componentId))
            .assertCountEquals(1)
        composeRule
            .onAllNodesWithTag(CalendarComponentTestTags.unregistered(body.componentId))
            .assertCountEquals(0)
    }

    @Test
    fun everyDecodedEventResolvesToItsRegisteredRenderer() {
        val month = loaded(decodeFixture()).body as MonthBodyUiState
        val countsByComponent = month.events.groupingBy { it.componentId }.eachCount()

        show { month.events.forEach { CalendarEventComponent(it, onAction = {}) } }

        countsByComponent.forEach { (componentId, count) ->
            composeRule
                .onAllNodesWithTag(CalendarComponentTestTags.registered(componentId))
                .assertCountEquals(count)
            composeRule
                .onAllNodesWithTag(CalendarComponentTestTags.unregistered(componentId))
                .assertCountEquals(0)
        }
    }

    @Test
    fun anActionOfEachTypeDecodesAndMapsToItsEffect() {
        val decoded = ACTION_PAYLOADS.map { json.decodeFromString<Action>(it) }

        assertEquals(
            listOf(
                NavigateAction("expense"),
                OpenUrlAction("https://example.com/help"),
                SwitchCalendarViewAction(AgendaViewSelection),
                OpenEventDetailAction("s1"),
                PresentModalAction(listOf("s1", "s2")),
            ),
            decoded,
        )
        assertEquals(
            listOf(
                ActionEffect.ShowScreen("expense"),
                ActionEffect.OpenExternalUrl("https://example.com/help"),
                null,
                ActionEffect.ShowEventDetail("s1"),
                ActionEffect.ShowEvents(listOf("s1", "s2")),
            ),
            decoded.map { it.toActionEffect() },
        )
    }

    // Must be called once per test: the rule refuses a second `setContent`.
    private fun show(content: @Composable () -> Unit) {
        // MainActivity composes the live shell in `onCreate`, and the rule refuses to set content
        // over an activity that already has some. Detach it first so only this content renders.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        }
        composeRule.setContent { AstroTheme { Column { content() } } }
        composeRule.waitForIdle()
    }

    private fun decodeFixture(): CalendarScreenResponse {
        val fixture =
            InstrumentationRegistry.getInstrumentation().context.assets.open(FIXTURE_ASSET).use {
                it.readBytes().decodeToString()
            }
        return json.decodeFromString<CalendarScreenResponse>(fixture)
    }

    private companion object {
        const val FIXTURE_ASSET = "calendar-month-screen.v0.example.json"

        /** One payload per contract action type, as the wire carries it. */
        val ACTION_PAYLOADS =
            listOf(
                """{"type":"navigate","screen":"expense"}""",
                """{"type":"openUrl","url":"https://example.com/help"}""",
                """{"type":"switchCalendarView","selection":{"type":"agenda"}}""",
                """{"type":"openEventDetail","eventId":"s1"}""",
                """{"type":"presentModal","eventIds":["s1","s2"]}""",
            )

        fun loaded(response: CalendarScreenResponse) =
            CalendarUiState(content = response, isLoading = false, failure = null)
    }
}
