---
name: ios-device-debug
description: 'Build, install, launch, and visually verify the iOS app (scheme "iosApp") end-to-end on a real device or simulator via Xcode 27''s DeviceHub — the mobile analog of astro-web''s browser-debug and astro-calendar-service''s runtime-debug. Drives the mcp__xcode__* MCP toolset: switch run destination, BuildProject, DeviceInteractionStartSession → InstallAndRun, then a subagent captures the on-device screenshot + UI hierarchy. Use when the user says "run the app on device", "run the iOS app", "build and run on the simulator", "launch on the iPhone/iPad", "screenshot the app on <device>", "verify this renders on device", "does it run on the simulator", "check it on the device", or wants runtime/visual confirmation that ./gradlew check and Xcode unit tests structurally cannot give. Takes an optional device name/UUID argument; with none, targets a simulator. iOS/Xcode only — Android runtime work routes to android-cli.'
argument-hint: '[device name/UUID, e.g. "iPhone 17 Pro" or a physical device name; omit to use a simulator]'
allowed-tools: Bash, Read, Agent, ToolSearch, mcp__xcode__XcodeListWindows, mcp__xcode__XcodeListSchemes, mcp__xcode__XcodeListRunDestinations, mcp__xcode__XcodeSwitchRunDestination, mcp__xcode__BuildProject, mcp__xcode__GetBuildLog, mcp__xcode__XcodeListNavigatorIssues, mcp__xcode__DeviceInteractionStartSession, mcp__xcode__DeviceInteractionInstallAndRun, mcp__xcode__DeviceInteractionSynthesize, mcp__xcode__DeviceInteractionEndSession, mcp__xcode__GetConsoleOutput
version: '1.0.0'
---

# iOS device/simulator debug loop (astro-mobile)

Drive the **running** iOS app on a real device or simulator over Xcode 27's
DeviceHub and *see* it: build → install → launch → capture a real screenshot +
UI hierarchy, in an iterative run → observe → fix → re-run loop. This is the
runtime/visual layer on top of the static gate — it does **not** replace
`./gradlew check` or the Xcode unit tests, which stay authoritative for source
correctness. It is additive: it catches the class of defect a green test can't
see (does the screen actually render, is anything overlapping/truncated, does
the app launch at all against the real SDK).

Siblings in the same family: **`browser-debug`** (astro-web, real Chrome) and
**`runtime-debug`** (astro-calendar-service, live gRPC + Postgres). This is the
mobile surface of that trio.

## Why this exists (the emulated-test gap)

`./gradlew :shared:iosSimulatorArm64Test` exercises shared Kotlin logic, and
Xcode unit tests exercise Swift units — but neither installs the built app on a
device and renders SwiftUI. So a green suite says "the units behave," not "the
app launches against the iOS 27 SDK and its login screen renders without
overlap." A real device/simulator does. It also surfaces **config-level** build
blockers that only appear against the real toolchain (e.g. an invalid
deployment-target floor — see Common build failures).

## Prerequisites (verify before the loop)

1. **Xcode 27 is running with the project open.** The bridge only enumerates
   tools once a project/workspace is open in the running Xcode; with none open,
   the MCP health check reads `Connected · tools fetch failed` (a `tools/list`
   timeout). Open it if needed:
   `open -a "/Applications/Xcode-27.0.0-Beta.3.app" iosApp/iosApp.xcodeproj`
   (adjust the Xcode path to yours). Confirm with `XcodeListWindows` → note the
   returned **`tabIdentifier`** (e.g. `windowtab1`); every `mcp__xcode__*` call
   needs it.
2. **The `xcode` MCP server (mcpbridge) is registered and connected.** It is a
   per-machine, **local-scope** server (it pins an absolute Xcode path, so it is
   intentionally *not* in the committed `.mcp.json`). If `mcp__xcode__*` tools
   are absent, register it once:
   ```bash
   DEV=/Applications/Xcode-27.0.0-Beta.3.app/Contents/Developer   # adjust to your Xcode
   claude mcp add xcode -s local -e DEVELOPER_DIR=$DEV -- $DEV/usr/bin/mcpbridge
   ```
   (Full rationale in `.claude/CLAUDE.md` → "Apple Xcode Agent Skills".)
3. **Load the tools.** If the `mcp__xcode__*` tools are deferred, load them in one
   `ToolSearch` `select:` call:
   `select:mcp__xcode__XcodeListWindows,mcp__xcode__XcodeListRunDestinations,mcp__xcode__XcodeSwitchRunDestination,mcp__xcode__BuildProject,mcp__xcode__DeviceInteractionStartSession,mcp__xcode__DeviceInteractionInstallAndRun,mcp__xcode__DeviceInteractionEndSession`
   (add `GetBuildLog`, `XcodeListNavigatorIssues`, `GetConsoleOutput` when
   debugging a failure).

## Device selection & fallback chain (the core logic)

The skill takes an **optional device argument** (name or UUID). Resolve the
target and a fallback order like this:

1. **Enumerate.** `XcodeListRunDestinations({tabIdentifier})` → the eligible
   build/run destinations, grouped `Devices` (physical) and `Simulators`, each
   with a `displayTitle`, `isSimulator`, `osVersion`, `isEligible`.
2. **Pick the primary target:**
   - **Arg given** → match it against destination `name`/UUID/`displayTitle`
     (case-insensitive, best-effort). That is the primary.
   - **No arg** → primary is a **simulator**, preferring the newest iOS runtime
     matching the active SDK (e.g. an `iPhone 17 (27.0)` sim). Simulators are the
     low-friction default: no code signing, no unlock.
3. **Build the fallback order** (try in sequence until one launches end-to-end):
   1. the primary,
   2. if the primary was a **physical device** and it fails → the next eligible
      physical device,
   3. → a simulator on the active-SDK runtime (always the final fallback; it has
      the fewest external preconditions).
   Only fall through on a genuine *launch/eligibility* failure (below), not on an
   in-app assertion you're there to debug. **Log each fallback** ("iPad Pro not
   eligible for an interactive session → falling back to iPhone 17 simulator") —
   never silently retarget, or a passing screenshot misrepresents what ran.

> **Physical-device caveat (learned).** `DeviceInteractionStartSession` keeps its
> **own** eligibility list, narrower than `XcodeListRunDestinations`. A physical
> device that is a valid *build* destination may still be rejected for an
> *interactive* session — in practice only targets on the active SDK's runtime
> (e.g. iOS 27) that are connected, unlocked, and in Developer Mode qualify. When
> StartSession rejects a device it returns the eligible list in its error; pick
> the closest match from **that** list rather than re-guessing.

## The loop

### 1. Resolve the tab and target
`XcodeListWindows` → `tabIdentifier`. `XcodeListRunDestinations` → choose the
primary + fallback order (above). `XcodeSwitchRunDestination({tabIdentifier,
displayTitle})` to make the chosen target active — **pass the `displayTitle`**
(the disambiguated picker label, e.g. `"iPhone 17 (27.0)"`), which round-trips
from the list tool's `displayTitle`/`activeDestinationDisplayTitle`.

### 2. Build FIRST — and surface errors before touching a session
`BuildProject({tabIdentifier})`. Read `buildResult` + the `errors` array. On
failure, do **not** proceed to install; inspect via `GetBuildLog({severity:
'error'})` or `XcodeListNavigatorIssues({severity:'error'})`, fix, rebuild.

> **Ordering matters (learned).** A long build (the KMP `shared` framework
> cross-compiles for iOS on the first run — minutes) **invalidates** an
> already-open interaction session (`InstallAndRun` then fails
> "Session key is invalid"). So build **before** starting the session, not after.

### 3. Pre-boot a simulator target (skip for physical devices)
A cold simulator makes the first `InstallAndRun` time out
("Session initialization timed out" / "device simulator cannot be connected").
Boot and settle it deterministically first:
```bash
export DEVELOPER_DIR=/Applications/Xcode-27.0.0-Beta.3.app/Contents/Developer  # adjust
UUID=<sim-uuid>
xcrun simctl boot "$UUID" 2>/dev/null || true      # idempotent; "already booted" is fine
xcrun simctl bootstatus "$UUID" -b                  # blocks until boot completes
# settle: poll until system services answer (avoids the connect race)
for i in $(seq 1 15); do
  xcrun simctl spawn "$UUID" launchctl print system >/dev/null 2>&1 && break
  xcrun simctl bootstatus "$UUID" >/dev/null 2>&1
done
```
(`sleep` is blocked in this harness — poll, don't sleep. The Simulator.app under
the beta may not exist at the classic path; a missing-Simulator.app warning from
`open` is harmless — DeviceHub drives the sim headlessly.)

### 4. Start the interaction session (fresh key each attempt)
`DeviceInteractionStartSession({tabIdentifier, sessionIdentifier, deviceIdentifier})`.
- **`deviceIdentifier` = bare device NAME or UUID** — *not* the run-destination
  `displayTitle`. Passing `"iPhone 17 (27.0)"` fails with "Cannot select
  specified device"; the bare `"iPhone 17"` or its UUID works. The error lists
  eligible devices with their UUIDs — use one of those verbatim.
- **`sessionIdentifier` must be unique per attempt** — a reused/recent name
  fails "currently in use or was recently used". Use a distinct Title-Case label
  each try (e.g. `"iOS Verify 1"`, `"iOS Verify 2"`).
Keep the returned `interactionSessionKey`; the main agent owns Start/End.

### 5. Install & launch
`DeviceInteractionInstallAndRun({tabIdentifier, interactionSessionKey})` →
expect `"Application installed and running"`. If it reports a target/connect
mismatch, ensure step 1's active destination matches this session's device and
that the sim is booted (step 3), then retry with a **fresh** session (step 4).
Optionally pass `commandLineArguments` / `environmentVariables` (with
`$(inherited)` to preserve the scheme's) for a one-off launch config.

### 6. Capture the screenshot — via a SUBAGENT (required)
DeviceHub interaction **must** run in a subagent that loads the
`device-interaction` skill (StartSession's own response mandates this). Spawn a
`general-purpose` agent that:
- loads the `device-interaction` skill,
- calls `DeviceInteractionSynthesize({interactionSessionKey, interactionCommand})`
  — an **empty** `interactionCommand` captures state (screenshot + UI hierarchy)
  without interacting; the command syntax (`t x y` tap, swipe, `sender keyboard
  kbd …` type, etc.) drives interactions,
  - **The loaded `device-interaction` skill mis-names this tool** as
    `DeviceEventSynthesize` — that tool does not exist on the bridge. The real
    registered tool is `DeviceInteractionSynthesize` (verified via `tools/list`;
    see `device-interaction/PROVENANCE.md`). Tell the subagent to call
    `DeviceInteractionSynthesize`, ignoring the skill's `DeviceEventSynthesize`.
- reads the returned screenshot + hierarchy and reports: did real UI render (vs.
  launch screen / SpringBoard), what's on screen (cite hierarchy elements +
  their `center` coords), any visual/functional defects, and the absolute
  artifact paths.
Give the subagent the exact `tabIdentifier` and session key; tell it **not** to
Start/End the session (the main agent owns that) and not to edit code.

Then surface the screenshot to the user (copy it out of the temp
`ActionArtifacts/` path and send it) — **don't claim "it runs" without the
screenshot as evidence.** Read logs with `GetConsoleOutput({tabIdentifier,
oslogSeverity:['error','fault']})` when chasing a runtime error.

### 7. Fix → rebuild → re-run
Edit code, then **rebuild (step 2) before a new session** (a rebuild invalidates
the old session). Start a fresh session, InstallAndRun, re-capture. Repeat until
the screen renders correctly.

### 8. Clean up
`DeviceInteractionEndSession({interactionSessionKey})` when done — open sessions
are resource-heavy and affect the user-facing Xcode UI. Mention if you left the
active run destination changed (that's local IDE state, not a repo change).

## Common build failures (Xcode 27)

- **`IPHONEOS_DEPLOYMENT_TARGET` below 15.0** → BuildProject fails at config time:
  "deployment target … set to 14.1, but the range of supported deployment target
  versions is 15.0 to 27.0.x." Xcode 27's iOS SDK floor is **15.0**; bump the
  target (both Debug + Release in `iosApp.xcodeproj/project.pbxproj`).
- **`Connected · tools fetch failed` / `tools/list` timeout** → no project open in
  the running Xcode (Prerequisite 1), or the wrong Xcode is selected. Not a code
  bug.

## Boundaries

- Tests the **running app**, not source correctness — keep `./gradlew check` and
  the Xcode/`:shared` tests as the gate. This loop is additive.
- **iOS/Xcode/DeviceHub only.** Android runtime verification routes to
  `android-cli` (its own emulator/screenshot flow), not here.
- Needs a live Xcode 27 with the project open and the `xcode` MCP server
  connected (Prerequisites). It can't launch Xcode's agent host for you.
- **All device interaction goes through a `device-interaction` subagent** — the
  main agent does Build/Start/Install/End; the subagent does Synthesize only.
- Physical-device *interactive* sessions need the device eligible (connected,
  unlocked, Developer Mode, on the active SDK's runtime). When in doubt the
  simulator is the reliable target — hence it's the no-arg default and the final
  fallback.
- Don't trigger blocking UI (system permission dialogs mid-capture) without
  accounting for it; recapture after a transient (spinner/animation) settles
  rather than reporting it as a defect.
