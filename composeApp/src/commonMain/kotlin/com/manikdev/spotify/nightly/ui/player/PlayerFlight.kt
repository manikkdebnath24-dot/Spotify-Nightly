package com.manikdev.spotify.nightly.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt

/**
 * The artwork in flight between the mini player and the full-screen player.
 *
 * [progress] is a lambda on purpose. It used to be a Float, read in composition, so every frame of
 * the slide recomposed this file and rebuilt its ImageRequest - the animation was paying for a
 * recomposition per frame, which is exactly the lag that got worse the moment the slide was added.
 * As a lambda it is read in the draw phase: the flight costs a draw, nothing more.
 */
@Composable
fun PlayerFlight(
    progress: () -> Float,
    artwork: Any?,
    modifier: Modifier = Modifier,
) {
    // BitChord's dock is the one source of both ends and of the fraction between them.
    val start = AppPlayerDock.miniArtOnScreen() ?: return
    var page by remember { mutableStateOf(IntSize.Zero) }
    if (page == IntSize.Zero) {
        Box(modifier.fillMaxSize().onSizeChanged { page = it })
        return
    }
    val end = playerCoverRect ?: estimatedArtRect(page)
    if (end.width <= 0f || end.height <= 0f) return

    val density = LocalDensity.current
    val widthDp = with(density) { end.width.toDp() }
    val heightDp = with(density) { end.height.toDp() }

    Box(
        modifier =
            modifier
                // Laid out once at the destination, then moved and scaled in the draw phase.
                .offset { IntOffset(end.left.roundToInt(), end.top.roundToInt()) }
                .size(width = widthDp, height = heightDp)
                // A fixed corner: an animated one would have to be read in composition, which is
                // the recomposition-per-frame this file exists to avoid.
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer {
                    val t = progress().coerceIn(0f, 1f)
                    // From the mini cover's own rectangle to the player's, in one transform.
                    val scaleXStart = start.width / end.width
                    val scaleYStart = start.height / end.height
                    scaleX = scaleXStart + (1f - scaleXStart) * t
                    scaleY = scaleYStart + (1f - scaleYStart) * t
                    translationX = (start.center.x - end.center.x) * (1f - t)
                    translationY = (start.center.y - end.center.y) * (1f - t)
                    // Gone before the sheet has finished, so an estimated landing cannot show.
                    alpha = if (t > 0.9f) ((1f - t) / 0.1f).coerceIn(0f, 1f) else 1f
                },
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(artwork)
                    .crossfade(0)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A centred square in the upper third, where every one of the player styles draws its cover. */
private fun estimatedArtRect(page: IntSize): Rect {
    val side = (page.width - 32f).coerceAtLeast(1f)
    val top = page.height * 0.12f
    val left = (page.width - side) / 2f
    return Rect(left, top, left + side, top + side)
}
