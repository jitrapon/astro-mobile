package io.jitrapon.astro.design.tokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Holds the generated design-token surface to the vendored design artifacts it was generated from.
 *
 * The artifacts are parsed here with kotlinx.serialization, independently of the build-time
 * generator's own JSON reader, so a generator that misreads a key or drops one fails on both the
 * JVM host and the iOS simulator rather than shipping a value nobody compared. Each family is
 * compared as a whole — the key set the test holds against the JSON's, then every value — so a key
 * added upstream fails here until it is generated and listed.
 */
class DesignTokensParityTest {

    private val base = parse(EmbeddedDesignArtifacts.BASE_JSON)

    @Test
    fun fontIdsMatchTheFontManifest() {
        val fonts = parse(EmbeddedDesignArtifacts.FONTS_JSON).getValue("fonts").jsonObject

        assertEquals(fonts.keys.toList(), FontId.entries.map { it.id })
        FontId.entries.forEach { fontId ->
            val family = fonts.getValue(fontId.id).jsonObject.getValue("family").jsonPrimitive
            assertEquals(family.content, fontId.family, "family of ${fontId.id}")
        }
    }

    @Test
    fun baseSetVersionMatchesTheBase() {
        assertEquals(base.getValue("baseSetVersion").jsonPrimitive.int, BASE_SET_VERSION)
    }

    @Test
    fun hairlineAndTypeScaledLengthsStayDistinctFromPlainOnes() {
        assertTrue(Spacing.gutter.hairline)
        assertEquals(0.0, Spacing.gutter.dp)
        assertTrue(ComponentMetrics.gridLineWidth.hairline)
        assertFalse(ComponentMetrics.chipAccentEdgeWidth.hairline)
        assertEquals(CHIP_ACCENT_EDGE_WIDTH_DP, ComponentMetrics.chipAccentEdgeWidth.dp)
        assertTrue(Spacing.eventGap.scalesWithType)
        assertFalse(Spacing.eventGap.hairline)
        assertEquals(2.0, Spacing.eventGap.dp)
    }

    @Test
    fun fullRadiusStaysDistinctFromANumericOne() {
        assertTrue(Radii.full.full)
        assertFalse(Radii.base.full)
        assertEquals(BASE_RADIUS_DP, Radii.base.dp)
    }

    @Test
    fun typeRampsCarryTheirSpacingAndMetrics() {
        assertEquals(DISPLAY_LG_LETTER_SPACING_EM, Typography.displayLg.letterSpacingEm)
        assertEquals(BODY_BASE_LINE_HEIGHT_SP, Typography.bodyBase.lineHeightSp)
        assertEquals(LABEL_SM_WEIGHT, Typography.labelSm.weight)
        assertEquals(0.0, Typography.headlineMd.letterSpacingEm, "absent letter spacing")
    }

    @Test
    fun spacingMatchesTheBase() {
        assertDimensionsMatch(
            "spacing",
            familyValues(base.getValue("spacing")),
            mapOf(
                "grid-unit" to Spacing.gridUnit,
                "gutter" to Spacing.gutter,
                "margin-page" to Spacing.marginPage,
                "event-gap" to Spacing.eventGap,
            ),
        )
    }

    @Test
    fun componentMetricsMatchTheBase() {
        assertDimensionsMatch(
            "component.metrics",
            familyValues(component().getValue("metrics")),
            mapOf(
                "grid.line-width" to ComponentMetrics.gridLineWidth,
                "chip.accent-edge-width" to ComponentMetrics.chipAccentEdgeWidth,
                "current-time.line-width" to ComponentMetrics.currentTimeLineWidth,
                "sidebar.active-accent-width" to ComponentMetrics.sidebarActiveAccentWidth,
                "layout.page-margin" to ComponentMetrics.layoutPageMargin,
                "layout.event-gap" to ComponentMetrics.layoutEventGap,
            ),
        )
    }

    @Test
    fun radiiMatchTheBase() {
        assertRadiiMatch(
            "radius",
            familyValues(base.getValue("radius")),
            mapOf(
                "sm" to Radii.sm,
                "base" to Radii.base,
                "md" to Radii.md,
                "lg" to Radii.lg,
                "xl" to Radii.xl,
                "full" to Radii.full,
            ),
        )
    }

    @Test
    fun componentRadiiMatchTheBase() {
        assertRadiiMatch(
            "component.radius",
            familyValues(component().getValue("radius")),
            mapOf("chip.radius" to ComponentRadii.chipRadius),
        )
    }

    @Test
    fun typographyMatchesTheBase() {
        val typography = base.getValue("typography").jsonObject
        assertEquals("sp", typography.getValue("sizeUnit").jsonPrimitive.content)
        val ramps = typography.getValue("ramps").jsonObject
        val generated =
            mapOf(
                "display-lg" to Typography.displayLg,
                "headline-md" to Typography.headlineMd,
                "body-base" to Typography.bodyBase,
                "label-sm" to Typography.labelSm,
                "mono-data" to Typography.monoData,
            )

        assertEquals(ramps.keys, generated.keys, "typography.ramps keys")
        ramps.forEach { (key, node) ->
            val ramp = node.jsonObject
            val letterSpacing = ramp["letterSpacing"]?.jsonObject
            val expected =
                TypeRampValues(
                    fontRole = ramp.getValue("fontRole").jsonPrimitive.content,
                    sizeSp = ramp.getValue("size").jsonPrimitive.double,
                    weight = ramp.getValue("weight").jsonPrimitive.int,
                    lineHeightSp = ramp.getValue("lineHeight").jsonPrimitive.double,
                    letterSpacingEm =
                        letterSpacing?.getValue("value")?.jsonPrimitive?.double ?: 0.0,
                )
            letterSpacing?.let {
                assertEquals("em", it.getValue("unit").jsonPrimitive.content, "$key letter spacing")
            }
            val actual = generated.getValue(key)
            assertEquals(
                expected,
                TypeRampValues(
                    fontRole = actual.fontRole.name.lowercase(),
                    sizeSp = actual.sizeSp,
                    weight = actual.weight,
                    lineHeightSp = actual.lineHeightSp,
                    letterSpacingEm = actual.letterSpacingEm,
                ),
                "typography.ramps.$key",
            )
        }
    }

    private fun assertDimensionsMatch(
        family: String,
        values: JsonObject,
        generated: Map<String, Dimension>,
    ) {
        assertEquals(values.keys, generated.keys, "$family keys")
        values.forEach { (key, node) ->
            val actual = generated.getValue(key)
            assertEquals(
                expectedDimension(node),
                DimensionValues(actual.dp, actual.hairline, actual.scalesWithType),
                "$family.$key",
            )
        }
    }

    private fun assertRadiiMatch(
        family: String,
        values: JsonObject,
        generated: Map<String, Radius>,
    ) {
        assertEquals(values.keys, generated.keys, "$family keys")
        values.forEach { (key, node) ->
            val actual = generated.getValue(key)
            val expected =
                if (node is JsonPrimitive) RadiusValues(node.double, full = false)
                else
                    RadiusValues(0.0, full = node.jsonObject.getValue("full").jsonPrimitive.boolean)
            assertEquals(expected, RadiusValues(actual.dp, actual.full), "$family.$key")
        }
    }

    /** The JSON's own reading of a dp-family value, written without the generator's code. */
    private fun expectedDimension(node: JsonElement): DimensionValues {
        if (node is JsonPrimitive) return DimensionValues(node.double, false, false)
        val form = node.jsonObject
        return if (form.containsKey("hairline")) {
            DimensionValues(0.0, form.getValue("hairline").jsonPrimitive.boolean, false)
        } else {
            DimensionValues(
                form.getValue("value").jsonPrimitive.double,
                false,
                form.getValue("scalesWithType").jsonPrimitive.boolean,
            )
        }
    }

    private fun familyValues(family: JsonElement): JsonObject {
        assertEquals("dp", family.jsonObject.getValue("unit").jsonPrimitive.content)
        return family.jsonObject.getValue("values").jsonObject
    }

    private fun component() = base.getValue("component").jsonObject

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private data class DimensionValues(
        val dp: Double,
        val hairline: Boolean,
        val scalesWithType: Boolean,
    )

    private data class RadiusValues(val dp: Double, val full: Boolean)

    private data class TypeRampValues(
        val fontRole: String,
        val sizeSp: Double,
        val weight: Int,
        val lineHeightSp: Double,
        val letterSpacingEm: Double,
    )

    /**
     * Known base.json values, pinned as literals so neither a generator change nor a re-vendor
     * moves them unseen.
     */
    private companion object {
        const val CHIP_ACCENT_EDGE_WIDTH_DP = 3.0
        const val BASE_RADIUS_DP = 8.0
        const val DISPLAY_LG_LETTER_SPACING_EM = -0.02
        const val BODY_BASE_LINE_HEIGHT_SP = 22.0
        const val LABEL_SM_WEIGHT = 500
    }
}
