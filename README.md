<div align="center">

# Outgo

**A lightweight, fast-starting expense tracker for Android.**

Open the app and you are already on the "add expense" screen. Log a purchase in a few taps.

[![Latest release](https://img.shields.io/github/v/release/ngth1010v/Outgo)](https://github.com/ngth1010v/Outgo/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin + Jetpack Compose](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

<img src="docs/add.png" width="30%" alt="Add screen" />&nbsp;
<img src="docs/home.png" width="30%" alt="Home screen" />&nbsp;
<img src="docs/analysis.png" width="30%" alt="Analysis screen" />

</div>

## Features

### Quick entry

<img src="docs/add.png" align="right" width="240" alt="Add an expense" />

- **The app opens on the add screen.** You do not have to go through a dashboard to log an expense.
- **Expense, Income and Transfer** use the same form. Swipe left or right to switch between them.
- **Smart category grid:** one row shows your 5 most recent categories, and one row shows your 5 most used. Most entries take one tap. **All ›** opens the full category list, which you can search.
- Pick the account, date, time and an optional note. After you tap **Save**, the form resets and a snackbar offers **Undo**.

<br clear="right" />

### Home dashboard

<img src="docs/home.png" align="right" width="240" alt="Home dashboard" />

- **Available, savings and total balance** at a glance.
- **Budgets** for this month: amount spent, amount remaining or over, and the change against last month.
- **Savings** progress toward each savings account's monthly target.
- **History** grouped by day with daily totals. You can filter by Expense, Income or Transfer. Tap a transaction to edit it or delete it.

<br clear="right" />

<p align="center">
  <img src="docs/history.png" width="240" alt="Transaction history" />
</p>

### Accounts and savings

<img src="docs/accounts.png" align="right" width="240" alt="Accounts" />

- Keep several accounts, for example cash, bank and e-wallet, each with its own icon and color.
- **Savings accounts** can have a monthly target, and a progress bar tracks it. The bar turns green when you reach the target.
- Edit an account's balance directly. Outgo records the difference as a balance adjustment, so the balance always matches the transaction history.
- Accounts that have history are archived, not deleted, so past records stay correct.

<br clear="right" />

### Categories and budgets

<img src="docs/categories.png" align="right" width="240" alt="Categories and budgets" />

- **Two-level categories** (parent and subcategory) for both expense and income. A default set is created on first launch.
- Set a **monthly budget** on any expense category. A parent budget counts the spending of all its subcategories.
- The progress bar shows the remaining amount. It turns red with an "over" amount when you exceed the budget.
- **105 built-in icons**, or **import your own PNG**. Imported icons are stored in the database, so they are included in backups.

<br clear="right" />

### Analysis

<img src="docs/analysis.png" align="right" width="240" alt="Analysis" />

- **One page per month**, swiped or stepped through with the `‹ Sep 2026 ›` picker in the top bar.
- **Month summary:** total spent, change against last month, average per day, income and net.
- **Where the money went:** a donut and a category breakdown (expense or income), a 6-month trend, and the biggest movers against last month.
- **When it went:** a cumulative spending pace line against last month, a daily heatmap that opens that day's history, and the average spend per weekday.
- **What it went on:** the mix of purchase sizes around the month's median, and the five largest purchases.

<br clear="right" />

### Settings and data

- **English and Vietnamese**, chosen in the app or taken from the system language.
- **Currency symbol:** pick one of 20 common currencies, or type your own.
- **Backup and restore to one `.sqlite` file.** All your data is in that file: accounts, categories, transactions, budgets, settings and imported icons. A restore keeps a safety copy of your current data on the device first.
- **Offline and private.** No account, no network, no ads. Your data stays on your phone.

## Download

1. Go to the [Releases page](https://github.com/ngth1010v/Outgo/releases/latest).
2. Download the latest `app-release.apk`.
3. Open the downloaded file on your Android phone to install it.
   - The first time, Android asks for permission to install apps from this source. Allow it, then tap **Install**.
4. Open **Outgo** from your app drawer.

Requires Android 11 (API 30) or newer.

## Build from source

Requirements: JDK 17+ and the Android SDK (`ANDROID_HOME` or `local.properties`).

```bash
build.bat            # debug APK   -> app\build\outputs\apk\debug\app-debug.apk
build.bat release    # release APK (R8 minified; unsigned unless you add a signing config)
build.bat clean      # clean, then debug APK
```

Or run Gradle directly: `gradlew.bat assembleDebug`, `gradlew.bat assembleRelease`.

## Under the hood

- **Kotlin, Jetpack Compose and Material 3.** A single-activity app with no DI framework and no image-loading library.
- **One SQLite file (Room).** Money is stored as `Long` in the smallest currency unit, never as floating point. SQL triggers maintain balances, monthly category totals and category usage counts.
- **Fast cold start.** The first frame draws before the database opens. A committed **Baseline Profile** AOT-compiles the startup path and every animated transition.

See [architecture.md](architecture.md) for the full design (in Vietnamese): data model, triggers, screens, backup format and performance budget.

## License

[MIT](LICENSE) © ngth1010v. Navigation and UI icons are based on [Phosphor Icons](https://phosphoricons.com) (MIT).
