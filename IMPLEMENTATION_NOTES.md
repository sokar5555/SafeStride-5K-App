# Implementation Notes

- Added `gradle/libs.versions.toml` defining a single source of truth for library versions to eliminate dependency issues. Added Kotlin, Compose, Hilt, Room, DataStore, JUnit etc., relying on up to date versions matching modern Android projects.
- Set up `settings.gradle.kts` properly scoping `google()` to prevent downloading issues or rate limit 429s.
- Made dynamic color false by default in `SafeStride5KTheme` to preserve the White/Blue Material 3 theme.
- For Phase 1 MVP, `HomeScreen` delegates to `WorkoutSelectionScreen`.
- `Divider()` is used which is deprecated in favor of `HorizontalDivider()`, but it compiles fine without failing.
- GPS and Workout foreground service deliberately left out to be implemented in a subsequent phase.