<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="SafeStride 5K App Icon" width="112" />
</p>

<h1 align="center">SafeStride 5K</h1>

<p align="center">
  An offline-first Android interval running &amp; Couch-to-5K training app built with Kotlin, Jetpack Compose, Foreground Services, Room, and Google Maps.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.0.21" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-3DDC84?logo=android&logoColor=white" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-3DDC84?logo=android&logoColor=white" alt="Target SDK 34" />
  <img src="https://img.shields.io/badge/Tests-103%20Passing-2E7D32" alt="103 Automated Tests Passing" />
</p>

---

## Overview

**SafeStride 5K** guides beginners from zero to running 5 kilometers through a structured 9-week Couch-to-5K training plan, progressive post-5K extensions, open-ended Free Run / Free Walk sessions, and a custom interval workout builder. Everything runs locally on-device with no account, backend, or network dependency required for core workouts.

> **Download:** Grab the latest signed APK (`v1.0.0`) from the [GitHub Releases](https://github.com/sokar5555/SafeStride-5K-App/releases/tag/v1.0.0) page.

---

## Key Features

- **Structured 9-Week C25K Program + Progressive Extensions**
  - Pre-seeded 27-workout Couch-to-5K progression (Warmup, Run, Walk, and Cooldown intervals).
  - Optional post-Week 9 progressive volume extensions (+10% weekly running volume) with configurable designated Long Run day.
- **Open Exercise (Free Run / Free Walk) & Custom Workout Builder**
  - Count-up **Free Run** and **Free Walk** modes with live in-workout switching and segmented duration/distance tracking.
  - Custom Workout Builder with editable Warmup/Cooldown, quick duration presets, long-press drag-to-reorder, and persistent Room storage.
- **Lock-Screen Resilient Foreground Workout Engine**
  - Authoritative timer anchored to `SystemClock.elapsedRealtime()` so pauses, resumes, backgrounding, and screen lock remain drift-free.
  - Persistent Foreground Service notification with live interval, elapsed time, distance, and current split pace (`This Mile Pace` / `This Km Pace`), plus Home screen recovery banner (`Workout in Progress`) if the UI is closed mid-run.
- **GPS Route Tracking & Noise Filtering**
  - `FusedLocationProviderClient` tracking piped through a dedicated `LocationFilter` that discards missing/poor accuracy fixes, stale/out-of-order timestamps, stationary drift (`< 2m`), and impossible speed spikes (`> 12 m/s`).
  - Interactive Google Maps route replay in Workout Details with per-interval distance breakdown.
- **Voice Coaching, Audio Focus Ducking & Haptics**
  - Text-to-Speech interval announcements, distance split callouts, and structured **30s / 10s / 5s** countdown cues.
  - Transient background audio focus ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) for music/podcasts alongside toggleable transition tones and vibration.
- **Training Calendar, Statistics & Local JSON Backup**
  - Interactive monthly schedule generated from your start date and preferred 3 training days/week, tracking `Completed`, `Available`, `Upcoming`, and `Skipped` sessions.
  - Analytics dashboard covering cumulative totals, Run vs. Walk ratio, Time-of-Day distribution, Weekly Distance chart, Program Progress, and **Longest Continuous Run** progression with Personal Record celebrations.
  - Full local JSON backup export & validated atomic import/restore via Android Storage Access Framework (`org.json`, zero third-party serialization dependencies).
- **Theming & Edge-to-Edge Polish**
  - Clean white-and-blue Material 3 Light theme by default, with user-selectable **Light / Dark / System** theme support and edge-to-edge system bar handling.

---

## Architecture

SafeStride 5K follows clean unidirectional data flow (UDF) with strict separation between UI, domain logic, timer engine, GPS tracking, and persistence:

```text
app/src/main/java/com/example/couchto5k/
├── data/
│   ├── backup/        # BackupJsonCodec (org.json) & DefaultBackupRepository (SAF export/restore)
│   ├── local/         # Room AppDatabase (v5), DAOs, Entities, TypeConverters
│   └── repository/    # TrainingPlan, WorkoutHistory, and DataStore Settings repositories
├── di/                # Hilt dependency injection modules
├── domain/
│   ├── model/         # Immutable domain models (TrainingWorkout, UserStats, ThemeMode, etc.)
│   ├── repository/    # Repository interfaces
│   └── usecase/       # GenerateScheduleUseCase, GetCombinedScheduleUseCase, GetStatsUseCase
├── engine/            # WorkoutEngine (elapsedRealtime state machine) & AudioHapticManager
├── location/          # LocationFilter & LocationPoint
├── service/           # WorkoutTrackingService (Foreground Service, GPS & notification updates)
└── ui/                # Jetpack Compose screens, ViewModels, Navigation, Material 3 Theme
```

### Engineering Highlights

| Concern | Implementation |
| --- | --- |
| **Drift-free Timing** | `WorkoutEngine` derives interval remaining time and total elapsed time from `SystemClock.elapsedRealtime()` timestamps minus accumulated pause intervals—never from UI tick counts. |
| **Single Source of Truth** | `WorkoutTrackingService` owns the active session lifecycle; `ActiveWorkoutViewModel` detects and re-attaches to active `RUNNING`/`PAUSED` sessions on configuration change or process UI recreation without resetting state. |
| **GPS Accuracy Hygiene** | `LocationFilter` enforces accuracy thresholds (`0 < accuracy <= 30m`), monotonic timestamps, minimum displacement (`2m`), and max plausible speed (`12 m/s`), and resets anchors between workouts. |
| **Zero Cloud Lock-In** | All workouts, GPS points, schedule preferences, and JSON backups stay on-device using Room, Preferences DataStore, and `org.json`. |

---

## Tech Stack

- **Language:** Kotlin (`2.0.21`), Coroutines & `StateFlow`
- **UI:** Jetpack Compose (BOM `2024.02.00`), Material 3, Navigation Compose (`2.7.7`)
- **DI:** Hilt (`2.60.1`) with KSP (`2.0.21-1.0.28`)
- **Local Storage:** Room (`2.6.1`), Preferences DataStore (`1.0.0`)
- **Location & Maps:** Google Play Services Location (`21.2.0`), Maps Compose (`4.3.3`)
- **Testing:** JUnit 4, `kotlinx-coroutines-test`, Room in-memory DB, Robolectric (`4.14.1`), Compose UI Test

---

## Testing

The test suite includes **103 automated tests** across unit, Room database integration, and Robolectric Compose UI layers:

- **Engine & Audio Timing:** `WorkoutEngineTest` (multi-pause drift invariance, countdown voice thresholds, split pace/audio cues, Open Exercise switching)
- **GPS Filtering:** `LocationFilterTest` (invalid accuracy, stale/out-of-order timestamps, stationary jitter, speed-cap boundaries, cross-workout reset)
- **Domain Use Cases:** `GenerateScheduleUseCaseTest`, `GetCombinedScheduleUseCaseTest` (month boundaries, late/cancelled completions, progressive weeks), `GetStatsUseCaseTest`
- **Persistence & Backup:** `WorkoutHistoryRepositoryTest` (Room in-memory DB integration), `BackupJsonCodecTest`, `DefaultBackupRepositoryTest`, `DefaultSettingsRepositoryTest`, `CustomWorkoutDaoTest`
- **ViewModels & Critical UI Flows:** `ActiveWorkoutViewModelTest`, `SettingsViewModelTest`, `CalendarViewModelTest`, `WorkoutBuilderViewModelTest`, `OnboardingViewModelTest`, `StatsViewModelTest`, `WorkoutDetailViewModelTest`, and `CriticalFlowsUiTest`

Run all unit & Robolectric UI tests:

```bash
./gradlew testDebugUnitTest
```

---

## Building Locally

1. **Clone the repository**
   ```bash
   git clone https://github.com/sokar5555/SafeStride-5K-App.git
   cd SafeStride-5K-App
   ```
2. **Add your Google Maps API key** in `local.properties` (gitignored):
   ```properties
   sdk.dir=/path/to/Android/Sdk
   MAPS_API_KEY=your_google_maps_api_key
   ```
   *(Optional for signed release builds: also define `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` in `local.properties`.)*
3. **Build & run tests**
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```
