# StrideLocal

A 100% offline, privacy-first step tracker for Android. No internet permission, no ads, no accounts. Everything stays on your phone.

## Features

- Live steps, distance (km) and active calories, with a daily goal ring.
- Hardware step counter (`TYPE_STEP_COUNTER`) in a low-power foreground service, restarted after reboot.
- Fallback pedometer for phones without a step chip: accelerometer peak detection with a dynamic threshold.
- Correct daily totals across reboots and midnight (see `StepDelta` in `sensor/StepMath.kt`).
- Profile in kg and cm: BMI and category, Mifflin-St Jeor BMR, stride (height x 0.414, or your calibrated value).
- Active calories from MET values (Compendium of Physical Activities) using your pace and weight.
- Home screen widget (Jetpack Glance): steps, goal bar, km, refresh button, tap to open.
- Local storage only: DataStore for your profile, Room for daily history. Cloud backup is disabled.
- The build fails if any library adds a network permission (`verifyDebugNoNetwork` task).

## Labs (code ready, screens coming next)

In `app/src/main/java/.../labs/`. Experiments, not medical devices.

1. Camera PPG heart rate: fingertip over rear camera and flash, brightness signal, autocorrelation.
2. Breathing rate: phone on chest while lying down, accelerometer tilt, 6 to 42 breaths/min.
3. Posture: proximity "too close" alert plus "text neck" alert when the phone is held low for over a minute.
4. MET calories: already live in the main tracker (`health/HealthMetrics.kt`).

## Install the pre-release APK

1. On your phone, open the Releases page of this repo.
2. Download `StrideLocal-v0.1.0-alpha.apk`.
3. Open it and allow "Install unknown apps" when asked.
4. Open StrideLocal, enter your metrics, allow Physical activity and Notifications.
5. Long-press the home screen, Widgets, StrideLocal, drag "Steps today" to the screen.

Tip: some phones (Xiaomi, Oppo, Vivo, Samsung) stop background apps. Set StrideLocal battery usage to "Unrestricted" in app settings.

## Build it yourself

1. Install Android Studio (free).
2. Open this folder.
3. Press Run.

Or in a terminal: `./gradlew testDebugUnitTest assembleDebug`

## Tech

Kotlin, Jetpack Compose, Material 3, Glance, Room, DataStore, ViewModel + StateFlow. minSdk 26, targetSdk 35 (Android 15).
