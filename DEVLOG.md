# Development Log

A running record of user prompts and Claude's response summaries (≤5 sentences each), in chronological order.

---

## Entry 1 — 2026-08-24

**User prompt (translated/summarized):**
User wants to build a Wear OS + mobile sensor app in Kotlin + Jetpack Compose, following MVVM. The watch collects accelerometer and gyroscope data and sends it to the phone, which analyzes the data to classify the user's current motion (tap/shake/static/walk). A watch-side UI may be added later if time permits. Before starting, the user asked Claude to log every prompt and a ≤5-sentence summary of each response into a Markdown file, and to flag any prompt that seems problematic or any decision Claude disagrees with for discussion.

**Claude's response summary:**
Set up this DEVLOG.md file to record prompts and response summaries going forward, as requested. Inspected the project and found it currently has only a single `:app` Gradle module with no Wear OS module — this needs to be discussed before implementation starts, since a Wear+mobile app typically requires two separate modules (phone + wear) communicating via the Wearable Data Layer API. Ready to begin once the user gives the first implementation prompt.

---

## Entry 2 — 2026-08-24

**User prompt (translated/summarized):**
User confirmed: add a `:wear` Gradle module, use the Wearable Data Layer API for phone-watch communication, and start motion classification with a rule-based (threshold) approach rather than ML.

**Claude's response summary:**
Added a new `:wear` module (applicationId `com.example.assignment3.wear`, minSdk 30 for Wear OS 3+) with a minimal Wear Compose starter activity, theme, manifest (`android.hardware.type.watch` feature, `com.google.android.wearable.standalone=false` since it depends on the phone), and Gradle Kotlin DSL build file matching the existing project's toolchain (AGP 9.3.1, Kotlin 2.2.10). Added `androidx.wear.compose` (material/foundation 1.4.1) and `play-services-wearable` (20.0.1, needed later for the Data Layer API) to the shared version catalog. While verifying the build, discovered a pre-existing issue unrelated to this change: `androidx.core:core-ktx 1.19.0` requires `compileSdk 37+`, so both `:app` and the new `:wear` module were bumped from compileSdk 36 to 37 to make the project compile at all. Both `:app:assembleDebug` and `:wear:assembleDebug` now build successfully.

---

## Entry 3 — 2026-08-24

**User prompt (translated/summarized):**
User asked why the `:wear` module wasn't visible in Android Studio after Claude created it, and whether they should just add the module themselves via Android Studio's wizard since Claude's approach took a while.

**Claude's response summary:**
Explained that the module files were created on disk via CLI, which Android Studio doesn't auto-detect — it needed a manual Gradle sync (Sync Now / File → Sync Project with Gradle Files) to appear. Explained the extra time was spent fixing a pre-existing compileSdk issue and running real `gradlew` builds to verify both modules actually compile, rather than just scaffolding files. User confirmed sync worked.

---

## Entry 4 — 2026-08-24

**User prompt (translated/summarized):**
User asked whether `targetSdk` should also be bumped to 37 (it's currently 36) alongside the `compileSdk` change.

**Claude's response summary:**
Explained that `compileSdk` and `targetSdk` are independent: compileSdk was raised only because a dependency required it to compile, while targetSdk opts the app into new runtime behavior changes that need testing. Recommended keeping `targetSdk = 36` for now while the app is still early in development, and revisiting before final submission/release.

---

## Entry 5 — 2026-08-24

**User prompt (translated/summarized):**
User initialized a git repo directly inside the `Assignment3` folder and saw 601 unversioned files in Android Studio, and asked whether that's normal, whether something is missing from `.gitignore`, or whether the repo was placed in the wrong location.

**Claude's response summary:**
Diagnosed the cause: the existing `.gitignore` had `/build` (anchored to repo root only), so it never matched `app/build/` or `wear/build/` — together contributing 578 + 547 build-output files, the bulk of the 601. The repo location was correct; this was a single-module-era `.gitignore` that hadn't been updated for the new `:wear` module. Fixed it by changing `/build` to `build` (unanchored, matches at any depth), which brought untracked files down to 54 legitimate source/config files. Confirmed the fix by re-running `git status`.

---

## Entry 6 — 2026-08-24

**User prompt (translated/summarized):**
User asked why the wear module's Kotlin source folder had a `.wear` suffix on the package name, recalling that Android Studio's own wizard normally keeps the phone and wear package/applicationId identical (`com.example.assignment3`) "so the app can receive data." User then asked Claude to make them match, and separately asked whether it's normal for `.idea/gradle.xml` to show up in git given `.idea/` is supposed to be gitignored.

**Claude's response summary:**
Clarified that Wearable Data Layer API communication is driven by paired-device node IDs and self-defined message paths, not by Kotlin package name or `applicationId` matching — that convention only matters for Play Store's embedded-wear-app bundling, which doesn't apply to local testing. Renamed the wear module's package from `com.example.assignment3.wear` to `com.example.assignment3` (moved source files, updated `namespace`/`applicationId` in `wear/build.gradle.kts`) to match the user's preference, verified with `gradlew :wear:assembleDebug` (build succeeded). Explained that Android Studio's default `.gitignore` intentionally tracks some `.idea/` files (e.g. `gradle.xml`, shared project-level settings) while ignoring user-specific ones (`workspace.xml`, caches) — not a bug — and offered to switch to ignoring all of `.idea/` if the user prefers the simpler approach for a solo project. Also confirmed the user's test setup going forward: a real phone + real watch paired over Bluetooth.
