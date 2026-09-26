package app.outgo.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline + startup profile: `gradlew :app:generateBaselineProfile` with an
 * API 33+ emulator/device connected. Assumes the device UI language is English.
 *
 * Journeys cover cold start (startup profile) and every screen switch that stutters on
 * un-compiled code: the Trade type tabs, the category sheet, each bottom-bar switch, the Home
 * history tabs and scroll, the Category tabs and an Analysis sub screen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("SAVE")), TIMEOUT)

        animationJourney()
    }
}

/** Walks every animated transition once; shared with [FrameBenchmark]. */
internal fun MacrobenchmarkScope.animationJourney() {
    // Trade: type tabs and the "All categories" sheet.
    tap(By.text("Income"))
    tap(By.text("Transfer"))
    tap(By.text("Expense"))
    tap(By.textStartsWith("All"))
    // Back only once the sheet is really up; on the bare Trade screen it would leave the app.
    if (device.wait(Until.hasObject(By.text("Select a category")), TIMEOUT)) {
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(700)
    }

    // Every bottom-bar destination, left to right and back.
    for (tab in listOf("Home", "Accounts", "Categories", "Analysis", "Settings", "Add", "Home")) {
        tap(By.desc(tab))
    }

    // Home: history tabs and a scroll through the list.
    tap(By.text("Income"))
    tap(By.text("Transfer"))
    tap(By.text("Expense"))
    device.findObject(By.scrollable(true))?.let {
        it.fling(Direction.DOWN)
        device.waitForIdle()
        it.fling(Direction.UP)
        device.waitForIdle()
    }

    // Category tabs.
    tap(By.desc("Categories"))
    tap(By.text("Income"))
    tap(By.text("Expense"))

    // Analysis: the month list and the year list scrolled through, plus a step back a month (the
    // neighbour page follows the scroll position) — that is what compiles the hand-drawn charts.
    tap(By.desc("Analysis"))
    flingDownAndUp()
    tap(By.desc("Previous month"))
    flingDownAndUp()
    tap(By.text("Yearly analysis"))
    flingDownAndUp()
    tap(By.text("Now"))
}

private fun MacrobenchmarkScope.flingDownAndUp() {
    device.findObject(By.scrollable(true))?.let {
        it.fling(Direction.DOWN)
        device.waitForIdle()
        it.fling(Direction.UP)
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.tap(selector: BySelector) {
    // A node found mid-animation can be recycled before the click lands; look it up again.
    for (attempt in 1..3) {
        try {
            device.wait(Until.findObject(selector), TIMEOUT)?.click()
            break
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    device.waitForIdle()
    // Let the screen settle (data loads, sheet animations) so its frames are recorded.
    Thread.sleep(700)
}

internal const val PACKAGE = "app.outgo"
internal const val TIMEOUT = 5_000L
