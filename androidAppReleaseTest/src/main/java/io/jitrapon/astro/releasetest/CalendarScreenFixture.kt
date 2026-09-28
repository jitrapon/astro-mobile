package io.jitrapon.astro.releasetest

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import org.json.JSONArray
import org.json.JSONObject

// Reads the vendored calendar-screen fixture as plain JSON. `org.json` ships with the platform, so
// the test APK needs no app class and no codec of its own to know what the app was sent.

internal fun destinationLabels(screen: JSONObject): List<String> =
    screen.getJSONObject("navigation").getJSONArray("destinations").objects().map {
        it.getString("label")
    }

/**
 * How [this] event is found on screen: by its accessibility label, which a chip speaks in place of
 * its drawn text, or else by the title its renderer draws — for a timed marker, its one
 * server-composed line.
 */
internal fun JSONObject.drawnAs(): BySelector {
    val presentation = getJSONObject("presentation")
    val label = presentation.optString("accessibilityLabel")
    if (label.isNotEmpty()) return By.desc(label)
    val title =
        presentation.optString("title").ifEmpty {
            presentation.getJSONObject("line").getString("text")
        }
    return By.text(title)
}

internal fun componentIds(screen: JSONObject): Set<String> {
    val body = screen.getJSONObject("body")
    return monthEvents(screen)
        .map { it.getJSONObject("presentation").getString("component") }
        .toSet() + body.getString("component")
}

internal fun monthEvents(screen: JSONObject): List<JSONObject> =
    screen.getJSONObject("body").getJSONObject("props").getJSONArray("events").objects()

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

/** The first event of each presentation component, keyed by that component's id. */
internal fun firstEventOfEachPresentation(events: List<JSONObject>): Map<String, JSONObject> =
    events
        .groupBy { it.getJSONObject("presentation").getString("component") }
        .mapValues { (_, sameComponent) -> sameComponent.first() }

/** A copy of this fixture with one month event of each presentation listed ahead of the rest. */
internal fun JSONObject.withOneEventOfEachPresentationFirst(): JSONObject {
    val served = JSONObject(toString())
    val props = served.getJSONObject("screen").getJSONObject("body").getJSONObject("props")
    val events = props.getJSONArray("events").objects()
    val leading = firstEventOfEachPresentation(events).values.toList()
    props.put("events", JSONArray(leading + (events - leading.toSet())))
    return served
}
