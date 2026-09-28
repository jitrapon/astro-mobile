package io.jitrapon.astro.presentation.action

import io.jitrapon.astro.data.calendar.OpenUrlAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pins which delivered URLs an [OpenUrlAction] may hand outside the app. */
class ActionEffectTest {

    @Test
    fun aWebUrlOpensOutsideTheApp() {
        for (url in
            listOf("https://example.com/help", "http://127.0.0.1/help", "HTTPS://example.com")) {
            assertEquals(ActionEffect.OpenExternalUrl(url), OpenUrlAction(url).toActionEffect())
        }
    }

    @Test
    fun aUrlWithAnyOtherSchemeHasNoEffect() {
        val refused =
            listOf(
                "file:///sdcard/secret.txt",
                "content://com.example.provider/item",
                "intent://scan/#Intent;scheme=zxing;end",
                "javascript:alert(1)",
                "no-scheme-at-all",
                "https", // a scheme name alone is not a URL with that scheme
            )
        for (url in refused) {
            assertNull(OpenUrlAction(url).toActionEffect(), "$url was handed outside the app.")
        }
    }
}
