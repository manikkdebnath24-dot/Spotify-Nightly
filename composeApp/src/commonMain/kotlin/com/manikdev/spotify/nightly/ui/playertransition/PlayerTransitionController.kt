package com.manikdev.spotify.nightly.ui.playertransition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Clean-room implementation of the supplied mini-player transition idea.
 * The sheet remains Material 3; only the artwork portal and transition progress are custom.
 */
@Stable
class PlayerTransitionController(
    private val scope: CoroutineScope,
    private val dock: PlayerDock,
    private val shown: () -> Boolean,
    private val setShown: (Boolean) -> Unit,
    private val sheet: () -> SheetState?,
) : PlayerPullController {
    private companion object {
        const val STIFFNESS = 195f
        const val SETTLED_PX = 0.5f
        const val FLICK_VELOCITY = 900f
    }

    private var job: Job? = null
    private var pullStartNanos = 0L
    private var closing by mutableStateOf(false)

    var manualProgress by mutableFloatStateOf(0f)
        private set

    fun open() {
        job?.cancel()
        closing = false
        if (!shown()) setShown(true)
        job = scope.launch {
            // Let the sheet create/layout before starting the portal animation.
            kotlinx.coroutines.yield()
            animateProgress(from = 0f, to = 1f)
            dock.transitionActive = false
        }
    }

    fun close(onFinished: () -> Unit) {
        if (!shown() || closing) return
        closing = true
        job?.cancel()
        job = scope.launch {
            animateProgress(from = currentProgress(), to = 0f)
            dock.transitionActive = false
            setShown(false)
            onFinished()
            closing = false
        }
    }

    fun currentProgress(): Float =
        if (dock.transitionActive) manualProgress else dock.sheetOpenFraction()

    override fun drag(deltaY: Float) {
        if (!shown()) {
            if (deltaY >= 0f) return
            job?.cancel()
            closing = false
            setShown(true)
            pullStartNanos = System.nanoTime()
            manualProgress = 0f
            dock.transitionActive = true
        }
        dock.transitionActive = true
        // Upward finger movement is negative: turn it into positive opening progress.
        val travel = dock.sheetTravelPx.takeIf { it > 0f } ?: 1000f
        manualProgress = (manualProgress - deltaY / travel).coerceIn(0f, 1f)
        dock.transitionProgress = manualProgress
    }

    override fun release(velocityY: Float) {
        if (!dock.transitionActive) return
        val elapsed = (System.nanoTime() - pullStartNanos) / 1_000_000_000f
        val averageProgressVelocity = if (elapsed > 0f) manualProgress / elapsed else 0f
        val speed = if (abs(averageProgressVelocity * 1000f) > abs(velocityY)) averageProgressVelocity * 1000f else -velocityY
        val open = if (abs(speed) >= FLICK_VELOCITY) speed > 0f else manualProgress >= 0.5f
        job?.cancel()
        job = scope.launch {
            if (open) {
                animateProgress(manualProgress, 1f, speed)
                dock.transitionActive = false
            } else {
                animateProgress(manualProgress, 0f, speed)
                dock.transitionActive = false
                setShown(false)
            }
        }
    }

    private suspend fun animateProgress(from: Float, to: Float, velocity: Float = 0f) {
        dock.transitionActive = true
        animate(
            initialValue = from.coerceIn(0f, 1f),
            targetValue = to.coerceIn(0f, 1f),
            initialVelocity = velocity / (dock.sheetTravelPx.takeIf { it > 1f } ?: 1000f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = STIFFNESS,
                visibilityThreshold = SETTLED_PX / (dock.sheetTravelPx.takeIf { it > 1f } ?: 1000f),
            ),
        ) { value, _ ->
            manualProgress = value.coerceIn(0f, 1f)
            dock.transitionProgress = manualProgress
        }
    }
}
