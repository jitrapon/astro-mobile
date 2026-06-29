# Provenance & vetting — `android-cli`

This skill is vendored **verbatim** from Google's official Android skills catalog. Do
not hand-edit `SKILL.md` or `references/`: refresh by re-running the install command
below so the copy never silently drifts from the upstream the CLI ships.

- **Upstream:** https://github.com/android/skills (`android-cli`), author Google LLC.
- **Served by:** the Android CLI (`android skills …`), Homebrew formula `android-cli`.
- **Imported with:** `android skills add android-cli --agent=claude-code --project .`
- **Android CLI version at import:** `1.0.15433482`.
- **Why vendored (vs. on-demand):** this is the entry-point skill — it documents how an
  agent drives the `android` CLI (project create/run, deploy, emulator, screenshots,
  `docs` knowledge-base search, and `android skills` itself). It is small, stable, and
  used on every branch, so it is committed; the product-UI android skills are served
  on-demand instead (see `.claude/CLAUDE.md` → "Agent skill routing & precedence").

## Vetting against the locked conventions

Checked against the five conventions enumerated for every imported skill (see the
`kotlin-*` skills' VETTING blocks). This skill is a CLI-usage guide and touches none of
them, so it landed verbatim with no scrub:

1. **ktfmt as the formatter** — checked: recommends no Kotlin formatter (ktlint/Spotless absent).
2. **No Detekt baseline / no `@Suppress` to silence findings** — checked: no suppression or baseline guidance.
3. **`Result<T>` for error handling** — checked: no error-handling guidance.
4. **`io.jitrapon.astro` layer-based package layout** — checked: no package-layout guidance.
5. **UI stays out of `:shared`** — checked: no shared-code / UI-placement guidance.

To refresh on a CLI upgrade: re-run the import command above and re-confirm the grep
`grep -rniE 'ktlint|spotless|detekt|@Suppress|compose multiplatform|shared.*compose.*ui'`
over this directory stays clean.
