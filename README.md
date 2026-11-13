# SubVenty

A native Android companion for the Storz & Bickel Venty and Volcano Hybrid, with the visual language of the original 1999 SubSeven client. Built on [orth4x0r/venty-android](https://github.com/orth4x0r/venty-android).

This branch contains the **native Android app**, plus the earlier browser design studies. The Android interface is Kotlin/Jetpack Compose, not a WebView.

## Try the Android app

1. Open this fork's [Releases page](https://github.com/thehillbeyondthisone/venty-android-umbrella/releases) while signed into the GitHub account that owns the fork. The first test build is a **draft release** visible to the owner.
2. Open **SubVenty 0.4.0 — native preview 1** and expand **Assets**.
3. Download `subventy-0.4.0-preview1.apk` on your Android phone and open it. Android may ask you to allow installs from the browser or Files app you used.
4. Launch **SubVenty**. Try **Demo Venty** or **Demo Volcano** first. Demo entries are labeled and stored separately from hardware entries.
5. For a real device, allow Nearby devices/Bluetooth access, enable Bluetooth, select **Scan for devices**, then select your Venty or Volcano.

Android 8.0+ is required. This preview uses a separate application ID (`io.github.thehillbeyondthisone.subventy`), so it can coexist with the upstream Venty app. Close/disconnect the other controller before connecting SubVenty to the same physical device.

You do **not** need to merge the PR, install Android Studio, or build anything yourself to install the APK. On GitHub, HTML files show their source; they do not launch the native app.

## Included in the first native preview

- SubSeven-style title bar, blue connection strip/work area, black shell, cyan sidebar, and status strip.
- Native permission, scan, connect, reconnect and disconnect flows using the existing BLE controller.
- Device-confirmed target/readiness, Venty battery, and Volcano live temperature.
- Heater, bounded temperature adjustments, presets, Venty normal/Boost/Superboost, and Volcano pump.
- **All controls / ramps / settings** opens the original advanced dashboard, preserving the existing ramp editor, offsets, settings, history, Find My and other device options. That advanced panel retains its earlier styling in this first preview.
- Permanent, local per-device collection archive: reached 5 °C Atlas bands, an Ember Trail, editable one-line notes, and a dismissible weekly postcard.
- **Do not track** pauses new session and battery logs while preserving existing notes/stamps/beads. Resume does not backfill a paused period; a hot session interrupted by a pause is excluded. Device controls continue working. On a new connection, recording waits for an observed heater-off state.
- Demo sessions have separate archive identities. No synthetic example collections are preloaded into the native app.

Atlas stamps use **device-reported reached targets**, not optimistic slider edits. Venty does not report live temperature, so the interface never presents a simulated measurement as a real one. Existing collection records survive the rolling 500-entry recent-session history limit. Notes and collections persist across app restarts; clearing Android app data/uninstalling deletes this local data.

## Scope and validation

This is a first native test APK. Compilation, unit tests and Android lint are checked locally in the managed build environment. Emulator and physical-device results are recorded in [the native preview notes](docs/native-preview/README.md). Physical Bluetooth, manufacturer-specific firmware behavior, watch integration and real-phone ergonomics still need device testing.

The APK is debug-signed for personal testing. Keep the signing key for future compatible updates; do not publish it in the repository. The in-app updater points to **this fork**, not the upstream release feed. Draft/prerelease builds are installed manually.

Passive achievements, Dial-in comparisons, calendar palettes, optional user-chosen chimes, and easter eggs remain in the [design brief](docs/design-exploration/README.md); they are not implemented in this first native build. There are no new push reminders, streaks, rankings, or consumption goals.

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

The original Venty/Volcano controller, services, session tools and advanced interface are from [orth4x0r/venty-android](https://github.com/orth4x0r/venty-android), under the [MIT license](LICENSE). The SubVenty UI is an independent implementation of the referenced historical visual design; no SubSeven runtime is included. Not affiliated with Storz & Bickel. Device names are their trademarks.
