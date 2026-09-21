# Outgo Performance Report

Scope: make the app as fast as possible **without changing any UI, UX, animation, or feature**.
Same durations, easings, layouts, colors, and behavior. Only *how* the work is done changes.

This report is written for an executing agent. Tasks are ordered by priority. Every task lists the
files it touches, the change to make, and how to verify it. Do the P0 tasks first and verify them
before moving on. Read `CLAUDE.md` first. Also read `architecture.md` §4 (cold start) before touching
startup code.

---

## 0. Main problem: animations lag right after launch, then get smoother

### Diagnosis

The pattern "lag at start, gets smoother with use" is the classic sign of **ART running
un-compiled code**. At startup the Compose runtime, Material 3, navigation, and the app's code run in
the interpreter or in JIT warm-up. As you use the app, the JIT compiles the hot methods, so frames
get faster. In this repo, three things add up:

1. **No Baseline Profile or Startup Profile exists.** `architecture.md` plans one (§3 lists the
   `:baselineprofile` module, §4 item 1, the §13 checklist), but it was never built (README lists it
   as deferred). Without one, the app's own code is never AOT-compiled ahead of the first launches.
   Compose's slide/fade animations (`NavHost` transitions, `AnimatedContent` + `tabSlide`,
   `ModalBottomSheet`) run their first frames through the interpreter or JIT. That is the lag.
2. **Probably testing a debug build.** `build.bat` builds debug by default, and the debug APK is
   `debuggable`. Debuggable apps run much slower Compose code (the runtime skips most compilation
   and adds debug checks). **Never judge animation smoothness on a debug build.** An earlier commit
   (`9928dbb`) also notes that no release-build measurement exists yet.
3. **Every screen's icons load asynchronously on every composition** (see P0-3). When you open a tab,
   each `IconView` first draws a placeholder vector. Then it loads the bitmap in a coroutine, swaps
   it in, and recomposes, all *during* the 300 ms slide. The first time, this also includes a DB
   lookup, a PNG decode, and a GPU texture upload.

### Quick check to confirm the diagnosis (do this first, no code change)

Build and install a **release** APK (see P0-1 for signing). Then:

```bash
# Simulate a fresh install: wipe any compiled code
adb shell cmd package compile --reset app.outgo
adb shell am force-stop app.outgo
# Launch, then switch tabs a few times: expect lag
adb shell dumpsys gfxinfo app.outgo reset
#   ...use the app for ~20 s: switch all 6 tabs, Expense/Income/Transfer, open "All categories"...
adb shell dumpsys gfxinfo app.outgo | findstr /C:"Janky frames" /C:"50th" /C:"90th" /C:"99th"

# Now force full profile-guided AOT compilation and repeat the exact same steps
adb shell cmd package compile -m speed -f app.outgo
adb shell am force-stop app.outgo
adb shell dumpsys gfxinfo app.outgo reset
#   ...repeat the same steps...
adb shell dumpsys gfxinfo app.outgo | findstr /C:"Janky frames" /C:"50th" /C:"90th" /C:"99th"
```

If the `speed` run is clearly smoother, the diagnosis is confirmed and P0-2 (Baseline Profile) is
the fix. Record both numbers in the final summary.

---

## P0: fixes for the startup animation lag

### P0-1. Make a release build that can be installed locally (measurement prerequisite)

- **Problem:** Release APKs are unsigned (`app/build.gradle.kts` has no `signingConfig`). Android
  refuses to install an unsigned APK, despite what the comment in `build.bat` says. So all testing
  happens on the slow debuggable build.
- **Change** (`app/build.gradle.kts`): do not sign the real `release` with the debug key. If you
  use the Baseline Profile plugin in P0-2, it already creates `nonMinifiedRelease` and
  `benchmarkRelease` variants signed with the debug key. Use `benchmarkRelease` for local testing.
  If you skip the plugin, add a `benchmark` build type:
  ```kotlin
  create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
      isDebuggable = false
  }
  ```
- Add `<profileable android:shell="true" tools:targetApi="29" />` inside `<application>` in
  `AndroidManifest.xml`. This lets macrobenchmark and Perfetto profile non-debuggable builds. It has
  no user-visible effect.
- **Note:** `debug` uses `applicationIdSuffix = ".debug"`, so release and benchmark builds install as
  a separate app with an empty DB. To test with real data, export a backup from the debug app
  (Setting → Export). Then import it in the release app. Backup/restore is a single-file copy.
- **Verify:** `gradlew.bat assembleBenchmarkRelease` (or `assembleBenchmark`) produces an APK that
  `adb install -r` accepts.

### P0-2. Add a Baseline Profile + Startup Profile (biggest win)

This is already the documented plan (`architecture.md` §3, §4.1, §13). It AOT-compiles the code
paths listed in the profile at install time, including Compose animation and layout code. Expected
gain: roughly 30–40% faster first frames, and most of the first-use animation jank goes away.

**Recommended: generated profile, `:baselineprofile` module**

1. `gradle/libs.versions.toml`: add these versions. They must be compatible with AGP 8.5.2 and
   Kotlin 2.0.21. Check this, and use the newest versions that still support AGP 8.5:
   - `androidx.baselineprofile` Gradle plugin, 1.3.x
   - `androidx.benchmark:benchmark-macro-junit4`, 1.3.x
   - `androidx.test.uiautomator:uiautomator`, 2.3.x
   - `androidx.test.ext:junit`
   - `androidx.profileinstaller:profileinstaller`, 1.3.x or newer
2. `settings.gradle.kts`: `include(":baselineprofile")`.
3. Root `build.gradle.kts`: add `alias(libs.plugins.android.test) apply false` and
   `alias(libs.plugins.baselineprofile) apply false`.
4. `app/build.gradle.kts`:
   - plugin `alias(libs.plugins.baselineprofile)`
   - `implementation(libs.androidx.profileinstaller)`. This dependency installs the profile on
     sideloaded installs, not only Play installs.
   - `"baselineProfile"(project(":baselineprofile"))`
   - `baselineProfile { saveInSrc = true; automaticGenerationDuringBuild = false }`
5. New module `baselineprofile/` (`com.android.test` + `androidx.baselineprofile` plugins,
   `targetProjectPath = ":app"`, minSdk 30), with one generator class
   `BaselineProfileGenerator` that uses `BaselineProfileRule`:
   - `rule.collect(packageName = "app.outgo", includeInStartupProfile = true) { ... }`
   - **Journey 1 (startup):** `pressHome(); startActivityAndWait()`. Wait until the Trade screen's
     amount field or Save button is visible.
   - **Journey 2 (the laggy animations):** from Trade, tap Expense → Income → Transfer → Expense
     (the `tabSlide` `AnimatedContent`). Type an amount. Tap a category cell. Open
     "All categories" and close it (`ModalBottomSheet`). Then tap **each of the 6 bottom-bar items**
     in order and back (the `NavHost` slides). On Home, switch the history tabs and fling the
     list. On Category, switch Expense/Income. On Analysis, open one sub screen and go back.
   - Wait for idle after each step (`device.waitForIdle()` or `wait(Until...)`) so each animation
     fully runs.
   - Find UI elements by text or content description. If needed, add
     `Modifier.semantics { testTagsAsResourceId = true }` on the root and `testTag`s. This is
     invisible to users.
6. Generate on an **API 33+ emulator or device** (non-rooted is fine on 33+):
   `gradlew.bat :app:generateBaselineProfile`. Commit the output files
   (`app/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`, or the
   release variant path the plugin prints).
7. Startup profile: `includeInStartupProfile = true` makes R8 put startup classes in the primary
   dex (dex layout optimization). This needs the release build with minify on, which is already
   the case.

**Fallback if no API 33+ device or emulator is available:** hand-write
`app/src/main/baseline-prof.txt` with wildcard rules and add the `profileinstaller` dependency:

```
HSPLapp/outgo/**->**(**)**
Lapp/outgo/**;
HSPLandroidx/compose/animation/**->**(**)**
HSPLandroidx/compose/foundation/**->**(**)**
HSPLandroidx/compose/material3/**->**(**)**
HSPLandroidx/navigation/compose/**->**(**)**
```

This is coarser (it compiles more than needed and gives no startup dex layout), but it still removes
most of the JIT cost. Prefer the generated profile.

**How the profile reaches the device:** Play Store installs apply it right away. For sideloaded
APKs, `profileinstaller` writes it on first launch, and ART compiles it at the next background
dexopt. To apply it immediately while testing:

```bash
adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE app.outgo/androidx.profileinstaller.ProfileInstallReceiver
adb shell am force-stop app.outgo
adb shell cmd package bg-dexopt-job
```

(Or `adb shell cmd package compile -m speed-profile -f app.outgo`.) Also, Android Studio's
"Run" of a release variant installs the profile automatically.

- **Verify:** repeat the gfxinfo steps from §0 after `compile --reset` + profile install. Jank %
  during the first tab switches should be close to the forced `-m speed` run. Optionally add a
  `StartupBenchmark` (`StartupTimingMetric`, `CompilationMode.None()` vs `Partial()`) in the same
  module to get TTFF numbers against the §4 budget (< 400 ms).

### P0-3. Make `IconView` render cached bitmaps in the first frame (no placeholder flash mid-animation)

- **Files:** `ui/component/IconView.kt`, `data/icon/IconStore.kt`, `data/icon/IconCache.kt`.
- **Problem:** `IconView` does `remember(iconId) { mutableStateOf<ImageBitmap?>(null) }` and then
  a `LaunchedEffect` → `bitmapFor()`. So **every time** a screen is composed, including every
  bottom-tab switch (NavHost disposes the old tab's composition), every icon:
  1. draws the `ph_image` placeholder vector for at least 1 frame,
  2. resumes a coroutine a frame later, even when the bitmap is already in the `LruCache`,
  3. recomposes to swap in the bitmap.

  All of this happens during the 300 ms slide. Home, Balance, Category, and Analysis each have
  10–30+ icons, so this is 10–30+ extra recompositions and a visible placeholder flash on every
  navigation.
- **Change:**
  - Add a non-suspending `fun cached(iconId: Long?): ImageBitmap?` to `IconStore` (`cache.get`
    only). Do the same for asset keys (`assetCache`).
  - In `IconView`: `var bitmap by remember(iconId) { mutableStateOf(container.iconStore.cached(iconId)) }`,
    and make the `LaunchedEffect` return early when `bitmap != null`. Do the same in
    `BuiltinIconImage`.
  - After decoding in `IconStore.decode` / `bitmapForAsset`, call `bitmap.prepareToDraw()` on the
    Android `Bitmap` before wrapping it. This starts the GPU texture upload asynchronously on the
    RenderThread, instead of doing it synchronously in the first frame that draws the icon.
  - Warm the cache early: `IconStore.preload()` exists but is **never called**. In `OutgoApp`'s
    existing IO coroutine, right after `writableDatabase` opens, launch (don't await) a preload of
    the icon ids of all active accounts and all non-archived categories. This is a handful of small
    PNGs, 128 px each, about 64 KB decoded each. The 8 MB cache holds about 120 of them. Keep it
    after DB open and off the main thread, so cold start (architecture §4) is unaffected.
  - Make `IconStore` cache access thread-safe if preload runs concurrently with UI calls.
    `android.util.LruCache` is synchronized. `assetCache` is a plain `HashMap`: switch it to
    `ConcurrentHashMap`, or to a second `LruCache`, which also bounds its memory.
- **UI impact:** none, except that the placeholder no longer flashes for already-loaded icons. The
  placeholder is still shown for icons that are not decoded yet, exactly as now.
- **Verify:** navigate Trade → Home → Balance → Home. On the second visit no placeholder is drawn
  (check with a slow-motion screen recording, or log in the placeholder branch). gfxinfo jank % for
  tab switches drops.

---

## P1: remove extra work that happens during animations

### P1-1. Stop re-creating Room Flows on every recomposition

- **`ui/trade/TradeScreen.kt:465`** (`AllCategoriesSheet`):
  `container.categoryRepository.observeAllOfType(type).collectAsState(...)` builds a **new Flow on
  every recomposition**. `collectAsState` is keyed on the Flow instance, so every recomposition
  (each search keystroke, and the sheet's own opening) cancels the collector and re-runs the Room
  query. Fix: `val flow = remember(type) { container.categoryRepository.observeAllOfType(type) }`,
  then `flow.collectAsState(...)`.
- **`ui/component/IconPickerSheet.kt:53`**: same bug with
  `container.database.iconDao().observeUserIcons()`. Wrap it in `remember { }`. Also, that query is
  `SELECT *`, so it loads **every imported icon's PNG BLOB** only to use `.id`. Add
  `@Query("SELECT id FROM icon WHERE kind = 1 ORDER BY created_at DESC") fun observeUserIconIds(): Flow<List<Long>>`
  to `IconDao` and use it here. Keep `observeUserIcons` if something else uses it (grep first).

### P1-2. Icon picker sheet composes 105 icons and triggers 105 recompositions while sliding up

- **File:** `ui/component/IconPickerSheet.kt`.
- **Problem:** all 105 `BuiltinIcons.ALL` cells are composed eagerly in a `verticalScroll` column.
  Each has its own `LaunchedEffect` + asset decode, and each result recomposes separately, all
  during the `ModalBottomSheet` enter animation.
- **Change (keep the layout pixel-identical):** keep the current `IconGrid`/`Row` layout. With
  P0-3's synchronous cache read, only the first open decodes. Also add
  `suspend fun IconStore.preloadAssets(keys)` and call it from the P0-3 warm-up (after DB open, low
  priority). Then the sheet opens with every bitmap already cached, with 0 async swaps. Do **not**
  switch to `LazyVerticalGrid` unless you verify the spacing is identical (`SpaceEvenly` rows +
  `4.dp` padding + `52.dp` filler spacers).

### P1-3. History: first page is queried twice on first open

- **Files:** `ui/history/HistoryViewModel.kt:362`, `ui/home/HomeScreen.kt:101`,
  `ui/history/HistoryScreen.kt:263`.
- **Problem:** `HistoryViewModel.init` calls `refresh()`, and both screens also call
  `LaunchedEffect(viewModel) { viewModel.refresh() }` on first composition. So the first visit to
  Home runs two `firstPage` queries and two state emissions, which means an extra full list
  recomposition during the Home slide-in.
- **Change:** remove the `refresh()` call from `init`. Both callers already refresh on entry, and
  that is what keeps the "re-read after editing a trade" behavior. Grep for other
  `HistoryViewModel(` constructions first. There must be none that rely on `init` loading.

### P1-4. History rows recompose on every state change

- **Files:** `ui/history/HistoryList.kt` (`historyItems`, `HistoryItem`), `ui/home/HomeScreen.kt`.
- **Problem:** `HistoryItem` takes the whole `HistoryUiState`. It is unstable (List/Map fields), so
  with strong skipping it is compared by identity. Each `copy()` (e.g. `isLoadingMore` true → false
  on every page load, and each accounts/categories emission) recomposes **every visible row**, twice
  per page load, while the user is scrolling.
- **Change:** pass `categoriesById: Map<Long, CategoryEntity>` and
  `accountsById: Map<Long, AccountEntity>` instead of `state`. These map instances survive
  unrelated `copy()` calls, so rows skip. Update the Home "outgoing" overlay call too
  (`HomeScreen.kt:259`, which uses `old.state`: store the two maps in `OutgoingHistory` instead).

### P1-5. Build history rows off the main thread

- **Files:** `ui/home/HomeScreen.kt:102`, `ui/history/HistoryScreen.kt:265`,
  `ui/history/HistoryViewModel.kt`.
- **Problem:** `remember(state.trades) { buildHistoryItems(state.trades) }` runs on the main thread.
  It formats a `EEEE, dd/MM/yyyy` date for **every** loaded trade, and rebuilds the whole list after
  every `loadMore` page. After a few pages this is hundreds of `DateTimeFormatter.format` calls in
  one frame, mid-scroll.
- **Change:** compute the item list in the ViewModel on `Dispatchers.Default` and expose it in
  `HistoryUiState` (e.g. `val items: List<HistoryListItem>`). Keep `buildHistoryItems` as it is,
  just move where it runs. Keys, content types, and ordering stay identical.

---

## P2: smaller wins (do after P0/P1, each is independent)

### P2-1. Bar chart: move per-draw work out of the draw lambda
`ui/home/StackedBarChart.kt`. Inside `Canvas { }`, every draw pass re-filters and re-sorts each
month's entries (`sortedBy { rootOrder.indexOf(...) }`, which is O(n²)). It also re-measures every
axis label (`textMeasurer.measure`). Precompute the per-month sorted income/expense lists in a
`remember(byMonth, rootOrder)`. Measure tick labels with `Modifier.drawWithCache` (cached until size
or inputs change), or `remember` them keyed on the tick values. Drawing output must be identical.

### P2-2. `OutgoTheme`: remember the color scheme
`ui/theme/Theme.kt`: `dynamicLightColorScheme(context)` is rebuilt on every recomposition of the
theme. Wrap it in `remember(context) { ... }`. This is tiny, but it is on the first-frame path.

### P2-3. `collectAsState` → `collectAsStateWithLifecycle`
The dependency (`lifecycle-runtime-compose`) is already declared but unused. Replace in all screens
(`grep -rn collectAsState app/src/main/java`). This stops UI collection while the app is in the
background. It does not change what is shown.

### P2-4. Trade screen: don't feed the whole state to `AnimatedContent`
`ui/trade/TradeScreen.kt:125`. `targetState = state` means every keystroke in the amount or note
field creates a new target. `contentKey` prevents a transition, but `AnimatedContent` still does
target bookkeeping on each change. This is optional and low value. Only do it if profiling shows
`AnimatedContent` in the typing path. It must keep the "old tab keeps its last state while sliding
out" behavior, which is *why* the full state is the target. If unsure, leave it.

### P2-5. DB: index for the monthly correlated subqueries
`AccountDao.observeActiveWithProgress` and the budget queries run correlated subqueries per account
on `trade.month_key`. The `OR (t.to_account_id = a.id ...)` prevents use of the
`(account_id, occurred_at)` index, so each account scans the month's trades twice (current and
previous month). This re-runs after every trade insert (Room invalidation), and it is the first data
that Home and Balance wait for. It is fine at hundreds of trades and slow at tens of thousands.
- Add `Index(value = ["month_key", "type"])` to `TradeEntity`. Bump the DB `version` to 5. Add a
  `MIGRATION_4_5` with **exactly** the SQL Room expects:
  `CREATE INDEX IF NOT EXISTS index_trade_month_key_type ON trade (month_key, type)`.
- Do **not** touch the triggers (`createTradeTriggers`). Update `SCHEMA_VERSION` too.
  Check `BackupManager` restore logic: it may compare schema versions, so an older backup must
  still restore (Room migrates it on open).
- Verify with `EXPLAIN QUERY PLAN` (via `adb shell` + `sqlite3` on a debug build) that the index is
  used.

### P2-6. Consider a Compose/AGP upgrade (only with full visual regression check)
Compose BOM `2024.09.02` (Compose 1.7) with AGP 8.5.2 and compileSdk 34. Newer Compose releases
improved lazy-list prefetch and composition/animation performance. Upgrading needs compileSdk 35+
and a newer AGP, and it can subtly change Material 3 visuals (spacing, ripple, bottom-sheet
motion). Since the goal is "UI must stay the same", do this last and as its own commit. Compare
screenshots of every screen before and after. Skip it if anything looks different.

---

## Things checked that are already fine (don't "fix" these)

- Cold-start design: `AppContainer` is all `by lazy`, and the DB is opened on IO in
  `OutgoApp.onCreate`. The first frame doesn't wait for the DB. Locale and currency come from
  SharedPreferences mirrors. Keep it this way.
- R8 full mode, `isShrinkResources`, `resourceConfigurations = en, vi`, WAL +
  `synchronous=NORMAL`. All already on.
- History uses keyset paging, `contentType`, and stable keys, and the history tab-switch overlay
  only freezes visible rows. Good.
- Room Flows are only collected from ViewModels via `stateIn(WhileSubscribed(5000))`, except the
  two sheet bugs in P1-1.
- The rest of the category, account, and budget queries have suitable indexes for current data
  sizes.
- Not actionable in-app: the first-ever launch after install also pays GPU shader compilation.
  HWUI caches it, so it only affects the very first run.

## Rules for the executing agent

- **No visual or behavior changes.** Same animation specs (`SLIDE_MS = 300`, `EaseInOut`), same
  layouts, same start destination (`Routes.TRADE`, a hard requirement), same features.
- Don't add image libraries (no Coil/Glide). Don't add DI. Follow the existing layering (see
  `CLAUDE.md`).
- Don't add synchronous IO to `OutgoApp.onCreate`, `MainActivity.onCreate`, or `TradeScreen`'s
  first composition.
- One task per commit, using the repo's commit-message format (`git-commit-message` skill).
- After each task: `gradlew.bat assembleDebug` and `gradlew.bat assembleRelease` must pass, and
  `gradlew.bat lint` must not show new errors.
- Report before/after numbers for P0 (gfxinfo jank % and 90th/99th percentile frame time for the
  §0 script, on a release/benchmark build after `compile --reset`). Say plainly if a number did not
  improve.
