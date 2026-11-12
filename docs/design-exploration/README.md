# Venty Umbrella: visual directions and light gamification

Open [index.html](index.html) in a browser for three working visual concepts. No build, server, account, or hardware is needed. The preview uses synthetic data and resets on reload. It does not send device commands or change the Android app.

![Three control directions: Quiet Instrument, Fieldnotes and Signal](control-options.png)

[View the optional Care screens](care-options.png).

## Recommendation

**Quiet Instrument with an optional Care view** is the best first iteration. It preserves the current dial and recognizable device controls, removes competing effects, and gives connection state, target temperature, and heater action a clearer hierarchy. Fieldnotes is the strongest change in personality; Signal is the strongest dark-mode direction.

| Direction | Visual language | Main interaction | Why choose it | Relative Compose effort |
| --- | --- | --- | --- | --- |
| **01 Quiet Instrument** | Porcelain `#F7F9F6`, pine `#205E50`, system sans, thin dial, rounded surfaces | Familiar dial with precise +/- and one primary heater button | Calm, readable, familiar; recommended default | Small–medium: theme and dashboard layout; add proper light theme support |
| **02 Fieldnotes** | Oat `#F3EDDF`, walnut `#6A4730`, serif headlines, ruled sections, stamp shapes | Linear target control and a journal-like presentation | A personal, warm object with room for notes | Medium: new layout, typography, and new journal data |
| **03 Signal** | Graphite `#181E24`, lichen `#D4E7A0`, bundled Barlow figures, monospace labels | Large numeric readout and a compact linear control | Technical, restrained, and suited to dark environments | Small–medium: dark theme and dashboard layout |

These are layout and product explorations, not merely accent palettes. The preview deliberately isolates the main controls and proposed Care view; it is not a complete replacement for the app's existing features.

### Interaction coverage in the preview

- Change targets within the Venty's 40–210 °C range using a slider or +/- buttons.
- Simulate heater off, heating, ready, and disconnected states. Disconnected controls are disabled.
- Switch each concept between Control and Care, or compare all Care views together.
- Collect one care stamp, prevent a duplicate collection, and undo it.
- Use keyboard focus, arrow-key tab navigation, and native range keyboard controls.
- Reset all synthetic data; no care events or preferences persist.

The dial arc is a target-setting visualization, not a measurement or heat-up progress estimate. A production version must label target and readiness explicitly, as the preview does. The state selector is for design review only.

## Light, repeatable gamification

**Reward looking after the device and useful reflection. Keep consumption metrics informational.** The existing project already tracks puffs, session durations, longest draws, use streaks, and monthly totals; turning those into quests or XP would encourage more use. Prefer small, predictable acknowledgements that take seconds and do not require daily engagement.

| Idea | Repeatable trigger | Small reward | Minimum implementation | Priority |
| --- | --- | --- | --- | --- |
| **Care stamps** | User marks a scheduled care task complete after doing it | A stamp in a permanent, private collection | Per-device care schedule and event store; completion, skip, snooze, undo | **First** |
| **Field notes** | Optional note on an existing session, or a standalone device observation | A saved card with a small illustration; no XP | Separate note store, edit/delete, optional link to stable session identity | Next |
| **Monthly keepsake** | User chooses to open a monthly recap | A collectible cover, favorite preset, notes and care summary | Reuse monthly filtering; include new notes/care data; handle empty months | Next |
| **Make it yours** | One-time setup steps, such as naming a device or saving a preset | A short checklist acknowledgement | A few local preferences; all controls and themes available from the start | Small optional addition |
| **Care palette collection** | A few distinct eligible care events over time | Cosmetic stamp styles the user can select | Cosmetic state only; all functional controls remain available | Later, only if users enjoy stamps |

### Smallest useful loop

1. **Notice:** a quiet Care card shows the next user-scheduled task. Reminders are off until explicitly enabled; no generic daily push.
2. **Act:** mark it done after following the manufacturer's care guidance. Self-reporting is sufficient; the app cannot detect cleaning.
3. **Acknowledge:** add one stamp and a short “A small thing, well done.” message. Use a brief, optional animation, respecting reduced motion and haptic preferences.
4. **Return whenever:** the collection remains. A missed date or skipped task never removes progress or creates a “broken streak.”

The preview starts with **one synthetic collected stamp** and one eligible task so the second stamp and undo are immediately reviewable. The three slots illustrate a collection, not three tasks that must be completed this month. A real collection can continue without a deadline.

### Rules that keep it light

- **No consumption XP:** no rewards for puff count, draw length, heat level, session duration, Boost/Superboost, consecutive use days, or opening the app daily.
- **No hardware coupling:** collecting a stamp, viewing a recap, or completing a task must never start heating, change a target, or run a ramp/pump.
- **No reward grind:** one stamp per eligible scheduled task occurrence, including repeated taps, process restarts, and offline retries. Undo removes that event and its stamp; redoing it restores the same event rather than generating extra awards.
- **No guilt:** no lost streaks, countdown pressure, leaderboards, randomized rewards, or expiring collections. Care can be hidden entirely.
- **User-set cadence:** care schedules follow the user's chosen timing and manufacturer guidance. Changing the clock or time zone must not create duplicate occurrences.
- **Private by default:** local storage, no account, no automatic sharing. Provide clear export/delete behavior alongside history controls. Disabling reminders stops them; hiding Care preserves the collection unless the user deletes it.
- **Cosmetics only:** any future unlock affects stamps or illustrations. Device operation, accessibility, and ordinary theme selection remain available to everyone.

### A compact first release

Ship only **one user-created care task, a permanent stamp collection, completion/undo, skip/snooze, and an opt-in reminder**. Keep notes and keepsakes for later. Show Care in an overflow menu or secondary navigation, not above the heater action. Measure whether people find it useful through feedback; if analytics are added later, get an explicit opt-in and avoid collecting session or substance details.

## How this maps to the current Android code

Reviewed against upstream commit `08355a64d8aede04733d68483de22bd569ee96ef`.

| Existing area | Suggested change after a direction is selected |
| --- | --- |
| `ui/theme/Theme.kt` | Add semantic light/dark palettes and a visual-style preference. Keep wallpaper/device accent behavior intentional and preserve semantic heating, ready, warning, and disconnected states. |
| `ui/dashboard/DashboardScreen.kt` | Extract device header, target control, primary heater action, and optional care entry into focused composables. Preserve history/settings access and current connection handling. |
| `ui/dashboard/TemperatureDial.kt` | Reuse dial geometry and input logic for Quiet; replace hardcoded track/marker colors with semantic theme tokens. Linear alternatives need the same input bounds and accessibility support. |
| `ui/session/SessionWidgets.kt` | Offer an optional note after session completion, without awarding consumption metrics. |
| `prefs/PrefsStore.kt` | Store style, Care visibility, reminder consent, and schedule preferences; avoid adding care events into unrelated settings. |
| New `care/` area | Pure care eligibility rules and a local event store keyed by stable device identity, task ID and occurrence ID; expose state to Compose. No dependency on BLE command methods. |
| `session/SessionTracker.kt` | Keep consumption tracking separate from reward rules. If notes reference sessions, introduce stable identities/migration rather than using list positions; current history retains at most 500 records. Keep notes/care when old sessions age out. |
| `session/Wrapped.kt` and `ui/wrapped/WrappedScreen.kt` | Keep factual stats neutral; place care and optional notes in a keepsake. Do not turn `longestStreakDays`, `longestDrawMs`, or puff/session totals into rewarded achievements. |
| `ble/DeviceKind.kt` | Continue capability-based layouts: Venty has no live-temperature reading; Volcano has a live reading and pump, but no battery/Boost. |

Before shipping native changes, preserve all existing controls: presets, normal/Boost/Superboost and offsets, ramps, heater, settings/history, and Volcano pump. Advanced controls can live in an explicitly labeled sheet, accessible in one tap. Keep target, mode-adjusted effective target, and live temperature distinct. Respect each device's bounds (Venty 40–210 °C; Volcano 40–230 °C), connection state, and existing reduced-motion behavior. A new navigation shell must not imply Care replaces history.

### Native acceptance checks for a later implementation

- Hardware controls remain usable when Care is off, hidden, or has no data.
- TalkBack announces target, units, connection and heater state; +/- and all controls have at least 48 dp touch targets. No meaning depends on color alone.
- Light/dark palettes meet contrast requirements; 200% font scaling does not hide heater or pump controls.
- Phone layouts work at narrow widths and with navigation/IME insets. The preview is a layout study, not validation of Compose accessibility.
- Reconnect, heating/ready transitions and device limits behave exactly as before; Volcano retains distinct pump behavior.
- Care eligibility tests cover duplicate completion, undo/redo, app restart, skip/snooze, time zones and date changes. Use a controllable clock and stable occurrence IDs.
- Local event migrations, clearing/exporting data and reminder cancellation are verified. Cosmetic progress never calls BLE writes.

No Kotlin, BLE, Gradle, signing, update URLs or workflows were changed by this exploration. The repo's workflows remain manual-only.

## Previewing and validation

Open `index.html` directly, or serve the repository using `python3 -m http.server 8765` and visit `http://localhost:8765/docs/design-exploration/`.

`control-options.png` and `care-options.png` are browser-rendered comparisons of the actual HTML concepts. They can be regenerated after changing the preview. The page uses system fonts and the repo's existing Barlow font; it makes no external asset requests.

Preview validation covers interactions, 40/210 °C limits, disconnected controls, care duplicate prevention/undo, keyboard tabs, all state labels, and desktop/mobile overflow. Native build/tests were not run because this change contains only design documentation and a browser prototype.
