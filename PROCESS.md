# PROCESS

**Status:** DONE
**Started:** 2026-10-29 20:45
**Last updated:** 2026-10-29 21:05

## Task
User: redo the Cash flow chart's settings panel:
- 3 parts filling most of the popup, switched by a segmented button at the top: Account / Category / Other.
- Account part: one section per (parent) account: header row = icon, name, toggle (show/hide). When
  shown: row "Show subaccount" + toggle. When on: grid of that account's subaccounts (toggle each).
- Category part: same idea with categories.
- Other part: toggles "Show balance adjust", "Fit to screen", "Show more detail for account".
- Note: account box becomes icon only (like categories), detail shown on focus.
Answers: keep "Show transfers" in Other; accounts icon-only by default, "Show more detail" ON = today's
box; list ALL active accounts/categories; Category part = Income heading, then Expense heading.

## Plan
1. [x] Model: ChartCard.expandedAccounts/expandedCategories/accountDetail (subAccounts/subCategories
       dropped, never released). Encode part 8 flags F/D, 9 hiddenCategories, 10/11 expanded lists.
2. [x] CashFlow.kt: atLevel per parent; showsAccount/showsCategory (parent hidden, or split + sub hidden).
3. [x] VM: AnalysisUiState.allAccountTree (every active account).
4. [x] CashFlowOptionsSheet.kt rewritten: tabs, sections w/ Switch rows, sub grid (cellRows), Other
       switches. Filter sheet + cashFilterItems + cashFlowSwitches removed; old strings removed.
5. [x] Chart: detail param; icon-only accounts (smaller metrics, radius 8dp), focus label name + net.
6. [x] Strings en/vi; tests 28/28; emulator checked (3 tabs, sub grid, focus label, Other glyph, full screen).

## Done
- See plan. New strings: analysis_cash_other, _show_subaccounts, _show_subcategories,
  _show_transfers, _show_adjustments, _more_detail. Removed: analysis_cash_transfers, _adjustments,
  _accounts, _categories, _subaccount, _subcategory, _filter, _filter_shown, _show, _full_screen, _scroll.

## Doing now
- Nothing. Not committed (user did not ask).

## Not done yet
- Nothing.

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| unit tests | move BudgetCarryTest.kt + AnalysisLayoutTest.kt aside (already broken), `gradlew.bat :app:testDebugUnitTest --tests app.outgo.ui.analysis.CashFlowTest`, move back | 28/28 pass |
| on device | `gradlew.bat :app:installDebug`; nav: tap (160,2335) then Analysis (684,2285); expand (138,318); gear in full screen (1010,204) | checked by screenshots |

## Notes for resuming
- JAVA_HOME = `C:\Users\LAPTOP\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2`.
- Previous (uncommitted) work in tree: sub levels, fit/scroll full screen, equal-flow fix.
- Inline python heredocs with nested quotes broke bash; write scripts to the scratchpad instead.
