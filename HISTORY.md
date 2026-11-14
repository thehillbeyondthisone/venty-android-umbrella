# History

Recent changes, newest first. Use it for context when starting a new session.

## 2026-11-14: iteration 3 brought back inside the SubSeven aesthetic

The owner wants to **build on the SubSeven look, not depart from it**. The authentic preview 2 chrome, reconstructed from the original GitHub source (see `docs/design-exploration/subventy/SOURCES.md`), is the baseline. Don't add new palettes, CRT effects or fonts.

- Restored `SubVentyChrome.kt` and the screen shell to preview 2: solid lavender caption, original buttons and tree, `Color.Gray` frame, segmented status strip and atlas chips. The tree shows on every page, and the "temperature controls » / « device information" footer is back.
- Removed the night palette, CRT glass, phosphor setting/command and VT323 font (and its license).
- Redrew the terminal, ICEBREAKER, loot and screensaver with S7 parts only. The new additions are a clock segment in the status strip, the `S7Input` `onSubmit` send action, and the trace bar in the original `$00C08000` menu-border blue.
- Commands, narration, ICEBREAKER rules, loot lore, the modem sound and the guardrails are unchanged. The tests are still 30 JVM tests (the phosphor test became a modem test).

## 2026-11-13: SubVenty iteration 3, "after hours" (first draft, look superseded above) (branch `claude/clever-ride-6r8e2e`)

Built on `design/visual-directions` (SubVenty preview 2, e70e457). Direction from the owner: a midnight vape session as a 90s hacker, mixing utility, form, nostalgia and hacking-game elements.

- **Night palette** for all SubVenty chrome (`S7` tokens now point at `Night` in `ui/subventy/NightTheme.kt`): a 98-style gradient caption, CRT scanlines, vignette and refresh band. Terminal text uses a selectable phosphor (amber by default, green or cyan) in VT323 (bundled, OFL in `third_party/vt323`).
- **Terminal home page** (`PAGE_TERMINAL = 10`, now the default): target readout, ASCII fader, ±, mode tabs, `RUN/KILL heat.exe`, stats, macro chips, scrollback and a command prompt. The tree is hidden on this page; the toolbar navigator still opens it.
- **Commands** are parsed in pure Kotlin (`TerminalModel.kt`) into `TermAction`s that the screen sends through the existing controller. There are about 20 hidden commands, recorded once found.
- **Feed**: dial-up/IRC/process narration from device-confirmed state (`Feed.diff`), held in memory in `NightStore`.
- **Taskbar** with notice, link, battery, moon phase and clock replaces the status strip. **vapor.scr** screensaver runs after 2 idle minutes on the terminal page (toggle in local options).
- **ICEBREAKER** (`Icebreaker.kt`): untimed Mastermind-style puzzle. Each crack pulls the next of 12 lore files (The Ember Line BBS, sysop nightjar) into `~/loot` (`PAGE_LOOT = 12`).
- **Modem sound** (`ModemSound.kt`): synthesised, off by default, plays on Connecting → Connected.
- Version bumped to `0.5.0-subventy.3` (code 11).
- New dev tool **`tools/night-preview`**: Compose for Desktop 1.5 harness (Maven Central only) that compiles the pure UI files, runs their 30 JVM tests and renders review PNGs. Renders are in `docs/native-preview/iteration3/`.
- Guardrails kept from `docs/design-exploration/README.md`: no speed or consumption rewards, no locked slots, no invented temperatures, sound off by default, reduced motion respected.
- **Not yet done:** the Android build, lint, app unit tests and emulator/device checks; publishing preview 3 to a `builds/` branch. `SubVentyScreen.kt`, `NightStore.kt`, `ModemSound.kt` and `VentyApp.kt` were reviewed but not compiled.

## Earlier (from git history)

- `e70e457` SubVenty preview 2: tree navigation, utility windows, ramp editor, device properties, archive export (APK on `builds/subventy-preview2`).
- `e202f24` First native SubVenty client and permanent private collections (preview 1).
- `fe5a08b`…`8d69d46` Browser design studies in `docs/design-exploration/`.
- `08355a6` Upstream venty-android v0.3.2 (main).
