# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Single-module Android app (Kotlin, package `fr.netstat.brightnessslider`, minSdk 30 / targetSdk 35) that puts an invisible touch overlay over the status bar. Sliding on it changes screen brightness, and other gestures trigger actions (lock screen, torch, screenshot, launching apps).

## Commands

```sh
./gradlew assembleDebug          # build debug APK -> app/build/outputs/apk/debug/
./gradlew assemble               # what CI runs (.github/workflows/build.yml, JDK 17)
./gradlew installDebug           # install on a connected device/emulator
./gradlew lint
```

There are no tests: only the `app/src/main` source set exists.

To use the app on a device, grant both permissions that `MainActivity` sends the user to: "Modify system settings" (`WRITE_SETTINGS`) and the accessibility service.

## Architecture

- **`StatusBarAccessibilityService`** is the core of the app. When the service connects, it adds a transparent `View` as a `TYPE_ACCESSIBILITY_OVERLAY` window at the top of the screen. The overlay is 80% of the screen width and as tall as the status bar. It implements `GestureListener` and maps gestures to the actions configured in settings (`BarAction.kt`):
  - each `GestureSlot` (slide, long-tap+slide, double-tap+slide, double tap per horizontal third, double long-tap per half) holds exactly one `BarAction`, so actions on the same gesture can't conflict. The stored value is an action ID or `app:<package>`; `SettingsFragment` appends the installed launcher apps to each list at runtime
  - `brightness` is only offered for slide slots: it sets `Settings.System.SCREEN_BRIGHTNESS` (0–255, linear or logarithmic depending on a preference, with a 100px dead zone on each side). Any other action on a slide fires once the finger has covered half the bar
  - defaults: slide locks the screen, long-tap+slide sets brightness, double tap launches translator / camera / calculator, double long-tap toggles the torch (left) or takes a screenshot (right)
  - app launches are skipped while the device is locked unless their slot is in the "Launch apps on the lock screen" multi-select
- **`EdgeBackGesture`** (owned by the service) adds a thin touch overlay (width is a preference, default 32dp) on the left and/or right screen edge (preference), below the status bar and above the keyboard (IME bounds come from the accessibility window list, refreshed on `TYPE_WINDOWS_CHANGED`). Dragging inward and releasing past a threshold performs `GLOBAL_ACTION_BACK`, or `GLOBAL_ACTION_RECENTS` past a configurable fraction of the screen width; the action fires on release only. A tap disables the edges for 5 s, a cancelled drag (released below the threshold, or vertical) for 2 s, so touches reach the app underneath. A separate non-touchable full-screen overlay draws an arrow indicator.

  It listens for `TYPE_WINDOW_STATE_CHANGED` events to update the overlay's visibility, and on configuration changes it recomputes the overlay size. It exposes a static `instance`.
- **`GestureDetector`** is a custom `View.OnTouchListener` state machine (`MotionState`). It tells taps, double taps, long taps, slides and flicks apart using hard-coded time and distance thresholds. The flick velocity threshold comes from preferences. If a slide handler returns `false`, the state becomes `CANCELLED` and the rest of that gesture is ignored. The detector's `onTouch` always returns `false`.
- **`StatusBarHeightDetector`** tries to find the real status bar height from the accessibility window list (a `TYPE_SYSTEM` window at layer 1) and caches it for portrait and landscape. If that fails, it uses the `status_bar_height` system dimen.
- **`AppLauncher`** launches an app by package ID. It can launch them in freeform windows (`WindowMode` bounds plus the `android.activity.windowingMode` = 5 extra). It won't launch an app on the lock screen unless `includeLockscreen` is set.
- **`Torch`** toggles the flashlight through `CameraManager`, using a strength level on API 33+.
- **Settings flow:** `MainActivity` hosts `SettingsFragment` (`res/xml/root_preferences.xml`). `Preferences` wraps the default SharedPreferences. Components talk to each other through **EventBus** (`Events.kt`): `SettingsFragment` posts `SettingsUpdatedEvent` from a SharedPreferences change listener (not on clicks), and the service then re-reads its preferences. The `sender` field (a resource ID such as `R.id.settings` or `R.id.preferences`) lets a component ignore events it posted itself.
- Preference keys are string resources in `res/values/strings.xml`, and the code reads them through `context.getString(...)`. Never change the *value* of an existing key string: it is the persisted SharedPreferences key, so changing it silently resets that setting for users (e.g. `shared_pref_is_globally_enabled` still stores under the legacy `shared_pref_is_globally_disabled` value). A `ListPreference` `defaultValue` must be one of its `entryValues`.
