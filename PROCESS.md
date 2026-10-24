# PROCESS

**Status:** DONE
**Started:** 2026-10-24 20:19
**Last updated:** 2026-10-24 20:45

## Task
"add a 'extend' icon on the right of current 'info' icon in each chart; when user click to it, the app
will show the full screen version of this chart". User answers:
- Stay portrait. Charts that would want landscape (line charts) -> drawn normally but taller, with the
  legend as rows (one row per line + its value).
- Every chart gets the icon (including Summary / list cards).
- Same controls (mode chip, gear settings) work in full screen; changes apply to the card too.

## Plan
1. [x] Expand icon after the (i) in `Section`'s title row (AnalysisSections.kt), via LocalSectionExpand.
2. [x] Full-screen view: ChartList (AnalysisScreen.kt) keeps `expandedId`; `FullscreenCard` shows the same
   card through the root overlay with BackHandler.
3. [x] Content grows: LocalFullscreenChart + plotHeight()/listHeight(); legend -> LegendRows with values;
   pie stacks vertically.
4. [x] Icons ph_arrows_out_simple / ph_arrows_in_simple, strings en + vi.
5. [x] Build + emulator check.

Decision: a Compose Dialog was tried first and dropped: it sizes its window to the first measured
content (screen minus bars, 2200px of 2400), so the bottom bar showed through and the status bar
icons turned white. Now `ui/component/OverlayHost.kt` (OverlayHost, ShowOverlay) draws over the whole
app from OutgoRoot (ui/nav/OutgoNavHost.kt), in the activity's own window.

## Done
- See Plan. Files: AnalysisSections.kt, AnalysisInsightCards.kt, AnalysisScreen.kt, OutgoNavHost.kt,
  ui/component/OverlayHost.kt, res/drawable/ph_arrows_{out,in}_simple.xml, values{,-vi}/strings.xml.

## Doing now
- Nothing.

## Not done yet
- Optional: regenerate the Baseline Profile with the expand transition in `animationJourney`.

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew.bat installDebug` | pass |
| line chart full screen + legend rows | emulator, month page | pass |
| pie full screen | emulator | pass |
| stacked bars, 8 categories | emulator | pass |
| summary + mode chip in full screen, change kept on card | emulator | pass (card set back to All) |
| gear sheet over full screen | emulator | pass |
| close icon / Back | emulator | pass |
| year page | emulator | pass |
| Trades/Savings/Budgets charts | none on the emulator's layouts | not run |
| dark theme | app theme setting is light | not run |

## Notes for resuming
- Emulator already has 617 test trades; don't wipe.
