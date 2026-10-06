# How StrideLocal works (plain English)

This guide explains the app without assuming you know Kotlin. Each section says what a part does and which file to open if you want to look.

All app code lives in `app/src/main/java/com/falakpatel/stridelocal/`. Paths below start from there.

## The big picture

1. Your phone has a small, low-power **step chip**. It counts steps by itself, even while the phone sleeps.
2. StrideLocal reads that count, works out how many **new** steps you took, and saves them for the right day.
3. Everything is saved in a small database **on your phone**. The app has no internet permission, so nothing can leave the phone.
4. The screens and the home screen widget simply show what is in that database.

## 1. Counting steps (`sensor/`)

- **StepCounterService.kt**: a background helper that keeps listening to the step chip. Android requires a notification for this; because the app does not ask for notification permission by default, it stays hidden on Android 13 and newer. Every 5 seconds it hands the newest number to the step engine.
- **StepEngine.kt**: the only place that writes steps. It locks itself while working, so two parts of the app can never count the same steps twice.
- **StepMath.kt**: the maths, kept separate so it can be tested:
  - The chip gives a running total since the phone was switched on, not "steps today". The app remembers the last total it saw and adds only the difference.
  - After a restart the chip starts again from 0. The app notices this (Android keeps a boot counter) and counts those steps as new.
  - If steps happen across midnight, they are split between the two days by time.
  - **Walk or run:** steps are grouped into minutes. 145 or more steps in a minute is running, 40 or more is walking.
- **AccelStepDetector.kt**: a backup step counter for phones without a step chip. It looks for the up-and-down rhythm of walking in the motion sensor.
- **BootReceiver.kt**: restarts counting (and the move reminder) after the phone restarts or the app updates.

## 2. Health maths (`health/HealthMetrics.kt`)

- **BMI** = weight (kg) / height (m) squared.
- **BMR** (calories your body burns at rest) uses the Mifflin-St Jeor formula: 10 x weight + 6.25 x height - 5 x age, then +5 for men or -161 for women.
- **Stride**: height x 0.414 for walking, height x 0.60 for running (or your own calibrated value).
- **Distance** = steps x stride.
- **Active calories** use MET values (how hard an activity is compared to sitting still) from the Compendium of Physical Activities. Your speed comes from steps per minute x stride.

## 3. Saving data (`data/`)

- **StepDatabase.kt**: the on-phone database (Room). Three tables: one row per day (steps, km, kcal, walk and run minutes), one row per food item, and one row remembering the step chip's last total.
- **StepRepository.kt**: the friendly front door to the database that screens use.
- **UserPreferences.kt**: your profile (weight, height, age, goal, colour, reminder on or off), saved with DataStore.
- **Backup.kt** and **Csv.kt**: save everything to a CSV file you choose, and load it back. Restoring never deletes anything.
- **HealthConnectImport.kt**: one-time import of old steps from Google Fit / Google Health through Health Connect, which also lives on the phone.
- **IndianDishes.kt**: the built-in food list with calories, protein, carbs and fat per serving.

## 4. Screens (`ui/` and `MainActivity.kt`)

The screens are written with Jetpack Compose: each screen is a function that describes what to show, and Android redraws it whenever the data changes.

- **MainActivity.kt**: the app's front door and the bottom tabs.
- **MainScreen.kt**: Home, with the double ring (steps and calories) and the 7-day chart.
- **ActivityScreen.kt**: walking vs running.
- **FoodScreen.kt**: food log and eaten vs burned.
- **DataScreen.kt**: Settings: storage, move reminder, backup, colour, Health Connect import, deleting days.
- **UserMetricsScreen.kt**: the profile form.
- **Theme.kt**: pitch black colours and your accent colour.
- **MainViewModel.kt**: gathers profile, today and the last 7 days for the screens.

## 5. Widget (`widget/`)

- **StepWidget.kt**: the home screen widget, built with Jetpack Glance. It changes layout with its size (1x1, 2x1, 1x2, 2x2).
- **StepWidgetReceiver.kt**: tells Android the widget exists.

## 6. Move reminder (`reminder/`)

- **ReminderSchedule.kt**: picks the next time: 75 to 105 minutes later, only between 7 am and 9 pm. It skips the nudge if you walked 300+ steps since the last check.
- **MoveReminder.kt**: sets one alarm at a time and shows a silent notification that disappears after 15 minutes.

## 7. Safety checks

- **Tests** (`app/src/test/`): check the step maths, health formulas, walk/run rules, food list, backup format and reminder timing. They run on every GitHub build.
- **No-internet guard** (`app/build.gradle.kts`): the build fails if any library tries to add an internet permission.
- **GitHub Actions** (`.github/workflows/prerelease.yml`): builds the signed APK and publishes it under Releases.
