# SubVenty Android preview 2

[Download the APK](https://github.com/thehillbeyondthisone/venty-android-umbrella/blob/builds/subventy-preview2/subventy-0.4.0-preview2.apk?raw=true)

Native Kotlin/Compose app, version **0.4.0-subventy.2** (code 10), for Android 8.0+. Open the downloaded APK on your Android phone and allow installation from your browser or Files app if Android asks.

This APK uses the same application ID and signing certificate as preview 1. Install it as an update to keep your local collections and preferences; no uninstall is needed. It installs separately from the original upstream Venty app. Launch **SubVenty** and try either demo mode before connecting hardware.

The new interface has tree navigation, a charcoal workspace, an icon toolbar and square SubSeven-style utility windows. Ramps, device properties, Find My, note editing, archive search and JSON export are available from the native client.

[Source at the build commit](https://github.com/thehillbeyondthisone/venty-android-umbrella/tree/e70e457691a81fc8a0826e0d53a634549785ab46) · [Native screenshots and verification](https://github.com/thehillbeyondthisone/venty-android-umbrella/blob/e70e457691a81fc8a0826e0d53a634549785ab46/docs/native-preview/iteration2/README.md) · [Pull request](https://github.com/thehillbeyondthisone/venty-android-umbrella/pull/1)

The APK is debug-signed for personal testing. SHA-256 is in `SHA256SUMS.txt`. This branch contains only build artifacts and installation notes. No signing key is included.

Validation: 116 unit tests pass; Android lint and the debug build succeed; APK signing verifies. Android 15 emulator checks cover installation/update and native demo interactions. Physical-device behavior is outside those emulator checks.
