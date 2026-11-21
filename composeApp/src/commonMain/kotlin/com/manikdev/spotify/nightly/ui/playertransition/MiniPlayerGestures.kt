package com.manikdev.spotify.nightly.ui.playertransition

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val VERTICAL_BIAS = 1.5f
private val HORIZONTAL_THRESHOLD = 72.dp

@Composable
fun Modifier.playerDockGestures(
    pull: PlayerPullController?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier {
    val currentPull by rememberUpdatedState(pull)
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val id = down.id
            var slop: PointerInputChange? = null
            var vertical = false
            while (slop == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                val delta = change.position - down.position
                if (delta.getDistance() < viewConfiguration.touchSlop) continue
                vertical = delta.y < 0f && abs(delta.x) < -delta.y * VERTICAL_BIAS
                if (!vertical && abs(delta.x) < abs(delta.y)) return@awaitEachGesture
                if (vertical && currentPull == null) return@awaitEachGesture
                change.consume()
                slop = change
            }

            if (!vertical) {
                var total = slop.position.x - down.position.x
                val finished = drag(id) { change ->
                    total += change.positionChange().x
                    change.consume()
                }
                if (finished) {
                    if (total <= -HORIZONTAL_THRESHOLD.toPx()) next() else if (total >= HORIZONTAL_THRESHOLD.toPx()) previous()
                }
                return@awaitEachGesture
            }

            val pullController = currentPull ?: return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            tracker.addPointerInputChange(slop)
            pullController.drag(slop.position.y - down.position.y)
            val finished = drag(id) { change ->
                val dy = change.positionChange().y
                change.consume()
                tracker.addPointerInputChange(change)
                pullController.drag(dy)
            }
            pullController.release(if (finished) tracker.calculateVelocity().y else 0f)
        }
    }
}
