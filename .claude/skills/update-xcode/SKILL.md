---
name: update-xcode
description: 'Upgrade the pinned Xcode (typically a 27.x beta) end-to-end and re-sync everything in this repo that hangs off its absolute path: install the new version via the `xcodes` CLI, accept the license, update every hardcoded `/Applications/Xcode-*.app` reference (.claude/CLAUDE.md, ios-device-debug, settings.local.json), re-register the local `xcode` MCP server (mcpbridge), re-export the six vendored Apple Agent Skills with PROVENANCE.md preservation, and run the post-upgrade checks (xcode-select, simulator runtimes, Swift lint gates). Use when the user says "update Xcode", "upgrade Xcode", "a new Xcode beta is out", "move to Beta N", "re-export the vendored Xcode skills", or after any Xcode install when the xcode MCP server fails to connect. Takes an optional target version; with none, targets the latest prerelease.'
argument-hint: '[target version, e.g. "27.0 Beta 4" or "26.1"; omit for the latest prerelease]'
allowed-tools: Bash, Read, Edit, Write, Grep, Glob
version: '1.0.0'
---

# Update Xcode & re-sync the repo (astro-mobile)

One Xcode upgrade touches five surfaces in this repo, because the setup pins an
**absolute, versioned app path** (`/Applications/Xcode-27.0.0-Beta.N.app` — the
`xcodes` CLI's naming convention). Missing any one of them leaves a silently
broken surface (a dead MCP server, stale docs, drifted vendored skills). Work
through the phases **in order** — later phases need the new Xcode installed,
licensed, and running.

The five surfaces:

1. the Xcode install itself (`xcodes`),
2. hardcoded path references (`.claude/CLAUDE.md`, `ios-device-debug/SKILL.md`,
   `.claude/settings.local.json`),
3. the local-scope `xcode` MCP server (mcpbridge),
4. the six vendored Apple Agent Skills + their `PROVENANCE.md` files,
5. post-upgrade toolchain checks.

## Phase 0 — establish old & target versions

```bash
xcodes installed                       # what's on disk + which is Selected
xcode-select -p                        # the active developer dir
grep -rn "Xcode-" .claude/CLAUDE.md .claude/skills/ios-device-debug/SKILL.md .claude/settings.local.json
```

The grep tells you the **old pinned path** (call it `$OLD_APP`). The target is
the skill argument, or the latest prerelease if none was given (`xcodes update`
then `xcodes list | tail` to see it). If the target is already installed *and*
all greps already show its path, there is nothing to do — say so and stop.

## Phase 1 — install via `xcodes`

```bash
xcodes install --latest-prerelease --select     # or: xcodes install "27.0 Beta 4" --select
```

- First run may prompt for an **Apple ID login and/or sudo password —
  interactive**, which this harness can't answer. If the command stalls or asks
  for input, have the user run it themselves via the `!` prefix:
  `! xcodes install --latest-prerelease --select`
- `aria2` (if on PATH) is auto-used and downloads 3–5x faster; the download is
  ~40 GB either way — run it in the background and continue only when done.
- `--select` makes the new version active (`xcode-select`) after install.
- The app lands at `/Applications/Xcode-<version>.app` (call it `$NEW_APP`,
  and `$DEV = $NEW_APP/Contents/Developer`).

**Accept the license** — needs sudo, so the user runs it in-session:

```
! sudo $NEW_APP/Contents/Developer/usr/bin/xcodebuild -license accept
```

Verify before continuing (phases 3–4 hard-fail on an unaccepted license):

```bash
DEVELOPER_DIR=$DEV xcodebuild -version    # prints version + build, e.g. "27.0 / 27A5218g"
```

Record the exact **marketing version + build** (e.g. `27.0 Beta 3 (27A5218g)`;
`xcodes installed` shows the beta label) — Phase 4 writes it into every
`PROVENANCE.md`.

## Phase 2 — update hardcoded path references

Replace `$OLD_APP` → `$NEW_APP` everywhere Phase 0's grep hit. The known set:

| File | What references the path |
| --- | --- |
| `.claude/CLAUDE.md` | the `DEV=` line in the mcpbridge setup snippet |
| `.claude/skills/ios-device-debug/SKILL.md` | `open -a`, `DEV=`, and `export DEVELOPER_DIR=` lines |
| `.claude/settings.local.json` | Bash permission-allowlist entries (untracked file — still update it, or the pre-approved patterns stop matching) |

```bash
sed -i '' "s|$OLD_APP|$NEW_APP|g" <each file the grep hit>
grep -rn "$OLD_APP" .claude/ && echo "STALE REFS REMAIN" || echo clean
```

Do **not** touch `PROVENANCE.md` files here — they record the version skills
were *exported from* (a historical fact) and are handled in Phase 4.

## Phase 3 — re-register the `xcode` MCP server

The server is **local-scope** (pins an absolute path — never in the committed
`.mcp.json`). Remove and re-add:

```bash
claude mcp remove xcode -s local
claude mcp add xcode -s local -e DEVELOPER_DIR=$DEV -- $DEV/usr/bin/mcpbridge
claude mcp get xcode
```

Expected health states:
- `Connected · tools fetch failed` = **healthy** when no project is open in the
  running Xcode (the tool service enumerates only with a project open) — not an
  error.
- `Failed to connect` = the binary path is wrong or the license is unaccepted —
  fix before proceeding.

## Phase 4 — re-export the vendored Apple Agent Skills

The six vendored skills (`device-interaction`, `swiftui-specialist`,
`swiftui-whats-new-27`, `uikit-app-modernization`, `modernize-tests`,
`audit-xcode-security-settings`) are served **live** by the running Xcode — the
export needs the **new** Xcode running:

```bash
pgrep -fl "$(basename $NEW_APP)" || open -a "$NEW_APP"
```

1. **Back up the hand-authored `PROVENANCE.md` files first** — the export's
   `--replace-existing` deletes them:
   ```bash
   mkdir -p <scratchpad>/provenance-backup
   for d in .claude/skills/*/PROVENANCE.md; do
     cp "$d" "<scratchpad>/provenance-backup/$(basename $(dirname $d)).md"
   done
   ```
2. **Export — with an ABSOLUTE output path** (a relative `--output-dir`
   resolves against `/`, not the cwd, and fails on the read-only root volume):
   ```bash
   DEVELOPER_DIR=$DEV xcrun agent skills export \
     --output-dir "$(pwd)/.claude/skills" --replace-existing
   ```
3. **Delete the excluded skill.** Apple exports seven; this repo deliberately
   does not vendor the C bounds-safety one (no C surface):
   `rm -rf .claude/skills/adopt-c-bounds-safety` (named `c-bounds-safety`
   before Xcode 27 Beta 3 — check the export output for the current name).
4. **Compare the exported set against the expected six.** A new, renamed, or
   removed skill is a decision for the user — surface it and update the
   `.claude/CLAUDE.md` skill tables accordingly; don't silently vendor a new
   one.
5. **Restore each `PROVENANCE.md`** from the backup, updating the two version
   lines (`bundled in Xcode <old>` → new, and the `**Xcode version at
   export:**` line with the new version/build + today's date). Leave
   historically-verified facts (e.g. "verified on <old build>") intact.
6. **Re-check the `DeviceEventSynthesize` upstream bug** (see
   `device-interaction/PROVENANCE.md`): Apple's exported `SKILL.md` has
   historically mis-named the real bridge tool `DeviceInteractionSynthesize`:
   ```bash
   grep -n "DeviceEventSynthesize" .claude/skills/device-interaction/SKILL.md
   ```
   Still present → update the provenance status line (persists as of this
   export). Gone → Apple fixed it: drop the discrepancy section from the
   provenance **and** the workaround note in `ios-device-debug/SKILL.md`
   step 6. If a DeviceHub session is live, confirm the real name against the
   bridge's `tools/list`.
7. **Vetting grep** (per the convention in every provenance file) — must come
   back clean:
   ```bash
   grep -rniE 'ktlint|spotless|detekt|@Suppress|swiftlint:disable' \
     .claude/skills/{device-interaction,swiftui-specialist,swiftui-whats-new-27,uikit-app-modernization,modernize-tests,audit-xcode-security-settings} \
     || echo CLEAN
   ```
8. **Review the content diff** (`git diff --stat .claude/skills/`) and note
   substantive upstream changes for the commit message / user summary.

## Phase 5 — post-upgrade checks

- **Selection:** `xcode-select -p` prints `$DEV`. If not:
  `xcodes select <version>` (or `sudo xcode-select -s $DEV`).
- **Simulator runtimes:** the new SDK may need a new iOS runtime.
  `DEVELOPER_DIR=$DEV xcrun simctl list runtimes` — if the matching iOS runtime
  is missing, install it (`xcodes runtimes` lists; `xcodes runtimes install
  "iOS <ver>"`, large download — confirm with the user first).
- **Swift gates still pass** — `swift format` ships **inside** Xcode, so a new
  toolchain can change formatting behavior:
  `./gradlew swiftFormatCheck swiftLintCheck`
- **Deployment-target floor:** a new major SDK can raise the minimum
  (Xcode 27 raised the iOS floor to 15.0 and older targets fail at config
  time — see `ios-device-debug/SKILL.md` → Common build failures). Surfaces on
  the first `BuildProject` / `verifyIos`; run `./gradlew verifyIos` if you want
  the full macOS-side gate now.
- **Old version cleanup:** each Xcode is ~40 GB on disk. `xcodes uninstall
  "<old version>"` — **ask the user first**; never auto-delete. (If the old
  app was already removed by hand, the dead MCP registration from Phase 3 was
  the symptom that led here.)

## Phase 6 — branch & commit

On `main`, branch first (`chore/xcode-<version>-upgrade`). Commit the path
updates + re-exported skills + provenance together, with a message that names
the new version/build and summarizes upstream skill changes (Phase 4 step 8).
`settings.local.json` is untracked — it changes but never commits.

## Boundaries

- **Interactive auth stays with the user** — Apple ID login, sudo password,
  and the license acceptance all go through `!`-prefixed commands the user
  runs; never work around them.
- **Never auto-delete an old Xcode or trigger a runtime download** without an
  explicit go-ahead — both are multi-GB, destructive-or-slow actions.
- The vendored skills are refreshed **verbatim** — upstream bugs (like the
  `DeviceEventSynthesize` naming) are documented in `PROVENANCE.md` and worked
  around in the files this repo owns, never patched in Apple's text.
- This skill owns the *upgrade + re-sync* loop only. Running the app on the
  new toolchain routes to `ios-device-debug`; day-to-day skill usage routes to
  the individual vendored skills.
