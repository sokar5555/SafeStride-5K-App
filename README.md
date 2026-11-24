<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="SafeStride 5K App Icon" width="112" />
</p>

<h1 align="center">SafeStride 5K</h1>

<p align="center">
  An offline-first Couch-to-5K running app for Android, built with Kotlin, Jetpack Compose, Room, and Google Maps.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Built%20With-Antigravity-1A73E8" alt="Built With Antigravity" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.0.21" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-3DDC84?logo=android&logoColor=white" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-3DDC84?logo=android&logoColor=white" alt="Target SDK 34" />
  <img src="https://img.shields.io/badge/Tests-103%20Passing-2E7D32" alt="103 Automated Tests Passing" />
</p>

---

## About the App

SafeStride 5K takes you from zero to running a 5K without accounts, subscriptions, or cloud sync. Everything stays on your phone. It includes the standard 9-week Couch-to-5K interval plan, optional post-5K progression weeks, open-ended Free Run and Free Walk tracking, and a custom interval workout builder.

> **Download:** You can grab the signed `v1.0.0` APK on the [GitHub Releases](https://github.com/sokar5555/SafeStride-5K-App/releases/tag/v1.0.0) page.

---

## How It Was Built (Antigravity + Human Review)

This app wasn't generated from a single prompt. I built it across 10 phases using **Antigravity** as the autonomous coding agent, paired with constant back-and-forth review and real-device testing.

### Setting the Rules Up Front
Before writing app code, I wrote [`AGENTS.md`](AGENTS.md) and [`PROJECT_PLAN.md`](PROJECT_PLAN.md) to set clear architectural boundaries:
- Keep business logic and GPS code out of Compose UI and ViewModels.
- Drive the workout timer off `SystemClock.elapsedRealtime()` instead of UI ticks so screen locks and pauses never cause timer drift.
- Store distances in meters everywhere and only convert for display.
- Keep everything local on-device with no backend or accounts.
- Log any intentional changes to the original plan in [`IMPLEMENTATION_NOTES.md`](IMPLEMENTATION_NOTES.md).

### Autonomous Phase Execution
With those guardrails in place, Antigravity worked through the project phase by phase on separate branches (`Phase-0` through `Phase-9`). For each phase, the agent inspected the existing code, wrote the Room entities/DAOs, Hilt wiring, foreground service, and Compose screens, wrote tests to cover them, and fixed its own compile or test errors before finishing.

### The Back-and-Forth Loop
Most of the quality came from going back and forth after each autonomous pass:

- **Code Review:** Reviewing diffs caught real edge cases early. Together we replaced Mockito mocks with simple fakes, stopped cancelled workouts from marking calendar days as completed, hardened GPS filtering against stale timestamps and cross-workout jumps, fixed active-workout recovery when reopening the app mid-run, and pulled a hardcoded Maps API key out into `local.properties` before cutting a release.
- **On-Device UI/UX Tweaks:** Running builds directly on a Pixel surfaced feel issues that unit tests can't catch. A lot of iterations went into tuning overscroll bounce and tap recoil so cards wouldn't shift under your finger, shortening navigation transitions that briefly blocked taps, anchoring the bottom bar so tabs didn't jump, keeping `START NOW` / `START WORKOUT` labels on a single line, cleaning up active-run controls, and dialing in edge-to-edge insets and Dark Mode colors.
- **Adding Features Beyond the Original MVP:** Once the core 9-week plan felt solid, we kept expanding the scope through discussion: adding the Custom Workout Builder with drag-to-reorder, Free Run / Free Walk with live mid-workout switching, optional +10% weekly post-5K extensions with a configurable Long Run day, 30s/10s/5s countdown voice cues that duck background music, and local JSON export/restore.

---

## Features

- **9-Week C25K Plan + Post-5K Extensions:** 27 pre-seeded workouts (Warmup, Run, Walk, Cooldown), plus optional progressive weeks that increase weekly running volume by 10% and let you pick your Long Run day.
- **Free Run / Free Walk & Custom Workouts:** Count-up open exercise mode where you can switch between running and walking mid-session, alongside a Custom Workout Builder with duration presets, drag-to-reorder intervals, and Room persistence.
- **Lock-Screen Safe Workout Engine:** Timestamps anchored to `SystemClock.elapsedRealtime()`, backed by a Foreground Service notification showing your current interval, elapsed time, distance, and split pace (`This Mile Pace` / `This Km Pace`). If you close the app mid-run, Home shows a `Workout in Progress` banner to jump back in.
- **GPS Tracking & Route Maps:** `FusedLocationProviderClient` fed through `LocationFilter` to drop poor-accuracy fixes (`<= 30m` required), out-of-order timestamps, tiny GPS jitter (`< 2m`), and unrealistic speed spikes (`> 12 m/s`). Completed workouts show the recorded route on Google Maps with per-interval distances.
- **Voice Cues, Audio Ducking & Vibration:** Text-to-Speech interval callouts, distance split announcements, and 30s / 10s / 5s countdown cues that briefly duck your music or podcast (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), plus optional transition tones and vibration.
- **Schedule, Stats & Local Backups:** Monthly training calendar built from your start date and 3 preferred workout days, a stats dashboard tracking total distance, Run vs. Walk ratio, time-of-day breakdown, weekly mileage, and Longest Continuous Run personal records, and full local JSON backup/restore via Android's Storage Access Framework (`org.json`).
- **Light, Dark & System Themes:** White-and-blue Material 3 Light theme by default, with Light / Dark / System options in Settings and proper edge-to-edge system bar handling.

---

## Project Structure

```text
app/src/main/java/com/example/couchto5k/
├── data/
│   ├── backup/        # JSON codec (org.json) & SAF export/import repository
│   ├── local/         # Room AppDatabase (v5), DAOs, Entities, TypeConverters
│   └── repository/    # Training plan, workout history, and DataStore settings
├── di/                # Hilt modules
├── domain/
│   ├── model/         # Domain models (TrainingWorkout, UserStats, ThemeMode, etc.)
│   ├── repository/    # Repository interfaces
│   └── usecase/       # Schedule generation, combined schedule, and stats math
├── engine/            # WorkoutEngine state machine & AudioHapticManager
├── location/          # LocationFilter & LocationPoint
├── service/           # WorkoutTrackingService (Foreground Service & GPS collection)
└── ui/                # Compose screens, ViewModels, navigation, and theme
```

---

## Tech Stack

- **Kotlin** (`2.0.21`), Coroutines & `StateFlow`
- **Jetpack Compose** (BOM `2024.02.00`), Material 3, Navigation Compose (`2.7.7`)
- **Hilt** (`2.60.1`) with KSP (`2.0.21-1.0.28`)
- **Room** (`2.6.1`) & **Preferences DataStore** (`1.0.0`)
- **Play Services Location** (`21.2.0`) & **Maps Compose** (`4.3.3`)
- **Testing:** JUnit 4, `kotlinx-coroutines-test`, Room in-memory DB, Robolectric (`4.14.1`), Compose UI Test

---

## Testing

There are **103 automated tests** covering the timer engine, GPS filter, schedule/stats use cases, Room database & JSON backup persistence, ViewModels, and Compose UI flows (`CriticalFlowsUiTest`).

Run the full suite locally:

```bash
./gradlew testDebugUnitTest
```

---

## Building Locally

1. Clone the repo:
   ```bash
   git clone https://github.com/sokar5555/SafeStride-5K-App.git
   cd SafeStride-5K-App
   ```
2. Add your Android SDK path and Google Maps API key to `local.properties` (gitignored):
   ```properties
   sdk.dir=/path/to/Android/Sdk
   MAPS_API_KEY=your_google_maps_api_key
   ```
   *(For signed release builds, also set `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` in `local.properties`.)*
3. Run tests and build:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```
