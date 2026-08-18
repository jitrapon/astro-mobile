# Provenance & vetting — `modernize-tests`

Vendored **verbatim** from Apple's first-party Agent Skills bundled in Xcode 27. Do
not hand-edit `SKILL.md` or `references/`: refresh by re-exporting from Xcode so the
copy never silently drifts from the upstream Apple ships.

- **Upstream:** Apple, bundled in Xcode 27.0 Beta 5 (27A5237l). Served by the `agent` CLI at
  `Xcode.app/Contents/Developer/usr/bin/agent` (a.k.a. `xcrun agent` / `xcrun mcpbridge`).
- **Exported with:** `xcrun agent skills export --output-dir <dir> --replace-existing`
  (requires a running Xcode 27 — skills are served live, not stored as static files in the app bundle).
- **Xcode version at export:** `27.0 Beta 5 (27A5237l)`. Imported 2026-08-18.
- **Portability:** Knowledge skill for migrating `iosApp` tests (XCTest → Swift Testing). Portable; runs anywhere.

## Vetting against the locked conventions

Apple's iOS/SwiftUI skills; checked against the five conventions enumerated for every
imported skill (see the `kotlin-*` skills' VETTING blocks). None apply — these skills
touch only Swift/Xcode surfaces in `iosApp/`, never `:shared` Kotlin:

1. **ktfmt as the formatter** — checked: no Kotlin formatter guidance (Swift-only).
2. **No Detekt baseline / no `@Suppress`** — checked: no Kotlin-suppression or baseline guidance; scan for `swiftlint:disable`/`swift-format` overrides came back clean.
3. **`Result<T>` for error handling** — checked: no Kotlin error-handling guidance.
4. **`io.jitrapon.astro` package layout** — checked: no Kotlin package-layout guidance.
5. **UI stays out of `:shared`** — checked: SwiftUI/UIKit guidance is inherently `iosApp`-scoped.

To refresh on an Xcode upgrade: re-run the export above into `.claude/skills/` and
re-confirm the grep `grep -rniE 'ktlint|spotless|detekt|@Suppress|swiftlint:disable'`
over this directory stays clean.
