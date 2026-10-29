package com.example.drivedeck

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.drivedeck.lyrics.MediaNotificationListener

class MainActivity : Activity() {
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var sourceView: TextView
    private lateinit var lyricsView: TextView
    private lateinit var permissionButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { renderSong(); handler.postDelayed(this, 1000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 16, 23)
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 28, 22, 20)
            setBackgroundColor(Color.rgb(13, 16, 23))
        }
        root.addView(TextView(this).apply {
            text = "DRIVEDECK 2.0.2"; textSize = 13f; letterSpacing = 0.15f
            setTextColor(Color.rgb(90, 230, 190))
        })
        titleView = TextView(this).apply {
            text = "Waiting for music…"; textSize = 26f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(0, 18, 0, 4)
        }
        root.addView(titleView)
        artistView = TextView(this).apply {
            textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY)
        }
        root.addView(artistView)
        sourceView = TextView(this).apply {
            text = "Spotify / YouTube Music"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 230, 190)); setPadding(0, 10, 0, 16)
        }
        root.addView(sourceView)
        permissionButton = Button(this).apply {
            text = "Enable notification access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        root.addView(permissionButton)
        root.addView(TextView(this).apply {
            text = "LYRICS"; textSize = 12f; letterSpacing = 0.18f
            setTextColor(Color.rgb(90, 230, 190)); setPadding(0, 22, 0, 8)
        })
        lyricsView = TextView(this).apply {
            text = "Play a song in Spotify or YouTube Music. DriveDeck will try to find its lyrics."
            textSize = 19f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 24)
        }
        val scroll = ScrollView(this).apply { addView(lyricsView) }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun renderSong() {
        val prefs = getSharedPreferences(MediaNotificationListener.PREFS, MODE_PRIVATE)
        val title = prefs.getString("title", "").orEmpty()
        val artist = prefs.getString("artist", "").orEmpty()
        val lyrics = prefs.getString("syncedLyrics", "").orEmpty()
        val plain = prefs.getString("lyrics", "").orEmpty()
        val pkg = prefs.getString("package", "").orEmpty()
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            .orEmpty().contains(packageName)
        permissionButton.visibility = if (enabled) View.GONE else View.VISIBLE
        if (title.isBlank()) {
            titleView.text = "Waiting for music…"
            artistView.text = "Play a song in Spotify or YouTube Music"
            sourceView.text = if (enabled) "Music notification access enabled" else "Enable access to detect the current song"
            lyricsView.text = "DriveDeck will search for matching lyrics after a supported music app starts playing."
            return
        }
        titleView.text = title
        artistView.text = artist
        sourceView.text = if (pkg == "com.spotify.music") "SPOTIFY" else "YOUTUBE MUSIC"
        lyricsView.text = activeLyrics(lyrics, plain, prefs)
    }

    private fun activeLyrics(lrc: String, plain: String, prefs: android.content.SharedPreferences): String {
        if (lrc.isBlank()) return if (plain.isNotBlank()) plain else
            "Searching for synced lyrics…\n\nThis song may not be available in the lyric catalogue."
        val entries = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]([^\\r\\n]*)")
            .findAll(lrc).mapNotNull { match ->
                val min = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val sec = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                (min * 60_000L + sec * 1000L + fraction) to match.groupValues[4].trim()
            }.toList()
        if (entries.isEmpty()) return lrc
        val base = prefs.getLong("positionMs", 0L)
        val updatedAt = prefs.getLong("updatedAt", android.os.SystemClock.elapsedRealtime())
        val speed = prefs.getFloat("playbackSpeed", 0f)
        val position = base + ((android.os.SystemClock.elapsedRealtime() - updatedAt).coerceAtLeast(0L) * speed).toLong()
        val active = entries.indexOfLast { it.first <= position }.coerceAtLeast(0)
        return entries.mapIndexed { index, entry ->
            when {
                index == active -> "▶  ${entry.second}"
                index == active + 1 -> "\n${entry.second}"
                index == active + 2 -> "\n${entry.second}"
                else -> ""
            }
        }.filter { it.isNotBlank() }.joinToString("\n")
    }
}
