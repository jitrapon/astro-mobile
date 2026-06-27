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
