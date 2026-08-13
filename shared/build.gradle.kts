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

// ----------------------------------------------------------------------------------------------
// Embedded contract artifacts for commonTest
//
// `kotlin.test` has no multiplatform resource-loading API, and the iOS simulator test binary's
// working directory is not a reliable base for file reads — so a test that must see the vendored
// BFF contract or its example fixture cannot simply open the file. This task compiles those
// artifacts into a Kotlin source file that lands on the commonTest compilation, giving every target
// (JVM host and iOS simulator alike) the same bytes through a plain constant. The reserved
// calendar-layout golden-vector corpus will load the same way.
//
// The file is emitted into build/, so neither Detekt (source = src) nor the ktfmt source-set tasks
// see it; it is a build output, not checked-in source.
// ----------------------------------------------------------------------------------------------
val generateEmbeddedContractSource =
    tasks.register("generateEmbeddedContractSource") {
        group = "build"
        description =
            "Emit the vendored BFF contract artifacts as Kotlin constants on the commonTest " +
                "compilation."

        val contractFile = rootProject.file("contracts/astro-bff/openapi.yaml")
        val fixtureFile =
            file("src/commonTest/resources/contract/calendar-month-screen.v0.example.json")
        val outputRoot = layout.buildDirectory.dir("generated/contract/commonTest/kotlin")

        // Declared inputs/outputs give the task real up-to-date checking: an untouched contract and
        // fixture re-run as UP-TO-DATE, while an edit to either re-emits the constants.
        inputs.file(contractFile).withPropertyName("vendoredContract")
        inputs.file(fixtureFile).withPropertyName("vendoredFixture")
        outputs.dir(outputRoot)

        // Resolve the output directory to a plain File at configuration time; capturing Files and
        // Strings (never the Project or a script-level function) keeps the action
        // configuration-cache-safe.
        val outputDirectory = outputRoot.get().asFile

        doLast {
            // A Kotlin string literal is written to the class file as a CONSTANT_Utf8 entry, which
            // the JVM caps at 65535 bytes — the fixture is already within a few KB of that and the
            // contract is past it. Splitting the text into chunks joined at runtime keeps every
            // individual literal far below the cap no matter how the artifacts grow.
            val maxCharsPerLiteral = 3000

            fun splitIntoLiteralChunks(text: String): List<String> {
                val chunks = mutableListOf<String>()
                var start = 0
                while (start < text.length) {
                    var end = minOf(start + maxCharsPerLiteral, text.length)
                    // Never cut between the halves of a surrogate pair: the two Chars are one
                    // code point, and separating them would emit two lone surrogates that no
                    // longer round-trip to the original character.
                    if (end < text.length && text[end - 1].isHighSurrogate()) end--
                    chunks.add(text.substring(start, end))
                    start = end
                }
                return chunks
            }

            fun asKotlinLiteral(text: String): String = buildString {
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

            fun renderChunkedConstant(name: String, text: String): String {
                val literals =
                    splitIntoLiteralChunks(text).joinToString("\n") {
                        "            ${asKotlinLiteral(it)},"
                    }
                return "    val $name: String =\n" +
                    "        listOf(\n" +
                    literals +
                    "\n        )\n" +
                    "            .joinToString(separator = \"\")\n"
            }

            // Slice `info.version` out of the contract textually rather than parsing the YAML: the
            // contract is inert YAML no build step reads, and adding a YAML parser to serve one
            // constant is not worth the dependency. The block ends at the first line that starts a
            // new top-level key (column 0, non-blank).
            val infoBlockLines =
                contractFile
                    .readText()
                    .lineSequence()
                    .dropWhile { it != "info:" }
                    .drop(1)
                    .takeWhile { it.isBlank() || it.first().isWhitespace() }
            val declaredVersion =
                infoBlockLines
                    .mapNotNull { Regex("""^ {2}version:\s*(\S+)\s*$""").find(it) }
                    .firstOrNull()
                    ?.groupValues
                    ?.get(1)
                    ?.trim('"', '\'')
                    ?: error(
                        "No `info.version` found in ${contractFile.path}. The contract's declared " +
                            "version is what the parity test holds the models to; a contract " +
                            "without one cannot be verified."
                    )

            // Wipe the directory rather than overwriting in place so a constant that is renamed or
            // dropped in a later run cannot survive as a stale generated file.
            outputDirectory.deleteRecursively()
            val packageDirectory = outputDirectory.resolve("io/jitrapon/astro/contract")
            packageDirectory.mkdirs()

            packageDirectory
                .resolve("EmbeddedContract.kt")
                .writeText(
                    "// GENERATED FILE — do not edit. Produced by the :shared " +
                        "`generateEmbeddedContractSource` task from the vendored contract " +
                        "artifacts.\n" +
                        "package io.jitrapon.astro.contract\n\n" +
                        "/**\n" +
                        " * The vendored BFF contract artifacts, compiled into constants because " +
                        "`kotlin.test` has no\n" +
                        " * multiplatform resource loader and the iOS test binary's working " +
                        "directory is not a reliable\n" +
                        " * base for file reads. Regenerated from " +
                        "`contracts/astro-bff/openapi.yaml` and the example\n" +
                        " * fixture under `src/commonTest/resources/contract/` on every build.\n" +
                        " */\n" +
                        "internal object EmbeddedContract {\n" +
                        "    /** `info.version` as the vendored contract itself declares it. */\n" +
                        "    const val DECLARED_CONTRACT_VERSION: String = " +
                        "${asKotlinLiteral(declaredVersion)}\n\n" +
                        "    /** The canonical month-screen example response, verbatim. */\n" +
                        renderChunkedConstant(
                            "MONTH_SCREEN_FIXTURE_JSON",
                            fixtureFile.readText(),
                        ) +
                        "}\n"
                )

            logger.lifecycle(
                "Embedded contract sources generated: declared version $declaredVersion, " +
                    "fixture ${fixtureFile.length()} bytes."
            )
        }
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
        // Android host (JVM) unit tests created by `withHostTest {}` live in `androidHostTest`.
        // AndroidGreetingTest asserts on the androidMain `Greeting` actual via JUnit directly, so
        // the host-test source set needs JUnit on its classpath (commonTest's kotlin("test") does
        // not supply org.junit on its own). No Kotlin-plugin accessor exists for this source set —
        // the Android KMP library plugin creates it — so it is looked up by name.
        getByName("androidHostTest").dependencies { implementation("junit:junit:4.13.2") }
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
