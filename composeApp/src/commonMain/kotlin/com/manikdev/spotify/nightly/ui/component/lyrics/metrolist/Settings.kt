package com.manikdev.spotify.nightly.ui.component.lyrics.metrolist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The handful of preferences Metrolist's renderer reads, answered by this app.
 *
 * Their `Settings.shared` is a Compose-state holder wired to their preference store. This app has
 * its own store, so rather than dragging theirs in, the fields their renderer actually touches are
 * declared here with this app's defaults. Only the nine they read are present - the renderer is the
 * only thing that touches this object.
 */
object Settings {
    val shared: Settings get() = this

    var lyricsTextSize by mutableStateOf(1f)
    var lyricsLineSpacing by mutableStateOf(0)
    var lyricsTextPosition by mutableStateOf(0)
    var lyricsAnimationStyle by mutableStateOf(0)
    var lyricsShowTranslations by mutableStateOf(true)
    var lyricsShowOriginalLyrics by mutableStateOf(true)
    var isLyricsRomanizationActive by mutableStateOf(false)
    var lyricsRomanizeAsMain by mutableStateOf(false)
    var lyricsSyllableRomanizationEnabled by mutableStateOf(false)
}
