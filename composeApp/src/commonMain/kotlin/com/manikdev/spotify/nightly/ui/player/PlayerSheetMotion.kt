package com.manikdev.spotify.nightly.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Ported from BitChord's `ui/player/PlayerSheetMotion.kt`.
 *
 * The idea, in their words: the sheet is never told to show or hide on a curve of its own. It is
 * left open and [held] says where the content really is — how far below fully open, in pixels. A
 * tap springs it, a finger drags it, a flick carries its own speed into the spring, and the artwork
 * dock reads the same number, so however the player is moved it moves one way.
 *
 * What is not ported: the Android `Window` handling (their sheet lives in its own window, so they
 * silence its exit animation and let touches through). Here the player is a Compose overlay, so
 * there is no window to quiet.
 */
class PlayerSheetMotion(
    private val scope: CoroutineScope,
    private val travelPx: () -> Float,
    /** Called when the slide has finished leaving, so the host can drop the player. */
    private val onClosed: () -> Unit,
) {
    /** NaN while the player stands fully open. */
    var held by mutableFloatStateOf(Float.NaN)
        private set

    val holding: Boolean get() = !held.isNaN()

    private var settle: Job? = null
    private var closing = false

    // When the current pull started and how far the finger had taken it - for a flick whose own
    // release speed came through as nothing: a short, fast swipe can lift with no speed at all.
    private var pullStartNanos = 0L
    private var pullStartAt = 0f

    /** How far below open the player currently is, in pixels. 0 = open. */
    fun positionPx(): Float = if (held.isNaN()) 0f else held

    /** 0 at the mini player, 1 fully open - what the visuals ride on. */
    fun progress(): Float {
        val travel = travelPx()
        if (travel <= 0f) return 1f
        return (1f - positionPx() / travel).coerceIn(0f, 1f)
    }

    fun open(velocity: Float = 0f) {
        settle?.cancel()
        closing = false
        val from = positionPx()
        settle =
            scope.launch {
                slide(from = from, to = 0f, velocity = velocity)
                held = Float.NaN
            }
    }

    fun close(velocity: Float = 0f) {
        if (closing) return
        closing = true
        settle?.cancel()
        val from = positionPx()
        held = from
        settle =
            scope.launch {
                slide(from = from, to = travelPx(), velocity = velocity.coerceAtLeast(0f))
                onClosed()
            }
    }

    fun drag(delta: Float) {
        settle?.cancel()
        closing = false
        val travel = travelPx()
        val start = if (held.isNaN()) 0f else held
        if (held.isNaN()) {
            pullStartAt = start
            pullStartNanos = nowNanos()
        }
        held = (start + delta).coerceIn(0f, travel)
    }

    fun release(velocity: Float, flingVelocityPx: Float = 900f) {
        if (held.isNaN()) return
        val travel = travelPx()
        // The finger's speed as it lifted, or across the whole pull if that was faster.
        val seconds = (nowNanos() - pullStartNanos) / 1e9f
        val average = if (seconds > 0f) (held - pullStartAt) / seconds else 0f
        val speed = if (abs(average) > abs(velocity)) average else velocity
        val flick = abs(speed) >= flingVelocityPx
        // A flick decides on its own, either way; otherwise it is where the finger left it.
        val open = if (flick) speed < 0f else held < travel / 2f
        val from = held
        closing = !open
        settle =
            scope.launch {
                // The finger's own speed goes into the spring, so the player carries on as it was
                // moving and then settles - a flick fast, a slow pull gently.
                slide(from = from, to = if (open) 0f else travel, velocity = speed)
                if (open) held = Float.NaN else onClosed()
            }
    }

    private suspend fun slide(
        from: Float,
        to: Float,
        velocity: Float = 0f,
    ) {
        val travel = travelPx()
        animate(
            initialValue = from,
            targetValue = to,
            initialVelocity = velocity,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = SLIDE_STIFFNESS,
                    visibilityThreshold = SLIDE_SETTLED_PX,
                ),
        ) { value, _ ->
            // Critically damped never bounces from rest, but thrown hard enough at its target it
            // can pass it once: never past open or past the bar.
            held = value.coerceIn(0f, travel)
        }
    }

    private companion object {
        /** BitChord's number: critically damped, about 0.45s of response, no fast end. */
        const val SLIDE_STIFFNESS = 195f

        /** Close enough to stop, in pixels, rather than a spring's whole long tail. */
        const val SLIDE_SETTLED_PX = 0.5f
    }
}

/** One monotonic mark for the whole process, so the readings are differences and not zero. */
private val motionClock = kotlin.time.TimeSource.Monotonic.markNow()

/** Monotonic clock without pulling a JVM-only API into common code. */
private fun nowNanos(): Long = motionClock.elapsedNow().inWholeNanoseconds
