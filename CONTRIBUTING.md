# Contributing to `astro-mobile`

This is a Kotlin Multiplatform Mobile (KMP) app — shared Kotlin business logic in `:shared`, with platform-native UIs (Jetpack Compose on Android, SwiftUI on iOS). See [`.claude/CLAUDE.md`](.claude/CLAUDE.md) for the full architecture, commands, conventions, and the spec-driven development workflow; this file is the practical contributor guide: local setup, the gate-before-push expectation, the git hooks, and a conventions recap a reviewer checks on every PR.

## Gate before you push

The single authoritative gate is:

```bash
./gradlew check
```

It aggregates compile + unit tests + Detekt + ktfmt verification across `:shared` and `:androidApp`, plus swift-format and SwiftLint wherever the Swift toolchain resolves on PATH. **Run it before opening a PR** — CI runs the same gate, so a green local `check` is the fastest path to a green PR. The shared/iOS tests run separately:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

> The KMP test runner does not reliably auto-discover newly-added test files on incremental builds. When you add new test sources, run `./gradlew clean check` for a trustworthy result.

## Local setup

Requires **JDK 17**. For the iOS app and Swift lint tooling you also need **Xcode** (macOS). Optional CLI tools the gate self-skips when absent: **SwiftLint** (`brew install swiftlint`) and, for the on-demand `peripheryScan`, **Periphery** (`brew install periphery`).

After cloning, install the git hooks once:

```bash
./gradlew installGitHooks
```

This sets `core.hooksPath=.githooks` and resolves the standalone ktfmt/Detekt fat jars (recorded in a gitignored `.gradle/lint-tools.properties`). The hooks are a fast local pre-flight — **`./gradlew check` remains the authoritative gate** whether or not they are installed.

### What the hooks enforce

- **`pre-commit`** runs ktfmt + Detekt on staged Kotlin via the fast CLI path (not the Gradle daemon). It aborts on partially-staged Kotlin, fails with a "rerun `resolveLintTools`" message when the resolved jar versions drift from `gradle/libs.versions.toml`, and runs `checkNoDetektBaseline` when a `detekt-baseline.xml` is staged. It also runs swift-format and SwiftLint on staged `.swift` files, each self-skipping if its binary isn't on PATH.
- **`pre-push`** runs the security scanners over the commits the push adds — betterleaks (secret scan) and semgrep (the `.semgrep/astro-mobile.yml` ruleset) — each warn-and-skipping independently when its CLI is absent or below the version floor.

### Escape hatch

Both hooks honor `--no-verify`:

```bash
git commit --no-verify
git push --no-verify
```

Use it sparingly — CI's `./gradlew check` and the security workflow still enforce the same checks on the PR, so bypassing locally only defers the failure.

## Android CLI & skills

Android development tasks (project/emulator/build/run/screenshots, plus the official Google Android skill catalog) go through the **Android CLI**, not ad-hoc Gradle invocations. Install it on macOS:

```bash
brew install android-cli
# or: curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash
android --version
android update          # self-update
```

The official [`github.com/android/skills`](https://github.com/android/skills) catalog is the registered vetted skill source — `android skills list` enumerates it and `android skills find <keyword>` searches it; no extra registration step is needed beyond installing the CLI. Stack-relevant skills (`adaptive`, `navigation-3`, `edge-to-edge`, `styles`, `testing-setup`) are served **on-demand** so they stay current with the CLI; fetch one when a task needs it:

```bash
android skills add <name> --agent=claude-code --project .
```

> Always pass `--agent=claude-code` — omitting it also writes a stray top-level `skills/` copy.

See the **"Agent skill routing & precedence"** section in `.claude/CLAUDE.md` for which skill family owns which work domain (official `android/*` for Android UI/platform/tooling; the vendored `kotlin-*` skills for `:shared`/KMP architecture; `kotlin-coroutines-skill` for async) and the not-applicable list.

## Conventions recap

The full convention set lives in [`.claude/CLAUDE.md`](.claude/CLAUDE.md); the load-bearing rules a reviewer checks:

- **Keep business logic in `commonMain`.** Use `expect`/`actual` for platform abstractions — never runtime `if (isAndroid)` branching. **UI stays out of `:shared`**: Compose in `androidApp`, SwiftUI in `iosApp`.
- **Error handling uses the sealed `Result<T>`** (`Success<T>` / `Error`), not thrown exceptions across boundaries.
- **No Detekt baseline and no `@Suppress` to silence findings** — refactor instead. Both are enforced mechanically (`checkNoDetektBaseline` + Detekt's `ForbiddenSuppress`). Likewise don't blanket-disable SwiftLint rules; fix the source.
- **Format before committing**: `./gradlew ktfmtFormat` (Kotlin), `./gradlew swiftFormatApply` (Swift). ktfmt/swift-format own formatting; Detekt/SwiftLint are correctness/style only.
- **Names communicate role + intent** — lead methods with an action verb naming *what* they do; name types for the specific artifact, not a generic noun. Base package is `io.jitrapon.astro`, organized by layer. Comments are self-contained (name the invariant/contract, not a branch/SPEC reference).
- **Pin CI action references to commit SHAs.**
