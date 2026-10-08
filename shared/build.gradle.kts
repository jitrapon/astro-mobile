import java.math.BigDecimal
import java.math.RoundingMode

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // The kotlinx.serialization compiler plugin — it generates the `KSerializer` implementations
    // that `@Serializable` declarations resolve to. Applied by id with no version so it inherits
    // the artifact pinned on the root buildscript classpath, which the catalog locks to the same
    // `kotlin` ref as the Kotlin Gradle plugin; a serialization plugin built against a different
    // Kotlin than the compiler loading it fails the build outright.
    kotlin("plugin.serialization")
    // ktfmt + Detekt versions come from the root version catalog (gradle/libs.versions.toml) so the
    // Gradle plugin, the pre-commit hook's CLI jars, and verifyKtfmtAlignment share one source.
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

// ktfmt — Kotlin source formatter. Registers `ktfmtCheck` (verify) and `ktfmtFormat` (rewrite)
// lifecycle tasks plus per-source-set variants. `kotlinLangStyle()` selects ktfmt's
// Kotlin-official-style-guide preset, matching `kotlin.code.style=official` in gradle.properties —
// not the default Meta style or `googleStyle()`.
ktfmt { kotlinLangStyle() }

// Verify the standalone `ktfmt-cli` jar the pre-commit hook invokes matches the formatter the
// ncorti Gradle plugin bundles, so local Gradle, the hook, and CI can never format differently. The
// plugin registers a `ktfmt` configuration whose resolved `com.facebook:ktfmt` artifact is the real
// formatter; compare its version against `ktfmt-cli` from the version catalog. Wired into this
// module's `check` below so the gate fails fast on drift.
val verifyKtfmtAlignment =
    tasks.register("verifyKtfmtAlignment") {
        group = "verification"
        description = "Fail if ktfmt-cli drifts from the ktfmt version the Gradle plugin bundles."
        // Capture the comparison as configuration-cache-safe locals: a plain String for the catalog
        // version, and a `Provider<String>` for the plugin's bundled version resolved lazily at
        // execution. Capturing the `ktfmt` configuration object directly in `doLast` instead breaks
        // under the configuration cache (the serialized task gets a null receiver).
        val expectedKtfmtCli = libs.versions.ktfmt.cli.get()
        val pluginKtfmtVersion =
            configurations.named("ktfmt").map { ktfmtConfiguration ->
                ktfmtConfiguration.incoming.resolutionResult.allComponents
                    .mapNotNull { it.moduleVersion }
                    .firstOrNull { it.group == "com.facebook" && it.name == "ktfmt" }
                    ?.version
                    ?: error(
                        "com.facebook:ktfmt not found in the plugin's `ktfmt` configuration — " +
                            "cannot verify alignment."
                    )
            }
        doLast {
            val bundledVersion = pluginKtfmtVersion.get()
            check(bundledVersion == expectedKtfmtCli) {
                "ktfmt version drift: the ncorti plugin bundles com.facebook:ktfmt:" +
                    "$bundledVersion but gradle/libs.versions.toml pins ktfmt-cli=" +
                    "$expectedKtfmtCli. Upgrade both in lockstep so the hook and the Gradle " +
                    "plugin format identically."
            }
            logger.lifecycle(
                "ktfmt alignment OK: plugin bundles $bundledVersion == ktfmt-cli $expectedKtfmtCli"
            )
        }
    }

tasks.named("check") { dependsOn(verifyKtfmtAlignment) }

// Detekt — static analysis for Kotlin code smells. Runs Detekt's bundled defaults plus the narrow
// Compose-aware overrides in config/detekt/detekt.yml (buildUponDefaultConfig layers them on top).
// No baseline file and no custom complexity thresholds — findings are fixed by refactoring, never
// suppressed. Formatting is owned by ktfmt, so the `formatting` ruleset stays off. `source` is set
// explicitly: this module's KMP source sets live under src/<sourceSet>/kotlin, which Detekt's
// default of src/main/kotlin would otherwise miss entirely.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
    source.setFrom(files("src"))
}

// Load the third-party `structured-coroutines` ruleset onto Detekt's rule classpath (40 syntactic
// coroutine rules; tiers live under `structured-coroutines:` in config/detekt/detekt.yml). Its
// KMP-only rules — DispatchersIOInCommonMain, RunBlockingInCommonMain, MainScopeWithoutCancel —
// earn their keep here: commonMain must stay dispatcher- and blocking-clean across every target.
// Version pinned in the catalog so the Gradle task, the pre-commit hook, and CI all load one jar.
dependencies { detektPlugins(libs.structured.coroutines.detekt.rules) }

/**
 * Emits the vendored BFF contract artifacts as Kotlin source on the commonTest compilation: the
 * month-screen example fixture verbatim, the contract's declared version, and the request/response
 * facts the contract-conformance test holds the API client and the models to.
 *
 * Deriving those facts from the contract at build time is what keeps that test a contract check
 * rather than a restatement of the client: rename a query parameter or edit a `required:` list
 * upstream and these constants change, so the test fails. They are read with the small YAML subset
 * reader below rather than a YAML library — the contract is inert YAML no other build step reads,
 * and a parser dependency to serve one test is not worth carrying.
 *
 * Two shapes are easy to miss, and missing either yields an *empty* fact that every downstream
 * assertion then passes vacuously rather than failing:
 * - the calendar screen operation reaches `knownTheme` through a `$ref` into
 *   `components.parameters`, so the inline parameter list alone is one parameter short;
 * - the response bodies hang off `oneOf` + `discriminator` mappings instead of sitting inline, so
 *   the agenda body — which no fixture exercises — is reachable only by following the mapping.
 *
 * So the task fails rather than emits a hole whenever a `$ref`, a composition base, or a
 * discriminator mapping target does not resolve.
 */
abstract class GenerateEmbeddedContractSource : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val vendoredContract: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val vendoredFixture: RegularFileProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val contractFile = vendoredContract.get().asFile
        val contractLines = contractFile.readLines()
        val contract = parseMapping(contractLines, contractLines.indices)

        val declaredVersion =
            asScalar(asMapping(contract["info"], "info")["version"], "info.version")
        val parameters = extractCalendarScreenParameters(contract)
        val schemas = extractResponseSchemas(contract)

        writeGeneratedSource(
            declaredVersion = declaredVersion,
            fixtureJson = vendoredFixture.get().asFile.readText(),
            parameters = parameters,
            schemas = schemas,
        )

        logger.lifecycle(
            "Embedded contract sources generated from ${contractFile.name}: version " +
                "$declaredVersion, ${parameters.size} query parameters, ${schemas.size} schemas."
        )
    }

    // ------------------------------------------------------------------------------------------
    // Contract facts
    // ------------------------------------------------------------------------------------------

    /** A query parameter of the calendar screen operation, with any `$ref` already resolved. */
    private data class ParameterFact(
        val name: String,
        val required: Boolean,
        val type: String,
        val format: String?,
        val enumValues: List<String>,
        val minimum: Int?,
        val maximum: Int?,
        val pattern: String?,
    )

    /**
     * What a response schema requires and what it enumerates, with `allOf` composition resolved.
     */
    private data class SchemaFact(
        val requiredProperties: List<String>,
        val enumsByProperty: Map<String, List<String>>,
        val oneOfBranches: List<String>,
        val discriminatorProperty: String?,
        val discriminatorMapping: Map<String, String>,
    )

    private fun extractCalendarScreenParameters(contract: Map<String, Any?>): List<ParameterFact> {
        val operationContext = "paths.$CALENDAR_SCREEN_PATH.get"
        val paths = asMapping(contract["paths"], "paths")
        val operation =
            asMapping(
                asMapping(paths[CALENDAR_SCREEN_PATH], "paths.$CALENDAR_SCREEN_PATH")["get"],
                operationContext,
            )
        val sharedParameters =
            asMapping(
                asMapping(contract["components"], "components")["parameters"],
                "components.parameters",
            )

        return asList(operation["parameters"], "$operationContext.parameters").map { node ->
            val declared = asMapping(node, "an entry of $operationContext.parameters")
            val reference = declared[REF_KEY] as? String
            val resolved =
                if (reference == null) {
                    declared
                } else {
                    val target = reference.substringAfterLast('/')
                    asMapping(
                        sharedParameters[target]
                            ?: error(
                                "$operationContext references components.parameters.$target, " +
                                    "which the contract does not declare."
                            ),
                        "components.parameters.$target",
                    )
                }

            val name = asScalar(resolved["name"], "the name of a parameter of $operationContext")
            val location = asScalar(resolved["in"], "parameter $name's `in`")
            // The client builds a query string. A parameter that moved to the path or to a header
            // would still match every name-level assertion while being sent where nothing reads it.
            check(location == "query") {
                "Parameter $name is declared `in: $location`; this client only sends query " +
                    "parameters, so the conformance test's request assertions would not apply."
            }
            val schema = asMapping(resolved["schema"], "parameter $name's schema")

            ParameterFact(
                name = name,
                // OpenAPI defaults a query parameter to optional; only `required: true` makes it
                // mandatory, so an absent flag is faithfully "optional" rather than a defect.
                required = (resolved["required"] as? String).toBoolean(),
                // Held to a non-blank scalar: the wire-format assertions rest on type and format,
                // and an empty type would let a date-time serialized into a date-only parameter
                // sail through the very check meant to catch it.
                type = asScalar(schema["type"], "parameter $name's schema type"),
                format = schema["format"] as? String,
                enumValues =
                    schema["enum"]?.let { asStrings(it, "parameter $name's enum") }.orEmpty(),
                minimum = (schema["minimum"] as? String)?.toIntOrNull(),
                maximum = (schema["maximum"] as? String)?.toIntOrNull(),
                pattern = schema["pattern"] as? String,
            )
        }
    }

    private fun extractResponseSchemas(contract: Map<String, Any?>): Map<String, SchemaFact> {
        val schemas =
            asMapping(
                asMapping(contract["components"], "components")["schemas"],
                "components.schemas",
            )
        val facts = LinkedHashMap<String, SchemaFact>()
        schemas.keys.forEach { name ->
            collectSchemaFacts(
                name,
                asMapping(schemas[name], "components.schemas.$name"),
                schemas,
                facts,
            )
        }
        check(facts.isNotEmpty()) {
            "No schemas were extracted from components.schemas — every response assertion built " +
                "on these facts would pass without checking anything."
        }
        // Every union branch and discriminator target must name a schema that was actually
        // extracted. The bodies hang off these mappings, so an unresolved target is precisely the
        // hole that would leave the agenda models — which no fixture covers — with no check at all.
        facts.forEach { (name, fact) ->
            (fact.oneOfBranches + fact.discriminatorMapping.values).forEach { target ->
                check(facts.containsKey(target)) {
                    "Schema $name points at $target, which components.schemas does not declare."
                }
            }
        }
        return facts
    }

    private fun collectSchemaFacts(
        name: String,
        node: Map<String, Any?>,
        allSchemas: Map<String, Any?>,
        into: MutableMap<String, SchemaFact>,
    ) {
        val required = mutableListOf<String>()
        val enums = LinkedHashMap<String, List<String>>()
        val properties = LinkedHashMap<String, Map<String, Any?>>()
        mergeComposedSchema(name, node, allSchemas, required, enums, properties, mutableSetOf(name))

        val discriminator = node["discriminator"]?.let { asMapping(it, "$name.discriminator") }
        into[name] =
            SchemaFact(
                requiredProperties = required.distinct(),
                enumsByProperty = enums,
                oneOfBranches =
                    (node["oneOf"] as? List<*>).orEmpty().map { branch ->
                        asScalar(
                                asMapping(branch, "an entry of $name.oneOf")[REF_KEY],
                                "the \$ref of an entry of $name.oneOf",
                            )
                            .substringAfterLast('/')
                    },
                discriminatorProperty = discriminator?.get("propertyName") as? String,
                discriminatorMapping =
                    discriminator
                        ?.get("mapping")
                        ?.let { asMapping(it, "$name.discriminator.mapping") }
                        ?.mapValues { (key, target) ->
                            asScalar(target, "$name.discriminator.mapping.$key")
                                .substringAfterLast('/')
                        }
                        .orEmpty(),
            )

        // Nested object schemas — an agenda day group, the theme document's token block — carry
        // their own `required:` lists and become their own Kotlin types, so they get their own
        // facts under the path that reaches them.
        properties.forEach { (property, propertyNode) ->
            if (declaresObjectShape(propertyNode)) {
                collectSchemaFacts("$name.$property", propertyNode, allSchemas, into)
            }
            val items = propertyNode["items"] as? Map<*, *>
            val itemsNode = items?.let { asMapping(it, "$name.$property.items") }
            if (itemsNode != null && declaresObjectShape(itemsNode)) {
                collectSchemaFacts("$name.$property[]", itemsNode, allSchemas, into)
            }
        }
    }

    private fun declaresObjectShape(node: Map<String, Any?>) =
        node.containsKey("required") || node.containsKey("properties")

    /**
     * Folds `allOf` composition into one flat view of a schema. Branches are merged before the
     * schema's own body so that a branch narrowing an inherited property — an event's `kind` pinned
     * to a single `const` — replaces the base's wider enum instead of losing to it.
     */
    private fun mergeComposedSchema(
        name: String,
        node: Map<String, Any?>,
        allSchemas: Map<String, Any?>,
        required: MutableList<String>,
        enums: MutableMap<String, List<String>>,
        properties: MutableMap<String, Map<String, Any?>>,
        visited: MutableSet<String>,
    ) {
        (node["allOf"] as? List<*>)?.forEach { branch ->
            val branchNode = asMapping(branch, "an entry of $name.allOf")
            val reference = branchNode[REF_KEY] as? String
            if (reference == null) {
                mergeComposedSchema(
                    name,
                    branchNode,
                    allSchemas,
                    required,
                    enums,
                    properties,
                    visited,
                )
            } else {
                val target = reference.substringAfterLast('/')
                check(visited.add(target)) { "Schema composition cycles back through $target." }
                mergeComposedSchema(
                    target,
                    asMapping(
                        allSchemas[target]
                            ?: error("$name composes $target, which components.schemas lacks."),
                        "components.schemas.$target",
                    ),
                    allSchemas,
                    required,
                    enums,
                    properties,
                    visited,
                )
            }
        }

        node["required"]?.let { required += asStrings(it, "$name.required") }

        (node["properties"] as? Map<*, *>)?.forEach { (key, value) ->
            val property = key.toString()
            val propertyNode = value as? Map<*, *> ?: return@forEach
            val typed = asMapping(propertyNode, "$name.properties.$property")
            properties[property] = typed
            val enumeration =
                typed["enum"]?.let { asStrings(it, "$name.properties.$property.enum") }
            val constant = typed["const"] as? String
            when {
                // A `const` is the contract's way of pinning a discriminator to one value, so it
                // is recorded as the single-value enum it is — the same shape the models express
                // as a sealed subtype's fixed tag.
                enumeration != null -> enums[property] = enumeration
                constant != null -> enums[property] = listOf(constant)
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Emission
    // ------------------------------------------------------------------------------------------

    private fun writeGeneratedSource(
        declaredVersion: String,
        fixtureJson: String,
        parameters: List<ParameterFact>,
        schemas: Map<String, SchemaFact>,
    ) {
        val directory = outputDirectory.get().asFile
        // Wipe rather than overwrite so a constant that is renamed or dropped in a later run
        // cannot survive as a stale generated file the compilation still picks up.
        directory.deleteRecursively()
        val packageDirectory = directory.resolve(GENERATED_PACKAGE.replace('.', '/'))
        packageDirectory.mkdirs()

        packageDirectory
            .resolve("EmbeddedContract.kt")
            .writeText(
                buildString {
                    appendLine("// GENERATED FILE — do not edit. Produced by the :shared")
                    appendLine(
                        "// `generateEmbeddedContractSource` task from the vendored contract."
                    )
                    appendLine("package $GENERATED_PACKAGE")
                    appendLine()
                    appendLine("/**")
                    appendLine(
                        " * The vendored BFF contract artifacts, compiled into Kotlin because"
                    )
                    appendLine(
                        " * `kotlin.test` has no multiplatform resource loader and the iOS test"
                    )
                    appendLine(
                        " * binary's working directory is not a reliable base for file reads."
                    )
                    appendLine(
                        " * Regenerated on every build from `contracts/astro-bff/openapi.yaml`"
                    )
                    appendLine(" * and the example fixture beside it.")
                    appendLine(" */")
                    appendLine("internal object EmbeddedContract {")
                    appendLine(
                        "    /** `info.version` as the vendored contract itself declares it. */"
                    )
                    appendLine(
                        "    const val DECLARED_CONTRACT_VERSION: String = " +
                            asKotlinLiteral(declaredVersion)
                    )
                    appendLine()
                    appendLine("    /** The path the calendar screen operation is declared at. */")
                    appendLine(
                        "    const val CALENDAR_SCREEN_PATH: String = " +
                            asKotlinLiteral(CALENDAR_SCREEN_PATH)
                    )
                    appendLine()
                    appendLine("    /** The canonical month-screen example response, verbatim. */")
                    append(renderChunkedConstant("MONTH_SCREEN_FIXTURE_JSON", fixtureJson))
                    appendLine()
                    appendLine("    /**")
                    appendLine(
                        "     * Every query parameter the calendar screen operation declares,"
                    )
                    appendLine(
                        "     * in contract order and with shared `\$ref` parameters resolved."
                    )
                    appendLine("     */")
                    append(renderParameters(parameters))
                    appendLine()
                    appendLine("    /**")
                    appendLine("     * Every schema under `components.schemas`, with `allOf`")
                    appendLine(
                        "     * composition folded in and nested object schemas keyed by the"
                    )
                    appendLine("     * path that reaches them (`Parent.property`, or")
                    appendLine(
                        "     * `Parent.property[]` for array items). A `const` is recorded as"
                    )
                    appendLine("     * a single-value enum.")
                    appendLine("     */")
                    append(renderSchemas(schemas))
                    appendLine("}")
                    appendLine()
                    appendLine("/** A query parameter the contract declares on an operation. */")
                    appendLine("internal data class ContractParameter(")
                    appendLine("    val name: String,")
                    appendLine("    val required: Boolean,")
                    appendLine("    val type: String,")
                    appendLine("    val format: String?,")
                    appendLine("    val enumValues: List<String>,")
                    appendLine("    val minimum: Int?,")
                    appendLine("    val maximum: Int?,")
                    appendLine("    val pattern: String?,")
                    appendLine(")")
                    appendLine()
                    appendLine(
                        "/** What a response schema requires, enumerates, and discriminates on. */"
                    )
                    appendLine("internal data class ContractSchema(")
                    appendLine("    val requiredProperties: List<String>,")
                    appendLine("    val enumsByProperty: Map<String, List<String>>,")
                    appendLine("    val oneOfBranches: List<String>,")
                    appendLine("    val discriminatorProperty: String?,")
                    appendLine("    val discriminatorMapping: Map<String, String>,")
                    appendLine(")")
                }
            )
    }

    private fun renderParameters(parameters: List<ParameterFact>) = buildString {
        appendLine("    val CALENDAR_SCREEN_PARAMETERS: List<ContractParameter> =")
        appendLine("        listOf(")
        parameters.forEach { parameter ->
            appendLine("            ContractParameter(")
            appendLine("                name = ${asKotlinLiteral(parameter.name)},")
            appendLine("                required = ${parameter.required},")
            appendLine("                type = ${asKotlinLiteral(parameter.type)},")
            appendLine("                format = ${parameter.format?.let { asKotlinLiteral(it) }},")
            appendLine("                enumValues = ${renderStrings(parameter.enumValues)},")
            appendLine("                minimum = ${parameter.minimum},")
            appendLine("                maximum = ${parameter.maximum},")
            appendLine(
                "                pattern = ${parameter.pattern?.let { asKotlinLiteral(it) }},"
            )
            appendLine("            ),")
        }
        appendLine("        )")
    }

    private fun renderSchemas(schemas: Map<String, SchemaFact>) = buildString {
        appendLine("    val RESPONSE_SCHEMAS: Map<String, ContractSchema> =")
        appendLine("        mapOf(")
        schemas.forEach { (name, fact) ->
            appendLine("            ${asKotlinLiteral(name)} to")
            appendLine("                ContractSchema(")
            appendLine(
                "                    requiredProperties = " +
                    "${renderStrings(fact.requiredProperties)},"
            )
            appendLine(
                "                    enumsByProperty = ${renderEnums(fact.enumsByProperty)},"
            )
            appendLine("                    oneOfBranches = ${renderStrings(fact.oneOfBranches)},")
            appendLine(
                "                    discriminatorProperty = " +
                    "${fact.discriminatorProperty?.let { asKotlinLiteral(it) }},"
            )
            appendLine(
                "                    discriminatorMapping = " +
                    "${renderStringMap(fact.discriminatorMapping)},"
            )
            appendLine("                ),")
        }
        appendLine("        )")
    }

    private fun renderStrings(values: List<String>) =
        if (values.isEmpty()) {
            "emptyList()"
        } else {
            values.joinToString(prefix = "listOf(", postfix = ")") { asKotlinLiteral(it) }
        }

    private fun renderStringMap(values: Map<String, String>) =
        if (values.isEmpty()) {
            "emptyMap()"
        } else {
            values.entries.joinToString(prefix = "mapOf(", postfix = ")") { (key, value) ->
                "${asKotlinLiteral(key)} to ${asKotlinLiteral(value)}"
            }
        }

    private fun renderEnums(values: Map<String, List<String>>) =
        if (values.isEmpty()) {
            "emptyMap()"
        } else {
            values.entries.joinToString(prefix = "mapOf(", postfix = ")") { (key, value) ->
                "${asKotlinLiteral(key)} to ${renderStrings(value)}"
            }
        }

    /**
     * Renders [text] as a list of literals joined at runtime. A Kotlin string literal becomes a
     * CONSTANT_Utf8 class-file entry, which the JVM caps at 65535 bytes; the fixture is already
     * within a few KB of that, so one literal would fail as the contract grows.
     */
    private fun renderChunkedConstant(name: String, text: String) = buildString {
        appendLine("    val $name: String =")
        appendLine("        listOf(")
        splitIntoLiteralChunks(text).forEach { appendLine("            ${asKotlinLiteral(it)},") }
        appendLine("        )")
        appendLine("            .joinToString(separator = \"\")")
    }

    private fun splitIntoLiteralChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + MAX_CHARS_PER_LITERAL, text.length)
            // Never cut between the halves of a surrogate pair: the two Chars are one code point,
            // and separating them emits two lone surrogates that no longer round-trip.
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            chunks.add(text.substring(start, end))
            start = end
        }
        return chunks
    }

    private fun asKotlinLiteral(text: String) = buildString {
        append('"')
        text.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    // ------------------------------------------------------------------------------------------
    // A YAML reader covering exactly the subset this contract uses
    //
    // Block mappings and sequences by indentation, flow mappings/sequences (`{ a: b }`, `[a, b]`)
    // including ones wrapped across lines, folded scalars (`>` / `|`), quoted keys and values, and
    // full-line comments. Trailing `#` is deliberately NOT treated as a comment: `$ref` targets and
    // hex-colour patterns contain one, and stripping it would corrupt them.
    // ------------------------------------------------------------------------------------------

    private fun parseMapping(lines: List<String>, range: IntRange): Map<String, Any?> {
        val mapping = LinkedHashMap<String, Any?>()
        var index = range.first
        while (index <= range.last) {
            if (isSkippable(lines[index])) {
                index++
                continue
            }
            val line = lines[index]
            val trimmed = line.trim()
            val separator = keySeparatorIndex(trimmed)
            check(separator >= 0) {
                "Expected `key: value` in the vendored contract at line ${index + 1}: $line"
            }
            val key = unquote(trimmed.substring(0, separator))
            var inlineValue = trimmed.substring(separator + 1).trim()
            val children = blockRange(lines, index + 1, indentOf(line))

            mapping[key] =
                when {
                    inlineValue.isEmpty() -> {
                        val firstChild = children.firstOrNull { !isSkippable(lines[it]) }
                        when {
                            firstChild == null -> null
                            lines[firstChild].trim().startsWith("-") ->
                                parseSequence(lines, children)
                            // A flow collection may open on the line after its key and wrap, which
                            // is how the six-slot calendar colour block writes its `required:`.
                            lines[firstChild].trim().first() in "[{" ->
                                parseFlow(joinContent(lines, children))
                            else -> parseMapping(lines, children)
                        }
                    }
                    inlineValue.startsWith(">") || inlineValue.startsWith("|") ->
                        joinContent(lines, children)
                    else -> {
                        var cursor = children.first
                        while (!bracketsBalanced(inlineValue) && cursor <= children.last) {
                            inlineValue += " " + lines[cursor].trim()
                            cursor++
                        }
                        parseFlow(inlineValue)
                    }
                }

            index = (children.lastOrNull() ?: index) + 1
        }
        return mapping
    }

    private fun parseSequence(lines: List<String>, range: IntRange): List<Any?> {
        val items = mutableListOf<Any?>()
        var index = range.first
        while (index <= range.last) {
            if (isSkippable(lines[index])) {
                index++
                continue
            }
            val line = lines[index]
            val dashIndent = indentOf(line)
            check(line.trim().startsWith("-")) {
                "Expected a sequence entry in the vendored contract at line ${index + 1}: $line"
            }
            val children = blockRange(lines, index + 1, dashIndent)
            val inlineContent = line.substring(dashIndent + 1).trim()

            items +=
                when {
                    inlineContent.isEmpty() -> parseMapping(lines, children)
                    !inlineContent.startsWith("{") && keySeparatorIndex(inlineContent) >= 0 -> {
                        // Blank out the dash so the entry reads as an ordinary block mapping whose
                        // first key happens to sit on the dash line — the columns already line up
                        // with the keys below it.
                        val withoutDash = lines.toMutableList()
                        withoutDash[index] =
                            line.substring(0, dashIndent) + " " + line.substring(dashIndent + 1)
                        parseMapping(withoutDash, index..(children.lastOrNull() ?: index))
                    }
                    else -> parseFlow(inlineContent)
                }

            index = (children.lastOrNull() ?: index) + 1
        }
        return items
    }

    private fun parseFlow(text: String): Any? {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("{") && trimmed.endsWith("}") ->
                splitTopLevel(trimmed.substring(1, trimmed.length - 1)).associate { entry ->
                    val separator = keySeparatorIndex(entry)
                    check(separator >= 0) { "Expected `key: value` in the flow mapping: $entry" }
                    unquote(entry.substring(0, separator)) to
                        parseFlow(entry.substring(separator + 1))
                }
            trimmed.startsWith("[") && trimmed.endsWith("]") ->
                splitTopLevel(trimmed.substring(1, trimmed.length - 1)).map { parseFlow(it) }
            else -> unquote(trimmed)
        }
    }

    /** The lines belonging to the block a key at [parentIndent] opens, starting at [start]. */
    private fun blockRange(lines: List<String>, start: Int, parentIndent: Int): IntRange {
        var end = start
        while (
            end < lines.size && (isSkippable(lines[end]) || indentOf(lines[end]) > parentIndent)
        ) {
            end++
        }
        return start until end
    }

    private fun joinContent(lines: List<String>, range: IntRange) =
        range.filterNot { isSkippable(lines[it]) }.joinToString(" ") { lines[it].trim() }

    private fun isSkippable(line: String) = line.isBlank() || line.trimStart().startsWith("#")

    private fun indentOf(line: String) = line.indexOfFirst { !it.isWhitespace() }

    /** The index of the `:` that separates a key from its value, or -1 when there is none. */
    private fun keySeparatorIndex(text: String): Int {
        var quote: Char? = null
        var depth = 0
        text.forEachIndexed { index, character ->
            when {
                quote != null -> if (character == quote) quote = null
                character == '"' || character == '\'' -> quote = character
                character == '{' || character == '[' -> depth++
                character == '}' || character == ']' -> depth--
                character == ':' &&
                    depth == 0 &&
                    (index == text.lastIndex || text[index + 1] == ' ') -> return index
            }
        }
        return -1
    }

    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var depth = 0
        text.forEach { character ->
            when {
                quote != null -> {
                    if (character == quote) quote = null
                    current.append(character)
                }
                character == '"' || character == '\'' -> {
                    quote = character
                    current.append(character)
                }
                character == ',' && depth == 0 -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> {
                    if (character == '{' || character == '[') depth++
                    if (character == '}' || character == ']') depth--
                    current.append(character)
                }
            }
        }
        parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun bracketsBalanced(text: String): Boolean {
        var quote: Char? = null
        var depth = 0
        text.forEach { character ->
            when {
                quote != null -> if (character == quote) quote = null
                character == '"' || character == '\'' -> quote = character
                character == '{' || character == '[' -> depth++
                character == '}' || character == ']' -> depth--
            }
        }
        return depth == 0
    }

    private fun unquote(text: String): String {
        val trimmed = text.trim()
        val quoted =
            trimmed.length >= 2 &&
                (trimmed.first() == '"' || trimmed.first() == '\'') &&
                trimmed.last() == trimmed.first()
        return if (quoted) trimmed.substring(1, trimmed.length - 1) else trimmed
    }

    private fun asMapping(node: Any?, context: String): Map<String, Any?> {
        check(node is Map<*, *>) { "$context is not a mapping in the vendored contract." }
        return node.entries.associate { (key, value) -> key.toString() to value }
    }

    private fun asList(node: Any?, context: String): List<Any?> {
        check(node is List<*>) { "$context is not a sequence in the vendored contract." }
        return node
    }

    private fun asStrings(node: Any?, context: String) =
        asList(node, context).map { entry ->
            check(entry is String) { "$context holds a non-scalar entry." }
            entry
        }

    private fun asScalar(node: Any?, context: String): String {
        check(node is String && node.isNotBlank()) {
            "$context is missing or empty in the vendored contract."
        }
        return node
    }

    private companion object {
        const val CALENDAR_SCREEN_PATH = "/screens/calendar"
        const val GENERATED_PACKAGE = "io.jitrapon.astro.contract"
        const val REF_KEY = "\$ref"
        const val MAX_CHARS_PER_LITERAL = 3000
    }
}

/**
 * Kotlin-source rendering shared by the design-token generators: string literals, literals chunked
 * under the class-file constant cap, and identifiers derived from the design system's kebab-case
 * and dotted keys. An `object` rather than top-level script functions, because a task class that
 * calls into the script body captures the script instance and Gradle can no longer instantiate it.
 */
object GeneratedKotlinSource {

    /** Stays well under the JVM's 65535-byte CONSTANT_Utf8 cap even for all-multibyte text. */
    private const val MAX_CHARS_PER_LITERAL = 3000

    private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9_]*")

    /** Kotlin's hard keywords, which no generated declaration may be named. */
    private val HARD_KEYWORDS =
        setOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while",
        )

    fun header(taskName: String, source: String) = buildString {
        appendLine("// GENERATED FILE — do not edit. Produced by the :shared")
        appendLine("// `$taskName` task from $source.")
    }

    fun literal(text: String) = buildString {
        append('"')
        text.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    /** A `val` joining [text]'s chunks at runtime, so no single literal outgrows the cap. */
    fun chunkedConstant(name: String, text: String) = buildString {
        appendLine("    val $name: String =")
        appendLine("        listOf(")
        literalChunks(text).forEach { appendLine("            ${literal(it)},") }
        appendLine("        )")
        appendLine("            .joinToString(separator = \"\")")
    }

    private fun literalChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + MAX_CHARS_PER_LITERAL, text.length)
            // Never cut between the halves of a surrogate pair: they are one code point.
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            chunks.add(text.substring(start, end))
            start = end
        }
        return chunks
    }

    /** `4` → `4.0`, `-0.02` → `-0.02`: a `Double` literal spelling the decimal exactly. */
    fun doubleLiteral(value: BigDecimal): String {
        val plain = value.stripTrailingZeros().toPlainString()
        return if ('.' in plain) plain else "$plain.0"
    }

    /** `grid.line-width` → `gridLineWidth`. */
    fun camelCaseName(key: String): String {
        val words = wordsOf(key)
        return words.first().lowercase() +
            words.drop(1).joinToString("") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    /** `ibm-plex-sans-thai` → `IBM_PLEX_SANS_THAI`; `displayLg` → `DISPLAY_LG`. */
    fun screamingSnakeName(key: String) = wordsOf(key).joinToString("_") { it.uppercase() }

    /**
     * Maps each key to its generated name, failing when two keys collapse onto one name or a key
     * yields no legal identifier — either would otherwise surface as a confusing compile error in
     * generated code, or as one design value silently shadowing another.
     */
    fun uniqueNames(keys: Collection<String>, context: String, naming: (String) -> String) =
        keys.associateWith(naming).also { names ->
            names.forEach { (key, name) ->
                check(IDENTIFIER.matches(name) && name !in HARD_KEYWORDS) {
                    "$context: `$key` yields `$name`, which is not a Kotlin identifier."
                }
            }
            names.entries
                .groupBy({ it.value }, { it.key })
                .filterValues { it.size > 1 }
                .forEach { (name, collided) ->
                    error("$context: ${collided.joinToString { "`$it`" }} all generate `$name`.")
                }
        }

    private fun wordsOf(key: String): List<String> {
        val words =
            key.split('-', '.', '_')
                .flatMap { it.split(Regex("(?<=[a-z0-9])(?=[A-Z])")) }
                .filter { it.isNotEmpty() }
        check(words.isNotEmpty()) { "Cannot derive a Kotlin name from the key `$key`." }
        return words
    }
}

/**
 * Generates the design system's plain-value Kotlin surface on the commonMain compilation from the
 * vendored copies under `shared/design-system/` — never from the astro-docs submodule, which the
 * iOS CI job and a fresh clone do not have.
 *
 * The output reaches Swift through the framework header, so it is restricted to shapes that bridge
 * as plain values: enums, and objects and classes of numbers and strings. The generator fails
 * rather than emits a guess on any shape it does not model — a missing required key, an unknown
 * value form, or two keys that collapse onto one generated name — because a design value silently
 * dropped or defaulted here would paint wrong on both platforms with nothing to flag it.
 */
abstract class GenerateDesignTokenSource : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fontManifest: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val base: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lightTheme: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val darkTheme: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val rules: RegularFileProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile
        // Wipe rather than overwrite so a declaration dropped upstream cannot survive as a stale
        // generated file the compilation still picks up.
        directory.deleteRecursively()
        val packageDirectory = directory.resolve(GENERATED_PACKAGE.replace('.', '/'))
        packageDirectory.mkdirs()

        val fonts = readFontManifest()
        packageDirectory.resolve("FontId.kt").writeText(renderFontIds(fonts))

        val baseDocument = requireObject(readJson(base), "base.json")
        val baseSet = readBase(baseDocument)
        renderBase(baseSet).forEach { (fileName, source) ->
            packageDirectory.resolve(fileName).writeText(source)
        }

        val themes = readThemes(fonts, readThemeRules())
        val colorBindings = readColorBindings(baseDocument, themes.first().colors.keys)
        renderThemes(themes, colorBindings, fonts).forEach { (fileName, source) ->
            packageDirectory.resolve(fileName).writeText(source)
        }

        logger.lifecycle(
            "Design tokens generated: ${fonts.size} font ids, ${baseSet.valueCount} base values, " +
                "${themes.size} bundled themes of ${themes.first().colors.size} color roles, " +
                "${colorBindings.size} color bindings."
        )
    }

    // ------------------------------------------------------------------------------------------
    // fonts.json
    // ------------------------------------------------------------------------------------------

    private data class FontFact(val id: String, val family: String, val bindableRoles: List<String>)

    private fun readFontManifest(): List<FontFact> {
        val manifest = requireObject(readJson(fontManifest), "fonts.json")
        requireInt(manifest["fontSetVersion"], "fonts.json: fontSetVersion")
        val fonts = requireObject(manifest["fonts"], "fonts.json: fonts")
        check(fonts.isNotEmpty()) { "fonts.json: fonts declares no font." }
        return fonts.map { (id, node) ->
            val font = requireObject(node, "fonts.json: fonts.$id")
            FontFact(
                id = id as String,
                family = requireString(font["family"], "fonts.json: fonts.$id.family"),
                bindableRoles =
                    requireStringList(font["bindableRoles"], "fonts.json: fonts.$id.bindableRoles"),
            )
        }
    }

    private fun renderFontIds(fonts: List<FontFact>): String {
        val names =
            GeneratedKotlinSource.uniqueNames(
                fonts.map { it.id },
                "fonts.json",
                GeneratedKotlinSource::screamingSnakeName,
            )
        return buildString {
            append(GeneratedKotlinSource.header(TASK_NAME, "shared/design-system/fonts.json"))
            appendLine("package $GENERATED_PACKAGE")
            appendLine()
            appendLine("/** A font the design system ships, keyed by its manifest id. */")
            appendLine(
                "public enum class FontId(public val id: String, public val family: String) {"
            )
            fonts.forEach { font ->
                appendLine(
                    "    ${names.getValue(font.id)}(" +
                        "${GeneratedKotlinSource.literal(font.id)}, " +
                        "${GeneratedKotlinSource.literal(font.family)}),"
                )
            }
            appendLine("}")
        }
    }

    // ------------------------------------------------------------------------------------------
    // base.json — the theme-invariant values
    // ------------------------------------------------------------------------------------------

    private data class DimensionFact(
        val dp: BigDecimal,
        val hairline: Boolean,
        val scalesWithType: Boolean,
    )

    private data class RadiusFact(val dp: BigDecimal, val full: Boolean)

    private data class TypeRampFact(
        val fontRole: String,
        val sizeSp: BigDecimal,
        val weight: Int,
        val lineHeightSp: BigDecimal,
        val letterSpacingEm: BigDecimal,
    )

    private class BaseFacts(
        val setVersion: Int,
        val spacing: Map<String, DimensionFact>,
        val radii: Map<String, RadiusFact>,
        val componentMetrics: Map<String, DimensionFact>,
        val componentRadii: Map<String, RadiusFact>,
        val typography: Map<String, TypeRampFact>,
    ) {
        val valueCount =
            spacing.size +
                radii.size +
                componentMetrics.size +
                componentRadii.size +
                typography.size
    }

    private fun readBase(base: Map<*, *>): BaseFacts {
        requireOnlyKeys(base, BASE_KEYS, "base.json")
        val component = requireObject(base["component"], "base.json: component")
        requireOnlyKeys(component, setOf("metrics", "radius"), "base.json: component")
        // `layout.web` is sized in CSS pixels for the web client and has no mobile counterpart; a
        // layout family for any other platform is new upstream shape, so it fails rather than
        // being skipped alongside it.
        base["layout"]?.let { layout ->
            requireOnlyKeys(
                requireObject(layout, "base.json: layout"),
                setOf("web"),
                "base.json: layout",
            )
        }
        val typography = requireObject(base["typography"], "base.json: typography")
        requireOnlyKeys(typography, setOf("sizeUnit", "ramps"), "base.json: typography")
        check(typography["sizeUnit"] == "sp") {
            "base.json: typography.sizeUnit: expected \"sp\", found ${describe(typography["sizeUnit"])}."
        }
        val ramps = requireObject(typography["ramps"], "base.json: typography.ramps")
        check(ramps.isNotEmpty()) { "base.json: typography.ramps declares no ramp." }

        return BaseFacts(
            setVersion = requireInt(base["baseSetVersion"], "base.json: baseSetVersion"),
            spacing =
                readFamily(base["spacing"], "base.json: spacing") { node, context ->
                    readDimension(node, context)
                },
            radii =
                readFamily(base["radius"], "base.json: radius") { node, context ->
                    readRadius(node, context)
                },
            componentMetrics =
                readFamily(component["metrics"], "base.json: component.metrics") { node, context ->
                    readDimension(node, context)
                },
            componentRadii =
                readFamily(component["radius"], "base.json: component.radius") { node, context ->
                    readRadius(node, context)
                },
            typography =
                ramps.entries.associate { (key, node) ->
                    key as String to readTypeRamp(node, "base.json: typography.ramps.$key")
                },
        )
    }

    /** A `{unit: "dp", values: {...}}` family, every value read by [readValue]. */
    private fun <T> readFamily(
        node: Any?,
        context: String,
        readValue: (Any?, String) -> T,
    ): Map<String, T> {
        val family = requireObject(node, context)
        requireOnlyKeys(family, setOf("unit", "values"), context)
        check(family["unit"] == "dp") {
            "$context.unit: expected \"dp\", found ${describe(family["unit"])}."
        }
        val values = requireObject(family["values"], "$context.values")
        check(values.isNotEmpty()) { "$context.values declares no value." }
        return values.entries.associate { (key, value) ->
            key as String to readValue(value, "$context.values.$key")
        }
    }

    /**
     * A dp-family value: a number, `{hairline: true}`, or `{value, scalesWithType: true}`. Each
     * object form is matched by its exact key set, so a variant with an extra key or a `false` flag
     * fails instead of being read as the nearest form it resembles.
     */
    private fun readDimension(node: Any?, context: String): DimensionFact =
        when {
            node is Number -> DimensionFact(decimal(node), hairline = false, scalesWithType = false)
            node is Map<*, *> && node.keys == setOf("hairline") && node["hairline"] == true ->
                DimensionFact(BigDecimal.ZERO, hairline = true, scalesWithType = false)
            node is Map<*, *> &&
                node.keys == setOf("value", "scalesWithType") &&
                node["scalesWithType"] == true ->
                DimensionFact(
                    requireNumber(node["value"], "$context.value"),
                    hairline = false,
                    scalesWithType = true,
                )
            else ->
                error(
                    "$context: unknown dimension form, ${describe(node)}; expected a number, " +
                        "{hairline: true}, or {value, scalesWithType: true}."
                )
        }

    /** A radius-family value: a number or `{full: true}`, matched as [readDimension] matches. */
    private fun readRadius(node: Any?, context: String): RadiusFact =
        when {
            node is Number -> RadiusFact(decimal(node), full = false)
            node is Map<*, *> && node.keys == setOf("full") && node["full"] == true ->
                RadiusFact(BigDecimal.ZERO, full = true)
            else ->
                error(
                    "$context: unknown radius form, ${describe(node)}; expected a number or " +
                        "{full: true}."
                )
        }

    private fun readTypeRamp(node: Any?, context: String): TypeRampFact {
        val ramp = requireObject(node, context)
        requireOnlyKeys(
            ramp,
            setOf("fontRole", "size", "weight", "lineHeight", "letterSpacing"),
            context,
        )
        val fontRole = requireString(ramp["fontRole"], "$context.fontRole")
        check(fontRole in FONT_ROLES) {
            "$context.fontRole: expected one of $FONT_ROLES, found \"$fontRole\"."
        }
        // An absent letter spacing is the type's natural tracking — zero — rather than a nullable
        // number, which would bridge to Swift boxed.
        val letterSpacing =
            ramp["letterSpacing"]?.let { node ->
                val spacing = requireObject(node, "$context.letterSpacing")
                requireOnlyKeys(spacing, setOf("value", "unit"), "$context.letterSpacing")
                check(spacing["unit"] == "em") {
                    "$context.letterSpacing.unit: expected \"em\", found ${describe(spacing["unit"])}."
                }
                requireNumber(spacing["value"], "$context.letterSpacing.value")
            } ?: BigDecimal.ZERO
        return TypeRampFact(
            fontRole = fontRole,
            sizeSp = requireNumber(ramp["size"], "$context.size"),
            weight = requireInt(ramp["weight"], "$context.weight"),
            lineHeightSp = requireNumber(ramp["lineHeight"], "$context.lineHeight"),
            letterSpacingEm = letterSpacing,
        )
    }

    /** Every base.json file to write, as file name to source. */
    private fun renderBase(base: BaseFacts): List<Pair<String, String>> {
        val source = "shared/design-system/base.json"
        val header =
            GeneratedKotlinSource.header(TASK_NAME, source) + "package $GENERATED_PACKAGE\n\n"
        return listOf(
            "Dimension.kt" to header + DIMENSION_DECLARATION,
            "Radius.kt" to header + RADIUS_DECLARATION,
            "FontRole.kt" to header + FONT_ROLE_DECLARATION,
            "TypeRamp.kt" to header + TYPE_RAMP_DECLARATION,
            "BaseSetVersion.kt" to
                header +
                    "/** The `baseSetVersion` of the design base these values were generated " +
                    "from. */\n" +
                    "public const val BASE_SET_VERSION: Int = ${base.setVersion}\n",
            "Spacing.kt" to
                header +
                    renderValueObject(
                        "Spacing",
                        "Spacing from the design base's `spacing` family.",
                        "base.json: spacing",
                        "Dimension",
                        base.spacing,
                        ::renderDimension,
                    ),
            "Radii.kt" to
                header +
                    renderValueObject(
                        "Radii",
                        "Corner radii from the design base's `radius` family.",
                        "base.json: radius",
                        "Radius",
                        base.radii,
                        ::renderRadius,
                    ),
            "ComponentMetrics.kt" to
                header +
                    renderValueObject(
                        "ComponentMetrics",
                        "Component sizes from the design base's `component.metrics` family.",
                        "base.json: component.metrics",
                        "Dimension",
                        base.componentMetrics,
                        ::renderDimension,
                    ),
            "ComponentRadii.kt" to
                header +
                    renderValueObject(
                        "ComponentRadii",
                        "Component corner radii from the design base's `component.radius` family.",
                        "base.json: component.radius",
                        "Radius",
                        base.componentRadii,
                        ::renderRadius,
                    ),
            "Typography.kt" to
                header +
                    renderValueObject(
                        "Typography",
                        "The type ramps from the design base's `typography.ramps`.",
                        "base.json: typography.ramps",
                        "TypeRamp",
                        base.typography,
                        ::renderTypeRamp,
                    ),
        )
    }

    private fun <T> renderValueObject(
        objectName: String,
        documentation: String,
        context: String,
        typeName: String,
        values: Map<String, T>,
        render: (T) -> String,
    ): String {
        val names =
            GeneratedKotlinSource.uniqueNames(
                values.keys,
                context,
                GeneratedKotlinSource::camelCaseName,
            )
        return buildString {
            appendLine("/** $documentation */")
            appendLine("public object $objectName {")
            values.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) appendLine()
                appendLine("    /** `$key` */")
                appendLine("    public val ${names.getValue(key)}: $typeName =")
                appendLine("        ${render(value)}")
            }
            appendLine("}")
        }
    }

    private fun renderDimension(fact: DimensionFact) =
        "Dimension(dp = ${GeneratedKotlinSource.doubleLiteral(fact.dp)}, " +
            "hairline = ${fact.hairline}, scalesWithType = ${fact.scalesWithType})"

    private fun renderRadius(fact: RadiusFact) =
        "Radius(dp = ${GeneratedKotlinSource.doubleLiteral(fact.dp)}, full = ${fact.full})"

    private fun renderTypeRamp(fact: TypeRampFact) =
        "TypeRamp(fontRole = FontRole.${fact.fontRole.uppercase()}, " +
            "sizeSp = ${GeneratedKotlinSource.doubleLiteral(fact.sizeSp)}, " +
            "weight = ${fact.weight}, " +
            "lineHeightSp = ${GeneratedKotlinSource.doubleLiteral(fact.lineHeightSp)}, " +
            "letterSpacingEm = ${GeneratedKotlinSource.doubleLiteral(fact.letterSpacingEm)})"

    // ------------------------------------------------------------------------------------------
    // themes/*.json — the bundled themes — and base.json's color bindings onto their roles
    // ------------------------------------------------------------------------------------------

    private class ThemeRules(val themeId: Regex, val themeVersion: Regex)

    private data class ShadowFact(
        val offsetXDp: BigDecimal,
        val offsetYDp: BigDecimal,
        val blurDp: BigDecimal,
        val spreadDp: BigDecimal,
        val argb: Long,
    )

    private class ThemeFact(
        val id: String,
        val version: String,
        val label: String,
        val colorScheme: String,
        val tokenSetVersion: Int,
        val colors: Map<String, Long>,
        val shadows: Map<String, ShadowFact>,
        val fonts: Map<String, String>,
    )

    /**
     * Only the theme-id and theme-version patterns: the rest of rules.json serves the read-time
     * validator of a delivered theme, which no bundled value depends on.
     */
    private fun readThemeRules(): ThemeRules {
        val rules = requireObject(readJson(rules), "rules.json")
        fun pattern(key: String): Regex {
            val rule = requireObject(rules[key], "rules.json: $key")
            return Regex(requireString(rule["pattern"], "rules.json: $key.pattern"))
        }
        return ThemeRules(themeId = pattern("themeId"), themeVersion = pattern("themeVersion"))
    }

    /**
     * Reads both bundled themes and holds them to each other: the same color roles and shadow keys
     * — a bundled theme missing a role would have nothing to paint it with — and exactly one theme
     * per color scheme, so a fallback by the system's scheme always finds one.
     */
    private fun readThemes(fonts: List<FontFact>, rules: ThemeRules): List<ThemeFact> {
        val themes = listOf(lightTheme, darkTheme).map { readTheme(it, fonts, rules) }
        val reference = themes.first()
        themes.drop(1).forEach { theme ->
            check(theme.colors.keys == reference.colors.keys) {
                "themes: `${theme.id}` and `${reference.id}` declare different color roles; " +
                    "only in `${theme.id}`: ${theme.colors.keys - reference.colors.keys}, " +
                    "only in `${reference.id}`: ${reference.colors.keys - theme.colors.keys}."
            }
            check(theme.shadows.keys == reference.shadows.keys) {
                "themes: `${theme.id}` and `${reference.id}` declare different shadows; " +
                    "only in `${theme.id}`: ${theme.shadows.keys - reference.shadows.keys}, " +
                    "only in `${reference.id}`: ${reference.shadows.keys - theme.shadows.keys}."
            }
        }
        themes
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .forEach { id -> error("themes: more than one bundled theme has the id `$id`.") }
        COLOR_SCHEMES.keys.forEach { scheme ->
            val matching = themes.filter { it.colorScheme == scheme }.map { it.id }
            check(matching.size == 1) {
                "themes: expected exactly one bundled theme with colorScheme \"$scheme\", " +
                    "found ${matching.size} $matching."
            }
        }
        return themes
    }

    private fun readTheme(
        file: RegularFileProperty,
        fonts: List<FontFact>,
        rules: ThemeRules,
    ): ThemeFact {
        val context = "themes/${file.get().asFile.name}"
        val theme = requireObject(readJson(file), context)
        requireOnlyKeys(theme, THEME_KEYS, context)
        val id = requireString(theme["id"], "$context: id")
        check(rules.themeId.containsMatchIn(id)) {
            "$context: id \"$id\" does not match rules.json's themeId pattern ${rules.themeId}."
        }
        val version = requireString(theme["version"], "$context: version")
        check(rules.themeVersion.containsMatchIn(version)) {
            "$context: version \"$version\" does not match rules.json's themeVersion pattern " +
                "${rules.themeVersion}."
        }
        val colorScheme = requireString(theme["colorScheme"], "$context: colorScheme")
        check(colorScheme in COLOR_SCHEMES) {
            "$context: colorScheme: expected one of ${COLOR_SCHEMES.keys}, found \"$colorScheme\"."
        }
        val tokens = requireObject(theme["tokens"], "$context: tokens")
        requireOnlyKeys(tokens, setOf("colors", "shadows", "fonts"), "$context: tokens")
        val colors = requireObject(tokens["colors"], "$context: tokens.colors")
        check(colors.isNotEmpty()) { "$context: tokens.colors declares no color." }
        val shadows = requireObject(tokens["shadows"], "$context: tokens.shadows")
        return ThemeFact(
            id = id,
            version = version,
            label = requireString(theme["label"], "$context: label"),
            colorScheme = colorScheme,
            tokenSetVersion = requireInt(theme["tokenSetVersion"], "$context: tokenSetVersion"),
            colors =
                colors.entries.associate { (role, node) ->
                    role as String to readArgb(node, "$context: tokens.colors.$role")
                },
            shadows =
                shadows.entries.associate { (key, node) ->
                    key as String to readShadow(node, "$context: tokens.shadows.$key")
                },
            fonts = readThemeFonts(tokens["fonts"], fonts, "$context: tokens.fonts"),
        )
    }

    /**
     * A `{hex, alpha?}` color as `0xAARRGGBB`, the alpha byte `round(alpha × 255)`. An absent alpha
     * is opaque, the contract's own default for a color value.
     */
    private fun readArgb(node: Any?, context: String): Long {
        val color = requireObject(node, context)
        requireOnlyKeys(color, setOf("hex", "alpha"), context)
        val hex = requireString(color["hex"], "$context.hex")
        check(HEX_COLOR.matches(hex)) {
            "$context.hex: expected six hex digits after `#`, found \"$hex\"."
        }
        val alpha = color["alpha"]?.let { requireNumber(it, "$context.alpha") } ?: BigDecimal.ONE
        check(alpha >= BigDecimal.ZERO && alpha <= BigDecimal.ONE) {
            "$context.alpha: expected a number from 0 to 1, found $alpha."
        }
        val alphaByte = alpha.multiply(BigDecimal(255)).setScale(0, RoundingMode.HALF_UP).toLong()
        return (alphaByte shl 24) or hex.substring(1).toLong(16)
    }

    /** A shadow in dp. An absent spread is zero, the contract's own default for a shadow value. */
    private fun readShadow(node: Any?, context: String): ShadowFact {
        val shadow = requireObject(node, context)
        requireOnlyKeys(shadow, setOf("offsetX", "offsetY", "blur", "spread", "color"), context)
        return ShadowFact(
            offsetXDp = requireNumber(shadow["offsetX"], "$context.offsetX"),
            offsetYDp = requireNumber(shadow["offsetY"], "$context.offsetY"),
            blurDp = requireNumber(shadow["blur"], "$context.blur"),
            spreadDp =
                shadow["spread"]?.let { requireNumber(it, "$context.spread") } ?: BigDecimal.ZERO,
            argb = readArgb(shadow["color"], "$context.color"),
        )
    }

    /** Every theme font role bound, each to a manifest font that may be bound to that role. */
    private fun readThemeFonts(
        node: Any?,
        fonts: List<FontFact>,
        context: String,
    ): Map<String, String> {
        val bound = requireObject(node, context)
        requireOnlyKeys(bound, THEME_FONT_ROLES, context)
        val manifest = fonts.associateBy { it.id }
        return THEME_FONT_ROLES.associateWith { role ->
            val id = requireString(bound[role], "$context.$role")
            val font =
                checkNotNull(manifest[id]) {
                    "$context.$role: font \"$id\" is not in fonts.json; expected one of " +
                        "${manifest.keys}."
                }
            check(role in font.bindableRoles) {
                "$context.$role: font \"$id\" may be bound only to ${font.bindableRoles}."
            }
            id
        }
    }

    /** base.json's `bindings.color`: a component-level name to the theme color role it paints. */
    private fun readColorBindings(base: Map<*, *>, colorRoles: Set<String>): Map<String, String> {
        val bindings = requireObject(base["bindings"], "base.json: bindings")
        requireOnlyKeys(bindings, setOf("color"), "base.json: bindings")
        val color = requireObject(bindings["color"], "base.json: bindings.color")
        return color.entries.associate { (key, node) ->
            val role = requireString(node, "base.json: bindings.color.$key")
            check(role in colorRoles) {
                "base.json: bindings.color.$key: binds to \"$role\", which the bundled themes " +
                    "do not declare."
            }
            key as String to role
        }
    }

    /** Every theme file to write, as file name to source. */
    private fun renderThemes(
        themes: List<ThemeFact>,
        colorBindings: Map<String, String>,
        fonts: List<FontFact>,
    ): List<Pair<String, String>> {
        val roles = themes.first().colors.keys
        val shadowKeys = themes.first().shadows.keys
        val roleEntries =
            GeneratedKotlinSource.uniqueNames(
                roles,
                "themes: tokens.colors",
                GeneratedKotlinSource::screamingSnakeName,
            )
        val roleProperties =
            GeneratedKotlinSource.uniqueNames(
                roles,
                "themes: tokens.colors",
                GeneratedKotlinSource::camelCaseName,
            )
        val shadowProperties =
            GeneratedKotlinSource.uniqueNames(
                shadowKeys,
                "themes: tokens.shadows",
                GeneratedKotlinSource::camelCaseName,
            )
        val themeProperties =
            GeneratedKotlinSource.uniqueNames(
                themes.map { it.id } + BUNDLED_THEMES_ALL,
                "themes: id",
                GeneratedKotlinSource::camelCaseName,
            )
        val fontEntries =
            GeneratedKotlinSource.uniqueNames(
                fonts.map { it.id },
                "fonts.json",
                GeneratedKotlinSource::screamingSnakeName,
            )
        val bindingProperties =
            GeneratedKotlinSource.uniqueNames(
                colorBindings.keys,
                "base.json: bindings.color",
                GeneratedKotlinSource::camelCaseName,
            )

        val themeHeader =
            GeneratedKotlinSource.header(TASK_NAME, "shared/design-system/themes/") +
                "package $GENERATED_PACKAGE\n\n"
        val colorRole = buildString {
            append(themeHeader)
            appendLine("/** A semantic color role every bundled theme paints, by its theme key. */")
            appendLine("public enum class ColorRole(public val key: String) {")
            roles.forEach { role ->
                appendLine(
                    "    ${roleEntries.getValue(role)}(${GeneratedKotlinSource.literal(role)}),"
                )
            }
            appendLine("}")
        }
        val themeColors = buildString {
            append(themeHeader)
            appendLine("/**")
            appendLine(
                " * A theme's color for every [ColorRole], each `0xAARRGGBB`. A `Long` rather than an"
            )
            appendLine(
                " * `Int`, since an opaque color's alpha byte sets the sign bit, and rather than an"
            )
            appendLine(" * unsigned type, which reaches Swift boxed.")
            appendLine(" */")
            appendLine("public class ThemeColors(")
            roles.forEach { appendLine("    public val ${roleProperties.getValue(it)}: Long,") }
            appendLine(") {")
            appendLine("    /** The color this theme paints [role] with. */")
            appendLine("    public fun color(role: ColorRole): Long =")
            appendLine("        when (role) {")
            roles.forEach { role ->
                appendLine(
                    "            ColorRole.${roleEntries.getValue(role)} -> " +
                        "${roleProperties.getValue(role)}"
                )
            }
            appendLine("        }")
            appendLine("}")
        }
        val themeShadows = buildString {
            append(themeHeader)
            appendLine("/** A theme's shadows, one per shadow key every bundled theme declares. */")
            appendLine("public class ThemeShadows(")
            shadowKeys.forEach {
                appendLine("    public val ${shadowProperties.getValue(it)}: Shadow,")
            }
            appendLine(")")
        }
        val bundledThemes = buildString {
            append(GeneratedKotlinSource.header(TASK_NAME, "shared/design-system/themes/"))
            appendLine("package $GENERATED_PACKAGE")
            appendLine()
            appendLine("import io.jitrapon.astro.data.calendar.ColorScheme")
            appendLine()
            appendLine("/** The themes compiled into the app, one per [ColorScheme]. */")
            appendLine("public object BundledThemes {")
            themes.forEach { theme ->
                appendLine("    /** `${theme.id}@${theme.version}` */")
                appendLine("    public val ${themeProperties.getValue(theme.id)}: BundledTheme =")
                appendLine("        BundledTheme(")
                appendLine("            id = ${GeneratedKotlinSource.literal(theme.id)},")
                appendLine("            version = ${GeneratedKotlinSource.literal(theme.version)},")
                appendLine("            label = ${GeneratedKotlinSource.literal(theme.label)},")
                appendLine(
                    "            colorScheme = ColorScheme.${COLOR_SCHEMES.getValue(theme.colorScheme)},"
                )
                appendLine("            tokenSetVersion = ${theme.tokenSetVersion},")
                appendLine("            colors =")
                appendLine("                ThemeColors(")
                theme.colors.forEach { (role, argb) ->
                    appendLine(
                        "                    ${roleProperties.getValue(role)} = ${argbLiteral(argb)},"
                    )
                }
                appendLine("                ),")
                appendLine("            shadows =")
                appendLine("                ThemeShadows(")
                theme.shadows.forEach { (key, shadow) ->
                    appendLine(
                        "                    ${shadowProperties.getValue(key)} = " +
                            "${renderShadow(shadow)},"
                    )
                }
                appendLine("                ),")
                appendLine("            fonts =")
                appendLine("                ThemeFonts(")
                theme.fonts.forEach { (role, id) ->
                    appendLine("                    $role = FontId.${fontEntries.getValue(id)},")
                }
                appendLine("                ),")
                appendLine("        )")
                appendLine()
            }
            appendLine("    /** Every bundled theme. */")
            appendLine(
                "    public val $BUNDLED_THEMES_ALL: List<BundledTheme> = " +
                    "listOf(${themes.joinToString { themeProperties.getValue(it.id) }})"
            )
            appendLine("}")
        }
        val colorBindingsSource = buildString {
            append(GeneratedKotlinSource.header(TASK_NAME, "shared/design-system/base.json"))
            appendLine("package $GENERATED_PACKAGE")
            appendLine()
            appendLine("/**")
            appendLine(" * The theme color role each component-level color in the design base's")
            appendLine(" * `bindings.color` paints with.")
            appendLine(" */")
            appendLine("public object ColorBindings {")
            colorBindings.entries.forEachIndexed { index, (key, role) ->
                if (index > 0) appendLine()
                appendLine("    /** `$key` */")
                appendLine(
                    "    public val ${bindingProperties.getValue(key)}: ColorRole = " +
                        "ColorRole.${roleEntries.getValue(role)}"
                )
            }
            appendLine("}")
        }
        return listOf(
            "ColorRole.kt" to colorRole,
            "ThemeColors.kt" to themeColors,
            "Shadow.kt" to themeHeader + SHADOW_DECLARATION,
            "ThemeShadows.kt" to themeShadows,
            "ThemeFonts.kt" to themeHeader + THEME_FONTS_DECLARATION,
            "BundledTheme.kt" to
                themeHeader +
                    "import io.jitrapon.astro.data.calendar.ColorScheme\n\n" +
                    BUNDLED_THEME_DECLARATION,
            "BundledThemes.kt" to bundledThemes,
            "ColorBindings.kt" to colorBindingsSource,
        )
    }

    private fun renderShadow(fact: ShadowFact) =
        "Shadow(offsetXDp = ${GeneratedKotlinSource.doubleLiteral(fact.offsetXDp)}, " +
            "offsetYDp = ${GeneratedKotlinSource.doubleLiteral(fact.offsetYDp)}, " +
            "blurDp = ${GeneratedKotlinSource.doubleLiteral(fact.blurDp)}, " +
            "spreadDp = ${GeneratedKotlinSource.doubleLiteral(fact.spreadDp)}, " +
            "color = ${argbLiteral(fact.argb)})"

    /** `0xFFB81311L`: the `L` keeps a color whose alpha byte is below `0x80` a `Long` literal. */
    private fun argbLiteral(argb: Long) = "0x%08XL".format(argb)

    // ------------------------------------------------------------------------------------------
    // Typed JSON reads that fail naming the offending path
    // ------------------------------------------------------------------------------------------

    private fun readJson(file: RegularFileProperty): Any? =
        groovy.json.JsonSlurper().parse(file.get().asFile, "UTF-8")

    /** Fails on a key outside [allowed], so new upstream shape is never silently skipped. */
    private fun requireOnlyKeys(node: Map<*, *>, allowed: Set<String>, context: String) {
        val unknown = node.keys.filterNot { it in allowed }
        check(unknown.isEmpty()) {
            "$context: unmodelled key(s) ${unknown.joinToString { "`$it`" }}; expected only $allowed."
        }
    }

    private fun requireNumber(node: Any?, context: String): BigDecimal {
        check(node is Number) { "$context: expected a number, found ${describe(node)}." }
        return decimal(node)
    }

    /** Through the number's decimal text, so a JSON `-0.02` stays exactly that. */
    private fun decimal(number: Number) = BigDecimal(number.toString())

    private fun requireObject(node: Any?, context: String): Map<*, *> {
        check(node is Map<*, *>) { "$context: expected a JSON object, found ${describe(node)}." }
        return node
    }

    private fun requireString(node: Any?, context: String): String {
        check(node is String && node.isNotBlank()) {
            "$context: expected a non-empty string, found ${describe(node)}."
        }
        return node
    }

    private fun requireInt(node: Any?, context: String): Int {
        check(node is Int) { "$context: expected an integer, found ${describe(node)}." }
        return node
    }

    private fun requireStringList(node: Any?, context: String): List<String> {
        check(node is List<*> && node.all { it is String }) {
            "$context: expected a list of strings, found ${describe(node)}."
        }
        return node.map { it as String }
    }

    private fun describe(node: Any?) =
        when (node) {
            null -> "nothing"
            is Map<*, *> -> "an object with keys ${node.keys}"
            is List<*> -> "a list"
            is String -> "the string \"$node\""
            else -> "the value $node"
        }

    private companion object {
        const val TASK_NAME = "generateDesignTokenSource"
        const val GENERATED_PACKAGE = "io.jitrapon.astro.design.tokens"

        /**
         * Every top-level base.json key. `bindings` is read with the themes it binds to; `states`
         * (state-layer opacities) and `layout` are not consumed by any mobile surface yet.
         */
        val BASE_KEYS =
            setOf(
                "baseSetVersion",
                "spacing",
                "radius",
                "component",
                "typography",
                "bindings",
                "states",
                "layout",
            )

        /** The font roles a type ramp may name; each is a [FONT_ROLE_DECLARATION] constant. */
        val FONT_ROLES = setOf("display", "body")

        val THEME_KEYS = setOf("id", "label", "version", "tokenSetVersion", "colorScheme", "tokens")

        /** The roles a theme binds a font to; each is a [THEME_FONTS_DECLARATION] property. */
        val THEME_FONT_ROLES = setOf("display", "body", "thai")

        /** A theme's `colorScheme` to the wire `ColorScheme` constant it generates. */
        val COLOR_SCHEMES = mapOf("light" to "LIGHT", "dark" to "DARK")

        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")

        /** `BundledThemes`' list of every theme, which no theme id may generate a name over. */
        const val BUNDLED_THEMES_ALL = "all"

        val SHADOW_DECLARATION =
            """
            |/** A drop shadow: its offsets, blur and spread in dp, and its color as `0xAARRGGBB`. */
            |public class Shadow(
            |    public val offsetXDp: Double,
            |    public val offsetYDp: Double,
            |    public val blurDp: Double,
            |    public val spreadDp: Double,
            |    public val color: Long,
            |)
            |"""
                .trimMargin()

        val THEME_FONTS_DECLARATION =
            """
            |/**
            | * The font a theme binds to each role: [display] and [body] for the type ramps' [FontRole]s,
            | * and [thai] for Thai script.
            | */
            |public class ThemeFonts(
            |    public val display: FontId,
            |    public val body: FontId,
            |    public val thai: FontId,
            |)
            |"""
                .trimMargin()

        val BUNDLED_THEME_DECLARATION =
            """
            |/**
            | * A complete theme compiled into the app, so there is always one to paint before any screen
            | * response arrives and whenever a response names a theme the app does not bundle.
            | */
            |public class BundledTheme(
            |    public val id: String,
            |    public val version: String,
            |    public val label: String,
            |    public val colorScheme: ColorScheme,
            |    public val tokenSetVersion: Int,
            |    public val colors: ThemeColors,
            |    public val shadows: ThemeShadows,
            |    public val fonts: ThemeFonts,
            |) {
            |    /** `id@version`, the form the screen request's `knownTheme` parameter carries. */
            |    public val knownThemeReference: String
            |        get() = "${'$'}id@${'$'}version"
            |}
            |"""
                .trimMargin()

        val DIMENSION_DECLARATION =
            """
            |/**
            | * A length in density-independent pixels, from a `dp` family of the design base.
            | *
            | * [hairline] is the thinnest line the display can draw — one physical pixel at any
            | * density — and carries `dp = 0.0`. It is a flag rather than a zero, so a renderer can
            | * never mistake a hairline for an absent line. [scalesWithType] marks a length that grows
            | * with the user's text size, as `sp` does, from [dp] at the default scale.
            | */
            |public class Dimension(
            |    public val dp: Double,
            |    public val hairline: Boolean,
            |    public val scalesWithType: Boolean,
            |)
            |"""
                .trimMargin()

        val RADIUS_DECLARATION =
            """
            |/**
            | * A corner radius in dp. [full] rounds the shorter side completely — a pill or a circle,
            | * whatever the size — and carries `dp = 0.0`.
            | */
            |public class Radius(public val dp: Double, public val full: Boolean)
            |"""
                .trimMargin()

        val FONT_ROLE_DECLARATION =
            """
            |/** The font role a type ramp draws in; a theme binds each role to a font. */
            |public enum class FontRole {
            |    DISPLAY,
            |    BODY,
            |}
            |"""
                .trimMargin()

        val TYPE_RAMP_DECLARATION =
            """
            |/**
            | * One step of the type ramp. Sizes are in `sp`; [letterSpacingEm] is a fraction of the
            | * font size, `0.0` where the design base declares none.
            | */
            |public class TypeRamp(
            |    public val fontRole: FontRole,
            |    public val sizeSp: Double,
            |    public val weight: Int,
            |    public val lineHeightSp: Double,
            |    public val letterSpacingEm: Double,
            |)
            |"""
                .trimMargin()
    }
}

/**
 * Embeds the text of every vendored design artifact as commonTest constants, so the parity tests on
 * both the JVM host and the iOS simulator can parse the JSON independently of the generator —
 * `kotlin.test` has no multiplatform resource loader. A task of its own rather than a second output
 * of [GenerateDesignTokenSource]: `kotlin.srcDir(<task provider>)` adds every output of a task to
 * the source set, which would compile these test payloads into the shipping framework.
 */
abstract class GenerateEmbeddedDesignArtifactSource : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lightTheme: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val darkTheme: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val base: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fontManifest: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val rules: RegularFileProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        val packageDirectory = directory.resolve(GENERATED_PACKAGE.replace('.', '/'))
        packageDirectory.mkdirs()

        val constants =
            listOf(
                "LIGHT_THEME_JSON" to lightTheme,
                "DARK_THEME_JSON" to darkTheme,
                "BASE_JSON" to base,
                "FONTS_JSON" to fontManifest,
                "RULES_JSON" to rules,
            )
        packageDirectory
            .resolve("EmbeddedDesignArtifacts.kt")
            .writeText(
                buildString {
                    append(
                        GeneratedKotlinSource.header(
                            "generateEmbeddedDesignArtifactSource",
                            "the vendored artifacts under shared/design-system/",
                        )
                    )
                    appendLine("package $GENERATED_PACKAGE")
                    appendLine()
                    appendLine("/** The vendored design artifacts' text, verbatim. */")
                    appendLine("internal object EmbeddedDesignArtifacts {")
                    constants.forEachIndexed { index, (name, file) ->
                        if (index > 0) appendLine()
                        append(
                            GeneratedKotlinSource.chunkedConstant(
                                name,
                                file.get().asFile.readText(),
                            )
                        )
                    }
                    appendLine("}")
                }
            )
    }

    private companion object {
        const val GENERATED_PACKAGE = "io.jitrapon.astro.design.tokens"
    }
}

// ----------------------------------------------------------------------------------------------
// Embedded contract artifacts for commonTest
//
// `kotlin.test` has no multiplatform resource-loading API, and the iOS simulator test binary's
// working directory is not a reliable base for file reads — so a test that must see the vendored
// BFF contract or its example fixture cannot simply open the file. This task compiles those
// artifacts into a Kotlin source file that lands on the commonTest compilation, giving every target
// (JVM host and iOS simulator alike) the same bytes through plain constants. The reserved
// calendar-layout golden-vector corpus will load the same way.
//
// The file is emitted into build/, so neither Detekt (source = src) nor the ktfmt source-set tasks
// see it; it is a build output, not checked-in source.
// ----------------------------------------------------------------------------------------------
val generateEmbeddedContractSource =
    tasks.register<GenerateEmbeddedContractSource>("generateEmbeddedContractSource") {
        group = "build"
        description =
            "Emit the vendored BFF contract's fixture, version, and request/response facts as " +
                "Kotlin constants on the commonTest compilation."
        vendoredContract.set(rootProject.file("contracts/astro-bff/openapi.yaml"))
        vendoredFixture.set(
            file("src/commonTest/resources/contract/calendar-month-screen.v0.example.json")
        )
        outputDirectory.set(layout.buildDirectory.dir("generated/contract/commonTest/kotlin"))
    }

// The design system's vendored artifacts, laid out path-for-path under astro-docs'
// `design/build/`. Both generators read only these copies; the root
// verifyVendoredDesignArtifactParity task is the one reader of the submodule.
val designSystemDirectory = layout.projectDirectory.dir("design-system")

val generateDesignTokenSource =
    tasks.register<GenerateDesignTokenSource>("generateDesignTokenSource") {
        group = "build"
        description =
            "Emit the vendored design artifacts as plain Kotlin token values on commonMain."
        fontManifest.set(designSystemDirectory.file("fonts.json"))
        base.set(designSystemDirectory.file("base.json"))
        lightTheme.set(designSystemDirectory.file("themes/light.json"))
        darkTheme.set(designSystemDirectory.file("themes/dark.json"))
        rules.set(designSystemDirectory.file("rules.json"))
        outputDirectory.set(layout.buildDirectory.dir("generated/designTokens/commonMain/kotlin"))
    }

val generateEmbeddedDesignArtifactSource =
    tasks.register<GenerateEmbeddedDesignArtifactSource>("generateEmbeddedDesignArtifactSource") {
        group = "build"
        description = "Emit the vendored design artifacts' text as Kotlin constants on commonTest."
        lightTheme.set(designSystemDirectory.file("themes/light.json"))
        darkTheme.set(designSystemDirectory.file("themes/dark.json"))
        base.set(designSystemDirectory.file("base.json"))
        fontManifest.set(designSystemDirectory.file("fonts.json"))
        rules.set(designSystemDirectory.file("rules.json"))
        outputDirectory.set(layout.buildDirectory.dir("generated/designTokens/commonTest/kotlin"))
    }

// Android Lint reads the source directories registered on a compilation as a plain file collection,
// which drops the producing-task edge that `kotlin.srcDir(<task provider>)` carries into the Kotlin
// compile tasks. Without an explicit dependency Gradle's validation fails the build — "uses this
// output of task ':shared:generateEmbeddedContractSource' without declaring an explicit or implicit
// dependency" — for the generated contract source. It surfaces only in `check`, because the
// narrower
// test tasks never run lint, so removing this edge fails the full gate and nothing before it. The
// design-token generators feed source directories the same way, so they need the same edge.
tasks
    .matching { it.name.startsWith("lintAnalyze") || Regex("generate.*LintModel").matches(it.name) }
    .configureEach {
        dependsOn(
            generateEmbeddedContractSource,
            generateDesignTokenSource,
            generateEmbeddedDesignArtifactSource,
        )
    }

kotlin {
    android {
        compileSdk { version = release(37) }
        namespace = "io.jitrapon.astro.shared"
        // Opt into Android/JVM host (unit) tests. The com.android.kotlin.multiplatform.library
        // plugin creates no host-test compilation by default, so without this commonTest would run
        // only on iOS — `withHostTest` adds `testAndroidHostTest` so the same shared tests run on
        // the JVM host too.
        withHostTest {}
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        // No `export(...)` here deliberately. Every dependency below is `implementation`, so Ktor
        // and Koin types stay out of the generated Objective-C headers and Swift never sees them.
        // The framework's public surface is the Kotlin facade this module owns; exporting the DI
        // or HTTP libraries instead would let Swift call-sites bind directly to them and turn a
        // library swap into an iOS-app refactor.
        it.binaries.framework { baseName = "shared" }
    }

    // Fail the link on a partial-linkage problem instead of shipping a stub. When a klib calls an
    // API that the version of a dependency Gradle selected no longer has — two third-party
    // libraries wanting different versions of a transitive they share is enough — Kotlin/Native
    // does not fail. It links a stub that throws only when that code runs, and by default it
    // reports the substitution silently, so the defect is an iOS-only crash on whichever code path
    // reaches the stub, invisible to every build and to the Android/JVM side entirely. `ERROR`
    // turns that report into a failed link. This is not a leftover compiler argument: a report is
    // fixed by aligning versions in gradle/libs.versions.toml, never by lowering this level or
    // carving a target out. Configured over every Native target rather than the list above so a
    // target added later inherits it.
    //
    // A debug link builds a compiler cache per dependency and reports only "There are linkage
    // errors reported by the partial linkage engine", swallowing the symbol names. To see which
    // symbol in which library is unresolved, re-run the failing link with the target's caches off
    // (`-Pkotlin.native.cacheKind.iosSimulatorArm64=none`) or link the release framework, which
    // uses none.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
    }

    sourceSets {
        // The `iosMain` / `iosTest` intermediate source sets — and their dependsOn edges across
        // iosX64/iosArm64/iosSimulatorArm64 — are created automatically by Kotlin's default
        // hierarchy template once the iOS targets above are declared. Declaring them by hand would
        // disable the template (and emit a "Default Kotlin Hierarchy Template was not applied"
        // warning). The accessors below are the Kotlin plugin's lazy providers, which only
        // configure what the template already created; the eager `by getting` delegate cannot be
        // used for `iosMain` — the template registers it too late for that to resolve.
        // The generated design tokens are production source: they ship in the framework and are
        // what both apps paint from. The task provider carries the producer edge to every target.
        commonMain { kotlin.srcDir(generateDesignTokenSource) }
        commonMain.dependencies {
            // Declared explicitly rather than inherited transitively through Ktor: this module's
            // data-layer API is suspend-based, so coroutines is part of its own contract and must
            // not silently follow whatever Ktor happens to depend on.
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            // ContentNegotiation is the plugin; ktor-serialization-kotlinx-json is the converter
            // it delegates to. Neither works without the other.
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
        }
        // Each platform contributes only its HTTP engine; everything else is shared. The engine is
        // the one piece that cannot be common — it binds to the platform's native networking stack
        // (OkHttp on Android, NSURLSession via Darwin on iOS).
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest {
            // Passing the task provider (rather than its path) carries the generation task's
            // outputs, so every target's test compilation depends on it implicitly — no manual
            // dependsOn per compile task, and none can be forgotten when a target is added.
            kotlin.srcDir(generateEmbeddedContractSource)
            kotlin.srcDir(generateEmbeddedDesignArtifactSource)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // MockEngine substitutes for a real engine so the client's request-building and
            // response-decoding halves are asserted without a network.
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            // Koin resolves at runtime with no compile-time graph validation, so a graph smoke
            // test is the only thing that catches a missing binding.
            implementation(libs.koin.test)
        }
        // `withHostTest {}` above creates the `androidHostTest` source set, which is what runs
        // commonTest on the JVM host. It declares no dependencies of its own: everything the
        // shared tests need comes from commonTest, and nothing here asserts through JUnit's own
        // API rather than kotlin.test.
    }
}

/**
 * Fails when the linked iOS framework's generated Objective-C header names a type no Swift call
 * site may bind to: a Koin, Ktor or kotlinx.coroutines type (a `Flow` above all), or one of the
 * calendar-screen query layer's implementation types.
 *
 * Keeping `implementation` dependencies unexported does not keep their types out of the header. A
 * public member that mentions one still emits it, under a mangled name such as
 * `SharedKotlinx_serialization_coreKSerializer`, and the Swift compiler binds to that name like any
 * other. Visibility modifiers are the only real guard, and without this task nothing but a reviewer
 * would notice one going missing — the xcodebuild app build compiles happily against a public
 * `Flow`-returning member.
 *
 * The query layer's names are read from its source files rather than listed here, and they are
 * selected by *where* a type is declared, never by its visibility. The compiler already keeps every
 * `internal` declaration off the header, so a list built from visibility could only ever fire on a
 * compiler bug, and it would stop covering a type at the exact moment its `internal` was deleted —
 * the regression this check exists for. A list built from location keeps covering that type, and
 * covers a new file in the layer the moment it exists.
 *
 * Three things make the check fail closed rather than pass vacuously:
 * - comments are stripped before matching, since KDoc copied into the header legitimately *names*
 *   `Flow` and query-layer types in prose, and a check that flagged prose would get worked around;
 * - the matcher is run first over a synthetic header holding one instance of every forbidden shape
 *   beside a comment-only mention and a project symbol that merely contains a library's name, and
 *   must flag exactly the forbidden ones, so a regex edit that stopped matching — or started
 *   matching too much — turns the task red instead of green;
 * - the header must declare the `DependencyGraph` facade Swift enters through, so a wrong or empty
 *   file cannot pass for a clean one.
 */
abstract class VerifyFrameworkHeaderSurface : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val frameworkHeader: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val queryLayerSources: ConfigurableFileCollection

    @get:OutputFile abstract val verdictFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val queryLayerNames = collectDeclaredTypeNames()
        check(queryLayerNames.isNotEmpty()) {
            "Found no class, interface or object declared in ${queryLayerSources.files} — the " +
                "declaration matcher or the source paths are wrong, so the header check would " +
                "have no query-layer type to forbid."
        }
        verifyMatcherFlagsEveryForbiddenShape(queryLayerNames.first())

        val headerFile = frameworkHeader.get().asFile
        val header = headerFile.readText()
        check(Regex("""\b$FRAMEWORK_PREFIX$FACADE_NAME\b""").containsMatchIn(header)) {
            "$headerFile does not declare $FRAMEWORK_PREFIX$FACADE_NAME — this is not the " +
                "shared framework's header, so a clean scan of it proves nothing."
        }

        val violations = findViolations(header, queryLayerNames)
        check(violations.isEmpty()) {
            buildString {
                append("The iOS framework header exposes types Swift must not bind to ")
                append("($headerFile):\n")
                violations.forEach { append("  - $it\n") }
                append(
                    "Fix: make the declaration or the member that mentions it `internal` (or " +
                        "`@HiddenFromObjC` where another module needs it), and reach Swift " +
                        "through a facade that takes and returns only shared-module types."
                )
            }
        }
        verdictFile.get().asFile.writeText("Clean: no library type and none of $queryLayerNames.\n")
        logger.lifecycle(
            "Framework header surface OK: no Koin, Ktor or coroutines type, and none of the " +
                "${queryLayerNames.size} query-layer types."
        )
    }

    /** Every class, interface and object declared in [queryLayerSources], at any visibility. */
    private fun collectDeclaredTypeNames(): Set<String> {
        val names = sortedSetOf<String>()
        queryLayerSources.asFileTree
            .matching { include("**/*.kt") }
            .forEach { source ->
                source.forEachLine { line ->
                    DECLARATION.find(line.trim())?.let { names += it.groupValues[1] }
                }
            }
        return names
    }

    private fun verifyMatcherFlagsEveryForbiddenShape(queryLayerName: String) {
        // The first two lines must pass: a library type named only in a comment, and a project
        // symbol that merely contains a library's name, the way the public `initKoin` entry point
        // does. Every line after them carries one forbidden shape and must be flagged.
        val allowedLines =
            listOf(
                "/** Returns a ${FRAMEWORK_PREFIX}Kotlinx_coroutines_coreFlow, in prose only. */",
                "+ (void)doInitKoinBaseUrl:(NSString *)baseUrl;",
            )
        val forbiddenLines =
            listOf(
                "- (${FRAMEWORK_PREFIX}Koin_coreKoin *)koin;",
                "- (id)client:(${FRAMEWORK_PREFIX}Ktor_client_coreHttpClient *)client;",
                "- (id<${FRAMEWORK_PREFIX}Kotlinx_coroutines_coreStateFlow>)state;",
                "@interface $FRAMEWORK_PREFIX$queryLayerName : ${FRAMEWORK_PREFIX}Base",
                "__attribute__((swift_name(\"Outer.$queryLayerName\")))",
            )
        val syntheticHeader = (allowedLines + forbiddenLines).joinToString("\n")
        val flaggedLines =
            findViolations(syntheticHeader, setOf(queryLayerName)).map { it.lineNumber }.toSet()
        val expectedLines = (allowedLines.size + 1..allowedLines.size + forbiddenLines.size).toSet()
        check(flaggedLines == expectedLines) {
            "The header matcher failed its self-test: expected it to flag exactly lines " +
                "$expectedLines of the synthetic header, but it flagged $flaggedLines. Fix the " +
                "matcher before trusting a clean scan."
        }
    }

    private data class HeaderViolation(val lineNumber: Int, val description: String) {
        override fun toString() = "line $lineNumber: $description"
    }

    /** One entry per forbidden identifier, with comments excluded from the scan. */
    private fun findViolations(
        header: String,
        queryLayerNames: Set<String>,
    ): List<HeaderViolation> {
        val violations = mutableListOf<HeaderViolation>()
        stripComments(header).lines().forEachIndexed { index, line ->
            val lineNumber = index + 1
            IDENTIFIER.findAll(line)
                .map { it.value }
                .distinct()
                .forEach { identifier ->
                    val library = libraryFamilyOf(identifier)
                    val isQueryLayerType =
                        identifier.startsWith(FRAMEWORK_PREFIX) &&
                            identifier.removePrefix(FRAMEWORK_PREFIX) in queryLayerNames
                    when {
                        library != null ->
                            violations +=
                                HeaderViolation(lineNumber, "`$identifier` is a $library type")
                        isQueryLayerType ->
                            violations +=
                                HeaderViolation(lineNumber, "`$identifier` is a query-layer type")
                    }
                }
            // A nested declaration is emitted as `SharedOuterInner`, which the exact match above
            // cannot attribute to `Inner`; its Swift name keeps the dotted path, so match that.
            SWIFT_NAME.findAll(line)
                .map { it.groupValues[1] }
                .filter { '.' in it && it.substringAfterLast('.') in queryLayerNames }
                .forEach { swiftName ->
                    violations +=
                        HeaderViolation(lineNumber, "Swift name `$swiftName` is a query-layer type")
                }
        }
        return violations
    }

    /**
     * The library [identifier] belongs to, read from the prefix Kotlin/Native gives a non-exported
     * dependency's types — `Shared` plus the klib name with its separators turned to underscores,
     * as in `SharedKoin_coreKoin`, `SharedKtor_client_coreHttpClient` and
     * `SharedKotlinx_coroutines_coreFlow`. Keying on that prefix rather than on the bare word keeps
     * a project symbol such as `initKoin` from reading as a Koin type, and still catches every Ktor
     * module (`Ktor_http`, `Ktor_utils`, …) and every coroutines type, `Flow` included.
     */
    private fun libraryFamilyOf(identifier: String): String? =
        LIBRARY_TYPE.find(identifier)?.groupValues?.get(1)?.let { LIBRARY_FAMILIES.getValue(it) }

    /** Blanks every comment while keeping its newlines, so reported line numbers stay true. */
    private fun stripComments(header: String) =
        BLOCK_COMMENT.replace(header) { match -> "\n".repeat(match.value.count { it == '\n' }) }
            .lines()
            .joinToString("\n") { it.replace(LINE_COMMENT, "") }

    private companion object {
        const val FRAMEWORK_PREFIX = "Shared"
        const val FACADE_NAME = "DependencyGraph"

        val IDENTIFIER = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
        val SWIFT_NAME = Regex("""swift_name\("([^"]*)"\)""")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//.*$""")

        val LIBRARY_FAMILIES =
            mapOf(
                "Koin" to "Koin",
                "Ktor" to "Ktor",
                "Kotlinx_coroutines" to "kotlinx.coroutines",
            )
        val LIBRARY_TYPE =
            Regex("""^$FRAMEWORK_PREFIX(${LIBRARY_FAMILIES.keys.joinToString("|")})_""")

        // A declaration line, after any annotations and modifiers. Anchored at the start of the
        // trimmed line, so a KDoc line (`* class …`) or a string literal never reads as one.
        val DECLARATION =
            Regex(
                """^(?:@\S+\s+|(?:internal|private|public|protected|data|sealed|enum|value|""" +
                    """fun|abstract|open|inner|annotation|expect|actual)\s+)*""" +
                    """(?:class|interface|object)\s+([A-Z][A-Za-z0-9_]*)"""
            )
    }
}

// ----------------------------------------------------------------------------------------------
// iOS framework header surface guard
//
// Links the debug simulator framework — the one CI's xcodebuild app build consumes — and scans its
// generated header. Every iOS target emits the same header, since it is derived from the Kotlin
// declarations rather than the architecture, so one link covers them all, and it reuses the
// iosSimulatorArm64 klibs the simulator tests already compiled.
//
// The query layer is `data/query/` plus the orchestrator above it. `CalendarScreenQueryState`,
// which sits beside that orchestrator, is deliberately not part of it: it is the one type of the
// layer that is meant to reach Swift.
//
// Kotlin/Native links Apple frameworks only on a macOS host, so the task skips elsewhere and is
// classified into `verifyIos` in the root build's CI partition.
// ----------------------------------------------------------------------------------------------
val verifyFrameworkHeaderSurface =
    tasks.register<VerifyFrameworkHeaderSurface>("verifyFrameworkHeaderSurface") {
        group = "verification"
        description =
            "Fail if the iOS framework header exposes a Koin, Ktor or coroutines type, or a " +
                "calendar-screen query-layer type."
        val framework =
            kotlin
                .iosSimulatorArm64()
                .binaries
                .getFramework(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG)
        dependsOn(framework.linkTaskProvider)
        frameworkHeader.set(framework.outputFile.resolve("Headers/${framework.baseName}.h"))
        val commonSources = "src/commonMain/kotlin/io/jitrapon/astro"
        queryLayerSources.from(
            "$commonSources/data/query",
            "$commonSources/data/calendar/CalendarScreenQuery.kt",
        )
        verdictFile.set(layout.buildDirectory.file("verification/framework-header-surface.txt"))
        val hostIsMac = System.getProperty("os.name").startsWith("Mac")
        onlyIf("Kotlin/Native links Apple frameworks only on a macOS host") { hostIsMac }
    }

tasks.named("check") { dependsOn(verifyFrameworkHeaderSurface) }

/**
 * Fails unless every Kotlin/Native link task is configured to turn a partial-linkage problem into a
 * failed link, and none is configured to undo that.
 *
 * The flag is set once, over every Native target's `compilerOptions`, and reaches a link only
 * because the Kotlin Gradle plugin seeds each link task's free compiler arguments from its
 * compilation's. Neither half of that shows up in a green build when it breaks: a link that no
 * longer receives the flag goes back to manufacturing stubs silently, which is the very outcome the
 * flag exists to prevent. So the two regressions this task guards against are someone removing the
 * flag, and a plugin upgrade changing how compilation options propagate — leaving the flag in the
 * build file while no link sees it.
 *
 * [freeCompilerArgsByLinkTask] holds each link task's `toolOptions.freeCompilerArgs`, which is the
 * complete list the link passes on: the `kotlin.native.linkArgs` Gradle property is appended to it,
 * and a binary's own `freeCompilerArgs` is a view over that same property rather than a second
 * input. Writing through that view — `+=` included — swaps the list seeded from the compilation for
 * a fixed one, so an override declared before the flag is added does not sit beside the flag but
 * drops it, and is reported as the flag missing rather than as a conflict.
 *
 * Three rules keep the check from passing vacuously:
 * - link tasks are enumerated, never listed by name, so a target or build type added later is held
 *   to the rule without anyone remembering to add it here;
 * - finding no framework link at all is a failure, since a rule that holds over nothing proves
 *   nothing — it means the enumeration, not the build, is what changed;
 * - the expected argument is spelt out here rather than shared with the line that sets it, so a
 *   typo there cannot agree with itself and pass.
 */
abstract class VerifyNativeLinksFailOnPartialLinkage : DefaultTask() {

    @get:Input abstract val freeCompilerArgsByLinkTask: MapProperty<String, List<String>>

    @get:Input abstract val frameworkLinkTaskNames: SetProperty<String>

    @TaskAction
    fun verify() {
        val argsByLinkTask = freeCompilerArgsByLinkTask.get().toSortedMap()
        val frameworkLinks = frameworkLinkTaskNames.get()
        check(frameworkLinks.isNotEmpty()) {
            "Found no Kotlin/Native framework link task among ${argsByLinkTask.keys} — the " +
                "enumeration is wrong, so every link would pass this check without being read."
        }

        val violations = argsByLinkTask.mapNotNull { (linkTask, args) ->
            val partialLinkageArgs = args.filter { it.startsWith(PARTIAL_LINKAGE_PREFIX) }
            val conflicting = partialLinkageArgs.filterNot { it == FAIL_ON_PARTIAL_LINKAGE }
            when {
                FAIL_ON_PARTIAL_LINKAGE !in partialLinkageArgs ->
                    "$linkTask is missing $FAIL_ON_PARTIAL_LINKAGE (has: $partialLinkageArgs)"
                conflicting.isNotEmpty() ->
                    "$linkTask also passes $conflicting, which overrides the ERROR level"
                else -> null
            }
        }
        check(violations.isEmpty()) {
            buildString {
                append(
                    "A Kotlin/Native link would ship a partial-linkage stub instead of failing:\n"
                )
                violations.forEach { append("  - $it\n") }
                append(
                    "Fix: keep $FAIL_ON_PARTIAL_LINKAGE on every Native target's compilerOptions " +
                        "and remove any per-binary or kotlin.native.linkArgs override. A linkage " +
                        "report is resolved by aligning versions in gradle/libs.versions.toml, " +
                        "never by lowering the level."
                )
            }
        }
        logger.lifecycle(
            "Partial-linkage enforcement OK: all ${argsByLinkTask.size} Kotlin/Native link tasks " +
                "(${frameworkLinks.size} framework links) pass $FAIL_ON_PARTIAL_LINKAGE."
        )
    }

    private companion object {
        const val PARTIAL_LINKAGE_PREFIX = "-Xpartial-linkage"
        const val FAIL_ON_PARTIAL_LINKAGE = "-Xpartial-linkage-loglevel=ERROR"
    }
}

// ----------------------------------------------------------------------------------------------
// Partial-linkage enforcement guard
//
// Reads what each link task is configured to pass and runs no compiler, so it costs nothing
// measurable. The arguments are handed over as providers of plain strings: they are read only once
// every binary has finished configuring, and the task action holds no reference to a link task,
// which keeps it configuration-cache-safe.
//
// Whether Kotlin/Native registers Apple link tasks on a non-macOS host is not something this build
// can establish, and the no-framework-link rule would turn that uncertainty into a failure, so the
// task skips elsewhere and is classified into `verifyIos` in the root build's CI partition.
// ----------------------------------------------------------------------------------------------
val verifyNativeLinksFailOnPartialLinkage =
    tasks.register<VerifyNativeLinksFailOnPartialLinkage>("verifyNativeLinksFailOnPartialLinkage") {
        group = "verification"
        description =
            "Fail if any Kotlin/Native link task would stub a partial-linkage problem " +
                "instead of failing the link."
        val nativeLinkTasks = tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>()
        freeCompilerArgsByLinkTask.set(
            provider {
                nativeLinkTasks.associate { it.name to it.toolOptions.freeCompilerArgs.get() }
            }
        )
        frameworkLinkTaskNames.set(
            provider {
                nativeLinkTasks
                    .filter { it.binary is org.jetbrains.kotlin.gradle.plugin.mpp.Framework }
                    .map { it.name }
            }
        )
        val hostIsMac = System.getProperty("os.name").startsWith("Mac")
        onlyIf("Kotlin/Native links Apple frameworks only on a macOS host") { hostIsMac }
    }

tasks.named("check") { dependsOn(verifyNativeLinksFailOnPartialLinkage) }

// Detect Apple Silicon *hardware*. `System.getProperty("os.arch")` is unreliable here: a Rosetta-
// translated Gradle daemon reports `x86_64` even on an arm64 Mac, so it would misclassify the host.
// `sysctl -n hw.optional.arm64` queries the hardware (not the process), returning "1" on every
// arm64 Mac regardless of translation; on Intel Macs / non-macOS it errors or returns 0, which we
// treat as "not Apple Silicon".
val isAppleSiliconHost =
    System.getProperty("os.name").startsWith("Mac") &&
        providers
            .exec {
                commandLine("sysctl", "-n", "hw.optional.arm64")
                isIgnoreExitValue = true
            }
            .standardOutput
            .asText
            .map { it.trim() == "1" }
            .getOrElse(false)

// `iosX64Test` runs an x86_64 iOS-simulator test binary, which cannot be exec'd on Apple Silicon
// hardware — the launcher aborts with "Bad CPU type in executable". Since `check` aggregates every
// target's test task, leaving it enabled would fail the gate on every arm64 dev machine and arm64
// CI runner. Disable the task on Apple Silicon; `iosSimulatorArm64Test` covers the simulator-test
// surface there, and on a genuine Intel Mac iosX64Test stays enabled and runs. This is decided at
// configuration time — a `Task.onlyIf` predicate does not work because the Kotlin Native test task
// resets onlyIf during execution.
if (isAppleSiliconHost) {
    tasks.named("iosX64Test") { enabled = false }
}
