import java.io.File
import javax.inject.Inject
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.gradle.process.ExecOperations

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath("com.android.tools.build:gradle:9.3.2")
        classpath(libs.kotlin.compose.compiler.gradle.plugin)
        // The kotlinx.serialization Kotlin compiler plugin. It sits on the classpath rather than
        // in `plugins {}` because :shared applies it by id with no version
        // (`kotlin("plugin.serialization")`) and inherits the version pinned here. The catalog
        // locks it to the same `kotlin` ref as the Kotlin Gradle plugin above: a serialization
        // plugin built against a different Kotlin than the compiler loading it fails the build.
        classpath(libs.kotlin.serialization.gradle.plugin)
    }
}

plugins {
    // ktfmt on the ROOT project itself, not just :shared/:androidApp. Those subprojects only format
    // their own build scripts, leaving the root build.gradle.kts / settings.gradle.kts unchecked by
    // `./gradlew check` (the pre-commit hook's CLI was the only thing catching them). Applying the
    // plugin here registers `ktfmtCheckScripts` over the root `*.gradle.kts`; it is wired into the
    // gate and the CI partition below.
    alias(libs.plugins.ktfmt)
    // CycloneDX SBOM generation. Applied ONLY here, on the root project — see the SBOM section
    // further down for why one root application already covers every subproject, and why the
    // resulting task is deliberately outside `check`.
    alias(libs.plugins.cyclonedx)
}

// Match the subprojects' formatter: ktfmt's Kotlin-official-style preset, per kotlin.code.style.
ktfmt { kotlinLangStyle() }

// Pull the root script-format check into the gate, mirroring the checkNoDetektBaseline / swift
// wiring below: every subproject's `check` depends on it, so `./gradlew check` verifies the root
// `*.gradle.kts`. It is also added to the verifyAndroidCommon partition below so the drift guard
// stays balanced.
subprojects {
    tasks
        .matching { it.name == "check" }
        .configureEach { dependsOn(rootProject.tasks.named("ktfmtCheck")) }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) { delete(rootProject.buildDir) }

// No-baseline gate. This project forbids Detekt baselines outright — findings are fixed by
// refactoring, never grandfathered into a baseline (the companion `ForbiddenSuppress` rule in
// config/detekt/detekt.yml forbids the per-site `@Suppress` escape hatch the same way). Fail the
// build if any `detekt-baseline.xml` appears anywhere in the working tree so the convention is
// enforced mechanically rather than by reviewer vigilance. Wired into every subproject's `check`
// below, so `./gradlew check` catches a stray baseline.
//
// Capturing `rootDir` (a serializable File) and walking it in `doLast` keeps this configuration-
// cache-safe and re-scans the live tree at execution time, so a baseline added after the task graph
// is cached is still caught. `build`/`.git`/`.gradle` are pruned: they hold generated or VCS
// internals, never a checked-in baseline, and walking them would be needless work.
val checkNoDetektBaseline =
    tasks.register("checkNoDetektBaseline") {
        group = "verification"
        description = "Fail the build if any detekt-baseline.xml exists; baselines are forbidden."
        val treeRoot = rootDir
        doLast {
            val prunedDirs = setOf("build", ".git", ".gradle")
            val baselines =
                treeRoot
                    .walkTopDown()
                    .onEnter { it.name !in prunedDirs }
                    .filter { it.isFile && it.name == "detekt-baseline.xml" }
                    .map { it.relativeTo(treeRoot).path }
                    .toList()
            check(baselines.isEmpty()) {
                "Detekt baseline files are forbidden in this project but were found:\n" +
                    baselines.joinToString("\n") { "  - $it" } +
                    "\nThis project does not grandfather findings into a baseline — refactor to " +
                    "resolve them instead, or suppress nothing (ForbiddenSuppress is active)."
            }
        }
    }

// Every module's `check` runs the no-baseline gate, so `./gradlew check` enforces it regardless of
// which subproject is being verified.
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkNoDetektBaseline) }
}

// Vendored-contract drift guard. `contracts/astro-bff/openapi.yaml` and the month-screen example
// under `shared/src/commonTest/resources/contract/` are copies of files the `docs/astro-docs`
// submodule owns — plain copies rather than a generated client, because the `astro-bff` repo that
// will own the contract does not exist yet and the mirror is not its owner either, so pointing
// codegen at astro-docs would only relocate the copy. Nothing in the build reads the vendored
// contract as a build input, which is exactly the problem: a copy left stale by an upstream edit is
// invisible to every other check in this repo until a request 400s against the real BFF. This task
// is the only signal that the copies are current.
//
// The two artifacts are held to different standards deliberately. The contract must be BYTE-
// identical, because the shared module's conformance test reads it by slicing the YAML textually —
// a re-indent or a reordered key changes what that test sees even when the contract still means the
// same thing. The fixture is compared as parsed JSON with the top-level `_comment` dropped, because
// that key is the one sanctioned local adaptation: it re-words the upstream note to name this
// repo's use of the file. Every other key, `_comment_theme` included, must agree, so a fixture edit
// that forgets its cross-repo half fails here.
//
// No inputs/outputs are declared, so the task re-reads the live files on every run rather than
// reporting UP-TO-DATE from a snapshot taken before `git submodule update --init` populated the
// mirror. Capturing `File`s and comparing them in `doLast` keeps it configuration-cache-safe.
val verifyVendoredContractParity =
    tasks.register("verifyVendoredContractParity") {
        group = "verification"
        description =
            "Fail if the vendored BFF contract or screen fixture has drifted from the astro-docs " +
                "mirror."

        val vendoredContract = file("contracts/astro-bff/openapi.yaml")
        val mirroredContract = file("docs/astro-docs/openapi.yaml")
        val vendoredFixture =
            file("shared/src/commonTest/resources/contract/calendar-month-screen.v0.example.json")
        val mirroredFixture = file("docs/astro-docs/calendar-month-screen.v0.example.json")
        val treeRoot = rootDir

        doLast {
            fun rel(f: File) = f.relativeTo(treeRoot).path

            // The mirror is a git submodule, so it is absent on a clone made without
            // `--recurse-submodules`. Name the remedy instead of failing on a comparison against a
            // file that isn't there — and never skip: a parity check that quietly passes when it
            // could not run is indistinguishable from one that ran and found nothing.
            val absentMirror = listOf(mirroredContract, mirroredFixture).filterNot { it.isFile }
            check(absentMirror.isEmpty()) {
                "The astro-docs mirror is not checked out, so the vendored contract artifacts " +
                    "cannot be verified:\n" +
                    absentMirror.joinToString("\n") { "  - missing ${rel(it)}" } +
                    "\nRun `git submodule update --init` to fetch it."
            }

            val absentVendored = listOf(vendoredContract, vendoredFixture).filterNot { it.isFile }
            check(absentVendored.isEmpty()) {
                "Vendored contract artifacts are missing from this repository:\n" +
                    absentVendored.joinToString("\n") { "  - missing ${rel(it)}" }
            }

            check(vendoredContract.readBytes().contentEquals(mirroredContract.readBytes())) {
                "${rel(vendoredContract)} is not byte-identical to the astro-docs mirror at " +
                    "${rel(mirroredContract)}. The mirror is upstream: re-copy its file over the " +
                    "vendored one rather than editing the vendored copy."
            }

            // Report where the fixtures diverge, not merely that they do: the file is ~430 lines of
            // nested JSON, and "not equal" would leave the reader to diff it by hand.
            fun firstDifference(vendored: Any?, mirrored: Any?, path: String): String? =
                when {
                    vendored is Map<*, *> && mirrored is Map<*, *> -> {
                        val vendoredOnly = vendored.keys - mirrored.keys
                        val mirroredOnly = mirrored.keys - vendored.keys
                        when {
                            vendoredOnly.isNotEmpty() ->
                                "$path: only in the vendored copy: ${vendoredOnly.joinToString()}"
                            mirroredOnly.isNotEmpty() ->
                                "$path: only in the mirror: ${mirroredOnly.joinToString()}"
                            else ->
                                vendored.keys.firstNotNullOfOrNull { key ->
                                    firstDifference(vendored[key], mirrored[key], "$path.$key")
                                }
                        }
                    }
                    vendored is List<*> && mirrored is List<*> ->
                        if (vendored.size != mirrored.size) {
                            "$path: ${vendored.size} entries in the vendored copy, " +
                                "${mirrored.size} in the mirror"
                        } else {
                            vendored.indices.firstNotNullOfOrNull { i ->
                                firstDifference(vendored[i], mirrored[i], "$path[$i]")
                            }
                        }
                    vendored == mirrored -> null
                    else -> "$path: vendored=$vendored, mirror=$mirrored"
                }

            // JsonSlurper ships with the Gradle distribution, so reading the fixture structurally
            // costs no dependency. Comparing parsed values rather than text also makes the check
            // indifferent to whitespace and to the trailing newline the vendored copy adds.
            fun withoutLocalComment(f: File): Any? {
                val parsed = groovy.json.JsonSlurper().parse(f, "UTF-8")
                return if (parsed is Map<*, *>) parsed.filterKeys { it != "_comment" } else parsed
            }

            val fixtureDifference =
                firstDifference(
                    withoutLocalComment(vendoredFixture),
                    withoutLocalComment(mirroredFixture),
                    "fixture",
                )
            check(fixtureDifference == null) {
                "${rel(vendoredFixture)} has drifted from the astro-docs mirror at " +
                    "${rel(mirroredFixture)}:\n  $fixtureDifference\n" +
                    "Only the top-level `_comment` may differ between the two copies; every other " +
                    "key must agree."
            }
        }
    }

// Every module's `check` runs the contract drift guard, mirroring the checkNoDetektBaseline wiring:
// the vendored copies are a repo-wide artifact, not a :shared-only one, so the gate belongs to
// `./gradlew check` as a whole rather than to whichever subproject happens to hold the fixture.
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyVendoredContractParity) }
}

// Git-hook tooling. The pre-commit hook invokes ktfmt and detekt as standalone CLI jars rather than
// through the Gradle daemon — the daemon path is ~15–20 s, the direct-jar path is ~1–2 s, which is
// the difference between a hook that runs on every commit and one that gets `--no-verify`'d away.
// `resolveLintTools` downloads those fat jars and records their absolute paths so the hook can find
// them without re-resolving; `installGitHooks` points git at .githooks and makes the scripts
// executable. The version catalog (gradle/libs.versions.toml) is the single source of truth for the
// jar versions, so `verifyKtfmtAlignment` guarantees the hook and the Gradle plugin format alike.
//
// Two separate, non-transitive configurations because the fat jars bundle their own runtime
// deps and pin conflicting kotlin-compiler-embeddable versions; resolving them together would
// force a clash.
val ktfmtTool by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val detektTool by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// The `structured-coroutines` ruleset jar, resolved separately so the pre-commit hook can hand
// it to detekt-cli via `--plugins`. Without it the CLI rejects config/detekt/detekt.yml outright:
// with config validation on (Detekt's default), the unknown `structured-coroutines:` block reads
// as a misspelled property and detekt-cli exits non-zero, so every Kotlin commit would fail. The
// Gradle `detekt` task gets the same jar via each module's `detektPlugins` configuration.
val detektRuleset by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    // `with-dependencies` (ktfmt) and `all` (detekt-cli) are the self-contained fat-jar
    // classifiers.
    ktfmtTool("com.facebook:ktfmt:${libs.versions.ktfmt.cli.get()}:with-dependencies")
    detektTool("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}:all")
    detektRuleset(libs.structured.coroutines.detekt.rules)
}

// ExecOperations must be injected to stay configuration-cache-safe — Project.exec is not available
// at execution time under the configuration cache.
interface ExecInjected {
    @get:Inject val exec: ExecOperations
}

tasks.register("resolveLintTools") {
    group = "git hooks"
    description =
        "Resolve ktfmt and detekt CLI jars; write their paths to .gradle/lint-tools.properties."

    val toolsFile = layout.projectDirectory.file(".gradle/lint-tools.properties")
    val ktfmtJars = ktfmtTool.incoming.files
    val detektJars = detektTool.incoming.files
    val rulesetJars = detektRuleset.incoming.files
    val ktfmtVersion = libs.versions.ktfmt.cli.get()
    val detektVersion = libs.versions.detekt.get()
    val rulesetVersion = libs.versions.structured.coroutines.get()

    inputs.files(ktfmtJars, detektJars, rulesetJars)
    inputs.property("ktfmtVersion", ktfmtVersion)
    inputs.property("detektVersion", detektVersion)
    inputs.property("rulesetVersion", rulesetVersion)
    outputs.file(toolsFile)

    doLast {
        val ktfmtJar =
            ktfmtJars.singleFile.takeIf { it.name.endsWith("-with-dependencies.jar") }
                ?: error("ktfmt CLI jar not resolved; got=${ktfmtJars.files.map { it.name }}")
        val detektJar =
            detektJars.singleFile.takeIf { it.name.endsWith("-all.jar") }
                ?: error("detekt-cli jar not resolved; got=${detektJars.files.map { it.name }}")
        val rulesetJar =
            rulesetJars.singleFile.takeIf { it.name.endsWith(".jar") }
                ?: error(
                    "detekt ruleset jar not resolved; got=${rulesetJars.files.map { it.name }}"
                )
        val out = toolsFile.asFile
        out.parentFile.mkdirs()
        out.writeText(
            """
            ktfmt.jar=${ktfmtJar.absolutePath.replace("\\", "/")}
            ktfmt.version=$ktfmtVersion
            detekt.jar=${detektJar.absolutePath.replace("\\", "/")}
            detekt.version=$detektVersion
            detekt.ruleset.jar=${rulesetJar.absolutePath.replace("\\", "/")}
            detekt.ruleset.version=$rulesetVersion

            """
                .trimIndent()
        )
    }
}

tasks.register("installGitHooks") {
    group = "git hooks"
    description = "Set core.hooksPath to .githooks and resolve the lint CLI jars the hook needs."
    dependsOn("resolveLintTools")

    val injected = project.objects.newInstance<ExecInjected>()
    val preCommit = file(".githooks/pre-commit")
    val prePush = file(".githooks/pre-push")

    doLast {
        injected.exec.exec { commandLine("git", "config", "core.hooksPath", ".githooks") }
        // The hook scripts are authored in later scaffolding steps; chmod each only once it
        // exists so installing the hooks early (before the scripts land) still wires
        // core.hooksPath cleanly.
        listOf(preCommit, prePush).filter { it.exists() }.forEach { it.setExecutable(true, false) }
        logger.lifecycle("Git hooks installed: core.hooksPath=.githooks")
    }
}

// iOS Swift formatting — Apple's toolchain `swift format` (config: iosApp/.swift-format), the iOS
// analog of the Kotlin ktfmt gate. `swiftFormatCheck` is wired into `check` (below) and the
// pre-commit hook, so iOS formatting is enforced exactly like Kotlin. `swift` ships with the Swift
// toolchain (present on macOS dev machines and on a CI runner once the toolchain is installed);
// guarding on tool *availability* (resolving `swift` on PATH) rather than on the OS lets the same
// task enforce in CI while skipping with a clear message anywhere swift is absent (e.g. a
// contributor's Linux box) instead of hard-failing on a missing binary. Reading PATH through the
// provider API keeps configuration-cache correctness — a PATH change invalidates the cache so
// newly-installed toolchains are picked up. `swiftFormatApply` rewrites in place;
// `swiftFormatCheck`
// is strict and fails on any finding.
val swiftAppSources = "iosApp/iosApp"
val pathDirs = providers.environmentVariable("PATH")

// Resolve `swift` on PATH inline (not via a script-level fun — referencing one from doLast captures
// the build script object, which the configuration cache cannot serialize). Returns null when the
// toolchain is absent so the tasks self-skip instead of hard-failing.
tasks.register("swiftFormatApply") {
    group = "formatting"
    description = "Apply swift-format to the iOS app sources in place (needs the Swift toolchain)."
    val injected = project.objects.newInstance<ExecInjected>()
    val srcDir = file(swiftAppSources)
    val pathValue = pathDirs.orNull
    doLast {
        val swift =
            pathValue
                ?.split(File.pathSeparator)
                ?.map { File(it, "swift") }
                ?.firstOrNull { it.canExecute() }
        if (swift == null) {
            logger.lifecycle("swiftFormatApply skipped: `swift` not found on PATH.")
            return@doLast
        }
        injected.exec.exec {
            commandLine(swift.path, "format", "--in-place", "--recursive", srcDir.path)
        }
    }
}

val swiftFormatCheck =
    tasks.register("swiftFormatCheck") {
        group = "verification"
        description =
            "Lint iOS app sources with swift-format (strict); part of `check` where swift is present."
        val injected = project.objects.newInstance<ExecInjected>()
        val srcDir = file(swiftAppSources)
        val pathValue = pathDirs.orNull
        doLast {
            val swift =
                pathValue
                    ?.split(File.pathSeparator)
                    ?.map { File(it, "swift") }
                    ?.firstOrNull { it.canExecute() }
            if (swift == null) {
                logger.lifecycle(
                    "swiftFormatCheck skipped: `swift` not found on PATH — install the Swift " +
                        "toolchain to enforce iOS formatting."
                )
                return@doLast
            }
            injected.exec.exec {
                commandLine(swift.path, "format", "lint", "--strict", "--recursive", srcDir.path)
            }
        }
    }

// iOS formatting rides the same aggregate gate as Kotlin: every subproject's `check` depends on
// swiftFormatCheck, so `./gradlew check` runs it once (it self-skips where swift is absent).
// Mirrors
// the checkNoDetektBaseline wiring above.
subprojects { tasks.matching { it.name == "check" }.configureEach { dependsOn(swiftFormatCheck) } }

// iOS static analysis — SwiftLint (config: iosApp/.swiftlint.yml), the iOS analog of Detekt the way
// swiftFormatCheck is the analog of ktfmtCheck: swift-format owns formatting, SwiftLint owns the
// style / code-smell / complexity rules. `--strict` promotes warnings to errors so any finding
// fails
// the gate exactly like detekt and the strict swift-format check. Unlike `swift`, `swiftlint` is
// NOT
// toolchain-bundled (install via Homebrew or the SwiftPM plugin); guarding on `swiftlint` resolving
// on PATH lets the same task enforce where it's installed (dev machines, CI once it installs the
// binary) and self-skip with a clear message where it's absent — the same posture as
// swiftFormatCheck.
val swiftLintConfig = "iosApp/.swiftlint.yml"
val swiftLintCheck =
    tasks.register("swiftLintCheck") {
        group = "verification"
        description =
            "Lint iOS app sources with SwiftLint (strict); part of `check` where swiftlint is present."
        val injected = project.objects.newInstance<ExecInjected>()
        val srcDir = file(swiftAppSources)
        val configFile = file(swiftLintConfig)
        val pathValue = pathDirs.orNull
        doLast {
            val swiftlint =
                pathValue
                    ?.split(File.pathSeparator)
                    ?.map { File(it, "swiftlint") }
                    ?.firstOrNull { it.canExecute() }
            if (swiftlint == null) {
                logger.lifecycle(
                    "swiftLintCheck skipped: `swiftlint` not found on PATH — install SwiftLint " +
                        "(brew install swiftlint) to enforce iOS static analysis."
                )
                return@doLast
            }
            injected.exec.exec {
                commandLine(
                    swiftlint.path,
                    "lint",
                    "--strict",
                    "--config",
                    configFile.path,
                    srcDir.path,
                )
            }
        }
    }

// SwiftLint rides the same aggregate gate as Detekt/swift-format: every subproject's `check`
// depends
// on swiftLintCheck (it self-skips where swiftlint is absent). Mirrors the swiftFormatCheck wiring.
subprojects { tasks.matching { it.name == "check" }.configureEach { dependsOn(swiftLintCheck) } }

// iOS unused-code analysis — Periphery (config: iosApp/.periphery.yml). This is the parity for the
// unused-member rules Detekt covers on the Kotlin side and SwiftLint only partially covers. It is
// DELIBERATELY NOT wired into `check`: unlike swift-format/SwiftLint (fast, per-file), Periphery is
// a
// whole-program analysis that must run a full `xcodebuild` to produce an index store before it can
// resolve symbols — a scan takes minutes and needs macOS + Xcode + the `shared` KMP framework
// built,
// so it cannot self-skip cleanly on a toolchain-less CI host the way the other iOS tools do. It is
// therefore an on-demand task, the iOS analog of the on-demand `:androidApp:debugStabilityDump`
// Compose-stability task (also kept out of `check` for the same heavyweight-build reason). Run it
// manually on a macOS dev machine before a refactor or release; guarded on `periphery` resolving on
// PATH (install via `brew install periphery`) so it self-skips with a clear message where absent.
tasks.register("peripheryScan") {
    group = "verification"
    description =
        "Scan iOS app sources for unused code with Periphery (on-demand; not part of `check`)."
    val injected = project.objects.newInstance<ExecInjected>()
    val iosDir = file("iosApp")
    val pathValue = pathDirs.orNull
    doLast {
        val periphery =
            pathValue
                ?.split(File.pathSeparator)
                ?.map { File(it, "periphery") }
                ?.firstOrNull { it.canExecute() }
        if (periphery == null) {
            logger.lifecycle(
                "peripheryScan skipped: `periphery` not found on PATH — install it " +
                    "(brew install periphery) to scan for unused iOS code."
            )
            return@doLast
        }
        // Run from iosApp/ so `periphery scan` auto-discovers iosApp/.periphery.yml (project +
        // schemes). The full xcodebuild it triggers is why this is on-demand, not a `check` gate.
        injected.exec.exec {
            workingDir = iosDir
            commandLine(periphery.path, "scan")
        }
    }
}

// ----------------------------------------------------------------------------------------------
// CycloneDX SBOM generation — the artifact the PR-blocking software-composition-analysis gate reads
//
// Software-composition analysis on the Gradle graph used to be post-merge only: the job feeding
// GitHub's Dependency Graph runs on push, so a vulnerable dependency was reported after it had
// already landed. This plugin produces a machine-readable bill of materials from the RESOLVED
// graph — post conflict-resolution, substitution, and transitive selection — so the scanner has
// something to read at PR time.
//
// Aggregation wiring, stated concretely rather than assumed. Plugin 3.x registers
// `cyclonedxDirectBom` on the project it is applied to AND on that project's subprojects, but
// registers the aggregating `cyclonedxBom` only on the applying project. Applying it once at the
// root therefore covers `:shared` and `:androidApp` with no per-module `apply` and no hand-rolled
// merge step. `:cyclonedxBom` is THE task CI invokes and its output is THE single artifact the
// scanner consumes; it composes the per-project Direct SBOMs and FAILS when one of them is missing
// rather than quietly emitting a short document — which is the property that makes a
// single-artifact scan trustworthy.
//
// Output path: `build/reports/cyclonedx/bom.json`, the plugin's default destination and default
// name. BOTH parts are load-bearing for the scanner and neither is free to change casually.
//
//   * The DIRECTORY is inside the gitignored `**/build/` tree, because an SBOM is a generated
//     artifact and is never committed. osv-scanner honours `.gitignore` when it walks a directory,
//     so a repo-root source scan cannot see this file at all — it walks zero inodes, extracts
//     nothing, and reports "no package sources found". The scan therefore names this path
//     explicitly rather than relying on discovery from the repository root.
//   * The FILE NAME must be one the CycloneDX extractor recognises — `bom.json`, or a
//     `*.cdx.json` suffix. Names outside that set (`sbom.json`, `astro-bom.json`) are not
//     recognised even when handed to the scanner as an explicit target: it finds no package source
//     rather than reporting a parse error. Renaming this output means renaming it to another name
//     on that list.
//   * The scan targets the FILE, not its directory. There is no output-format switch on the task —
//     `jsonOutput` and `xmlOutput` are separate file properties and both are always written — so
//     `bom.xml` always sits beside `bom.json` carrying identical content. A directory target
//     extracts both and counts every component twice, which would corrupt any assertion made on the
//     parsed package count.
//
// DELIBERATELY NOT wired into `check`, and so deliberately outside the `verifyCheckPartition` drift
// guard below. Generating an SBOM produces a build artifact, and artifact production is coverage
// BEYOND `check` — the same classification this repo already gives `:androidApp:assemble` and
// `:shared:linkReleaseFrameworkIosArm64`, both of which CI runs as job steps rather than through an
// aggregate. Wiring a `dependsOn` from any `check` would pull the task into the guard's closure and
// fail the build until it were also classified into a CI half; that failure is the intended signal
// that this decision is being reversed, not an obstacle to route around.
// ----------------------------------------------------------------------------------------------

// The scope of the graph the BOM covers, pinned explicitly rather than inherited from the plugin's
// conventions: a plugin upgrade that narrowed a default would otherwise shrink what the gate can
// see, silently, and while still passing.
//
// Every resolvable configuration of every project is in scope by default. An empty `includeConfigs`
// means "no allow-list", so a new KMP target, a new source set, or a new module widens the BOM
// automatically. An allow-list of configuration names would instead have to be edited in lockstep
// with the build, and a stale or misspelled entry there narrows coverage without failing anything.
//
// TEST GRAPHS ARE IN SCOPE, AND A VULNERABLE TEST-ONLY DEPENDENCY BLOCKS A PULL REQUEST. That is
// the plain reading of what this gate promises — a Gradle dependency carrying a high-or-worse
// advisory fails the pull request — and it matches the coverage of the inventory channel the gate
// sits alongside. `testConfigs` is emptied so that no component is stamped
// `cdx:maven:package:test`, which keeps the document from carrying an annotation a downstream
// scanner could use to filter those components back out. Narrowing the gate to shipping
// dependencies only would be a deliberate change to what it promises, not a filter detail.
//
// The exclusions are the one class of dependency this repository cannot act on: build-time tooling
// whose version the Android Gradle Plugin pins, which ships inside no artifact, and which therefore
// has no remediation available here short of a whole-toolchain upgrade. Left in, the tooling alone
// contributes a critical and dozens of high advisories that no source change can clear — and since
// the severity policy forbids waving a high-or-worse advisory through the ignore list, the gate
// would be permanently red and so permanently uninformative. Each pattern is a full-string regex:
//
//   ^classpath$              — the buildscript classpath, the same exclusion the dependency-graph
//                              inventory job applies for the same reason. `includeBuildEnvironment`
//                              already keeps buildscript configurations out of the traversal
//                              entirely; this is the second layer that holds if it is flipped back.
//   ^androidLintTool$        — the Android lint tool's own runtime (`:shared` and `:androidApp`);
//                              carries the Bouncy Castle stack AGP pins.
//   ^unified-test-platform-  — AGP's Unified Test Platform harness; carries a gRPC/Netty stack
//     .*$                      several minor versions behind, pinned by AGP.
//
// Deliberately still IN scope: `coreLibraryDesugaring` (its artifact is bundled into the APK),
// `detekt*` / `ktfmt*` (versions this repository pins in the version catalog and can bump on its
// own), and the Kotlin compiler classpaths (remediable by a Kotlin upgrade). The dividing line is
// remediability from this repository, not whether a dependency is "tooling".
//
// Traversing every resolvable configuration means traversing three that cannot resolve their own
// declared dependencies: `appleMainCInterop`, `iosMainCInterop`, and `nativeMainCInterop`. Those
// are
// the commonized-cinterop configurations Kotlin creates for shared native source sets, and the
// libraries this project depends on publish no variant matching them, so each dependency resolves
// FAILED. The generator tolerates that and keeps going, which is the only reason an empty
// `includeConfigs` is safe here; every coordinate those three declare reaches the BOM anyway
// through the per-target `ios<Target>CInterop` and `ios<Target>CompileKlibraries` configurations,
// which resolve the full native graph down to the target-specific artifacts. If a generator upgrade
// ever makes an unresolvable configuration fatal, this is the shape of the failure and the skip
// list is where those three would have to be named.
allprojects {
    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs.set(emptyList<String>())
        skipConfigs.set(listOf("^classpath$", "^androidLintTool$", "^unified-test-platform-.*$"))
        testConfigs.set(emptyList<String>())
        includeBuildEnvironment.set(false)
    }
}

// ----------------------------------------------------------------------------------------------
// Reproducible CI partition — verifyAndroidCommon (host-portable) + verifyIos (macOS-only)
//
// `./gradlew check` stays THE local gate (see CLAUDE.md): one command, runs everything. CI instead
// splits that same work across two runners so the expensive macOS runner (~10x the per-minute cost
// of Linux) only carries the genuinely Mac-bound surface. The two aggregates partition everything
// `check` runs:
//   * verifyAndroidCommon — Android build/unit tests, JVM/common (host) tests, and Kotlin
//     static-analysis + formatting (Detekt + ktfmt). Runs on a cheap Linux runner. It also lints
//     iosMain *source* — Detekt/ktfmt parse every Kotlin source set, and parsing needs no Mac; only
//     iOS *compilation* does. So "android-common" is the host-portable half, not "no iOS at all".
//   * verifyIos — the shared module's iOS simulator tests plus the Swift gates (swift-format +
//     SwiftLint). These need a Mac: Kotlin/Native cross-compiles iOS targets only on a macOS host,
//     and the Swift tools ship with / are installed onto the macOS runner.
//
// `verifyCheckPartition` mechanically proves the split stays exhaustive — local == CI by
// construction. It fails if the set of action-bearing tasks reachable from `check` differs from the
// set reachable from the two aggregates, so a verification task wired into `check` but forgotten in
// a partition can't silently go un-run in CI. This is the same posture as the repo's other drift
// guards (checkNoDetektBaseline, verifyKtfmtAlignment): a task enforces the convention, not a
// reviewer.
// ----------------------------------------------------------------------------------------------
// The partition as data: (projectPath, taskName) pairs. This single source both wires each
// aggregate's `dependsOn` and feeds the drift guard's walk below, so the two can never disagree.
// Paths use ":" for the root project. Each named task is itself either an action-bearing leaf or a
// no-action umbrella (e.g. ktfmtCheck) the guard expands to its per-source leaves.
fun taskPath(projectPath: String, taskName: String) =
    if (projectPath == ":") ":$taskName" else "$projectPath:$taskName"

val iosVerification =
    listOf(
        // iosX64Test is disabled on Apple Silicon hosts (see shared/build.gradle.kts) but stays
        // listed so a genuine Intel runner still covers it; iosArm64 is a device target (no test).
        ":shared" to "iosSimulatorArm64Test",
        ":shared" to "iosX64Test",
        ":" to "swiftFormatCheck",
        ":" to "swiftLintCheck",
    )

val androidCommonVerification =
    listOf(
        ":androidApp" to "testDebugUnitTest",
        ":androidApp" to "lintDebug",
        ":androidApp" to "detekt",
        ":androidApp" to "ktfmtCheck",
        ":androidApp" to "verifyKtfmtAlignment",
        ":shared" to "testAndroidHostTest",
        ":shared" to "detekt",
        ":shared" to "ktfmtCheck",
        ":shared" to "verifyKtfmtAlignment",
        ":" to "checkNoDetektBaseline",
        ":" to "ktfmtCheck",
        // Host-portable: the guard only reads two checked-out files and compares them, so it needs
        // no Mac. It sits in this half because the astro-docs submodule is fetched on the Linux
        // job.
        ":" to "verifyVendoredContractParity",
    )

val verifyIos =
    tasks.register("verifyIos") {
        group = "verification"
        description = "macOS-only half of `check`: iOS simulator tests + Swift format/lint gates."
        dependsOn(iosVerification.map { (p, t) -> taskPath(p, t) })
    }

val verifyAndroidCommon =
    tasks.register("verifyAndroidCommon") {
        group = "verification"
        description = "Host-portable half of `check`: Android build/tests, host tests, Kotlin lint."
        dependsOn(androidCommonVerification.map { (p, t) -> taskPath(p, t) })
    }

// Tasks excluded from the closure comparison below. The two aggregates, the guard, and `check`
// itself are scaffolding/lifecycle roots, not verification work. `allTests` is the KMP
// KotlinTestReport aggregator: it has an action but runs no tests — it only collates the per-target
// test results, and those individual test tasks ARE partitioned, so it carries no coverage of its
// own. It also inherently spans both platforms (it reports iOS *and* Android host results), so it
// can't sit in either half; excluding it keeps the partition honest without losing any signal.
val partitionMetaTaskNames =
    setOf("check", "verifyIos", "verifyAndroidCommon", "verifyCheckPartition", "allTests")

val verifyCheckPartition =
    tasks.register("verifyCheckPartition") {
        group = "verification"
        description =
            "Fail if `check` and verifyAndroidCommon ∪ verifyIos don't run the same task set."
    }

// Run the guard as part of the local gate (every subproject `check`, mirroring the other drift
// guards) AND as part of the Linux CI aggregate, so the partition is validated even though CI never
// invokes `check` directly. The guard only walks task metadata, so it runs fine on a Linux host.
verifyAndroidCommon { dependsOn(verifyCheckPartition) }

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyCheckPartition) }
}

// Compute both task closures once all projects (including AGP's afterEvaluate wiring) are evaluated
// so the comparison sees the final graph. Only task *paths* (Strings) are captured into the task
// action, keeping it configuration-cache-safe.
gradle.projectsEvaluated {
    // Walk the verification work `check` (or an aggregate) pulls in, collecting only action-bearing
    // leaf tasks. Two rules make this both meaningful and Gradle-9-safe:
    //   * Record only tasks that carry actions (real work). Pure lifecycle/aggregator tasks
    //     (`check`, `build`, `allTests`, the `ktfmtCheck` umbrella, the two aggregates) carry none,
    //     so the comparison lines up on real work no matter how each side aggregates it.
    //   * Expand (recurse into) ONLY no-action aggregators. Calling getDependencies() on an
    //     action-bearing task such as `testAndroidHostTest` resolves its runtime classpath, which
    //     needs the owning project's state lock and throws when walked from the root thread. Those
    //     tasks are leaves here anyway — we record them and stop — so we never trigger that
    //     resolution, and shared compile plumbing beneath them (which would cancel out regardless)
    //     simply never gets recorded.
    fun actionBearingClosure(roots: List<Task>): Set<String> {
        val visited = mutableSetOf<String>()
        val withActions = mutableSetOf<String>()
        val stack = ArrayDeque(roots)
        while (stack.isNotEmpty()) {
            val task = stack.removeLast()
            if (!visited.add(task.path)) continue
            if (task.actions.isNotEmpty()) {
                if (task.name !in partitionMetaTaskNames) withActions.add(task.path)
                continue
            }
            task.taskDependencies.getDependencies(task).forEach { stack.addLast(it) }
        }
        return withActions
    }

    // Resolve the aggregate roots to concrete Task objects via findByName (lock-safe cross-project
    // at this phase, unlike walking the aggregate tasks' lazily-resolved string-path dependencies).
    fun resolveRoots(pairs: List<Pair<String, String>>) = pairs.mapNotNull { (p, t) ->
        rootProject.project(p).tasks.findByName(t)
    }

    val checkClosure = actionBearingClosure(allprojects.mapNotNull { it.tasks.findByName("check") })
    val verifyClosure =
        actionBearingClosure(resolveRoots(iosVerification + androidCommonVerification))

    val onlyInCheck = (checkClosure - verifyClosure).toSortedSet()
    val onlyInVerify = (verifyClosure - checkClosure).toSortedSet()
    val closureSize = checkClosure.size

    verifyCheckPartition.configure {
        doLast {
            check(onlyInCheck.isEmpty() && onlyInVerify.isEmpty()) {
                buildString {
                    append("CI partition drift: `check` and verifyAndroidCommon ∪ verifyIos must ")
                    append("run the same action-bearing tasks, but they diverge.\n")
                    if (onlyInCheck.isNotEmpty()) {
                        append("  Run by `check` but no CI aggregate — CI would SKIP these:\n")
                        onlyInCheck.forEach { append("    - $it\n") }
                        append("  Fix: add each to verifyAndroidCommon or verifyIos.\n")
                    }
                    if (onlyInVerify.isNotEmpty()) {
                        append("  Run by a CI aggregate but not `check` — CI does extra work:\n")
                        onlyInVerify.forEach { append("    - $it\n") }
                        append("  Fix: remove it from the aggregate, or wire it into `check`.\n")
                    }
                }
            }
            logger.lifecycle(
                "CI partition OK: verifyAndroidCommon ∪ verifyIos covers exactly the $closureSize " +
                    "action-bearing tasks `check` runs."
            )
        }
    }
}
