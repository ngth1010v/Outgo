# PROCESS — Analysis v2

Live status file for the work described in `docs/analysis-v2-plan.md`.
Update it at the end of every phase, and whenever a phase is interrupted.

- Branch: `feat/analysis-v2` (off `feat/analysis-rebuild`)
- Verify with: `gradlew.bat :app:assembleDebug` and `gradlew.bat :app:testDebugUnitTest`
- Last verified: assembleDebug OK, 55 unit tests green, and run on an API 36 emulator —
  month page, year page, all three modes, transfers and the raw-trade sections all render

## Phase status

| # | Phase | State |
|---|---|---|
| 1 | Data layer: `types` param, transfer query, both kinds in aggregation, unit tests | DONE |
| 2 | Navigation: `AnalysisPage`, flat page list, two header rows, page-keyed cache | DONE |
| 3 | Mode switch + mode-aware sections 1, 2, 3, 6, 10 | DONE |
| 4 | Two-series charts for All mode (donut, pace, bars) | DONE |
| 5 | Transfers section + summary line | DONE |
| 6 | Year page sections | DONE |
| 7 | Cleanup: lint, dark mode, Vietnamese, baseline profile, README, report | DONE |

## Done

**Phase 1 — data**
- `TradeDao.amountsAndTimesForMonths(monthKeys, types)` now takes a type list and projects `type`,
  so one query and one pass cover expense and income.
- `TradeDao.transferTotalsForMonths(monthKeys)` — new aggregate, `GROUP BY account_id, to_account_id`.
- `QueryModels`: `TradeSlim.type`, new `TransferTotal`.
- `TradeRepository`: pass-throughs for both.
- `AnalysisAggregate.kt`: `buildStats` keeps both kinds; `buildTrades` splits by type in the single
  pass and takes transfers + account names; new `buildYearStats`, `buildYearTrades`, `buildTransfers`,
  `largestOf`. Year-to-date compares against the same months of last year.
- `AnalysisModels.kt`: `AnalysisMode`, `KindSummary`, `MonthBar`/`BarsUi` (replaces `TrendBar`/`TrendUi`),
  `PaceSeries`, `TransferPair`/`TransfersUi`, `YearSummaryUi`, `YoySeries`/`YoyUi`, `YearStatsData`,
  `YearTradesData`. Every model carries both kinds so the mode switch never re-queries.

**Phase 2 — navigation**
- New `AnalysisPage.kt`: sealed `Month`/`Year`, `buildPages`, `yearStepTarget`, `yearlyPageIndex`,
  `currentMonthIndex` — all pure, covered by `AnalysisPageTest`.
- `AnalysisViewModel` rewritten: page-keyed state and four LRU caches (month/year × stats/trades),
  per-page stats ranges, neighbour prefetch and cancel on settle, intro-animation set keyed by page.
- `AnalysisScreen`: two header rows exactly as specified (year row with a "Yearly analysis" button,
  month row with a "Now" button), flat pager over `pages`.

**Phases 3–6 — UI**
- `ModeSwitch` as the first `LazyColumn` item; one `mode` state for the whole screen.
- Sections 1, 2, 3, 5, 6, 10 follow the mode; 7, 8, 9 are expense-only and are dropped in Income mode.
- `AnalysisCharts.kt` rewritten: two-ring donut, two-line pace, diverging month bars, `YoyChart`,
  `TransferBar`.
- Section 11 (Transfers) on both page types, plus the transfer line in the summary.
- `YearPage` with summary, 12-month bars, category donut + breakdown, year-over-year, largest, transfers.
- Strings added to `values` and `values-vi` (203 entries each), plus a `month_full` array.

**Crash fix after first device run**
- `HorizontalPager(key = ...)` returned an `AnalysisPage`, and lazy-layout keys are written into a
  Bundle: `IllegalArgumentException: Type of the key Month(monthKey=202609) is not supported`, which
  killed the app the moment the Analysis tab was opened. Keys are now `AnalysisPage.key`, a plain
  `Int` (`yyyyMM` for a month, `yyyy00` for a year, which months can never collide with).
  Regression test in `AnalysisPageTest`.

**Transfers section**
- Month page: the section only appears in All mode. It moves money without spending or earning it,
  so it has nothing to say while the switch names one kind. The YEAR page still shows it in every
  mode — the instruction named the monthly analysis only; worth confirming.
- Each row now opens with the two accounts it moved between: source icon, arrow, target icon,
  replacing the flat blue dot. `TransferPair` carries each end's icon and colour, and the ViewModel
  feeds `buildTransfers` the accounts rather than just their names.

**Donut centre, share precision and split movers**
- The donut centre carries the period-over-period change under the amount; `SliceSet` now holds
  `prevTotal`/`deltaAmount`/`deltaPercent`, so a kind that vanished this month still reports -100%.
- The breakdown's share figure has two decimals — a small category rounded to "0%" said nothing.
  The change column on the right is still whole percent on purpose.
- Biggest movers is now one ranking across both kinds, split into an expense half and an income
  half: at most 6 rows in total, each half never empty. If the top six are all one kind, the
  smallest gives up its place to the biggest mover of the other; a kind with no movers at all gets
  a single placeholder row and the other half is capped at 5, so the section is always 6 rows tall.
  Movers no longer follow the mode switch — it always shows both halves.

**Follow-up polish (after the second device run)**
- Header rows shrunk to 34dp (0.7x the 48dp default), with 20dp arrow icons and a tight text button.
- Titles that named a kind now follow the mode: "Spending pace" / "Income pace" / "Cash flow pace",
  "Largest purchases" / "Largest income" / "Largest movements", and "By category" in All mode. The
  skeleton and the loaded section share one title helper, so they can never disagree.
  Content descriptions that said "spent" were made kind-neutral.
- Pace chart: each kind now scales to its own peak (All mode to both), so a single kind fills the
  chart instead of sitting at a fraction of it. `PaceSeries` keeps amounts rather than fractions.
- Pace chart axes: 5 day labels across the bottom (always day 1 and the last day, evenly spaced,
  `%02d`) and a 1/2/5 x 10^k value axis with gridlines down the right, sharing `compactAmount`
  with the Home chart rather than a second copy.

**Phase 7 — cleanup**
- Two strings that v2 stopped using (`analysis_month_label`, `analysis_income_and_net`) removed from
  both locales; 197 strings each, in sync.
- Baseline profile journey now walks all three modes, scrolls the month page, steps back a month,
  opens the year page, scrolls it and taps "Now".
- README's Analysis section rewritten around the modes, transfers and the year page.
- Fixed while verifying: the observed stats range now spans the visible page **and both
  neighbours**, so a year page next to December is built from a full year rather than half of one;
  the pager now opens on the current month instead of scrolling to it after the first frame.
- Lint: only the pre-existing `HomeScreen.kt:110` error remains. The new `PluralsCandidate`
  warnings match the repo's existing practice (`category_delete_parent_confirm_message`) and are
  left alone — Vietnamese has no plural forms.

## In progress

Nothing.

## Not done

- Open item from the plan: an income-only history with a single "Salary" category makes the income
  movers and largest sections trivial. Worth a look on real data (§12 of the plan).
- Baseline profile has NOT been regenerated (needs an API 33+ emulator):
  `gradlew.bat :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.class=app.outgo.baselineprofile.BaselineProfileGenerator`
- Verified on an emulator in Vietnamese: month and year pages, all three modes, the two-ring donut,
  diverging bars, heatmap, size mix, largest and transfers. **Dark mode is still unchecked.**

## Notes for whoever picks this up

- The shipped v1 screen is on `feat/analysis-rebuild`; v2 extends it, deletes nothing.
- No DB schema change, no migration, no new index — that constraint still holds.
- Compose Foundation is 1.7.0: no `LazyLayoutCacheWindow`. Do not upgrade for it.
- Aggregation must stay pure Kotlin in `AnalysisAggregate.kt` so the JVM tests keep working.
- `AnalysisMode.kt` from the plan was folded into `AnalysisModels.kt` (enum) and
  `AnalysisSections.kt` (the control) — one less file for ~20 lines.
- Pre-existing lint error in `ui/home/HomeScreen.kt:110` (`StateFlowValueCalledInComposition`) is
  unrelated to this work and still fails `lintDebug`.
