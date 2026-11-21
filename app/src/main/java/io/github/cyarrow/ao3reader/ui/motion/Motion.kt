package io.github.cyarrow.ao3reader.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Shared motion tokens. Everything that animates uses these, so the app moves with one voice:
 * fast, decisive ease-out for things that appear, quicker exits, nothing over 300ms except sheets.
 * Compose scales all of these by the system "animation duration scale", so "Remove animations"
 * in Android settings turns them off.
 */
object Motion {
    /** Strong ease-out: starts fast, settles gently. For anything entering or responding to a tap. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Strong ease-in-out: for movement between two on-screen positions (page turns). */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** iOS-like drawer curve: for panels that slide in from an edge. */
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    const val Fast = 150
    const val Standard = 220
    const val PageTurn = 260

    fun <T> enter(duration: Int = Standard): FiniteAnimationSpec<T> = tween(duration, easing = EaseOut)
    fun <T> exit(duration: Int = Fast): FiniteAnimationSpec<T> = tween(duration, easing = EaseOut)
}
