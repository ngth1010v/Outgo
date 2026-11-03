# PROCESS

**Status:** DONE
**Started:** 2026-11-03 18:57
**Last updated:** 2026-11-03 19:50

## Task
"update the charts in the analysis screen:
- in all chart that have setting/config: make all of that chart have a gear icon in the top-right, do not show the setting directly on the chart anymore
- in the yearly charts: add some new charts that stand for the avg of each month (example: avg category spend); that chart will not count the month that do not have any data"

User answers:
- Avg chart type: "Avg breakdown" only (pie like year Breakdown; slice = avg per month this year; change vs last year's avg per month).
- Groups: category amounts AND category trade counts (expense/income x category/subcategory) = 8 new year charts.
- Empty month = per chart kind (month counts if it has any trade of that kind, e.g. any expense).
- Extras: year Summary per-month avg skips empty months too; leave out the live current month (unless it is the only month with data).

## Plan
1. [x] Gear: move Expense/Income/All ChoiceChip (CardChips in AnalysisScreen.kt) into ChartOptionsSheet (new `mode` flag in ChartOptions, separate from `metric`).
2. [x] Data: avg share lines in YearLinesUi (AnalysisYear.kt buildYearCategories) + months-with-data rule.
3. [x] 8 new ChartType YEAR_*_AVG_SHARE (base = month *_SHARE), icons, names, info text, groups, card drawing.
4. [x] Year summary avg divisor (buildYearSummary / kindSummary).
5. [x] Strings en + vi, README.
6. [x] Build `build.bat`.

## Done
- ChartOptionsSheet.kt: ChartOptions gets `mode` flag; CardChips (AnalysisScreen.kt) now gear only; ChoiceChip removed from AnalysisSections.kt.
- AnalysisYear.kt: YearAverageUi + averageLines(); buildYearCategories(live=); buildYearSummary(live=) divides by months with data, live month left out.
- AnalysisLayout.kt: 8 YEAR_*_AVG_SHARE types + ChartType.isAverage; in YEAR groups (not first-run layout).
- Icons res/drawable/ic_chart_avg*_{pie,slices}_{expense,income}.xml.
- YearLinesCard isAverage branch; BalanceShareSection(tenths=) for count averages.
- Strings en/vi (titles, short, info); all "chip" wording -> "settings (gear)".

## Doing now
- Nothing: done. README Analysis section updated.

## Not done yet
- Not committed (user did not ask). On-device check of the new charts not done.
- Bash tool collapses double backslashes: edit XML with Edit tool

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew.bat assembleDebug` | pass |
| emulator: gear-only title rows, mode in gear sheet | mdn_test API 37, Month summary gear | pass |
| emulator: year summary avg = income/6 - expense/7 = 4,771,638 | compared with DB copy | pass |
| emulator: expense/income avg pies match DB (/7, /6) | 3 new charts added | pass |
| emulator: count avg shows tenths (2.1, 1.3...) | | pass |
| emulator: hiding a category keeps others' averages | gear sheet | pass |
| Add chart label "avg. transactions" fits (was truncated) | | pass |
| no new warnings in changed files | `:app:compileDebugKotlin --rerun-tasks` | pass |

## Notes for resuming
- Year charts with `base` draw via the month chart's card from year data (see AnalysisViewModel / AnalysisScreen chart dispatch).
