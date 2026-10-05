# Changelog

## v0.4.0-alpha (2026-10-05)

- New Activity tab: walking vs running minutes and steps, told apart by step speed (145+ steps per minute is running).
- Running minutes use a longer stride and a higher calorie rate, so distance and kcal are closer to reality.
- Removed the Measure tab (heart rate, breathing, posture): phone sensors were not reliable enough. Camera permission removed too.
- Widget now resizes: 1x1, 2x1 (default), 1x2 and 2x2.
- Backups now include walk and run minutes (older backup files still restore).

## v0.3.0-alpha (2026-10-05)

- Bottom tabs like Google Fit: Home, Food, Measure, Settings.
- Food log: search 50 common Indian dishes (or add your own), servings in half steps, protein, carbs and fat.
- Daily balance on the Food tab: eaten vs burned (BMR + active kcal), shown as deficit or surplus.
- Measure tab: heart rate with the camera and flash, breathing rate with the phone on your chest, posture alerts.
- Backup and restore to a CSV file you choose (steps, food and profile). Restore never deletes anything.
- Deleting a day now removes that day's food too.

## v0.2.0-alpha (2026-10-04)

- No notification in the status bar: the app no longer asks for notification permission (Android 13+).
- New step engine: every reading goes through one locked, database-backed path (no double counting).
- Steps around midnight are split between the two days by time.
- Pitch black theme, accent colour picker (10 rainbow presets + hue slider), Google Fit style double ring (steps + active kcal).
- Data & settings screen: unlimited local history, delete one day, or the oldest days in 1 or 5 day steps.
- One-time import of past steps from Google Fit / Google Health through Health Connect (on-device).
- Smaller APK: minified release build, English-only resources.
- Fixed release signing key, so future updates install over the old app and keep your data.

## v0.1.0-alpha (2026-10-04)

- First pre-release.
- Step tracking service with hardware counter, accelerometer fallback, reboot and midnight handling.
- Profile screen with BMI, BMR and stride. Home screen with goal ring, 7 day chart, body metrics.
- Glance home screen widget with refresh.
- Labs code: camera PPG, breathing rate, posture alerts.
- Unit tests for step delta, health formulas and signal estimators.
