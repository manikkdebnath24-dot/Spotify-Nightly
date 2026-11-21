package com.manikdev.spotify.nightly.ui.playertransition

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * Small, allocation-free coordinate hub shared by the mini player and the player sheet.
 * The mini player publishes its artwork bounds once layout changes; the sheet consumes them
 * only while a transition is active.
 */
@Stable
class PlayerDock {
    private var miniArtworkCoordinates: LayoutCoordinates? = null
    private var miniArtworkRect: Rect? = null
    private var miniArtworkCorner: Dp = 8.dp

    var sheetTravelPx by mutableFloatStateOf(0f)
    var sheetOffsetProvider: (() -> Float)? by mutableStateOf(null)

    /** Explicit transition progress. 0 = mini player, 1 = full player. */
    var transitionProgress by mutableFloatStateOf(0f)
    var transitionActive by mutableStateOf(false)

    /** Optional controller used by the mini player for an upward pull gesture. */
    var pullController: PlayerPullController? by mutableStateOf(null)

    fun reportMiniArtwork(coordinates: LayoutCoordinates, corner: Dp) {
        miniArtworkCoordinates = coordinates
        miniArtworkRect = runCatching {
            val size = coordinates.size.toSize()
            Rect(
                coordinates.localToScreen(Offset.Zero),
                coordinates.localToScreen(Offset(size.width, size.height)),
            )
        }.getOrNull()
        miniArtworkCorner = corner
    }

    fun releaseMiniArtwork(coordinates: LayoutCoordinates) {
        if (miniArtworkCoordinates === coordinates) miniArtworkCoordinates = null
    }

    fun miniArtworkOnScreen(): Rect? {
        val coordinates = miniArtworkCoordinates
        if (coordinates?.isAttached == true) {
            val size = coordinates.size.toSize()
            miniArtworkRect = Rect(
                coordinates.localToScreen(Offset.Zero),
                coordinates.localToScreen(Offset(size.width, size.height)),
            )
        }
        return miniArtworkRect
    }

    fun miniArtworkCorner(): Dp = miniArtworkCorner

    fun sheetOpenFraction(): Float {
        if (transitionActive) return transitionProgress.coerceIn(0f, 1f)
        val travel = sheetTravelPx
        val offset = sheetOffsetProvider?.invoke() ?: return 0f
        if (travel <= 0f || offset.isNaN()) return 0f
        return (1f - offset / travel).coerceIn(0f, 1f)
    }

    val hideMiniArtwork: Boolean
        get() = transitionActive && transitionProgress > 0.001f
}

val LocalPlayerDock = staticCompositionLocalOf<PlayerDock?> { null }

/** Minimal bridge between the mini player gesture detector and the sheet host. */
interface PlayerPullController {
    fun drag(deltaY: Float)
    fun release(velocityY: Float)
}
