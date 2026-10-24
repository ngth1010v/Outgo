# PROCESS

**Status:** DONE
**Started:** 2026-10-24 18:25
**Last updated:** 2026-10-24 18:45

## Task
Full-screen ("extend") versions of Analysis charts:
- Transfer flows: plot takes almost all the screen height (only the total line under it).
- Day calendar: square cells; tapping a day also lists that day's trades.
- Weekday and time: new setting "Show by: Amount / Transactions" (card and full screen); full
  screen adds a list of weekday-time slots sorted by that setting.

User answers:
- The setting lives in the gear sheet, saved per card.
- Week list: only slots with trades; row = rank, "Mon 8h–12h", amount and trade count (the
  sorted-by value on top), share bar; tapping a row selects the cell.
- Day list follows the mode chip, sorted by time; tapping a row opens the trade editor.
- Also asked for: Transaction sizes (tap a bucket, list its trades) and Transactions per month
  (table: month, count, total, average) in full screen.

Follow-up (same day):
- Weekday and time: Expense/Income/All moved from the title chip into the gear sheet.
- Day calendar: gets the Amount/Transactions setting too; both settings in the gear sheet.
- Transfer flows: card merges accounts whose bar is shorter than their label into "Other" (both
  sides; a lone small one stays itself), replacing the fixed top-5 rule; full screen shows every
  account. Every bar is at least its label's height, so small accounts stay readable.
- Test data: 8 accounts (Ví điện tử, Tiết kiệm, Momo, ZaloPay, ShopeePay, Techcombank, VCB,
  Sổ tiết kiệm) and 17 transfers in Sep/Oct 2026, from scratchpad `seed_transfers.py`.

## Plan
1. [x] `ChartCard.byCount` + `ChartType.hasMetric`; layout string gets an 8th part (`C` or empty).
2. [x] Gear sheet: "Show by" segmented switch (ChartOptionsSheet.kt).
3. [x] WeekHeatmap takes values (amounts or counts); CalendarHeatmap `square` mode (own height, only
   the month's week rows); PairedShareBars tap-to-select.
4. [x] Cards (AnalysisInsightCards.kt): full-screen Column of the content height, list takes the rest
   by weight. Shared `BarRow` / `TradeList` (Top trades now uses them too).
5. [x] Strings en + vi.
6. [x] Build + emulator check.

## Done
- Files: AnalysisLayout.kt, AnalysisScreen.kt, ChartOptionsSheet.kt, AnalysisInsightCharts.kt,
  AnalysisInsightCards.kt, AnalysisInsights.kt (`SizeBucketsUi.bucketOf`, unused WeekGridUi helpers
  removed), values{,-vi}/strings.xml.

## Doing now
- Nothing.

## Not done yet
- Optional: regenerate the Baseline Profile.

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew.bat installDebug` | pass |
| transfer flow full height | emulator | pass |
| calendar square cells, 5-week month has no empty row | emulator | pass |
| calendar day tap lists trades + Open | emulator | pass |
| week list sorted by amount, then by count after gear switch | emulator | pass |
| sizes bucket tap lists trades | emulator | pass |
| frequency table | emulator | pass |
| card (non full screen) layouts unchanged | emulator | pass |
| gear sheet: kind + show by, calendar and week | emulator | pass |
| calendar All + Transactions shades by count, saved (`C` in layout) | emulator + DB | pass |
| transfer card merges small, full screen shows all 12 accounts readable | emulator | pass |
| year page week chart | not opened | not run |
| dark theme | not run | not run |

## Notes for resuming
- Emulator test data: 300 trades Jul–Oct 2026; don't wipe. The month layout was edited directly in
  the DB to add the calendar, week, sizes and frequency charts.
