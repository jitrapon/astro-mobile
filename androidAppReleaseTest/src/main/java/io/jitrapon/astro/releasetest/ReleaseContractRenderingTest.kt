package io.jitrapon.astro.releasetest

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.InetAddress
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the APK users get decodes a delivered calendar screen and renders it through the component
 * registry — the polymorphic decoding R8 strips when nothing keeps it.
 *
 * Runs against `releaseLoopback`: `release`'s shrink with no keep rule added for a test, pointed at
 * a backend on the device's loopback. This test is that backend. It serves the vendored contract
 * fixture, launches the app from its launcher intent, and reads the screen through UiAutomator. It
 * references no app class and runs in a process of its own, so nothing here can keep alive a type
 * the shipping shrink removes — the gap a white-box test in `:androidApp`'s minified suite leaves,
 * since the keep rules generated for it pin every contract model it names.
 *
 * What it reads comes from the fixture, not from a copy of it: the destination labels only a
 * decoded navigation carries (each destination's action is a polymorphic type), and the event text
 * only the month renderer draws. The registry's fallback names the component id it has no renderer
 * for, so no visible text may contain one of the fixture's component ids.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseContractRenderingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val fixture =
        JSONObject(
            instrumentation.context.assets.open(FIXTURE_ASSET).use {
                it.readBytes().decodeToString()
            }
        )
    private val backend = MockWebServer()

    @Before
    fun startBackend() {
        val body = fixture.toString()
        backend.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.url.encodedPath == CALENDAR_SCREEN_PATH) {
                        MockResponse.Builder()
                            .code(HTTP_OK)
                            .setHeader("Content-Type", "application/json")
                            .body(body)
                            .build()
                    } else {
                        MockResponse.Builder().code(HTTP_NOT_FOUND).build()
                    }
            }
        val port = checkNotNull(InstrumentationRegistry.getArguments().getString("backendPort"))
        backend.start(InetAddress.getByName(LOOPBACK), port.toInt())
    }

    @After
    fun stopBackend() {
        backend.close()
    }

    @Test
    fun theShippedAppRendersTheDeliveredScreenThroughItsRegisteredRenderers() {
        launchAppUnderTest()
        val screen = fixture.getJSONObject("screen")

        destinationLabels(screen).forEach { label ->
            assertTrue(
                "The delivered destination \"$label\" never appeared as a tab.",
                device.wait(Until.hasObject(By.text(label)), SETTLE_TIMEOUT_MILLIS),
            )
        }
        val eventTexts = plainEventTexts(screen)
        assertTrue(
            "None of the delivered events' text appeared: $eventTexts",
            eventTexts.any { device.hasObject(By.text(it)) },
        )
        componentIds(screen).forEach { componentId ->
            assertFalse(
                "The registry fell back for $componentId.",
                device.hasObject(By.textContains(componentId)),
            )
        }
        assertEquals(CALENDAR_SCREEN_PATH, backend.takeRequest().url.encodedPath)
    }

    private fun launchAppUnderTest() {
        val context = instrumentation.context
        val launch =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)) {
                "$APP_PACKAGE is not installed, or declares no launcher activity."
            }
        context.startActivity(
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        assertTrue(
            "$APP_PACKAGE never reached the foreground.",
            device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), SETTLE_TIMEOUT_MILLIS),
        )
    }

    private fun destinationLabels(screen: JSONObject): List<String> =
        screen.getJSONObject("navigation").getJSONArray("destinations").objects().map {
            it.getString("label")
        }

    /**
     * Each event's visible text, for events without an accessibility label — one replaces what a
     * chip exposes to UiAutomator, so its title could not be matched as text.
     */
    private fun plainEventTexts(screen: JSONObject): List<String> =
        monthEvents(screen)
            .map { it.getJSONObject("presentation") }
            .filterNot { it.has("accessibilityLabel") }
            .mapNotNull { presentation ->
                presentation.optString("title").ifEmpty { null }
                    ?: presentation.optJSONObject("line")?.optString("text")?.ifEmpty { null }
            }

    private fun componentIds(screen: JSONObject): Set<String> {
        val body = screen.getJSONObject("body")
        return monthEvents(screen)
            .map { it.getJSONObject("presentation").getString("component") }
            .toSet() + body.getString("component")
    }

    private fun monthEvents(screen: JSONObject): List<JSONObject> =
        screen.getJSONObject("body").getJSONObject("props").getJSONArray("events").objects()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private companion object {
        const val APP_PACKAGE = "io.jitrapon.astro"
        const val FIXTURE_ASSET = "calendar-month-screen.v0.example.json"
        const val LOOPBACK = "127.0.0.1"
        const val CALENDAR_SCREEN_PATH = "/api/screens/calendar"
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404

        // A cold emulator's first launch, composition and exchange all fall inside this.
        const val SETTLE_TIMEOUT_MILLIS = 30_000L
    }
}
