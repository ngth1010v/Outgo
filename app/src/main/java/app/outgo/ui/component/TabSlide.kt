package app.outgo.ui.component

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Shared timing for every slide in the app: under 1s, ease-in-out. */
const val SLIDE_MS = 300

/**
 * AnimatedContent transform for content under a tab switcher: the old tab slides out one side
 * while the new one slides in from the other, by [index] order.
 */
fun <S> AnimatedContentTransitionScope<S>.tabSlide(index: (S) -> Int): ContentTransform {
    val forward = index(targetState) > index(initialState)
    return (
        slideInHorizontally(tween(SLIDE_MS, easing = EaseInOut)) { if (forward) it else -it } +
            fadeIn(tween(SLIDE_MS, easing = EaseInOut)) togetherWith
            slideOutHorizontally(tween(SLIDE_MS, easing = EaseInOut)) { if (forward) -it else it } +
            fadeOut(tween(SLIDE_MS, easing = EaseInOut))
        ) using SizeTransform { _, _ -> tween(SLIDE_MS, easing = EaseInOut) }
}
