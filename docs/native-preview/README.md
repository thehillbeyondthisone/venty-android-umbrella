# Native SubVenty preview 1

**Current build: [preview 2, with screenshots and validation](iteration2/README.md).** The notes below record the first native preview.

This is the Kotlin/Compose implementation, package `io.github.thehillbeyondthisone.subventy`, version `0.4.0-subventy.1` (code 9). It installs alongside the original app.

[Download the APK](https://github.com/thehillbeyondthisone/venty-android-umbrella/blob/builds/subventy-preview1/subventy-0.4.0-preview1.apk?raw=true). The separate builds branch contains the APK and checksum; the source implementation branch contains no APK binary.

## Verification

- Final local Gradle debug APK build succeeds with Android SDK 36 and JDK 17; APK v2 signature verifies.
- **110 unit tests pass** (zero failures/errors). Tests cover the existing controller/protocol/session calculations plus collection identity, duplicate snapshots, note preservation, history rollover, demo separation, legacy-band handling and pause/resume/connection privacy boundaries.
- Android lint passes with no errors (existing warnings remain). Guarded API annotations and observable locale reads were corrected where the original code produced lint errors.
- The APK installed successfully on an Android 15/API 35 emulator, and the native client screen rendered. Emulator System UI repeatedly became unresponsive during software emulation without KVM; automated interaction testing could not be completed reliably. No SubVenty AndroidRuntime crash was observed in the inspected log. This is an installation/render smoke check, not a claim of full emulator or physical-device validation.
- Physical BLE is unavailable in this test environment.

## First device check

1. Install the APK, open SubVenty, and try each demo mode.
2. Inspect temperature controls, settings/ramps in All controls, and the Volcano pump in its demo.
3. Disconnect the demo. Allow Bluetooth, scan, select your actual device, and verify displayed name and battery/readiness.
4. Test a small target adjustment and heater off/on. Venty target range remains 40–210 °C; Volcano is 40–230 °C. The underlying controller still clamps values and debounces writes.
5. Confirm a completed session appears under the correct archive. Field Notes should survive closing/reopening the app. Do not track must preserve old entries without recording a paused interval.

Use ordinary device controls to turn heating off whenever appropriate. This preview does not claim that emulator tests verify real Bluetooth/firmware behavior.

## Storage and lifecycle details

Collection records use a stable identity derived from device identity and recorded start/end timestamps. They are merged append-only into a separate local archive, preserving notes when recent-history snapshots repeat and preserving older keepsakes when recent history rolls over. Atlas stamps are based only on reached bands captured in new device reports; legacy averages do not create guessed stamps.

The UI observes optimistic target changes for responsiveness; session/Atlas recording observes a separate **device-confirmed** status flow. This prevents an unconfirmed slider edit from stamping a band. Pausing clears the active transient session without retaining it. Recording resumes only after a heater-off observation. Starting while already hot does not invent a partial session.

No backend or account is required. Collections, notes and tracking preferences remain on-device. Clearing app storage/uninstalling removes them. The first APK does not implement export, cloud sync, achievements, comparison ratings, palette unlocking or chime selection.

## Building the same preview

The managed build installed official Android command-line tools and SDK 36 under `/workspace/android-sdk`, and an official Temurin JDK 17 under `/workspace/toolchains`. Those tools and signing files are outside the repository. Standard Android Studio/JDK 17 environments can use the normal Gradle command in the root README.

No push/PR workflow triggers were added and no GitHub Actions build was dispatched.
