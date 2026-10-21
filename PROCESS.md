# PROCESS

**Status:** DONE
**Started:** 2026-10-21
**Last updated:** 2026-10-21

## Task
User: "ở tất cả các chart, giúp tôi thêm description": long-press a chart in the 'Add chart' sheet ->
description popup; an 'info' icon LEFT of each chart card's header label -> same popup. Popup has:
general description, meaning, how to use/read. Ask if unsure.

## Plan
1. [x] ui/analysis/ChartInfo.kt: chartInfo(type) -> (about, meaning, how), ChartInfoDialog (AlertDialog, like Home/Category info)
2. [x] Section: LocalSectionInfo (ChartType) provided per card in ChartList; (i) before the title
3. [x] AddChartSheet: ChartCell combinedClickable onLongClick -> dialog; hint line under the title
4. [x] Strings en + vi: templates for account (scope arg) and category (kind + scope args) charts, full text for SUMMARY + 17 insight charts
5. [x] Build, emulator check, (commit only if asked)

## Done
- ChartInfo.kt (texts + ChartInfoDialog), Section (i) via LocalSectionInfo, sheet long-press + hint, 83 strings en+vi

## Doing now
- Nothing.

## Not done yet
- Not committed (user did not ask).

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew.bat :app:installDebug` | pass |
| emulator | (i) on Account balance by day; long-press Budget burn-down and Income 5 months (subcategory) in sheet | popups show, templates filled, no chart added on long-press |

## Notes for resuming
- Build env: JAVA_HOME = C:\Users\LAPTOP\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2, clear NoDefaultCurrentDirectoryInExePath.
- adb driver script: scratchpad ui.py (tap/find/swipe/shot/texts).
