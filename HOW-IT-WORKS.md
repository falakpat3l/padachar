# How it works

Code: `app/src/main/java/com/falakpatel/stridelocal/`

## Steps

The phone has a step chip that keeps a running total since it was switched on. The app saves the last total it saw and adds only the difference to today. After a restart the total starts from 0 again; the app notices and carries on.

| File | What it does |
|---|---|
| `sensor/StepCounterService.kt` | Keeps listening to the step chip in the background |
| `sensor/StepEngine.kt` | The only place that saves steps |
| `sensor/StepMath.kt` | Restarts, midnight, walk vs run (145+ steps a minute = run) |
| `sensor/AccelStepDetector.kt` | Backup counter for phones without a step chip |
| `sensor/BootReceiver.kt` | Starts tracking again after a restart |

## Numbers

`health/HealthMetrics.kt`

- BMI = weight / height²
- BMR (Mifflin-St Jeor) = 10 x kg + 6.25 x cm - 5 x age, +5 men / -161 women
- Stride = height x 0.414 walking, x 0.60 running
- Calories use MET values from walking or running speed

## Data

| File | What it does |
|---|---|
| `data/StepDatabase.kt` | On-phone database: days, food, step chip state |
| `data/UserPreferences.kt` | Profile, colour, reminder setting |
| `data/Backup.kt` | Save and restore a CSV file |
| `data/HealthConnectImport.kt` | Import old days from Google Fit |
| `data/IndianDishes.kt` | Built-in food list |

## Screens

`MainActivity.kt` (tabs), `ui/MainScreen.kt` (Home), `ui/ActivityScreen.kt`, `ui/FoodScreen.kt`, `ui/DataScreen.kt` (Settings), `ui/UserMetricsScreen.kt` (Profile), `ui/Theme.kt`.

## Widget and reminder

- `widget/StepWidget.kt`: layout changes with size.
- `reminder/ReminderSchedule.kt`: picks a random time 75 to 105 minutes ahead, 7 am to 9 pm, skipped if you walked.
- `reminder/MoveReminder.kt`: sets the alarm and shows a silent notification.

## Checks

- `app/src/test/`: tests for the maths, food list, backup and reminder.
- `app/build.gradle.kts`: the build fails if anything adds internet permission.
- `.github/workflows/prerelease.yml`: builds and publishes the APK.
