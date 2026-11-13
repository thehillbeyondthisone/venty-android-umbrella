# SubVenty native preview 1

[Download the Android APK](https://github.com/thehillbeyondthisone/venty-android-umbrella/blob/builds/subventy-preview1/subventy-0.4.0-preview1.apk?raw=true)

On your Android phone, download the APK above, open it, and allow installation from your browser/Files app if Android asks. Launch **SubVenty**, then try **Demo Venty** or **Demo Volcano** first. For your device, allow Bluetooth/Nearby devices access, scan, and select the device. Disconnect any other app controlling the same device before connecting.

Android 8.0+ is required. The app installs separately from the upstream Venty app. No merge or local build is needed.

- Version: `0.4.0-subventy.1`, code 9; debug-signed personal test build.
- Application ID: `io.github.thehillbeyondthisone.subventy`.
- Build source: commit `e202f245338e787f1e116fcdb4eaad4f33d3dddd` in `design/visual-directions`.
- Validation: debug APK compilation, 110 passing unit tests, Android lint without errors and APK v2 signature verification.
- Emulator: installed and rendered on Android 15; software-emulation System UI instability limited automated interaction checks. Physical Bluetooth testing is still required.
- Advanced ramps/settings retain the original panel, accessible through **All controls**.
- Notes/Atlas/Ember Trail and tracking preference persist locally. Uninstalling or clearing app storage deletes this data. Export/sync, achievements, comparison ratings, palette unlocks and chime selection are not implemented yet.

This branch holds build artifacts only; the implementation and review are in [PR #1](https://github.com/thehillbeyondthisone/venty-android-umbrella/pull/1). GitHub release-asset uploads were unavailable to the current integration, so this separate branch provides the download. `SHA256SUMS.txt` records the APK checksum.
