package com.manikdev.spotify.nightly.ui.playertransition

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A draw-phase artwork portal. Layout is calculated only when an anchor changes; transition
 * progress itself is consumed by graphicsLayer so every spring frame avoids a subtree remeasure.
 */
@Composable
fun PlayerArtworkPortal(
    dock: PlayerDock,
    artwork: ImageBitmap?,
    fullArtworkBounds: Rect?,
    modifier: Modifier = Modifier,
) {
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    val mini = dock.miniArtworkOnScreen()
    val full = fullArtworkBounds
    val root = rootBounds
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        if (artwork != null && mini != null && full != null && root != null && full.width > 0f && full.height > 0f) {
            val fullLeft = full.left - root.left
            val fullTop = full.top - root.top
            val fullWidth = full.width
            val fullHeight = full.height
            val miniLeft = mini.left - root.left
            val miniTop = mini.top - root.top
            val miniWidth = mini.width
            val miniHeight = mini.height

            Image(
                bitmap = artwork,
                contentDescription = null,
                modifier = Modifier
                    .offsetPx(fullLeft, fullTop)
                    .size(with(density) { fullWidth.toDp() }, with(density) { fullHeight.toDp() })
                    .graphicsLayer {
                        // Read progress here, not in composition/layout. The spring therefore
                        // invalidates only this render layer instead of recomposing the player.
                        val p = dock.transitionProgress.coerceIn(0f, 1f)
                        val targetLeft = lerp(miniLeft, fullLeft, p)
                        val targetTop = lerp(miniTop, fullTop, p)
                        val targetWidth = lerp(miniWidth, fullWidth, p)
                        val targetHeight = lerp(miniHeight, fullHeight, p)
                        translationX = targetLeft - fullLeft
                        translationY = targetTop - fullTop
                        scaleX = targetWidth / fullWidth
                        scaleY = targetHeight / fullHeight
                    },
            )
        }
    }
}

private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    graphicsLayer {
        translationX = x
        translationY = y
    }

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
