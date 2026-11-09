# Changelog

## 0.8.0-alpha
- New, optional cycle tracking. Switch it on in Settings, Cycle tracking, and a Cycle tab appears between Food and Settings. It is off by default, so nothing changes for anyone who does not need it
- Log a day from the calendar: flow (spotting, light, medium or heavy) and how you feel (cramps, headache, back pain, bloating, tender breasts, acne, tired, low mood, mood swings, cravings)
- Logging the first day of an earlier period can fill in the days after it, up to your usual period length
- The Cycle tab shows the day of your cycle, when the next period is expected and your usual cycle and period length, worked out from your last six cycles (28 and 5 days until there is enough history). A cycle over 60 or under 15 days, usually a missed log, is left out
- Expected days are outlined on the calendar, and a list shows your recent periods with each cycle's length
- Optional reminder at about 9 am the day before the next period is expected. On the lock screen it only says "padachar reminder"
- Backups include the cycle log (a switch in Settings leaves it out). Restoring only adds days that are not on the phone yet, and turns the Cycle tab on
- The estimates are only for your information: no fertile days or "safe days", and not for birth control
- Like everything else in padachar, the cycle log stays on the phone. No internet permission

## 0.7.5-alpha
- Restore stops with a clear message when the picked file is far too big to be a backup (over 16 MB or 250,000 lines), instead of the app running out of memory
- The restart-after-reboot part of the app can no longer be triggered by other apps, only by Android itself
- Build: the signing key file on GitHub is only readable by the build and is deleted when the build ends

## 0.7.4-alpha
- Home shows when you reach your step goal: the steps line ends in a check mark, "Goal reached" appears under the count with how far over you are (for example "+1,240 over your goal"), and the percent keeps counting past 100%
- The first time you reach the goal each day, the check mark pops in with a short, calm animation. It plays once a day and is skipped when the phone's "Remove animations" setting is on
- Widget: a check mark next to the percent once the goal is reached, and "Goal reached" as the 2x2 heading. The percent now goes past 100% too
- New "Daily goal reached" notification, once a day. On by default, switch it off in Settings, Notifications. Like the move reminder, it needs notification permission, which is only asked for when you switch one of them on
- Home chart: bars that reached the goal are in your accent colour, the others grey, and the caption says on how many days you reached it

## 0.7.3-alpha
- Widget and Home switch to the new day within minutes of midnight, and again right away when you change the clock or time zone
- Restore and Google Fit import no longer overwrite steps being counted at the same moment. Restore keeps the higher of each total for a day, so today's count is never lowered
- Saving a backup over an old file now clears it first, so no old lines are left at the end
- A food name with a line break no longer breaks the backup. Older backups with such a name now restore it too
- Move reminders keep going even if one check fails, instead of stopping until you switch them off and on
- Colour swatches in Settings are easier to tap (48 dp tall touch area)
- Fixed a possible crash when step counting could not start and the app tried to start it again at the same time

## 0.7.2-alpha
- Move reminder: reads the latest step count before deciding, so a walk with the screen off no longer gets a nudge
- Home: allowing Physical activity in the phone's settings now hides the warning card and starts counting when you come back
- Google Fit import keeps the walking and running minutes already counted for that day
- Restore accepts backup files that an editor saved with a byte order mark
- Widget settings no longer crashes when the widget was already removed
- Food search says so when nothing matches, and points to + Custom
- Settings: "1 day" instead of "1 days", a hint when notifications are blocked, and the source code link points to the padachar repo
- Better TalkBack labels for the colour swatches and the W / M / Y tabs

## 0.7.1-alpha
- Targets Android 16 (API level 36), as Google Play now requires. No changes to how the app works.

## 0.7.0-alpha
- New app ID, `com.falakpat3l.padachar`, ready for Google Play. Android treats this as a new app: it installs next to the old one instead of over it. Save a backup in the old app first, then restore it in the new one.

## 0.6.2-alpha
- Swipe left or right to move between Home, Activity, Food and Settings (Home is the first page, Settings the last). The bottom bar still works too
- Swiping across a chart still moves the day marker, not the page
- The "avg" label on the Home chart no longer hides behind the first bar
- New screenshots in the README

## 0.6.1-alpha
- Widget text now uses the app's font, Exo 2
- Each widget can show steps, distance or calorie deficit: long-press it, then "Widget settings". Everything else about the widget stays the same
- The widget updates straight away when you add or remove food
- The rings widget is gone, replaced on Home by the four goal lines

## 0.6.0-alpha
- New name: **padachar** (formerly StrideLocal), with a new logo as the app icon. Updates still install over the old app and keep your data; old backups still restore.
- New font: Exo 2, bundled in the app (SIL Open Font License, see docs/fonts)
- Charts in the Google Fit style: rounded pill bars that grow in, the day's total inside each bar, the picked day's numbers on top, and a marker that glides from bar to bar when you tap or slide, with a light vibration tick
- Home chart: W / M / Y tabs with arrows to go back in time, a dashed daily-average line, and this month plus the two before as average steps a day
- Walking and running shown as symbols instead of words
- Home: today's goals as four straight progress lines (steps, distance, kcal burned, kcal eaten), each with a small symbol at the start and a bigger one at the goal end
- Profile: optional daily food goal, used by the eaten ring
- White symbols for kcal eaten (a Pac-Man), kcal burned (a flame), distance (a double arrow) and calorie deficit, in `res/drawable/` (`ic_eat`, `ic_burn`, `ic_distance`, `ic_deficit`) so they are easy to edit or replace
- About: logo, version, Falak, and tap-to-open symbols for the website, source code, Google Scholar, LinkedIn and X

## 0.5.2-alpha
- New look to match falakpatel.com: serif text, thin outlined cards, small corners, link-blue default colour
- Titles, cards and text centred
- Charts: step numbers on the y axis, day names on the x axis, the total above each bar (or km when the number does not fit), and tap a bar for that day's details
- Colour picker is now one short row of swatches
- App icon: pitch black background, same walking figure
- Settings: new About section (app version, who made it, links to my website and the source code)

## 0.5.1-alpha
- Shorter, cleaner text across the app and docs

## 0.5.0-alpha
- Move reminder (off by default)
- How-it-works guide and screenshots

## 0.4.0-alpha
- Activity tab: walking vs running
- Smaller widget sizes
- Removed the Measure tab and camera permission

## 0.3.0-alpha
- Food log with Indian dishes
- Backup and restore
- Bottom tabs

## 0.2.0-alpha
- No status bar notification
- Black theme with accent colours
- Import from Google Fit (Health Connect)
- Delete days, signed release builds

## 0.1.0-alpha
- First release: steps, distance, calories, widget
