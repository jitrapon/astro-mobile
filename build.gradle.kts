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

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

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