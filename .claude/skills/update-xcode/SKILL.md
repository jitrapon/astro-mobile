---
name: update-xcode
description: 'Upgrade the pinned Xcode (typically a 27.x beta) end-to-end and re-sync everything in this repo that hangs off its absolute path: install the new version via the `xcodes` CLI, accept the license, update every hardcoded `/Applications/Xcode-*.app` reference (.claude/CLAUDE.md, ios-device-debug, settings.local.json), re-register the local `xcode` MCP server (mcpbridge), re-export the eight vendored Apple Agent Skills with PROVENANCE.md preservation, run the post-upgrade checks (xcode-select, simulator runtimes, Swift lint gates), and re-pin CI''s Xcode (the `verify-ios` runner label + `DEVELOPER_DIR` in .github/workflows/ci.yml). Use when the user says "update Xcode", "upgrade Xcode", "a new Xcode beta is out", "move to Beta N", "re-export the vendored Xcode skills", or after any Xcode install when the xcode MCP server fails to connect. Takes an optional target version; with none, targets the latest prerelease.'
argument-hint: '[target version, e.g. "27.0 Beta 4" or "26.1"; omit for the latest prerelease]'
allowed-tools: Bash, Read, Edit, Write, Grep, Glob
version: '1.1.0'
---

# Update Xcode & re-sync the repo (astro-mobile)

One Xcode upgrade touches six surfaces in this repo, because the setup pins an
**absolute, versioned app path** (`/Applications/Xcode-27.0.0-Beta.N.app` — the
`xcodes` CLI's naming convention). Missing any one of them leaves a silently
broken surface (a dead MCP server, stale docs, drifted vendored skills, CI
checking with a different toolchain than developers build with). Work
through the phases **in order** — later phases need the new Xcode installed,
licensed, and running.

The six surfaces:

1. the Xcode install itself (`xcodes`),
2. hardcoded path references (`.claude/CLAUDE.md`, `ios-device-debug/SKILL.md`,
   `.claude/settings.local.json`),
3. the local-scope `xcode` MCP server (mcpbridge),
4. the eight vendored Apple Agent Skills + their `PROVENANCE.md` files,
5. post-upgrade toolchain checks,
6. CI's Xcode pin (the `verify-ios` job in `.github/workflows/ci.yml`).

## Phase 0 — establish old & target versions

```bash
xcodes installed                       # what's on disk + which is Selected
xcode-select -p                        # the active developer dir
grep -rn "Xcode-" .claude/CLAUDE.md .claude/skills/ios-device-debug/SKILL.md .claude/settings.local.json
grep -n "runs-on: \|DEVELOPER_DIR:" .github/workflows/ci.yml   # CI's label + pin
```

The first grep tells you the **old pinned path** (call it `$OLD_APP`); the
second, CI's runner label and pinned Xcode. The target is
the skill argument, or the latest prerelease if none was given (`xcodes update`
then `xcodes list | tail` to see it). If the target is already installed *and*
all greps already show its path (CI's at the target's major.minor), there is
nothing to do — say so and stop.

**Side-by-side install** — the user wants the new version on disk but keeps the
current one active: run Phase 1 **without** `--select`, skip its
`sudo xcode-select -s`, and stop there. The pin has not moved, so Phases 2–6 do
not apply — paths, MCP server, vendored skills and CI all stay on the selected
version. Two effects of the new install reach the version still in use:

- The first tool run from the new developer dir — even
  `DEVELOPER_DIR=$DEV xcrun simctl list runtimes` — installs its system
  components unprompted and upgrades + restarts the **shared** CoreSimulator
  service every installed Xcode uses. Check nothing is booted first
  (`xcrun simctl list devices | grep -c "(Booted)"` → 0).
- That same auto-install can leave nothing for sudo to do:
  `DEVELOPER_DIR=$DEV xcodebuild -checkFirstLaunchStatus` exiting 0, plus
  `simctl` exiting 0 rather than 69, means first launch and the license are
  both done.

## Phase 1 — install via `xcodes`

```bash
xcodes install --latest-prerelease --select     # or: xcodes install "27.0 Beta 4" --select
```

- First run may prompt for an **Apple ID login and/or sudo password —
  interactive**, which this harness can't answer. If the command *genuinely*
  stalls, have the user run it themselves via the `!` prefix:
  `! xcodes install --latest-prerelease --select`
- **Silence is not a stall — never judge progress from the log.** `xcodes` prints
  progress with a plain `print`, and Swift's stdout is line-buffered on a TTY but
  **fully buffered on a pipe**. Backgrounding the command, or piping it to `tee`
  or a file, flips it to full buffering — so the whole download and unarchive sit
  in the buffer and flush only at exit, and a log still 0 bytes after several
  minutes is the *normal* appearance of a healthy 40 GB download. Judge progress
  from the **artifact on disk** (`ls -d /Applications/Xcode-*.app`), not from the
  log, and not from whether `aria2c` shows up in `pgrep` — `xcodes` only shells
  out to it for some transfers, so its absence proves nothing. Killing a
  "stalled" install throws away a download that was nearly finished.
- **A sudo failure at the end does not undo the download.** With no password
  available, `xcodes` still completes steps 1–5 — download, unarchive, move to
  `/Applications`, trash the `.xip`, verify code signing — and fails only at
  `(6/6) Finishing installation` with "xcodes requires superuser privileges".
  The app is already in place and correct at that point: do **not** re-run the
  install, just finish it with the sudo commands below.
- `aria2` (if on PATH) is auto-used and downloads 3–5x faster; the download is
  ~40 GB either way — run it in the background and continue only when done.
- `--select` makes the new version active (`xcode-select`) after install.
- The app lands at `/Applications/Xcode-<version>.app` (call it `$NEW_APP`,
  and `$DEV = $NEW_APP/Contents/Developer`).

**Finish the install and accept the license** — all need sudo, so the user runs them in-session:

**`sudo` cannot run through the `!` prefix** — it needs a controlling TTY and
fails with "a terminal is required to read the password". Have the user run
these in a real **Terminal.app** window, and **in this order** — acceptance is
recorded against the *selected* developer dir, so selecting first is what makes
it stick:

```
sudo xcode-select -s $NEW_APP/Contents/Developer
sudo xcodebuild -license accept
sudo xcodebuild -runFirstLaunch
```

`-runFirstLaunch` is the additional-component install that `xcodes`' own
`(6/6) Finishing installation` step performs; when that step died on the missing
password, this is what completes it.

If `-license accept` still refuses, `sudo xcodebuild -license` opens the
interactive agreement (page to the end, type `agree`).

Verify before continuing (phases 3–4 hard-fail on an unaccepted license) — and
verify with a command that actually **consults** the license, not just
`xcodebuild -version`, which prints the version fine on an unaccepted install:

```bash
xcode-select -p                            # must print $NEW_APP/Contents/Developer
DEVELOPER_DIR=$DEV xcrun simctl list runtimes | head -3   # exit 69 = license still unaccepted
DEVELOPER_DIR=$DEV xcodebuild -version     # version + build, e.g. "27.0 / 27A5237l"
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
were *exported from* (a historical fact) and are handled in Phase 4. CI's pin
is not in this set either: runner images name Xcode differently
(`Xcode_27.0.app`, not `Xcode-27.0.0.app`), so this sed never matches it — it
moves in Phase 6.

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

The eight vendored skills (`device-interaction`, `swiftui-specialist`,
`swiftui-whats-new-27`, `uikit-app-modernization`, `modernize-tests`,
`app-intents-specialist`, `app-intents-whats-new-27`,
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
3. **Delete the excluded skills.** Apple exports ten as of Beta 5; this repo
   deliberately vendors neither the C bounds-safety one (no C surface) nor the
   document-based one (no document surface) — check the export output for the
   current names, both have been renamed before:
   ```bash
   rm -rf .claude/skills/adopt-c-bounds-safety \
          .claude/skills/building-document-based-swiftui-applications
   ```
4. **Compare the exported set against the expected eight.** A new, renamed, or
   removed skill is a decision for the user — surface it and update the
   `.claude/CLAUDE.md` skill tables accordingly; don't silently vendor a new
   one. Apple also moves content *between* skills across betas (Beta 5 moved
   `swiftui-whats-new-27`'s `references/document-based-apps.md` into the new
   document-based skill), so a reference vanishing is not always a deletion —
   check whether it reappeared elsewhere in the export before reporting it.
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
- **Old version cleanup:** an installed Xcode 27 bundle measures ~3.6 GB
  (`du -sh /Applications/Xcode-*.app`) — far less than its ~40 GB download,
  since simulator runtimes live outside the bundle and are shared across
  versions. `xcodes uninstall "<old version>"` — **ask the user first**; never
  auto-delete. (If the old app was already removed by hand, the dead MCP
  registration from Phase 3 was the symptom that led here.) Note `uninstall`
  **moves the app to the Trash rather than deleting it**, so the space is not
  reclaimed until the Trash is emptied. Quit the old version's leftover helper
  processes first (`XcodeService`, `DeviceHub`, `mcpbridge`) or they keep files
  open — but scope the `pkill` pattern to the old app path, since an
  unscoped `mcpbridge` match kills the bridge the *current* session is attached
  to and drops the `mcp__xcode__*` toolset until the session restarts.
  Emptying the Trash from this harness is usually blocked: macOS TCC denies
  listing `~/.Trash` ("Operation not permitted") while still allowing `stat` on
  a known path inside it, so a wildcard delete cannot be reviewed first —
  delete the old bundle by its exact name, or let the user empty the Trash in
  Finder.

## Phase 6 — re-pin CI's Xcode

The `verify-ios` job in `.github/workflows/ci.yml` pins Xcode with a job-level
`DEVELOPER_DIR` on a runner label whose image carries that version, so CI
formats and compiles with the toolchain developers use (swift-format ships
inside Xcode). Move it **only because the local pin moved** in Phase 2 — never
for a side-by-side install.

Pin GitHub's `Xcode_<major>.<minor>.app` alias, never an exact
`Xcode_<major>.<minor>.<patch>.app` path: an image keeps one patch per minor
and the alias tracks the newest, so an exact path vanishes on the next image
rollout and turns every PR red.

1. **Find an image that carries the target.** Each label's installed Xcodes
   are listed in its runner-images readme:
   ```bash
   gh api repos/actions/runner-images/contents/images/macos --jq '.[].name' | grep Readme
   gh api repos/actions/runner-images/contents/images/macos/<image>-Readme.md \
     --jq .content | base64 -d | grep -A12 '^### Xcode'
   ```
   `xcode-27` → `xcode-27-arm64-Readme.md`, `macos-26` →
   `macos-26-arm64-Readme.md`; the runner-images `README.md` image table maps
   every label. A new major has so far arrived first under a **preview** label
   (`xcode-<major>`) before a GA `macos-<N>` image carries it — tell the user
   (GitHub warns of queueing delays and instability on previews), and once a
   GA label carries the pinned version, move off the preview.
2. **The image lacks the target** (a beta not rolled out yet, or a version
   dropped) → do **not** point `DEVELOPER_DIR` at a path the image lacks — the
   job's `Verify pinned Xcode` step would fail every run. Tell the user and
   leave CI on its current pin until an image carries it.
3. **Edit `ci.yml`:** `DEVELOPER_DIR`, `runs-on:` if the label changes, and the
   version named in the job's comment. Then update `.claude/CLAUDE.md`'s CI
   section — the `verify-ios` bullet and the "pinned, not inherited" paragraph.
4. **After pushing, confirm the pin took** from the PR's run — the
   `Verify pinned Xcode` step prints the version and build:
   ```bash
   gh run view <run-id> --log | grep -E "verify-ios.*(Xcode [0-9]|Build version)"
   ```

## Phase 7 — branch & commit

On `main`, branch first (`chore/xcode-<version>-upgrade`). Commit the path
updates + re-exported skills + provenance + CI pin together, with a message that
names the new version/build and summarizes upstream skill changes (Phase 4
step 8). `settings.local.json` is untracked — it changes but never commits.

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
