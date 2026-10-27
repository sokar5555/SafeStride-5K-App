```markdown
# Agent Instructions

Read PROJECT_PLAN.md before making architectural decisions.

Implement only the phase or task explicitly assigned.

Always inspect existing code before changing architecture.

Run relevant Gradle builds and tests before declaring work complete.
Fix compilation and test failures caused by your changes before stopping.

Do not remove, disable, or weaken tests merely to make a build pass.

Do not replace the white-and-blue Material theme.
Do not enable dynamic color as the default.

Do not introduce a backend, account system, cloud sync, social features,
or other non-MVP functionality unless explicitly requested.

Keep business logic outside composables.
Keep GPS implementation outside composables and ViewModels.
Keep training-plan definitions separate from the timer engine.
Use timestamps/elapsed time rather than UI tick counts for authoritative timers.
Store distance internally in meters.

Once WorkoutTrackingService exists, treat it as the authoritative source of
active-workout state.

Do not commit API keys, signing keys, credentials, local.properties, or other
secrets.

If a requirement is ambiguous and the choice would materially affect the
architecture, stop and report the decision rather than inventing a major
product requirement.

Record material architectural deviations from PROJECT_PLAN.md in
IMPLEMENTATION_NOTES.md.

At the end of every task report:
1. What you changed
2. Important files changed
3. Tests/build commands run and their results
4. Anything requiring manual verification
5. Known limitations
6. Recommended next task

### Build & Dependency Management
- Target the newest stable versions of Gradle, Android Gradle Plugin (AGP), Kotlin, and Jetpack libraries. Do not use alpha/beta/RC versions unless absolutely necessary for the MVP.
- Always use a Version Catalog (`gradle/libs.versions.toml`) to manage dependencies. Do not hardcode version strings in `build.gradle.kts` files.
- Prefer KSP (Kotlin Symbol Processing) over KAPT for all annotation processing (Hilt, Room, etc.).
- When upgrading Kotlin, explicitly verify that the KSP and Compose Compiler plugins are compatible with the new Kotlin version before committing.
- Never leave the build in a broken state. Resolve all Gradle sync errors, dependency conflicts, and compilation failures before proceeding to application logic.

```

Commit this file.
