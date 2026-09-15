# Outgo

A lightweight, fast-starting Android expense tracker. See [architecture.md](architecture.md) for
the full design (data model, triggers, screens, navigation, backup format).

## Build it

Requires a JDK (17+) and the Android SDK — installing **Android Studio** gets you both. If you
already have `ANDROID_HOME` set (Android Studio sets this up for you), just run:

```bash
build.bat
```

This builds a **debug APK** at `app\build\outputs\apk\debug\app-debug.apk` and prints an `adb
install` command for it. Variants:

```bash
build.bat            REM debug APK (default)
build.bat release     REM release APK, minified — unsigned unless you add a signing config
build.bat clean       REM clean build, then debug APK
```

If `ANDROID_HOME`/`ANDROID_SDK_ROOT` isn't set and there's no `local.properties`, the script warns
you and you'll need to either install Android Studio or set `ANDROID_HOME` yourself.

The first build downloads the Gradle 8.9 distribution (~180 MB, one-time, cached under
`%USERPROFILE%\.gradle`) and all dependencies, so it needs an internet connection and will take a
few minutes. Later builds are much faster.

To open it in Android Studio instead: **File → Open** and pick this folder.

## Project layout

```
app/src/main/
├── java/app/outgo/
│   ├── data/          # Room entities/DAOs/triggers, repositories, icon store, backup manager
│   ├── domain/         # Plain enums/constants/models — no Android dependency
│   ├── di/             # AppContainer: manual dependency graph (no Hilt)
│   ├── ui/             # One package per screen, plus theme/, nav/, component/
│   ├── util/           # Money formatting, month-key math, date formatting
│   ├── OutgoApp.kt      # Application: builds AppContainer, warms the DB in the background
│   └── MainActivity.kt
├── assets/icons/        # Built-in category/account icon PNGs (Phosphor Icons, MIT license)
└── res/                 # Themes, strings (en default + vi), nav bar vector icons
```

## What's implemented vs. architecture.md

Everything in architecture.md is implemented **except**:

- **Baseline Profile / Macrobenchmark module** — the cold-start *design* (deferred DB open,
  static-first frame, WAL, etc.) is all there, but the separate `:baselineprofile` Gradle module
  and its generated profile are not, to keep this deliverable to one module. Adding it later is
  additive, not a redesign.
- **"Manage imported icons" screen** — Setting → Icons has the row, but the icon-usage list/delete
  UI behind it isn't wired up yet (`IconDao.findUnusedUserIcons()` is already there for it).
- **Per-account history** — `TradeRepository`/`TradeDao` already expose paged queries by account,
  but the Balance screen doesn't yet have an entry point into it; only the Home screen's
  Expense/Income history links are wired into navigation.
- Amount field auto-focus on opening Trade, and `kotlinx.collections.immutable` for list state
  (mentioned in architecture.md's perf checklist) were left out to keep the dependency list small;
  neither changes behavior, just polish.

Everything else — the single-file SQLite database with triggers keeping balances/stats/usage
counts consistent, all six screens, icon import, and backup/restore — is implemented as designed.
