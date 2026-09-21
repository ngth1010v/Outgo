package app.outgo.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times of the first pass through every animation right after a cold start — the
 * "laggy at launch" case. Run like [StartupBenchmark], with `class=...FrameBenchmark`.
 * Needs a real device: on the API 37 emulator the Perfetto trace records no app frames, so
 * FrameTimingMetric fails with "0 found for frameDurationCpuMs".
 */
@RunWith(AndroidJUnit4::class)
class FrameBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun animationsNoCompilation() = animations(CompilationMode.None())

    @Test
    fun animationsBaselineProfile() = animations(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun animations(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("SAVE")), TIMEOUT)
        },
    ) {
        animationJourney()
    }
}
