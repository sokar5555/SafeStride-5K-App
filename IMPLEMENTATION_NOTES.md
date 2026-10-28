# Implementation Notes

- Added `gradle/libs.versions.toml` defining a single source of truth for library versions to eliminate dependency issues. Added Kotlin, Compose, Hilt, Room, DataStore, JUnit etc., relying on up to date versions matching modern Android projects.
- Set up `settings.gradle.kts` properly scoping `google()` to prevent downloading issues or rate limit 429s.
- Made dynamic color false by default in `SafeStride5KTheme` to preserve the White/Blue Material 3 theme.
- Custom Workout Builder feature added to Home screen and Room DB (`CustomWorkoutEntity`, `CustomWorkoutIntervalEntity`, `CustomWorkoutDao`). Standard workouts and custom workouts seamlessly map to `TrainingWorkout` domain model and integrate with `TrainingPlanRepository` using an ID offset (>= 10000L).
- `HomeScreen` displays a "Build Custom Workout" feature card, quick access to C25K 9-week program, and a list of Saved Custom Workouts at the bottom.
- `WorkoutBuilderScreen` pre-populates Warmup (5 min) and Cooldown (5 min), allows adding custom Run and Walk intervals for any duration via duration picker dialog, and includes "FINISH BUILDING WORKOUT" and "SET WORKOUT NOW" action buttons.
- GPS and Workout foreground service active tracking implemented in subsequent active workout service phase.