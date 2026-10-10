# Changelog

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
