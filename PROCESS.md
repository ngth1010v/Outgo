# PROCESS

**Status:** DONE
**Started:** 2026-10-21
**Last updated:** 2026-10-21

## Task
User: "execute tất cả chart trên (tự vẽ icon cho từng chart để có chart icon phù hợp)".
Implement on the Analysis screen (month pages) 3 new "+"-sheet groups with these charts, each with its own
hand-drawn vector icon:
- Trades: 1 calendar heatmap, 2 weekday x time-slot heatmap, 3 amount-size histogram (count + sum),
  4 top 10 largest trades (tap -> edit), 5 count + avg per trade by month, 6 transfer flows between accounts.
- Savings (respect AccountPrivacy.progressHiddenInAnalysis): 1 progress vs pace bullet bars,
  2 burn-up + month-end projection, 3 12-month hit grid + streak, 4 savings rate by month (saved/income),
  5 waterfall of savings sources.
- Budgets: 1 month-end projection vs limit (sorted by risk), 2 burn-down of one budget (top-ups as steps),
  3 6-12 month compliance grid, 4 effective-limit waterfall, 5 budget coverage stacked bars,
  6 day-limit-reached dot plot.

## Plan
1. [x] Read Analysis code (ViewModel, Aggregate, Charts, Layout, Screen) + budget/savings repos
2. [x] Design data loading (DAO queries) + models
3. [x] Icons (res/drawable/ic_chart_*.xml)
4. [x] Trades charts
5. [x] Savings charts
6. [x] Budgets charts
7. [x] Strings (en + vi), layout groups, baseline journey check
8. [x] Build, emulator test

Design (decided 2026-10-21):
- New files in ui/analysis: AnalysisInsights.kt (models + pure aggregation), AnalysisInsightCharts.kt
  (Canvas charts), AnalysisInsightCards.kt (card composables). 17 new ChartType entries, 3 new
  ChartGroups (Trades/Savings/Budgets) in LayoutSlot.MONTH.groups; not added to the default list.
- ChartType gets `pick: Pick?` (SAVINGS | BUDGET): card.accountId stores the picked id (chip in CardChips).
- Separate per-month caches in AnalysisViewModel for the 3 groups, loaded only when the layout holds
  a chart of that group, reloaded on trade changes and when the Analysis tab becomes visible
  (AnalysisScreen(visible, onOpenTrade, onOpenDay)).
- New queries: TradeDao.savingsTrades, TradeDao.budgetMoves, StatDao.categoryMonthStats,
  AccountDao.allSavingsMonths, BudgetRepository.progressFor(months) (one-shot progress + carry).
- Savings privacy: current-month charts use progressHiddenInAnalysis(saved,target); multi-month charts
  skip an account when progressHide != NEVER && !progressInAnalysis. hideBalance accounts skip waterfall.
- BalanceLine gets `start` (index offset) so DailyBalanceChart can draw projection lines from today.
- Months: history-type charts use 6 months, savings history 12.

## Done
- DAO: TradeDao.savingsTrades/budgetMoves, StatDao.categoryMonthStats, AccountDao.allSavingsMonths (+ QueryModels rows)
- Repos: TradeRepository.savingsTrades/budgetMoves, BudgetRepository.progressFor, AccountRepository.allSavingsMonths
- ui/analysis/AnalysisInsights.kt (models+aggregation), AnalysisInsightCharts.kt (canvas charts)
- AnalysisLayout.kt: 17 ChartTypes (insight/pick params) + TradeCharts/SavingCharts/BudgetCharts groups
- AnalysisViewModel: insight caches, loadInsights, onShown, budgetRepository ctor param
- BalanceLine.start + DailyBalanceChart uses it
- AnalysisInsightCards.kt, AnalysisScreen wiring (visible/onOpenTrade/onOpenDay, pick chip), OutgoNavHost, strings en+vi, 17 icons (generator: scratchpad icons.py)
- compileDebugKotlin passes

## Doing now
- Nothing.

## Not done yet
- Not committed (user did not ask).
- Baseline profile not regenerated (new charts are not in the default list or the journey).
- Dark theme not checked on device (app theme setting is light).

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| compiles + installs | `gradlew.bat :app:installDebug` | pass |
| lint | `:app:lintDebug` | no new errors (PluralsCandidate warnings, same pattern as existing strings) |
| emulator | all 17 added via + sheet, Oct (live) + Sep (finished) | render with data; calendar->History day and top trade->editor work |

## Notes for resuming
- Not committed unless user asks.
