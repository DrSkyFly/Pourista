# Changelog

The format is [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
the numbering is [semantic](https://semver.org/).

*[По-русски](CHANGELOG.md)*

## [1.9.6] — 2026-09-14

### Changed

- The swirl starts as soon as the pour is over, and the time left over goes
  into the pause behind it. If the recipe has no pause, one is added. A stir is
  pulled in on a par with a swirl.

### Fixed

- The cooldown timer did not start by itself after a recipe was recorded.

## [1.9.5] — 2026-09-08

### Fixed

- The brew ended by itself halfway through the recipe. A press on the cone —
  on the lid of an immersion one, say — comes off the scale as a jump of a
  hundred grams. The jump went into the calculations whole: the app counted the
  water as poured and took the real weight coming back for a lifted cone, and
  five seconds later called the finish. The weight of the jump went into the
  history along with it.
- The weight used for calculations now accepts only the growth water could have
  brought: no more than 20 g per reading and 40 g per second. A larger jump is
  accepted only if it holds for five seconds — the same rule as for a dip.
- The lift-off threshold is counted from the settled weight rather than from any
  reading: a jump no longer raises it above what is actually poured.
- A jump of the weight no longer goes into the flow rate or into the chart.

## [1.9.4] — 2026-09-04

### Added

- The cooldown timer. A clock in the brew header: from 30 seconds to 20 minutes
  in half-minute steps, three by default. While the timer runs, a countdown
  stands in place of the icon.
- Starting the timer automatically when a brew ends, by a switch. Recording a
  recipe and brews that are too short do not start it.
- The cue for the end of the cooldown — the same as at the finish of a brew. It
  sounds even if the brew screen is closed.

## [1.9.3] — 2026-09-02

### Fixed

- The tare on a Timemore Black Mirror Dot worked every other time: the app now
  checks by the readings whether the scale heard the command, and repeats it.
- A stalled command queue held until the app was restarted: the connection is
  now brought up again.
- In the scale log a battery frame was labelled "could not parse".
- The unit command went out right on the heels of the mode command.
- The flow rate counted by the scale itself went to the screen unaveraged and
  jumped with every packet.

### Added

- A "Filter" field in a recipe — under the grind. It goes into the recipe file,
  into the backup and is described in the format help.
- The filter in the brew notes: filled in from the recipe, edited by hand and
  put on the picture when exporting — next to the grind.
- A "Flow smoothing" setting: none, light, ordinary, heavy.
- The grinder in the history list — before the grind.

### Changed

- A step shorter than its pour is no longer an error: at the end of the input
  the pour is trimmed to the step and the rate is recalculated for the same
  volume.
- The scale log records a press of "Tare" and the fate of the command.
- The instructions for the log: the tare is checked with a load on the scale,
  and the tare button on the scale itself only if it has one.
- The "Grind" section of a recipe is called "Grind and filter".

## [1.9.2] — 2026-09-01

### Fixed

- A weight taken already on the way down went into the history: the result was
  understated by tens of grams. The countdown of a lasting dip was not dropped
  when the weight came back inside the tolerance, and by the time the cone was
  lifted it had long expired — the very first reading of the descent was taken
  for a settled one.
- The tail of the weight chart came down in five-second steps instead of one fall.
- The app started with Bluetooth off no longer hangs in the search: it now sees
  that the adapter is off, says so, and resumes the search by itself when
  Bluetooth is turned on.
- A tap on the Bluetooth icon after the adapter was turned off brought the app down.

## [1.9.1] — 2026-08-30

### Changed

- The flow rate on Timemore Black Mirror Dot and Basic 3 scales is taken from
  the scale packet rather than counted from the weight gain: the reading lags by
  0.6 s instead of 1.0 s.
- The flow rate on the other scales is counted from the gain over half a second
  with half a second of smoothing rather than from the gain over a second with a
  second of smoothing: the reading lags by 0.5 s instead of 1.0 s.

### Fixed

- The flow rate was counted by the number of ticks rather than by time, and was
  overstated by exactly as much as a tick did not fit into 100 ms: at a tick of
  130 ms, by a quarter.
- In the first second of a brew the flow rate was understated: an incomplete
  window was divided by a whole second.
- An implausible jump of the weight zeroed the flow rate for a second; the
  reading now holds while the window fills up again.
- Version 1.9.0 was missing from the "What is new" dialog.

## [1.9.0] — 2026-08-29

### Added

- Converting a grind setting between grinders: an icon in the brew header. 206
  models from 66 makes.
- A "Grind conversion" button in the recipe editor, under the grind field.

### Changed

- The icons in the brew header stand closer together: a button width of 40dp
  instead of 48dp.
- The "Brew without a recipe" button is called "No recipe".
- The recipe format help no longer explains opening a .pour file or importing a
  file under any name.

## [1.8.2] — 2026-08-28

### Added

- A question on the first run: "Do you have a Bluetooth scale?". Bluetooth
  permissions are only requested after a "Yes". Those who updated are not asked.
- A "Do not use a scale" setting. The Bluetooth icon and the connection line
  disappear from the brew header, no scale is looked for at startup, no
  permissions are requested, and the current connection is dropped. The rest of
  the "Scale" section is hidden meanwhile.

### Changed

- The step for wobbling the cone is called "Свирл" in the Russian and Ukrainian
  interface: that is what it is called in recipes and in a coffee shop.

## [1.8.1] — 2026-08-27

### Added

- A file type of our own for recipes — `.pour`. Such a file opens with a tap
  from a messenger, from mail or from a file manager: the recipe goes into the
  list, becomes the current one at once, and the brew screen opens.
  "Share to Pourista" works too.
- Presets for the 4:6 generator. The list is to the right of the title and holds
  "No preset", the saved settings with a cross to delete them, and "Save as…".
  The save button appears once the dials have been turned after loading a
  preset; the room for it is always taken, so the list does not shift.

### Changed

- Exporting recipes creates `.pour` files with the type
  `application/vnd.pourista.recipe`. The import still reads the old `.json`
  files and text from the clipboard.
- In the 4:6 sheet the "Done" button is taken out of the scroll and is always
  visible, while the list of steps lives in a box of constant height: the number
  of pours changes with the strength, and the sheet used to grow and shrink at
  every turn of a dial.

## [1.8.0] — 2026-08-27

### Added

- The interface in 14 languages: English, German, Spanish, French, Italian,
  Dutch, Polish, Portuguese, Turkish, Russian, Ukrainian, Japanese, Korean and
  Chinese. The language is chosen in the settings, apart from the system one.
- A backup: the recipes together with their favourite marks and order, and the
  whole history with the charts and the notes — in one file. A restore adds
  rather than replaces: a recipe with the same name and a brew with the same
  time are skipped.
- Support for Acaia (Lunar, Pearl, Pyxis), Difluid Microbalance, Eureka Precisa
  and Varia AKU scales.
- Deleting a brew by swiping left, with an undo in the snackbar.
- A request to testers in the "What is new" dialog: without them the app will
  not reach Google Play.

### Changed

- The recipe steps are shown as a ribbon: an icon in a circle, a line from
  circle to circle.
- A refreshed look: rounded icons, larger card roundings, heavier headings, an
  oval search field with a clear button, large figures for the dose, the water
  and the ratio in the recipe tile, a sparkline instead of the full chart in the
  history list, a short bottom bar, transitions between screens and a smooth
  change of the pace colour.
- The order of recipes is changed by a long press on a card — the drag handle is
  gone.
- The brew header shows the name of the connected scale.
- The main figure is squeezed instead of wrapping at a large system font.
- Empty recipe and history lists show an icon and a hint.
- The build is split into two flavours: `github` — the APK from the releases
  page, `play` — the bundle for Google Play without the update check button.

### Fixed

- Auto-finish wrote down a weight taken already on the way down: the
  intermediate readings of the cone being lifted understated the result by tens
  of grams. The chart now ends where the total on the card does.
- The "Dose" label in the readings was cut off by the rounding of the button.
- The temperature label in the editor did not fit its field and broke onto two
  lines.
- The chart fill ran into the field of time labels with a slanted edge, and the
  breaks of the curve looked chopped off.
- The recipe format help was shown in Russian in every language but Russian.

## [1.7.2] — 2026-08-24

### Added

- A picture of a brew for "share": the name, the figures, both charts and a
  caption with the app icon.
- Turning auto-finish off in the settings.

### Changed

- The charts are laid out for people: the weight in fifties, the flow rate in
  2.5 g/s, a time scale with ticks at the bottom.
- The charts in the history and on the picture are taller.
- "Create a recipe" is a button of its own under the charts rather than an icon
  in the header.
- The tick in the brew card saves the notes and goes back to the list.

### Fixed

- Auto-finish did not fire if water was added after the recipe step had changed.
  "Finish" by hand in that case wrote down the weight from after the cone was
  lifted.
- A recipe from a recording ended with a pour as long as the whole drawdown —
  the drawdown is now a step of its own.
- The recipe preview in the editor drew an even hill instead of pours with pauses.

## [1.7.1] — 2026-08-23

### Added

- A recipe from a recorded pour: a button in the history takes the chart apart
  into steps and opens the finished draft in the editor.
- The value under a finger on the chart: holding it shows the weight and the
  time at that point.
- Deleting a brew straight from the history list.

### Changed

- With the start of a pour the tare and the dosing leave the screen.
- The list of steps shows the length of a step.

### Fixed

- On short screens the two charts did not fit: they are now squeezed to the
  height of the screen.

## [1.7.0] — 2026-08-23

### Added

- Aeropress mode in a recipe: the weight is written as it comes and auto-finish
  is off — the chart shows the moment of the press. The built-in AeroPress has
  it on.
- An "Olive" palette.

### Fixed

- The keyboard covered the lower fields in the brew card and in the editor.
- A swirl longer than two seconds dropped the weight chart and gave a spike of
  the flow rate.
- "Finish" after the cone was lifted wrote down an understated weight.
- The history showed the recipe name as of the brew rather than the present one.

## [1.6.1] — 2026-08-22

### Added

- A bell when the last step of the recipe is played out: the water is through,
  time to take the cone off. The brew carries on meanwhile.

### Fixed

- Auto-finish on large volumes: the drop threshold was a share of the maximum,
  and the weight had to fall by half. A lifted cone carries its mass away
  together with the soaked coffee — on 600 g that is a quarter of the weight,
  and the app did not notice the lift-off. The threshold is now 12% of the
  maximum, but never less than 30 g.
- A tablet in portrait got the landscape layout: it was chosen by the width of
  the window rather than by the orientation.

## [1.6.0] — 2026-08-22

### Added

- The scale log: "Settings → Diagnostics" records the services of the device,
  the raw packets from every notifying characteristic and the commands sent, and
  offers to send the finished file. Needed when a model will not start.
- The "What is new" dialog shows the whole version history and opens from the
  settings.

### Fixed

- The landscape layout of the brew screen: the recipe on top across the full
  width, the readings and the pour chart on the left, the steps and the flow
  chart on the right. The scrolling is shared and paired cards are of one
  height. The second column used to stay empty during a pour.

## [1.5.0] — 2026-08-21

### Added

- Landscape mode and tablets: the sections move into a side rail, the brew
  screen lays out in two columns — the readings with the guidance on the left,
  the recipe and the charts on the right.

### Changed

- Lists do not stretch across the full width of a wide screen.

## [1.4.0] — 2026-08-21

### Added

- Colour palettes: coffee, "4:6" and the standard one. They are chosen in the
  settings next to the light and dark theme.
- The "What is new" dialog — once after an installation and after every update.

### Changed

- The coffee palette is muted: the fills of the pour guidance take whole cards,
  and a saturated colour over such an area tired the eyes.

## [1.3.0] — 2026-08-21

### Added

- The 4:6 generator of Tetsu Kasuya: the dose, the water, the taste balance and
  the strength — and out comes a finished recipe, which goes straight into the
  brew tile. The numbers and the schedule of the pours repeat the original app
  of the method.
- Special thanks to Kofezavr in the "About" section.

### Changed

- The buttons in the recipe tile are in one row and filled.

## [1.2.0] — 2026-08-17

### Added

- Water rises inside the step ring: the level follows the pour, the surface
  sways. With a scale by the actual weight, without one by the plan.

### Fixed

- Wobbling the cone gave a spike of the flow rate: the readings sank, and the
  app took their return for a pour. The flow rate, the charts and the detection
  of the end of a pour are now counted from a non-decreasing weight; a dip
  longer than two seconds (a tare, a lifted cup) is taken as a new baseline.
- Auto-finish waits five seconds instead of three, so that a last wobble does
  not pass for the end of a brew. A weight gone negative still needs three: that
  only happens when everything is taken off the scale at once.

## [1.1.0] — 2026-08-17

### Added

- Beta support for other scales: Felicita, Bookoo, Decent Scale and Timemore
  Black Mirror Dot. The protocols are written from open implementations and have
  not been checked on live hardware.
- The scale protocol is moved into a driver: the app looks for every known model
  at once and picks the right one by the device name.

## [1.0.0] — 2026-08-17

The first release.

### Features

- Recipes by steps: bloom, pours, pauses, swirl, stir, drawdown, press. The
  bloom is always the first step, the drawdown the last.
- A recipe editor: the addition and the length of a step, interchangeable rate
  and pour time, auto-start after the dose, dragging recipes in the list.
- Brewing without a scale is a full mode of its own: the main figure on the
  screen is the time, the step target next to it, the dose is entered by hand
  and the recipe is recalculated for it.
- With a Futula Kitchen Scale 3 (LEFU CK811) over Bluetooth: weight in real
  time, tare, dosing, battery level, keeping the unit in grams.
- Guidance during a pour: the step target next to the readings, how much is left
  to the target, a ring timer with a mark for the end of the pour, a verdict on
  the pace with an adjustable tolerance, a hint for the next pour based on your
  own measured rate.
- The end of a pour is detected by fact — by reaching the target or by the
  weight stopping.
- Auto-finish: after the last pour, a cone or a cup lifted off the scale ends
  the brew.
- Recalculating a recipe for the actual dose, with the water rounded to 5 g and
  a "Water as in the recipe" switch.
- Recording a recipe from a real pour.
- Importing and exporting recipes in JSON — as a file and through the clipboard,
  with the format described in the settings.
- The brewing history: the weight and flow curves, notes on the bean, the
  roaster and the grind, and a search.
- Sound and vibration cues on the alarm stream: a step change, a countdown
  before a pour, a cue a few grams before the target, the finish.
- An update check: a button in the settings opens the page of the freshest
  release. The app has no access to the internet.
- The interface in Russian and English, light and dark themes, Material You
  dynamic colours. The built-in recipes are translated together with the app
  language.

[1.2.0]: https://github.com/DrSkyFly/Pourista/releases/tag/v1.2.0
[1.1.0]: https://github.com/DrSkyFly/Pourista/releases/tag/v1.1.0
[1.0.0]: https://github.com/DrSkyFly/Pourista/releases/tag/v1.0.0
