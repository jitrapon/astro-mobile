import java.io.File
import javax.inject.Inject
import org.gradle.process.ExecOperations

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
        classpath("com.android.tools.build:gradle:9.2.1")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.10")
    }
}

plugins {
    // ktfmt on the ROOT project itself, not just :shared/:androidApp. Those subprojects only format
    // their own build scripts, leaving the root build.gradle.kts / settings.gradle.kts unchecked by
    // `./gradlew check` (the pre-commit hook's CLI was the only thing catching them). Applying the
    // plugin here registers `ktfmtCheckScripts` over the root `*.gradle.kts`; it is wired into the
    // gate and the CI partition below.
    alias(libs.plugins.ktfmt)
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

dependencies {
    // `with-dependencies` (ktfmt) and `all` (detekt-cli) are the self-contained fat-jar
    // classifiers.
    ktfmtTool("com.facebook:ktfmt:${libs.versions.ktfmt.cli.get()}:with-dependencies")
    detektTool("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}:all")
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
    val ktfmtVersion = libs.versions.ktfmt.cli.get()
    val detektVersion = libs.versions.detekt.get()

    inputs.files(ktfmtJars, detektJars)
    inputs.property("ktfmtVersion", ktfmtVersion)
    inputs.property("detektVersion", detektVersion)
    outputs.file(toolsFile)

    doLast {
        val ktfmtJar =
            ktfmtJars.singleFile.takeIf { it.name.endsWith("-with-dependencies.jar") }
                ?: error("ktfmt CLI jar not resolved; got=${ktfmtJars.files.map { it.name }}")
        val detektJar =
            detektJars.singleFile.takeIf { it.name.endsWith("-all.jar") }
                ?: error("detekt-cli jar not resolved; got=${detektJars.files.map { it.name }}")
        val out = toolsFile.asFile
        out.parentFile.mkdirs()
        out.writeText(
            """
            ktfmt.jar=${ktfmtJar.absolutePath.replace("\\", "/")}
            ktfmt.version=$ktfmtVersion
            detekt.jar=${detektJar.absolutePath.replace("\\", "/")}
            detekt.version=$detektVersion

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
