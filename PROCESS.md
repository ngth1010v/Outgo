# PROCESS

**Status:** DONE
**Started:** 2026-10-23
**Last updated:** 2026-10-23

## Task
User asked:
- add 'number of decimal places' input in setting screen; money always shows n decimals even if zero (decimal=2 -> "1.00$")
- analysis screen: 'month'/'year' button top-right height x0.7
- edit category/sub-category and edit account/sub-account screens: equal gaps between save/cancel/delete buttons (reduce cancel–delete gap); all toggles same size (= current small one)
- home screen: budget section 'Add budget' button only when no budget, shown as a row; saving section: 'Add saving' button with same logic
- "if you have something not sure -> ask me for confirm"

## Plan
1. [x] Explore code
2. [x] Decimal places setting
3. [x] Analysis toggle height
4. [x] Edit screens buttons/toggles
5. [x] Home budget/saving add rows
6. [x] Build (assembleDebug passes)
7. [x] Check on emulator (mdn_test): setting row, Home decimals, Analysis switch, editor buttons/toggles OK

## Done
- Money.kt: `decimals` snapshot state (0..2, default 0), `groupThousands` always writes n decimals (rounded half-up), `inputText` rounds + trims trailing zeros, `cleanInput` caps typed decimals at n (drops separator at 0).
- SettingKeys.DECIMALS, SettingRepository.observeDecimals/setDecimals, CurrencyPrefs mirror, OutgoApp loads/observes, SettingViewModel + SettingScreen row + OptionSheet, strings `setting_decimals` (en/vi).
- AnalysisScreen: mode switch row `.height(28.dp)`.
- EditorScaffold.DeleteButton spacer 24 -> 8dp (also affects Trade edit, same shared order).
- SectionSwitch moved from CategoryScreen.kt to component/SwitchField.kt; SwitchField now uses it (small 0.75 switch).
- Home: Add budget = PlusRow card only when no budgets (removed `home_no_budgets` string); Savings section always shown, PlusRow "Add saving" when empty -> SavingAccountSheet -> Routes.accountEdit(id, savings=true) -> AccountEditScreen(openSavings) makes it savings, turns this month's target on, focuses target.

## Doing now
- Nothing

## Not done yet
- Nothing (Add-saving flow not clicked through on emulator: test data already has savings)

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew assembleDebug` | pass |

## Notes for resuming
- User decisions: decimals range 0/1/2 (display only, storage stays hundredths), default 0; 'Add saving' = pick-existing-account sheet then open account editor with Savings preset.
- Baseline profile not regenerated (needs API 33+ emulator run of :app:generateBaselineProfile).
