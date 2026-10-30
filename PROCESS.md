# PROCESS

**Status:** DONE
**Started:** 2026-10-30
**Last updated:** 2026-10-30

## Task
Cash flow chart (Analysis screen): accounts always sit on a top-left -> bottom-right diagonal.
User: "help me execute the: 1, 2, 3, 4" of these suggestions:
1. Layered grid: accounts of the same transfer level share a row/column (no transfers -> one row).
2. Order accounts within a level by barycenter of their carets (fewer crossings), a few passes.
3. Aspect-aware: wide box -> levels go left->right; tall box -> levels go top->bottom.
4. Size-weighted spacing instead of even `step(i)`.
Constraint: keep "money flows only down/right" (receiver of a transfer below OR right of sender).

## Plan
1. [x] Read CashNodeLayout.kt, CashRouting.kt, CashFlowChart.kt to learn constraints.
2. [x] Rewrite account placement in `planFrame` / `layoutCashNodes` (+ `cashNaturalHeight`).
3. [x] Build `gradlew.bat assembleDebug`.

## Done
- CashNodeLayout.kt: FramePlan gained `levels`, `midL`, `midR`. New helpers packLines, linesDepth,
  linesLength, gapNeeds, segmentsCross, segmentHitsBox. cashNaturalHeight + layoutCashNodes rewritten:
  rows-vs-columns pick by shrink factor (tie: 1 level -> rows, else levels along longer side), gaps
  sized by links crossing them, 4 barycenter reorder rounds keeping the lowest cost() placement.
- Added after first emulator look: per-account position tries (CROSS_TRIES=8, 2 sweeps); cost()
  penalises accounts inside a link's bounding box (not just straight line); rows-vs-columns tie
  (>1 level) decided by arrangeFor(true) < arrangeFor(false) cost instead of pure aspect.

## Doing now
- Nothing; done.

## Not done yet
- Not committed (user didn't ask).

## Tests / verification
| Case | How | Result |
|------|-----|--------|
| builds | `gradlew.bat :app:installDebug` | pass |
| card (wide) | emulator screenshot | columns: Cash left, 2 receivers right — good |
| full screen (tall) | emulator screenshot | columns: Cash mid-left, receivers right, no crossings — good |

## Notes for resuming
- All changes in app/src/main/java/app/outgo/ui/analysis/CashNodeLayout.kt (account x/y only).

- Later additions: cost weighs length by money (`pull`), penalises links running past a whole line of accounts.
