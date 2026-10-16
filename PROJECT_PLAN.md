# Couch to 5K Android App — Agent Implementation Plan

## 1. Mission
Build a polished, native Android Couch-to-5K application that guides a beginner from run/walk intervals toward approximately 30 minutes of continuous running and a 5K. The MVP is local-first and must include a training plan/calendar, reliable interval timers, audio/haptic cues, GPS tracking, distance and pace, route maps, workout history, cumulative statistics, and running-progression statistics.

The primary progression metric is **Longest Continuous Run**. This is a core product feature, not optional polish.

## 2. MVP Boundaries
Build:
- Structured 9-week beginner plan, 3 workouts/week
- Home dashboard and next-workout flow
- Calendar and scheduling
- Generic run/walk interval engine
- Audio, sound, and vibration cues
- Foreground workout tracking
- GPS route collection
- Distance and average pace
- Post-workout route map
- Persistent workout history
- Cumulative stats and charts
- Longest-continuous-run progression
- Miles/kilometers setting
- Short onboarding

Do not build for MVP:
- Accounts or backend
- Cloud sync
- Social/friends/leaderboards
- Wear OS
- Strava integration
- Health Connect
- Heart-rate support
- Music integration
- AI coaching
- Custom plan builder
- Live workout map
- Automatic run/walk classification
- Advanced cadence/elevation/split analytics

## 3. Required Stack
Use the latest stable, mutually compatible versions at implementation time.
- Kotlin
- Jetpack Compose
- Material 3
- MVVM / unidirectional data flow
- Coroutines and Flow/StateFlow
- Room
- DataStore
- Hilt
- Google Play Services Fused Location Provider
- Google Maps Compose
- Android foreground service for active workout tracking

No backend is required for MVP.

## 4. Architectural Rules
1. Keep business logic out of composables.
2. Keep GPS logic out of composables and ViewModels.
3. Use repositories as data-source boundaries.
4. Room is the source of truth for completed workout data.
5. Once implemented, the foreground workout service is authoritative for active-workout state.
6. Use timestamps/elapsed time for timer truth; never rely on decrementing UI counters.
7. Store distance canonically in meters; convert only for presentation.
8. Store durations consistently in milliseconds or seconds.
9. Store the training plan as structured data; never hard-code transitions in UI code.
10. Use semantic Material theme roles; do not scatter hard-coded colors through screens.
11. Work through phases sequentially unless a dependency requires otherwise.
12. Document architectural deviations in `IMPLEMENTATION_NOTES.md` before or with the deviation.
13. Do not remove or weaken tests simply to make a build pass.
14. Run relevant Gradle builds/tests before declaring a task complete.

## 5. Package Structure
```text
com.example.couchto5k
├── data
│   ├── local
│   │   ├── dao
│   │   ├── database
│   │   └── entity
│   ├── location
│   ├── repository
│   └── preferences
├── domain
│   ├── model
│   ├── repository
│   └── usecase
├── service
│   └── WorkoutTrackingService.kt
├── ui
│   ├── home
│   ├── calendar
│   ├── workout
│   ├── history
│   ├── stats
│   ├── settings
│   ├── onboarding
│   ├── navigation
│   ├── components
│   └── theme
└── MainActivity.kt
```
Start with a single app module unless modularization becomes demonstrably useful.

## 6. Navigation
Bottom navigation:
1. Home
2. Calendar
3. History
4. Stats

Additional routes:
- Workout Preview
- Active Workout (outside normal bottom-nav hierarchy)
- Workout Detail
- Settings
- Onboarding

## 7. Visual System
Design intent: clean, calm, modern, athletic, readable while moving. Primarily white with restrained blue accents.

Suggested light palette:
```text
Primary Blue       #1565C0
Dark Blue          #0D47A1
Light Blue         #E3F2FD
Very Light Blue    #F3F8FE
Background         #FFFFFF
Surface            #FFFFFF
Surface Variant    #F4F7FA
Primary Text       #17212B
Secondary Text     #5F6B76
Outline            #D7E0E8
Success            #2E7D32
Warning            #ED6C02
Error              #BA1A1A
```

Rules:
- Do not enable dynamic color as the default; preserve the branded white/blue identity.
- Use blue for primary actions, active run state, important stats, and selected states.
- Use light blue for secondary cards, calendar selection, upcoming workouts, and chart surfaces.
- Use rounded cards (~12–16dp), generous whitespace, minimal elevation, large numeric typography, simple icons, and large touch targets.
- Dark mode is not required for MVP.

## 8. Training Plan Domain
Use a generic interval model:
```kotlin
enum class IntervalType { WARMUP, WALK, RUN, COOLDOWN }

data class WorkoutInterval(
    val sequence: Int,
    val type: IntervalType,
    val durationSeconds: Int
)

data class TrainingWorkout(
    val id: Long,
    val week: Int,
    val workoutNumber: Int,
    val title: String,
    val intervals: List<WorkoutInterval>
)
```

Seed a 9-week beginner plan with 3 workouts/week. It should progress from short run/walk intervals to roughly 30 minutes continuous running. Keep plan definitions separate from timer/business logic so the plan can be changed later.

Every workout must expose/calculably provide:
- Week and workout number
- Total planned duration
- Total planned running time
- Total planned walking time
- Longest planned running interval
- Ordered interval sequence

Users may repeat workouts or weeks. Completing a workout advances the recommended next workout but must not lock previous workouts or permanently lock later workouts.

## 9. Running Progression — First-Class Feature
Track at minimum:
- Longest completed continuous running interval
- Total running time
- Total walking time
- Running percentage of workout
- Completed workout number
- Program week reached

For MVP, a continuous run is a RUN interval actually completed by the user. Do not infer running versus walking from GPS.

Persist completed interval timing so skipped/interrupted run intervals do not incorrectly count as completed.

Main Stats UI should emphasize:
```text
Longest Continuous Run
Starting longest run: 1:00
Current longest run: 18:00
Improvement: +17:00
```

Progression chart should preferably show cumulative record progression. If workout results are 1, 1, 3, 2, 5 minutes, the headline record series is 1, 1, 3, 3, 5.

## 10. Home Screen
Show:
- Greeting/header
- Current program week and workout progress
- Next Workout card
- Program completion progress
- Recent/longest-run accomplishment

Example next-workout card:
```text
NEXT WORKOUT
Week 4 • Workout 2
31 min
16 min running
Longest run: 5 min
[ START WORKOUT ]
```

## 11. Calendar
Onboarding/settings should collect:
- Program start date
- Three preferred workout days/week

Suggested default: Monday / Wednesday / Saturday, but editable.

Calendar must distinguish:
- Completed workout
- Today's workout
- Upcoming workout
- Missed/incomplete scheduled workout
- Rest day

Store scheduled date separately from actual completion date. A late completion must not corrupt progression. Allow repeating workouts/weeks.

## 12. Workout Preview
Show:
- Week/workout number
- Estimated duration
- Total running time
- Longest run interval
- Interval breakdown
- GPS readiness/status
- Start button

## 13. Active Workout UI
Prioritize glanceability. Show only essential information:
- Current interval type
- Interval countdown
- Next interval
- Total elapsed time
- Distance
- Average pace
- Pause/resume

Do not put a live map or dense charts on the MVP active-workout screen.

## 14. Workout Engine
Create a dedicated engine independent of Compose supporting:
- Start
- Pause
- Resume
- Advance interval
- Previous interval
- Complete
- Cancel
- Restore active workout

Suggested state:
```kotlin
data class ActiveWorkoutState(
    val workoutId: Long,
    val currentIntervalIndex: Int,
    val intervalRemainingMillis: Long,
    val totalElapsedMillis: Long,
    val isPaused: Boolean,
    val distanceMeters: Double,
    val startedAt: Instant
)
```

Use timestamps/elapsed realtime for authoritative calculations so delayed UI updates do not cause timer drift.

## 15. Audio & Haptics
At interval transitions:
- Vibrate
- Play audible cue
- Optionally use Android Text-to-Speech

Examples:
- “Start running. Five minutes.”
- “Walk for two minutes.”
- “Begin cool down.”
- “Workout complete.”

Settings:
- Voice cues on/off
- Sound cues on/off
- Vibration on/off

## 16. GPS Tracking
Use `FusedLocationProviderClient` during active workouts.

Each accepted point should include at least:
```kotlin
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float,
    val timestamp: Long
)
```

Filter obviously bad data such as:
- Extremely poor accuracy
- Impossible jumps
- Duplicate positions
- Clearly invalid points

Do not implement map matching for MVP.

Handle approximate location gracefully. Explain that precise location improves distance, pace, and route mapping. Permission denial must not crash the app.

## 17. Foreground Workout Service
`WorkoutTrackingService` is responsible for:
- Active workout state
- Timer progression
- GPS requests
- Distance accumulation
- Notification updates
- Interval transitions
- Audio/haptic cues
- Persisting recoverable session state
- Continuing while screen is locked/app UI is backgrounded

Declare the correct current Android foreground-service type and permissions for location at implementation time.

Ongoing notification example:
```text
Couch to 5K
RUN • 4:32 remaining
1.42 mi • 18:21 elapsed
[ PAUSE ] [ STOP ]
```

## 18. Distance & Pace
Calculate distance between accepted consecutive GPS points. Store meters internally.

Support display units:
- miles / min per mile
- kilometers / min per km

MVP primary pace metric: average workout pace. Avoid presenting unstable instantaneous GPS pace as the primary value.

Aggregate average pace must be calculated from total eligible time / total distance, not by averaging workout pace values.

## 19. Route Map
After completion, display the recorded route using Google Maps Compose:
- Route polyline
- Start marker
- Finish marker
- Camera automatically frames route

No live workout map is required for MVP.

## 20. Persistence Model
Suggested Room entities:

### TrainingWorkoutEntity
- id
- week
- workoutNumber
- title
- plannedDurationSeconds
- plannedRunSeconds
- longestRunIntervalSeconds

### TrainingIntervalEntity
- id
- workoutId
- sequence
- intervalType
- durationSeconds

### ScheduledWorkoutEntity
- id
- trainingWorkoutId
- scheduledDate
- status

### CompletedWorkoutEntity
- id
- trainingWorkoutId
- scheduledWorkoutId nullable
- startedAt
- completedAt
- durationSeconds
- runningSeconds
- walkingSeconds
- distanceMeters
- averagePaceSecondsPerKm
- longestCompletedRunSeconds
- status

### CompletedIntervalEntity
- id
- completedWorkoutId
- trainingIntervalId
- intervalType
- plannedDurationSeconds
- actualDurationSeconds
- completed
- sequence

### LocationPointEntity
- id
- completedWorkoutId
- timestamp
- latitude
- longitude
- altitude
- accuracyMeters
- sequence

Repositories:
- TrainingPlanRepository
- WorkoutHistoryRepository
- WorkoutTrackingRepository
- SettingsRepository

Use DataStore for:
- Distance units
- Preferred workout days
- Program start date
- Voice cues
- Sound cues
- Vibration
- Onboarding complete

## 21. History & Workout Detail
History: newest completed workouts first, showing date, week/workout, distance, duration, pace, completion status.

Workout Detail must show:
- Distance
- Total time
- Average pace
- Running time
- Walking time
- Longest run
- Route map
- Interval breakdown and completion state

## 22. Stats Dashboard
Four sections:

### Overall
- Workouts completed
- Total distance
- Total workout time
- Total running time
- Aggregate average pace
- Longest workout

### Running Progression
- Current longest continuous run
- Improvement from starting point
- Record-progression chart

### Distance Progression
- Weekly distance chart

### Program Progress
- Completed / total workouts
- Current week
- Program completion percentage

Required calculations:
- COUNT completed workouts
- SUM distance
- SUM duration
- SUM running time
- SUM walking time
- MAX distance
- MAX longest completed run
- Correct weighted/aggregate pace

## 23. Program Status
Suggested status enum:
```kotlin
enum class WorkoutStatus {
    UPCOMING, AVAILABLE, IN_PROGRESS, COMPLETED, SKIPPED
}
```

Encourage sequential progression but do not permanently lock later workouts. Allow repeating completed workouts, repeating weeks, starting missed workouts late, and continuing from the last completed workout.

## 24. Recovery
An active workout should survive/recover from:
- Activity recreation
- Screen rotation/configuration change
- Screen lock
- UI going to background
- Recoverable process/UI interruption where practical

Never store authoritative active timer state solely in a composable or ViewModel.

## 25. Onboarding
Keep short:
1. Welcome / purpose
2. Miles or kilometers
3. Select 3 training days
4. Select start date
5. Request permissions contextually when their purpose is understandable

Do not request every permission immediately on first launch.

## 26. Accessibility
Minimum:
- Large touch targets
- Adequate contrast
- Content descriptions for meaningful icons
- Do not communicate state only by color
- Support system font scaling
- Timer remains usable with large fonts
- Prefer text labels for important controls

## 27. Implementation Phases

### Phase 0 — Bootstrap
Tasks:
- Create Kotlin/Compose project
- Material 3 white/blue theme
- Navigation
- Hilt
- Room
- DataStore
- Package structure
- Placeholder Home/Calendar/History/Stats screens

Done when: app builds/opens, bottom navigation works, theme is visible.

### Phase 0.5: Build & Toolchain Modernization (Foundation)
- [ ] Implement Gradle Version Catalog (`gradle/libs.versions.toml`) to centralize all dependency versions and prevent mismatch errors.
- [ ] Migrate annotation processors from KAPT to KSP (Kotlin Symbol Processing) for faster builds and modern Kotlin 2.0 compatibility.
- [ ] Audit and upgrade core toolchain to newest stable:
  - Gradle & Android Gradle Plugin (AGP)
  - Kotlin 2.0+ & Jetpack Compose Compiler
  - Hilt & Room (KSP compatible versions)
  - 
### Phase 1 — Training Plan
Tasks:
- Domain models
- Interval types
- Seed full program
- Repository
- Workout selection/preview
- Summary calculations
- Unit tests

Tests: duration, run duration, longest run, interval sequence, ordering.

Done when: every workout can be browsed and inspected as structured data.

### Phase 2 — Timer / Workout Engine
Tasks:
- Start/countdown/transitions
- Pause/resume
- Previous/next
- Cancel/complete
- Audio/haptics
- Restore state

Done when: a complete workout can be executed without GPS, pauses do not cause drift, and recreation does not reset it.

### Phase 3 — Persistence & History
Tasks:
- Persist completed workouts/intervals
- Actual interval durations
- Longest completed run
- History screen
- Workout detail

Done when: completed workouts survive app restart and longest run is correct.

### Phase 4 — Calendar
Tasks:
- Start date
- Preferred days
- Schedule generation
- Month calendar
- Completed/upcoming/missed states
- Late completion/repeats

Done when: full schedule is visible and scheduled vs. actual completion dates remain distinct.

### Phase 5 — GPS & Foreground Service
Tasks:
- Permission flow
- Fused location
- WorkoutTrackingService
- Foreground notification
- Screen-lock/background tracking
- Distance
- GPS filtering

Done when: an outdoor workout can continue while locked and restore correct time, interval, distance, and state.

### Phase 6 — Maps & Pace
Tasks:
- Persist route points
- Maps Compose
- Route polyline/start/end
- Average pace
- Miles/km

Done when: completed GPS workouts show a route and persisted distance/pace.

### Phase 7 — Stats & Running Progression
Tasks:
- Cumulative statistics
- Longest-run progression chart
- Weekly distance chart
- Program progress
- Personal-best update

Done when: a newly completed workout immediately updates stats and a new longest run updates the record/progression correctly.

### Phase 8 — UX Polish
Tasks:
- Empty/loading/error states
- Permission-denied/GPS-disabled states
- Cancel confirmation
- Completion celebration
- Accessibility review
- Settings
- Appropriate animations

### Phase 9 — QA
Timer scenarios:
- pause/resume
- screen lock/background
- recreation
- long workout
- interval changes
- finish/cancel

GPS scenarios:
- good/poor signal
- GPS disabled
- permission denied
- approximate/precise location
- stationary user
- screen locked

Calendar scenarios:
- month boundaries
- repeat/missed/late workouts
- week transition

Stats scenarios:
- zero/one/many workouts
- repeated workouts
- new personal record
- non-record workout

## 28. Automated Test Priorities
Unit tests:
- Timer state machine
- Workout progression
- Interval calculations
- Distance calculations
- Pace calculations
- Running-record calculations
- Calendar scheduling
- Aggregate statistics

Database tests:
- Workout insertion
- Completed intervals
- Route points
- Aggregates
- History ordering

UI tests for critical flows:
- Launch
- Open workout
- Start
- Pause/resume
- Complete
- History
- Stats

## 29. MVP Definition of Done
A new user can:
1. Install/open the app.
2. Choose miles/km.
3. Select 3 workout days.
4. Select start date.
5. See schedule on calendar.
6. Open next workout.
7. Inspect intervals.
8. Start workout.
9. Receive run/walk prompts.
10. Lock phone while workout continues.
11. Continue GPS recording.
12. Pause/resume.
13. Complete workout.
14. See distance/time/pace.
15. See route map.
16. See workout in history.
17. See cumulative stats.
18. See longest continuous run.
19. See running progression over time.
20. Continue to next workout.

All 20 must work reliably before calling MVP functionally complete.

## 30. Agent Execution Protocol
For every assigned phase/task:
1. Read this file and `AGENTS.md` if present.
2. Inspect existing implementation before changing architecture.
3. State/record assumptions when requirements are ambiguous.
4. Implement only the requested phase unless a small dependency is essential.
5. Add/update tests with the implementation.
6. Run relevant unit tests and Gradle build/check tasks.
7. Fix failures caused by the change.
8. Do not delete tests or features to hide failures.
9. Record material architectural deviations in `IMPLEMENTATION_NOTES.md`.
10. Return a concise completion report containing:
   - What changed
   - Files/areas changed
   - Tests/build commands run and results
   - Known limitations or manual verification still required
   - Recommended next phase

## 31. First Assignment
Implement **Phases 0 and 1 only**.

Deliver:
- Runnable Android app
- White/blue Material 3 theme
- Bottom navigation
- Placeholder Home/Calendar/History/Stats
- Training-plan domain model
- Seed 9-week program
- Workout-selection UI
- Workout preview
- Unit tests for training calculations

Do not implement GPS, maps, or the foreground service in the first assignment.

Architectural dependency:
```text
TRAINING PLAN
     ↓
WORKOUT ENGINE
     ↓
┌───────────────┐
↓               ↓
INTERVAL TIMER  GPS TRACKER
└───────┬───────┘
        ↓
COMPLETED WORKOUT
        ↓
┌───────┼────────┐
↓       ↓        ↓
HISTORY MAP     STATS
                 ↓
        RUNNING PROGRESSION
```
