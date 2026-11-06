package com.example.drivedeck.lyrics

import android.app.Notification
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.SystemClock
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MediaNotificationListener : NotificationListenerService() {
    private var registeredController: MediaController? = null
    private val playbackCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state?.let { persistPlaybackState(it) }
        }
    }

    private fun attachPlaybackUpdates(controller: MediaController) {
        if (registeredController?.sessionToken == controller.sessionToken) return
        try { registeredController?.unregisterCallback(playbackCallback) } catch (_: Exception) {}
        registeredController = controller
        try {
            controller.registerCallback(playbackCallback)
            controller.playbackState?.let { persistPlaybackState(it) }
        } catch (_: Exception) {}
    }

    private fun persistPlaybackState(state: PlaybackState) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putLong("positionMs", state.position.coerceAtLeast(0L))
            .putFloat("playbackSpeed", if (state.state == PlaybackState.STATE_PLAYING) state.playbackSpeed else 0f)
            .putLong("updatedAt", SystemClock.elapsedRealtime())
            .apply()
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("listenerConnected", true)
            .apply()
    }

    override fun onListenerDisconnected() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("listenerConnected", false)
            .apply()
        activeController = null
        try { registeredController?.unregisterCallback(playbackCallback) } catch (_: Exception) {}
        registeredController = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val token = try {
            extras.getParcelable<android.media.session.MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        } catch (_: Exception) { null }

        val pkg = sbn.packageName
        val knownPlayer = pkg == "com.spotify.music" ||
            pkg == "com.google.android.apps.youtube.music" ||
            pkg == "com.google.android.youtube"
        val preferredSource = getSharedPreferences("drivedeck_settings", MODE_PRIVATE)
            .getString("source", "auto") ?: "auto"
        val matchesPreferredSource = when (preferredSource) {
            "spotify" -> pkg == "com.spotify.music"
            "ytmusic" -> pkg == "com.google.android.apps.youtube.music" ||
                pkg == "com.google.android.youtube"
            else -> true
        }
        if (!matchesPreferredSource) return
        // Accept any app publishing a media-session notification in Auto mode,
        // while retaining Spotify / YouTube Music notification fallback when no token exists.
        if (preferredSource == "auto" && !knownPlayer && token == null) return
        if (preferredSource != "auto" && !knownPlayer) return

        val controller = try { token?.let { MediaController(this, it) } } catch (_: Exception) { null }
        if (controller != null) { activeController = controller; attachPlaybackUpdates(controller) }
        val metadata = controller?.metadata
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() && it != "Spotify" && it != "YouTube Music" }
            ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE))
            ?.trim().orEmpty()
        val artist = (metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            ?.trim().orEmpty()
        if (title.isBlank()) return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val sameTrack = prefs.getString("title", "") == title &&
            prefs.getString("artist", "") == artist &&
            prefs.getString("package", "") == pkg
        prefs.edit()
            .putBoolean("listenerConnected", true)
            .putString("lastDetectedPackage", pkg)
            .putString("title", title)
            .putString("artist", artist)
            .putString("package", pkg)
            .putLong("updatedAt", SystemClock.elapsedRealtime())
            .apply()

        if (sameTrack && (prefs.getString("lyrics", "").orEmpty().isNotBlank() ||
            prefs.getString("syncedLyrics", "").orEmpty().isNotBlank())) {
            updatePlaybackPosition(sbn, prefs)
            return
        }

        if (!sameTrack) {
            prefs.edit()
                .putString("lyrics", "")
                .putString("syncedLyrics", "")
                .putLong("positionMs", 0L)
                .apply()
        }
        updatePlaybackPosition(sbn, prefs)
        thread(name = "DriveDeckLyricsLookup") {
            lookupLyrics(title, artist)?.let { result ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("lyrics", result.optString("plainLyrics", ""))
                    .putString("syncedLyrics", result.optString("syncedLyrics", ""))
                    .apply()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep the last song visible while media apps refresh their notification.
    }

    private fun updatePlaybackPosition(sbn: StatusBarNotification, prefs: android.content.SharedPreferences) {
        try {
            val token = sbn.notification.extras?.getParcelable<android.media.session.MediaSession.Token>(
                Notification.EXTRA_MEDIA_SESSION
            ) ?: return
            val controller = MediaController(this, token)
            activeController = controller
            attachPlaybackUpdates(controller)
            val state = controller.playbackState ?: return
            persistPlaybackState(state)
        } catch (_: Exception) {
            // Some players do not expose a media-session token in notification extras.
        }
    }

    private fun lookupLyrics(title: String, artist: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val query = "track_name=" + enc(title) + "&artist_name=" + enc(artist)
            connection = (URL("https://lrclib.net/api/get?$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "DriveDeck/2.0.2 (Android)")
            }
            if (connection.responseCode != 200) null
            else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val PREFS = "drivedeck_lyrics"

        @Volatile
        private var activeController: MediaController? = null

        /** Keep dashboard transport controls working without exposing a MediaBrowserService. */
        fun controlPlayback(command: String): Boolean {
            val controller = activeController ?: return false
            val controls = controller.transportControls
            return try {
                when (command) {
                    "play" -> controls.play()
                    "pause" -> controls.pause()
                    "playPause" -> {
                        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause()
                        else controls.play()
                    }
                    "next" -> controls.skipToNext()
                    "previous" -> controls.skipToPrevious()
                    else -> return false
                }
                true
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }
    }
}
