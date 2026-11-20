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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt

/**
 * The artwork in flight between the mini player and the full-screen player.
 *
 * This is the half of BitChord's dock that a translation cannot do: the content can slide, but only
 * a cover drawn at the interpolated rectangle appears to *grow out of* the mini player and land on
 * the player's own cover. [progress] is the same value the sheet moves on - 0 at the mini player,
 * 1 at the player - so the flight cannot drift out of step with the sheet.
 *
 * The destination is the player's cover when a style reports it ([PlayerDock.playerArtBounds]);
 * otherwise it is estimated as a centred square in the upper part of the page. The flight fades out
 * over the last tenth, so even an estimated landing cannot show an edge: by the time it is
 * transparent, the real artwork is what you are looking at.
 */
@Composable
fun PlayerFlight(
    progress: Float,
    thumbnails: List<Any>?,
    modifier: Modifier = Modifier,
) {
    val start = PlayerDock.coverBounds ?: return
    var page by remember { mutableStateOf(IntSize.Zero) }
    if (page == IntSize.Zero) {
        Box(modifier.fillMaxSize().onSizeChanged { page = it })
        return
    }
    val end = PlayerDock.playerArtBounds ?: estimatedArtRect(page)
    val t = progress.coerceIn(0f, 1f)
    if (t >= 0.999f) return

    val rect =
        Rect(
            left = lerp(start.left, end.left, t),
            top = lerp(start.top, end.top, t),
            right = lerp(start.right, end.right, t),
            bottom = lerp(start.bottom, end.bottom, t),
        )
    val density = LocalDensity.current
    val widthDp = with(density) { (rect.right - rect.left).toDp() }
    val heightDp = with(density) { (rect.bottom - rect.top).toDp() }
    val corner = lerp(6f, 16f, t).dp

    Box(
        modifier =
            modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(width = widthDp, height = heightDp)
                .clip(RoundedCornerShape(corner))
                // Gone before the sheet has finished, so its landing never has to be exact.
                .alpha(((1f - t) / 0.1f).coerceIn(0f, 1f)),
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(thumbnails)
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
