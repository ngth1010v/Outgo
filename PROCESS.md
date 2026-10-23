# PROCESS

**Status:** IN_PROGRESS
**Started:** 2026-10-23 22:00
**Last updated:** 2026-10-23 22:00

## Task
User: "bump the version; commit all changes; build and release a new version; rewrite a README.md
(you can use emulator to create gif/pic); commit and push all changes".

## Plan
1. [ ] Bump app/build.gradle.kts 4.17.0 (46) -> 4.18.0 (47); commit with PROCESS.md
2. [ ] build.bat release, verify signing cert ad5e3f, gh release v4.18.0 with Outgo.apk
3. [ ] Screenshots/GIF from emulator (mdn_test) into docs/; rewrite README.md
4. [ ] Commit + push (main + tag)

Changes since v4.17.0: draft list on Add screen (66c4ebe), swipe undo snackbar (111f548),
amount cursor follows taps (61f4d7c). No schema change.

## Done
## Doing now
- step 1
## Not done yet
- steps 2-4
## Tests / verification
| Case | How | Result |
|------|-----|--------|
| release build | build.bat release | not run |

## Notes for resuming
- See memory build-env-windows-gotchas (JAVA_HOME = ~/.gradle/jdks/eclipse_adoptium-21..., clear NoDefaultCurrentDirectoryInExePath).
