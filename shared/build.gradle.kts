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

    sourceSets {
        // The `iosMain` / `iosTest` intermediate source sets — and their dependsOn edges across
        // iosX64/iosArm64/iosSimulatorArm64 — are created automatically by Kotlin's default
        // hierarchy template once the iOS targets above are declared. Declaring them by hand would
        // disable the template (and emit a "Default Kotlin Hierarchy Template was not applied"
        // warning). The accessors below are the Kotlin plugin's lazy providers, which only
        // configure what the template already created; the eager `by getting` delegate cannot be
        // used for `iosMain` — the template registers it too late for that to resolve.
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
