# StrideLocal

A 100% offline, privacy-first step tracker for Android. No internet permission, no ads, no accounts. Everything stays on your phone.

## Screenshots

| Home | Activity | Food | Settings |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/home.png" width="190" alt="Home screen with step rings"> | <img src="docs/screenshots/activity.png" width="190" alt="Walking vs running"> | <img src="docs/screenshots/food.png" width="190" alt="Food log with Indian dishes"> | <img src="docs/screenshots/settings.png" width="190" alt="Data and settings"> |

<p align="center"><img src="docs/screenshots/widget.png" width="320" alt="Home screen widget in 2x2, 2x1, 1x2 and wide sizes"><br><em>The widget in different sizes</em></p>

New to the code? Read [HOW-IT-WORKS.md](HOW-IT-WORKS.md), a plain-English tour of every part of the app.

## Features

- Live steps, distance (km) and active calories, with a daily goal ring.
- Hardware step counter (`TYPE_STEP_COUNTER`) in a low-power foreground service, restarted after reboot.
- Fallback pedometer for phones without a step chip: accelerometer peak detection with a dynamic threshold.
- Correct daily totals across reboots and midnight (see `StepDelta` in `sensor/StepMath.kt`).
- Profile in kg and cm: BMI and category, Mifflin-St Jeor BMR, stride (height x 0.414, or your calibrated value).
- Active calories from MET values (Compendium of Physical Activities) using your pace and weight.
- Home screen widget (Jetpack Glance): resizes 1x1, 2x1, 1x2 and 2x2; steps, goal bar, km, tap to open.
- Local storage only: DataStore for your profile, Room for daily history. Cloud backup is disabled.
- Unlimited history, kept until you delete it (one day, or the oldest days in 1 or 5 day steps), plus backup and restore to a file.
- Pitch black theme with your own accent colour.
- One-time import of old steps from Google Fit / Google Health via Health Connect (on-device, no internet).
- The build fails if any library adds a network permission (`verifyDebugNoNetwork` task).

## Food and Activity

- Food tab: log meals from a built-in list of common Indian dishes or your own entries, and see eaten vs burned for the day.
- Activity tab: walking vs running minutes, from step speed (145+ steps per minute is running).
- Move reminder (optional, off by default): a silent nudge at random times about every 1.5 hours, 7 am to 9 pm, skipped if you already walked.
- Backup: save everything to a CSV file and restore it on any phone.
- Coming next: on-device photo recognition of Indian food.

## Install the pre-release APK

1. On your phone, open the Releases page of this repo.
2. Download the newest `StrideLocal-v...apk`.
3. Open it and allow "Install unknown apps" when asked.
4. Open StrideLocal, enter your metrics, allow Physical activity.
5. Long-press the home screen, Widgets, StrideLocal, drag "Steps today" to the screen.

Tip: some phones (Xiaomi, Oppo, Vivo, Samsung) stop background apps. Set StrideLocal battery usage to "Unrestricted" in app settings.

## Build it yourself

1. Install Android Studio (free).
2. Open this folder.
3. Press Run.

Or in a terminal: `./gradlew testDebugUnitTest assembleDebug`

## Tech

Kotlin, Jetpack Compose, Material 3, Glance, Room, DataStore, ViewModel + StateFlow. minSdk 26, targetSdk 35 (Android 15).
