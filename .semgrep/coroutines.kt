// Fixtures for .semgrep/coroutines.yml. Never compiled: this file is outside the Gradle source
// sets, and `**/.semgrep/**` keeps the rule from scanning it in a directory scan.
//
//   semgrep --test --config .semgrep/coroutines.yml .semgrep/coroutines.kt
//
// .githooks/pre-commit feeds every staged *.kt to the ktfmt and detekt CLIs by extension alone, so
// this file must satisfy both even though no Gradle task looks at it. Hence: no package declaration
// (InvalidPackageDeclaration), top-level functions only (so no class-scoped structured-coroutines
// rule such as HardcodedDispatcherInClass fires), and only literals detekt's defaults tolerate.

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Top-level suspend fun: detekt's HardcodedDispatcherInClass does NOT catch this (it only fires
// inside a class). This is the gap kotlin-hardcoded-dispatcher fills.
suspend fun topLevelHardcodedDispatcher() {
    // ruleid: kotlin-hardcoded-dispatcher
    withContext(Dispatchers.IO) { println("blocking bridge") }
}

// Builder-argument form, also outside HardcodedDispatcherInClass's reach.
fun builderArgumentDispatcher(scope: CoroutineScope) {
    // ruleid: kotlin-hardcoded-dispatcher
    scope.launch(Dispatchers.Default) { println("work") }
}

fun returnsUnconfined(): CoroutineDispatcher {
    // ruleid: kotlin-hardcoded-dispatcher
    return Dispatchers.Unconfined
}

// Injected dispatcher — the sanctioned shape the rule must leave alone.
suspend fun takesInjectedDispatcher(io: CoroutineDispatcher) {
    // ok: kotlin-hardcoded-dispatcher
    withContext(io) { println("blocking bridge") }
}
