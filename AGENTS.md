# AGENTS.md

Guidance for AI coding agents working in this repo.

## Subagent model choice

Pick the model by how hard the task is:

| Task | Model | Examples |
|------|-------|----------|
| Small, or medium but easy | **Haiku** | Lookups, renames, small edits, docs, boilerplate, reading logs |
| Medium | **Sonnet** | A feature in one area, a bug with a clear repro, refactors across a few files |
| Hard | **Opus** | BLE/GATT protocol work, connection and reconnect logic, concurrency, architecture, bugs nobody can explain yet |
| Design | **Opus** preferred, Sonnet OK | UI/UX design, screen layouts, visual polish, theming |

Concurrency limits, at most running at the same time:

- 10 Haiku agents
- 2 Sonnet agents
- 1 Opus agent

When unsure, start one tier lower and escalate if the agent struggles.

## Project

- Native Android app for the Storz & Bickel Venty and Volcano Hybrid, for personal use. The Mighty+ has no Bluetooth, so it can't be supported.
- Kotlin + Jetpack Compose (Material 3). Package `io.github.orth4x0r.venty`.
- The BLE protocol is in [docs/PROTOCOL.md](docs/PROTOCOL.md). Read it before touching device code. It has not been verified on a real device yet, so treat anything marked unconfirmed as a guess.

## Building

- Builds and tests run on the owner's PC (Android Studio), not in CI.
- GitHub Actions is manual-only (`workflow_dispatch`) to avoid cost. Do not add push or PR triggers, and do not start workflow runs unless the owner asks.
- Cloud containers usually can't reach Google Maven, so Gradle builds there are expected to fail.
- `./gradlew -p tools/night-preview test run` works from Maven Central alone. It compiles the pure-Compose SubVenty terminal files against Compose for Desktop, runs their JVM tests and renders review PNGs to `tools/night-preview/out`. Keep those files free of Android types so this keeps working. Maven Central sometimes returns 429; wait and retry.
