package com.example.drivedeck.media

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Android Auto browse surface for the current external music session.
 * Spotify / YouTube Music remain the audio players; DriveDeck exposes metadata
 * and any lyrics found by MediaNotificationListener.
 */
class DriveDeckMediaBrowserService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "DriveDeckMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = setExternalPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                override fun onPause() = setExternalPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                    .build()
            )
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
    }

    private fun setExternalPlaybackState(state: Int) {
        // This session is a companion view. It cannot directly control another app's
        // playback without a separate, explicit MediaController integration.
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setState(state, 0L, 0f)
                .build()
        )
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val title = prefs.getString("title", "").orEmpty().ifBlank { "No song detected yet" }
        val artist = prefs.getString("artist", "").orEmpty()
        val packageName = prefs.getString("package", "").orEmpty()
        val source = when (packageName) {
            "com.spotify.music" -> "Spotify"
            "com.google.android.apps.youtube.music" -> "YouTube Music"
            else -> "Open Spotify or YouTube Music on your phone"
        }
        val synced = prefs.getString("syncedLyrics", "").orEmpty()
        val plain = prefs.getString("lyrics", "").orEmpty()
        val lyricsLines = parseLyrics(synced).ifEmpty {
            plain.lines().map { it.trim() }.filter { it.isNotBlank() }
        }

        val items = when (parentId) {
            ROOT_ID -> mutableListOf(
                item("now_playing", "Now Playing: $title", if (artist.isBlank()) source else "$artist • $source", true),
                item("lyrics", "Lyrics", if (lyricsLines.isEmpty()) "No lyrics found yet" else "${lyricsLines.size} lines available", true)
            )
            "now_playing" -> mutableListOf(
                item("current_song", title, if (artist.isBlank()) source else "$artist • $source", false)
            )
            "lyrics" -> if (lyricsLines.isEmpty()) {
                mutableListOf(item("no_lyrics", "Lyrics not available", "Try another song or check internet access", false))
            } else {
                lyricsLines.take(MAX_LYRICS_LINES).mapIndexed { index, line ->
                    item("lyric_$index", line, if (index == 0) "Lyrics • $title" else "", false)
                }.toMutableList()
            }
            else -> mutableListOf()
        }
        result.sendResult(items)
        updateSessionMetadata(title, artist, source)
    }

    private fun updateSessionMetadata(title: String, artist: String, source: String) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, source)
                .build()
        )
    }

    private fun item(id: String, title: String, subtitle: String, browsable: Boolean) =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title.take(MAX_TITLE_CHARS))
                .setSubtitle(subtitle.take(MAX_SUBTITLE_CHARS))
                .build(),
            if (browsable) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )

    private fun parseLyrics(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val timestamp = Regex("\\[\\d{2}:\\d{2}(?:\\.\\d{1,3})?\\]")
        return raw.lines().map { timestamp.replace(it, "").trim() }.filter { it.isNotBlank() }
    }

    override fun onDestroy() {
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val ROOT_ID = "drivedeck_root"
        private const val PREFS = "drivedeck_lyrics"
        private const val MAX_LYRICS_LINES = 80
        private const val MAX_TITLE_CHARS = 100
        private const val MAX_SUBTITLE_CHARS = 120
    }
}
