# SubVenty

A native Android companion for the Storz & Bickel Venty and Volcano Hybrid, with an amber LCD, graphite shell and tactile cassette-era controls. Built on [orth4x0r/venty-android](https://github.com/orth4x0r/venty-android).

This branch contains the **native Android app**, plus the earlier browser design studies. The Android interface uses Kotlin and Jetpack Compose.

## Try the Android app

1. On your Android phone, download SubVenty Preview 11. [Download the APK directly](https://raw.githubusercontent.com/thehillbeyondthisone/venty-android-umbrella/refs/heads/builds/subventy-preview11/subventy-0.4.0-preview11.apk)
2. Open the downloaded APK. Android may ask you to allow installs from the browser or Files app you used.
3. Launch **SubVenty**. Try **Demo Venty** or **Demo Volcano** first. Demo entries are labeled and stored separately from hardware entries.
4. For a real device, allow Nearby devices/Bluetooth access, enable Bluetooth, select **Scan for devices**, then select your Venty or Volcano.

You can also download the ZIP and extract the APK from it. [Download the ZIP](https://github.com/thehillbeyondthisone/venty-android-umbrella/archive/refs/heads/builds/subventy-preview11.zip)

Preview 11 is version **0.4.0-subventy.11** (code 19). Install it over an earlier preview to retain your local archive and preferences.

Android 8.0+ is required. This preview uses a separate application ID (`io.github.thehillbeyondthisone.subventy`), so it can coexist with the upstream Venty app. Close/disconnect the other controller before connecting SubVenty to the same physical device.

You do **not** need to merge the PR, install Android Studio, or build anything yourself to install the APK. On GitHub, HTML files show their source; they do not launch the native app.

## Room lighting

**Settings → Lighting Desk** applies a saved look to your Home Assistant lights during heat-up, at temperature, during an estimated Venty draw, and at session end. Edit and share named profiles with their own colors, brightness and fades. A Home Assistant blueprint handles the light selection. [Set up Govee room lighting](docs/home-assistant/README.md).

## Session objects

On Venty, open **Device → Session objects** and choose a seat under **Next draw**. The app assigns the selected color when a draw starts. The **⋮** menu switches sculptures and opens **By person** or **Browse draws**.

- **Resin Window** lights a full-height field of ribbons as a draw begins. The resin grows inside it, then sets into the mosaic.
- **Kinetic Mobile** grows glass at the end of a lit filament during the draw. The bead attaches to a branch and the counterweight shifts as it lands. Pull a bead sideways and release to pluck it.
- Tap a piece to see its duration and access **Replay draw** or **Pluck**. **By person** shows draw count, total time and average duration.

Open a saved session under **Archive → Draw sessions** to view either sculpture in a dedicated viewer. The back arrow returns to the filtered list. Finished draws are included in JSON export. Venty estimates draw timing from its shutoff timer. Closely spaced resets on the same seat may become one draw. **Do not track** pauses collection. In Demo Venty, **Simulate draw** is under **⋮** once the device reaches its target. [Watch the live scenes](docs/native-preview/iteration11/README.md)

## Session collections

- **Temperature Atlas** opens around your recorded 5 °C bands. Tap a stamp to inspect the sessions behind it or open their Field Notes. The full map is available on demand.
- **Ember Trail** draws one ember per recorded session. Page through the trail to see older sessions.
- **Dial-in** compares two or three distinct reached bands from past sessions. Choose the session for each band, add optional impressions and a short note, and save one editable comparison for the current archive view. Comparisons are included in JSON export.
- **Weekly postcard** shows the recorded session count and the most frequently recorded band. Swipe sideways or use the button to put it away. The app remembers the dismissal for the week and lets you reopen it.

## Device controls

- The fixed header and three tabs frame a scrollable Device dashboard. The LCD displays Venty's effective target and readiness, or a live Volcano Hybrid reading with a separate target when available.
- Use the linear target slider, holdable plus/minus keys, preset keys, and dedicated heater key. Venty adds Normal, Boost and Superboost. Volcano Hybrid adds a pump key. Long-press a preset to remove it and tap **Undo** to restore it.
- Expand **Ramp session** to edit a Simple or Steps plan and load saved profiles. Start is an explicit action. While running, the LCD shows the program and the next step or hold countdown. **Stop ramp** leaves the heater setting in place. **Turn heater off** cancels the ramp and sends the heater-off command.
- Scroll down for battery history and estimates, boost offsets, firmware and heater time. The connection menu offers Disconnect and Forget device.
- **Settings** opens device properties, pairing, Find My, link activity, client options, Lighting Desk and JSON export. **Do not track** pauses session and battery logging and automatic lighting.

Archive search and Field Notes display local records. Device-reported reached targets stamp the Atlas. The collection archive retains entries beyond the rolling 500-entry recent-history limit. Demo entries have separate archives. Clearing the app's Android storage removes local data.

## Scope and validation

This is a native test APK. The debug build, unit tests and Android lint run locally. The Android emulator uses the built-in device simulators. Physical Venty and Volcano behavior needs a hardware check. [Preview 11 test results](docs/native-preview/iteration11/README.md)

The APK is debug-signed for personal testing. Keep the signing key for future compatible updates; do not publish it in the repository. The in-app updater points to **this fork**, not the upstream release feed. Draft/prerelease builds are installed manually.

The [design brief](docs/design-exploration/README.md) covers future ideas including passive achievements, calendar palettes, optional chimes and easter eggs.

## Build locally (optional)

Use Android Studio with Android SDK 36 and JDK 17:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. The existing GitHub Actions workflow remains manual-only; no workflow runs are required for this preview.

## Design references

- [SubVenty source references](docs/design-exploration/subventy/SOURCES.md): original screenshots, historical Delphi form geometry, colours, and fidelity notes.
- [Interactive browser reconstruction](docs/design-exploration/subventy/index.html).
- [Earlier handwritten/cassette/sculptural studies](docs/design-exploration/style-studies.html).
- [Collection mechanics and plan](docs/design-exploration/README.md).
- [BLE protocol reference](docs/PROTOCOL.md).

## Credits and license

The original Venty/Volcano controller, services, session tools and advanced interface are from [orth4x0r/venty-android](https://github.com/orth4x0r/venty-android), under the [MIT license](LICENSE). The SubVenty interface is an independent cassette-era design. Not affiliated with Storz & Bickel. Device names are their trademarks.
