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
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != "com.spotify.music" &&
            pkg != "com.google.android.apps.youtube.music") return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val artist = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim().orEmpty()
        if (title.isBlank() || title == "Spotify" || title == "YouTube Music") return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getString("title", "") == title && prefs.getString("artist", "") == artist) {
            updatePlaybackPosition(sbn, prefs)
            return
        }

        prefs.edit()
            .putString("title", title)
            .putString("artist", artist)
            .putString("package", pkg)
            .putString("lyrics", "")
            .putString("syncedLyrics", "")
            .putLong("positionMs", 0L)
            .putLong("updatedAt", SystemClock.elapsedRealtime())
            .apply()
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
        if (sbn.packageName == "com.spotify.music" ||
            sbn.packageName == "com.google.android.apps.youtube.music") {
            // Keep the last song visible if the music app briefly refreshes its notification.
        }
    }

    private fun updatePlaybackPosition(sbn: StatusBarNotification, prefs: android.content.SharedPreferences) {
        try {
            val token = sbn.notification.extras?.getParcelable<android.media.session.MediaSession.Token>(
                Notification.EXTRA_MEDIA_SESSION
            ) ?: return
            val controller = MediaController(this, token)
            val state = controller.playbackState ?: return
            prefs.edit()
                .putLong("positionMs", state.position.coerceAtLeast(0L))
                .putFloat("playbackSpeed", if (state.state == PlaybackState.STATE_PLAYING) state.playbackSpeed else 0f)
                .putLong("updatedAt", SystemClock.elapsedRealtime())
                .apply()
        } catch (_: Exception) {
            // Some music app notifications do not expose a media-session token.
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
    }
}
