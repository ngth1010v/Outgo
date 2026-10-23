# PROCESS

**Status:** DONE
**Started:** 2026-10-23 06:00
**Last updated:** 2026-10-23 06:00

## Task
Fix UX/logic issues found in an emulator audit (numbered 1-30 in chat). User decisions:
- Keep as is: 1, 8, 18, 19, 22, 23.
- 2: only clear validation errors when leaving the Add tab.
- 7: fixed money format `<money><currency>` with no space for every currency ("0$", "12.5$", "1000đ").
- 16: negative balance becomes a warning, saving allowed.
- 17: auto-select account when only one exists; remember last account only as a ghost (fallback for prediction).
- 26: include adjustments in History "All" and add an "Adjustment" filter type.
- 29: bottom-bar tabs work on editor screens, asking to discard changes first.
- Others (3,4,5,6,9,10,11,12,13,14,15,20,21,24,25,27,28,30): fix as recommended:
  3 autofocus amount only when form empty; 4 budget-limit error only after Save; 5 unified editor buttons
  (back top-left, Save primary, Delete last); 6 budget colour by threshold (<75 ok, <=100 warn, >100 red) + "150$ remain";
  9 one period selector Month/Year; 10 disable Income/Transfer when editing; 11 label "+" buttons, distinct default
  subaccount name, "Edit subaccount" title; 12 mark current colour; 13 cursor to end on amount focus;
  14 seed data by language, currency by region, one term ("transaction"); 15 "No data in the last 5 months";
  20 "+ Budget" on Home; 21 plain-words labels, hide budget-exempt switch when not applicable;
  24 "Savings target" switch only for Savings accounts; 25 Back on Add with data asks first;
  27 first balance entry = "Opening balance" with a date, not drawn as a jump; 28 self-transfer labels;
  30 month summary wording, "—" instead of % when previous is 0.

## Plan
1. [x] Read relevant code
2. [~] Implement fixes one by one
3. [ ] Build, run on emulator (AVD mdn_test), verify
4. [ ] Update baseline profile journey labels if changed ("SAVE"->"Save", seed names now by language)

## Done
- #7 Money.format/formatSigned/formatHidden no space; AmountField no gap before symbol.
- #6 BudgetProgress.budgetRemainingColor(spent, limit) thresholds; remain/over/to-go texts use Money.format; "%1$s left".
- #30 percentChange returns Int? (null when prev 0); KindSummary/ShareRow deltaPercent Int?; AnalysisSections comparisonText (new strings analysis_cmp_*); Home MonthAmountBlock "—".
- #15 analysis_no_data -> "No data for this period".
- #16/#17/#2/#25 TradeViewModel: NEGATIVE_BALANCE error removed, hasInput, clearErrors(), discardInput(), withAccountDefault (single account), withLastAccount (ghost).
- TradeScreen (scratchpad edit_trade.py applied): clear errors on hide, autofocus only when empty, Back-with-input dialog, EditorTitle on edit, warning under account, self-transfer labels+note, Save/Cancel/Delete order, exempt rows hidden when not applicable.
- Strings (scratchpad edit_strings.py applied): trade->transaction (EN), plain labels #21, new trade_* strings, removed trade_save & trade_error_negative_balance.

- #5 EditorScaffold rewritten (EditorTitle back-left, EditorButtons Save/Cancel/Delete, DeleteButton); common_back string.
- #29 DiscardGuard: LeaveGuard + LocalLeaveGuard; OutgoNavHost.selectTab reads current route (stale-lambda bug) and asks guard.
- #10 OutgoSegmentedButton disabled colours. #4 category+account editors: showErrors after Save tap, saveEnabled=true.
- #27 recordAdjustment(occurredAt); AccountRepository.create(openingAt, defaultSubName)/update(openingAt); editor
  "Opening balance" + OpeningDateField; buildDailyBalance new accounts start late; sumLines honours BalanceLine.start.
- #24 Savings target section only for SAVINGS accounts. #11 PlusRow labels, balance_edit_sub_title, default sub name "Main"/"Chính".
- #14 seed bilingual (Locale vi/en), seed currency row = Money.regionalSymbol(); CurrencyPrefs default regional; SeedPalette from palette.
- #12 ColorPickerGrid check mark + nearest swatch. #13: no change needed (field already appends at end; audit finding was wrong).
- compileDebugKotlin passes (after escaping an apostrophe; scratchpad fix_apos.py).

- #26 HistoryFilter.ADJUSTMENT, All includes ADJUST_IN/OUT, TradeDao.openingAdjustmentIds -> "Opening balance" label, adjustment rows not clickable, show account icon.
- #20 Home "+ Add budget" -> BudgetCategorySheet -> Routes.categoryEdit(id, budget=true) -> editor turns budget on + focuses limit.
- #9 AnalysisHeader: one row, arrows per mode, Now, Month/Year switch (strings analysis_period_month/mode_*).
- Baseline journey: "SAVE"->"Save", "Food & drink", "Cash", "Year".
- Docs: architecture.md (money format, sub name, adjustments/opening, negative warning, analysis picker, trade form rows), README accounts bullet.
- assembleDebug passes; verified on emulator (fresh data): 0$ format, English seed, Wallet auto-picked, negative warning + save,
  Back-with-input dialog, editor layout, tab-from-dirty-editor dialog -> lands on History, opening balance 01/10 in History,
  Analysis chart flat from day 1, Month/Year toggle, summary wording, Home Add budget -> green bar "150$ left", self-transfer labels.

## Doing now
- Nothing: all fixes done, lintDebug passes.

## Not done yet
- Baseline profile regeneration (not run: long, wipes emulator app data) — offer to user.

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew assembleDebug` | not run |

## Notes for resuming
- Helper for emulator: scratchpad e.sh (shot/ui/tap/text/key/swipe). JAVA_HOME must be the Gradle JDK 21.
