package com.example.drivedeck.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Minimal Android Auto media-browser integration.
 *
 * This first milestone exposes a browsable library and session controls.
 * Actual audio playback/catalog integration will be added in the next milestone.
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
                override fun onPlay() {
                    val state = PlaybackStateCompat.Builder()
                        .setActions(
                            PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                        )
                        .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f)
                        .build()
                    setPlaybackState(state)
                }

                override fun onPause() {
                    val state = PlaybackStateCompat.Builder()
                        .setActions(
                            PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                        )
                        .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f)
                        .build()
                    setPlaybackState(state)
                }
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

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val items = mutableListOf(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("library")
                    .setTitle("DriveDeck Library")
                    .setSubtitle("Audio library integration is the next milestone")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        result.sendResult(items)
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
    }
}
