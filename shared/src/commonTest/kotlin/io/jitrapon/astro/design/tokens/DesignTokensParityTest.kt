package io.jitrapon.astro.design.tokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Holds the generated design-token surface to the vendored design artifacts it was generated from.
 *
 * The artifacts are parsed here with kotlinx.serialization, independently of the build-time
 * generator's own JSON reader, so a generator that misreads a key or drops one fails on both the
 * JVM host and the iOS simulator rather than shipping a value nobody compared.
 */
class DesignTokensParityTest {

    @Test
    fun fontIdsMatchTheFontManifest() {
        val fonts = parse(EmbeddedDesignArtifacts.FONTS_JSON).getValue("fonts").jsonObject

        assertEquals(fonts.keys.toList(), FontId.entries.map { it.id })
        FontId.entries.forEach { fontId ->
            val family = fonts.getValue(fontId.id).jsonObject.getValue("family").jsonPrimitive
            assertEquals(family.content, fontId.family, "family of ${fontId.id}")
        }
    }

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
}
