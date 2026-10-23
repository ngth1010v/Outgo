# PROCESS

**Status:** DONE
**Started:** 2026-10-23 22:00
**Last updated:** 2026-10-23 22:45

## Task
User: "bump the version; commit all changes; build and release a new version; rewrite a README.md
(you can use emulator to create gif/pic); commit and push all changes".

## Plan
1. [x] Bump app/build.gradle.kts 4.17.0 (46) -> 4.18.0 (47); commit with PROCESS.md
2. [x] build.bat release, verify signing cert ad5e3f, gh release v4.18.0 with Outgo.apk
3. [x] Screenshots/GIF from emulator (mdn_test) into docs/; rewrite README.md
4. [x] Commit + push (main + tag)

Changes since v4.17.0: draft list on Add screen (66c4ebe), swipe undo snackbar (111f548),
amount cursor follows taps (61f4d7c). No schema change.

## Done
- Commit 23312c4 bump 4.18.0 (pushed). Release https://github.com/ngth1010v/Outgo/releases/tag/v4.18.0 with Outgo.apk, cert ad5e3f verified.
- Debug app data on emulator backed up to scratchpad debug_backup.tar (databases/shared_prefs/files); debug app now holds sample English data (scratchpad seed.py, sample2.sqlite). Restored afterwards (done).
## Doing now
- nothing
## Not done yet
- nothing
## Tests / verification
| Case | How | Result |
|------|-----|--------|
| release build | build.bat release | pass, v4.18.0 (47) |

- README rewritten; docs/ screenshots retaken (add, home, accounts, categories, analysis, analysis-categories, history, draft) + docs/add-expense.gif; docs/reorder.png removed.

## Notes for resuming
- See memory build-env-windows-gotchas (JAVA_HOME = ~/.gradle/jdks/eclipse_adoptium-21..., clear NoDefaultCurrentDirectoryInExePath).
