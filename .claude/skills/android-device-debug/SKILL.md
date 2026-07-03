---
name: android-device-debug
description: 'Build, install, launch, and visually verify the Android app (:androidApp) end-to-end on a real device or emulator via the official `android` CLI + adb — the Android counterpart of ios-device-debug, and a sibling of astro-web''s browser-debug / astro-calendar-service''s runtime-debug. Gradle builds the APK; the `android` CLI deploys, launches, screenshots, and dumps the UI layout. Use when the user says "run the app on the emulator", "run the Android app", "build and run on Android", "launch on the Android device/emulator", "screenshot the app on the emulator", "check it on the emulator", "does it run on Android", or wants runtime/visual confirmation that ./gradlew check and unit tests structurally cannot give. Takes an optional device serial / AVD name argument; with none, targets an emulator. Android/Gradle only — iOS runtime work routes to ios-device-debug, and Android UI *development* (Compose, navigation, adaptive) still routes to the official android/* skills; this is the runtime/visual verification loop that complements them.'
argument-hint: '[device serial (adb) or AVD name; omit to use an emulator]'
allowed-tools: Bash, Read, Edit, Grep, Agent
version: '1.0.0'
---

# Android device/emulator debug loop (astro-mobile)

Drive the **running** Android app on a real device or emulator and *see* it:
build the APK → install → launch → capture a screenshot + UI layout, in an
iterative run → observe → fix → re-run loop. This is the runtime/visual layer
on top of the static gate — it does **not** replace `./gradlew check` or the
unit tests, which stay authoritative for source correctness. It catches the
class of defect a green test can't: does the screen actually render, is anything
overlapping/truncated, does the app launch and stay up on a real runtime.

The mirror of **`ios-device-debug`** (which drives Xcode 27 DeviceHub over MCP).
The Android loop is **simpler**: it's plain `android` CLI + `adb` over the
Android Debug Bridge — **no MCP host, no IDE running, no interaction subagent
required** (the iOS side needs all three).

Siblings across repos: **`browser-debug`** (astro-web, real Chrome) and
**`runtime-debug`** (astro-calendar-service, live gRPC + Postgres).

## Why this exists (the emulated-test gap)

`./gradlew :shared:testAndroidHostTest` exercises shared Kotlin on the JVM, and
`:androidApp` unit tests run without a real device. Neither installs the built
APK and renders Compose on a device. So a green suite says "the units behave,"
not "the app launches on API 34 and its login screen renders without overlap."
A real device/emulator does.

## Prerequisites

- **`android` CLI** on PATH (`brew install ...`; `android --version`) — the entry
  point. Its usage is documented by the vendored **`android-cli`** skill; read
  `android-cli/references/interact.md` for the layout/screenshot/input surface.
- **`adb`** on PATH (ships in the Android SDK `platform-tools`;
  `~/Library/Android/sdk/platform-tools/adb`). `adb devices` lists connected
  devices + running emulators.
- **Gradle** builds the APK — the CLI *deploys* an APK, it does not build it.
- App facts (from `androidApp/`): `applicationId`/namespace `io.jitrapon.astro`,
  **`minSdk 23`** (the device/emulator API floor), launcher activity
  `io.jitrapon.astro/io.jitrapon.astro.ui.main.MainActivity`.

## Device selection & fallback chain (the core logic)

Optional argument = a device serial (as shown by `adb devices`) or an AVD name
(as shown by `android emulator list`). Resolve target + fallback order:

1. **Enumerate.** `adb devices` (physical devices + already-running emulators,
   with serials) and `android emulator list` (defined AVDs, may be stopped).
2. **Pick the primary:**
   - **Arg given** → match it against a serial or AVD name; that's the primary.
   - **No arg** → primary is an **emulator** (the low-friction default: no cable,
     no unlock, no USB-debugging authorization).
3. **Fallback order** (try in sequence until one launches end-to-end):
   1. the primary,
   2. if the primary was a **physical device** and it fails → the next
      `adb devices` entry that is `device` (not `unauthorized`/`offline`),
   3. → an **emulator** (always the final fallback; create/start one if none is
      running — see step 3 of the loop).
   **Log every fallback** ("device ABC123 unauthorized → falling back to
   emulator Pixel_7_API_36") — never silently retarget, or a passing screenshot
   misrepresents what ran. Fall through only on real launch/connect failures,
   not on an in-app assertion you're there to debug.
   Pass the chosen target to `android`/`adb` via `--device <serial>` / `-s
   <serial>` so a build with several devices attached is unambiguous.

## The loop

### 1. Resolve the target
`adb devices` + `android emulator list` → choose primary + fallback (above).

### 2. Build the APK (Gradle)
```bash
./gradlew :androidApp:assembleDebug     # produces the debug APK
```
Read Gradle's output; on failure, fix and rebuild before deploying. The APK
lands at the conventional path (confirm via `android describe` if unsure):
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
(`./gradlew :androidApp:installDebug` builds **and** installs via adb in one step
— handy, but `android run` below gives explicit launch + device control.)

> Unlike the iOS loop, the build has **no session to invalidate** — order is
> flexible and a long build costs nothing but time.

### 3. Ensure a target is running
- **Physical device:** confirm it shows as `device` in `adb devices` (unlocked,
  USB debugging on, RSA prompt accepted). If `unauthorized`, tell the user to
  accept the on-device prompt.
- **Emulator:** `android emulator start <avd>` — **blocks until fully booted and
  ready** (cleaner than iOS's simctl boot-poll). If `android emulator list` is
  empty, bootstrap one first (see Common failures → no AVD).

### 4. Install & launch
```bash
android run --apks=androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --activity=io.jitrapon.astro/io.jitrapon.astro.ui.main.MainActivity \
  --device=<serial>
```
`--device` is optional with a single target; pass it whenever more than one
device/emulator is attached. (Equivalent low-level path:
`adb -s <serial> install -r <apk>` then `adb -s <serial> shell am start -n
<pkg>/<activity>`.)

### 5. Capture — screenshot + UI layout
- **Layout first (primary, cheap, structured):**
  `android layout -p` → JSON tree with per-element `text`, `resourceId`,
  `interactions`, `state`, `bounds`, `center`, `off-screen`. Use
  `android layout --diff` after an action to see only what changed (keeps context
  small). Prefer this over screenshots for *reasoning about* the UI.
- **Screenshot (visual confirmation):**
  `android screen capture -o <out.png>` → **Read the PNG and visually examine
  it.** Copy it out to a stable path and surface it to the user — don't claim
  "it runs" without the screenshot as evidence.
- If `layout` fails (WebView / mid-animation), fall back to
  `android screen capture --annotate -o <png>` (labeled bounding boxes) and, to
  act on a label, `android screen resolve --screenshot=<png> --string="tap #N"`.

### 6. Interact (optional — the tap/type analog)
Use `adb shell input` with `center` coords from the layout dump:
- Tap: `adb shell input tap <x> <y>`
- Swipe/scroll: `adb shell input swipe <x1> <y1> <x2> <y2> <ms>` (scroll slowly)
- Type: ensure the field has `"focused"` in its `state` first, then
  `adb shell input text 'hello'`
Re-run `android layout --diff` after an interaction to confirm the result.
(You *may* delegate steps 5–6 to a subagent for context hygiene, but — unlike
iOS — it is **not required**; these are plain CLI calls.)

### 7. Fix → rebuild → redeploy
Edit code, `:androidApp:assembleDebug`, `android run` again (no session to
restart). Re-capture layout/screenshot. Repeat until the screen is correct.

### 8. Clean up
Optionally `android emulator stop <avd>` when done (a left-running emulator is
harmless but consumes resources — mention it if you leave it up).

## Common failures

- **No AVD / no system image** (`android emulator list` empty) — the emulator
  fallback needs a one-time bootstrap (heavier first-run than iOS, which ships
  simulators with Xcode):
  ```bash
  android sdk install "system-images;android-36;google_apis;arm64-v8a"   # ~hundreds of MB
  android emulator create --name Astro_API36 ...      # see `android emulator create --help`
  android emulator start Astro_API36
  ```
- **`adb devices` shows `unauthorized`** → accept the RSA debugging prompt on the
  device. `offline` → replug / `adb kill-server && adb start-server`.
- **Install fails on API floor** → the app is `minSdk 23`; a device/emulator
  below API 23 can't install it. Pick an API ≥ 23 image.
- **`android layout` returns nothing** → a WebView or running animation; use
  `screen capture --annotate` and retry `layout` after navigating.

## Boundaries

- Tests the **running app**, not source correctness — keep `./gradlew check` and
  the `:shared`/`:androidApp` unit tests as the gate. This loop is additive.
- **Android/Gradle only.** iOS runtime verification routes to `ios-device-debug`.
- **Complements, doesn't replace, the official `android/*` skills.** Android UI
  *development* (Compose, `adaptive`, `navigation-3`, `edge-to-edge`, `styles`,
  `testing-setup`) routes to those per the CLAUDE.md precedence rule; this skill
  is the *runtime/visual verification* loop you run after building.
- Needs the `android` CLI + `adb` on PATH and either a connected device or a
  bootstrappable emulator (system image installed). The emulator branch may
  require a first-run image download.
- Prefer `android layout`/`--diff` over screenshots for *acting on* elements
  (cheaper, structured); use the screenshot for visual confirmation you surface
  to the user.
