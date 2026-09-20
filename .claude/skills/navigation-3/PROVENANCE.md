# Provenance & vetting — `navigation-3`

This skill is vendored **verbatim** from Google's official Android skills catalog. Do
not hand-edit `SKILL.md` or `references/`: refresh by re-running the install command
below so the copy never silently drifts from the upstream the CLI ships.

- **Upstream:** https://github.com/android/skills (`navigation-3`), author Google LLC.
- **Served by:** the Android CLI (`android skills …`), Homebrew formula `android-cli`.
- **Imported with:** `android skills add navigation-3 --agent=claude-code --project .`
- **Android CLI version at import:** `1.0.15433482`.
- **Skill `last-updated` at import:** `2026-09-10`.
- **Why vendored (vs. on-demand):** the app shell's back stack is built on Navigation 3,
  so this is not a skill a branch reaches for once — it documents the navigation library
  the Android app is now committed to, and the recipes it carries (multiple back stacks,
  scenes, deep links, results) are the roadmap for the screens still to be built. It is
  the second `android/*` skill to be committed, alongside `android-cli`; the remaining
  product-UI skills stay on-demand (see `.claude/CLAUDE.md` → "Agent skill routing &
  precedence").
- **Licence note:** the skill's own frontmatter reads `license: Complete terms in
  LICENSE.txt`, but the CLI's export does **not** include a `LICENSE.txt`. The upstream
  repository is the authority for the terms; re-check it before redistributing this
  directory outside the repo.

## Vetting against the locked conventions

Checked against the five conventions enumerated for every imported skill (see the
`kotlin-*` skills' VETTING blocks):

1. **ktfmt as the formatter** — checked: recommends no Kotlin formatter (ktlint/Spotless absent).
2. **No Detekt baseline / no `@Suppress` to silence findings** — checked. The convention grep
   returns two `@Suppress("UNCHECKED_CAST")` hits, in `migration-guide.md` and
   `recipes/bottomsheet.md`. Both annotate a generic cast in sample code and are Kotlin
   *compiler* warning suppressions; neither silences a Detekt finding, which is what the
   convention forbids. Left verbatim.
3. **`Result<T>` for error handling** — checked: no error-handling guidance.
4. **`io.jitrapon.astro` layer-based package layout** — checked: no package-layout guidance.
5. **UI stays out of `:shared`** — checked: the skill is Android-only and never suggests
   putting Compose in a shared module.

To refresh on a CLI upgrade: re-run the import command above, re-run the grep
`grep -rniE 'ktlint|spotless|detekt|@Suppress|compose multiplatform|shared.*compose.*ui'`
over this directory, and confirm any new hits are still benign by the reading in (2).
