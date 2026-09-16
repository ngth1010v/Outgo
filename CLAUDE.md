# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Outgo — a lightweight, fast-starting Android expense tracker (Kotlin + Jetpack Compose, Material 3).
Single Gradle module (`:app`). The full design (data model, triggers, screens, navigation, backup
format, perf budget) lives in `architecture.md` (written in Vietnamese, uses Mermaid diagrams
heavily) — read it before making non-trivial changes, especially to the DB schema or cold-start path.
`README.md` has the build instructions and a section listing what's implemented vs. `architecture.md`
(a few deferred items: baseline profile module, "manage imported icons" screen, per-account history
entry point).

## Build

Requires JDK 17+ and the Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` or `local.properties`).

```bash
build.bat            # debug APK -> app\build\outputs\apk\debug\app-debug.apk
build.bat release     # release APK, minified (R8 full mode), unsigned unless a signing config is added
build.bat clean       # clean, then debug APK
```

Equivalent raw Gradle (useful for targeted tasks the wrapper script doesn't expose):

```bash
gradlew.bat assembleDebug
gradlew.bat assembleRelease
gradlew.bat lint                  # Android Lint
gradlew.bat test                  # JVM unit tests (none exist yet — module has no src/test)
gradlew.bat connectedAndroidTest  # instrumented tests (none exist yet — module has no src/androidTest)
```

There is currently no test source set in `app/`. If you add tests, they'll need `src/test/` (JVM/Robolectric) or `src/androidTest/` (instrumented) directories created first.

## Architecture

### Layering (strict, one-directional)

```
UI (Compose screens, one package per screen under ui/)
  -> ViewModel (StateFlow<UiState>, exposes event handlers)
    -> Repository (data/repo/*) — suspend fns + Flow, never touches main thread
      -> Room DAOs (data/db/dao/*) -> OutgoDatabase (single SQLite file)
```

- No UseCase layer. Business logic lives in repositories; balance/stat bookkeeping lives in **SQL
  triggers**, not Kotlin.
- No DI framework. `di/AppContainer.kt` is a hand-rolled graph of `by lazy` properties, built in
  `OutgoApp.onCreate`. Adding a new repository/manager means adding a `by lazy` property there, not
  wiring a module/component.
- `domain/` (Enums.kt, Models.kt) has zero Android dependencies — keep it that way.

### The single-SQLite-file model

Everything the user owns — accounts, categories, trades, budgets, settings, and **imported icon PNGs
as BLOBs** — lives in one `outgo.sqlite` file (`data/db/OutgoDatabase.kt`), so backup/restore is a
single-file copy (`VACUUM INTO`, see `data/backup/BackupManager.kt` and architecture.md §9).
`SharedPreferences` is used only as a startup mirror of theme/locale (so the first frame can paint
correctly without opening the DB) — never as a source of truth for user data.

Money is stored as `Long` in the smallest currency unit (no floating point). Time is stored as both
`occurred_at` (epoch ms UTC) and `month_key` (int `yyyyMM`, local time) so monthly queries can use an
indexed integer instead of date functions.

### Triggers, not app-layer bookkeeping

`OutgoCallback.onCreate` in `OutgoDatabase.kt` creates three triggers (`trg_trade_ai/ad/au`) that Room
doesn't know about (they're invisible to Room schema validation/migrations — added as raw SQL, not
`@Entity`). On every trade insert/delete/update, these triggers:

- adjust the owning `account.balance`,
- upsert `category_month_stat` (total + trade_count per category per month, used by the Home chart),
- maintain `category.use_count` / `last_used_at` (used to compute the Trade screen's "top 5 used" /
  "recent 5" category grid).

**If you change trade semantics (new trade type, new balance-affecting field), update the trigger SQL
in `OutgoDatabase.kt`, not just the Kotlin repository layer** — the repository never manually updates
balances/stats, it relies entirely on these triggers firing.

### Cold start (why things are lazy)

The whole point of `AppContainer` being `by lazy` and `OutgoApp.onCreate` kicking off
`appScope.launch(Dispatchers.IO) { database.openHelper.writableDatabase }` is that the first Compose
frame (`Trade` screen, which is `startDestination`) must render before the DB is open. Don't add
synchronous DB/IO work to `OutgoApp.onCreate`, `MainActivity.onCreate`, or the first composition of
`TradeScreen` — it defeats this design. See architecture.md §4 for the full budget (target: TTFF <
400ms, TTFD < 600ms).

### Navigation

Plain string routes (`ui/nav/Routes.kt`) — no Safe Args/type-safe nav library, deliberately, since the
route set is small and fixed. `startDestination = Routes.TRADE` (opening the app goes straight to
"add expense", not Home — this is a hard requirement, not a default that can be casually changed).
Six bottom-nav destinations (Home, Trade, Balance, Category, Analysis, Setting) plus three
parameterized routes for history/trade-edit pushed on top.

### Icons

Two sources, both resolved through `data/icon/IconStore.kt` + `IconCache` (an in-memory
`LruCache<Long, ImageBitmap>` — no Coil/Glide, deliberately, per architecture.md's decision table):
built-in icons ship as PNGs under `app/src/main/assets/icons/` and are referenced by `asset_key`;
user-imported icons are normalized, deduped by SHA-256, and stored as BLOBs in the `icon` table so
they travel with backups.

### Key implementation detail: manual dependency wiring

When adding a new screen/feature, the pattern is: entity (`data/db/entity/`) -> DAO
(`data/db/dao/`) -> repository (`data/repo/`) -> register in `AppContainer` as `by lazy` -> ViewModel
(constructed via `LocalAppContainer`, see `ui/LocalAppContainer.kt`) -> screen composable in its own
`ui/<feature>/` package. There's no code generation or module scanning tying these together — every
step is an explicit line you have to add.
