package com.manikdev.spotify.nightly.ui.player

import androidx.compose.ui.geometry.Rect

/**
 * Where the mini player's cover is on screen, for the player's open and close.
 *
 * BitChord does this with a PlayerDock that the mini player reports into and the player reads:
 * the sheet is never asked to slide on its own curve, the app places the content itself, so a tap
 * from the mini player and a drag on the sheet move the player the same way.
 *
 * This is the part of that idea this app can carry without rewriting how the sheet opens: the mini
 * player reports the cover's bounds, and the player starts its travel from there instead of from a
 * fixed 120dp lift. The destination is an estimate - each player style puts its own artwork at its
 * own height - so the travel is measured from the real cover to a fixed fraction of the page.
 */
object PlayerDock {
    /** The mini player's cover in window coordinates, or null when it is not on screen. */
    var coverBounds: Rect? = null
        private set

    fun reportCover(bounds: Rect) {
        coverBounds = bounds
    }

    fun clearCover() {
        coverBounds = null
    }

    /** Where the player's own cover rests, reported by whichever style draws it. */
    var playerArtBounds: Rect? = null
        private set

    fun reportPlayerArt(bounds: Rect) {
        playerArtBounds = bounds
    }

    /**
     * How far below its resting place the player starts, in pixels, for a page [pageHeight] tall.
     *
     * Falls back to a modest lift when there is no cover to fly from - a cold open straight into
     * the player, or a desktop window where the mini player is not drawn.
     */
    fun startTravelPx(pageHeight: Float): Float {
        if (pageHeight <= 0f) return 0f
        val cover = coverBounds ?: return pageHeight * 0.15f
        // The player's artwork rests in the upper third; the cover sits near the bottom.
        val destination = pageHeight * 0.30f
        return (cover.center.y - destination).coerceIn(0f, pageHeight)
    }
}
