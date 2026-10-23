# PROCESS

**Status:** DONE
**Started:** 2026-10-24
**Last updated:** 2026-10-24

## Task
Follow-up on the draft screen's Impact tab: "chỉnh các budget/saving để nó có format giống bên home
screen (có process bar / process chart)", with:
- line chart: the drafts' change dashed — 3 lines: now / now + drafts / predict (plan) line
- progress bar: the drafts' change faded
User answers: plan line becomes dotted (only in Impact); right-side amount = after drafts + % vs last
month + "(±draft)"; gap shading between with-drafts line and plan; chart collapsed, tap to open (like Home).

## Plan
1. [x] Chart: `BalanceLine.dotted`, `DailyBalanceChart(gapLines = …)`, `AccountLegend(strokeSamples = …)`.
2. [x] `BudgetProgressBlock(draft: DraftOverlay?)`: faded bar part, dashed with-drafts line, dotted plan, pace texts after drafts.
3. [x] `MonthAmountBlock` moved from HomeScreen to `ui/component/MonthAmountBlock.kt`, + `draftDelta`.
4. [x] VM: `BudgetImpact.addedByDay` / `SavingImpact.addedByDay` via `runningByDay`.
5. [x] DraftListScreen: `BudgetImpactRow` / `SavingImpactRow` like Home's rows.
6. [x] Strings `progress_line_with_drafts` (EN/VI); removed unused `draft_impact_before_after`.
7. [x] Build + emulator check.
8. [x] Follow-up "lấy luôn saving/budget row bên home": Home's BudgetRow/SavingsAccountRow + their Card/over-budget badge moved to `ui/component/ProgressCards.kt` (`BudgetCard`, `SavingsCard`, optional `BudgetDraftChange`/`SavingDraftChange`); Home and the Impact tab both use them. Emulator: Impact cards look like Home's.

## Done
- All of the above. Home's rows pass no draft, so they draw as before.

## Doing now
- Nothing.

## Not done yet
- Not committed. Baseline profile not regenerated.

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew.bat :app:assembleDebug` | pass |
| budget row + chart | emulator, expense draft 120,000 in Đi chợ (child of Ăn uống) | 3 lines + legend samples, faded bar part, (-120,000) |
| savings row + chart | emulator, income draft 300,000 into Quỹ khẩn cấp | dashed line jumps to 3.8M today, shading follows it |

## Notes for resuming
- Drafts are memory-only: reinstalling the APK clears them.
