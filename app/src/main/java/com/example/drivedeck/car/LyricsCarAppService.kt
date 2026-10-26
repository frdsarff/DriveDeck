package com.example.drivedeck.car

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import com.example.drivedeck.bridge.MediaBridge
import com.example.drivedeck.lyrics.LyricLine
import com.example.drivedeck.lyrics.LyricsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "DriveDeckCarLyrics"

/**
 * Dedicated Android Auto template app for synchronized lyrics.
 * Kept separate from PlaybackService: the media browser handles playback,
 * while this CarAppService owns the lyrics UI.
 */
class LyricsCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session {
        Log.i(TAG, "Lyrics CarAppService session requested")
        return LyricsSession()
    }
}

private class LyricsSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        Log.i(TAG, "Creating DriveDeck Lyrics screen")
        return LyricsScreen(carContext)
    }
}

private class LyricsScreen(
    carContext: androidx.car.app.CarContext
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var trackKey: String? = null
    private var lyrics: List<LyricLine> = emptyList()
    private var loading = false
    private var errorMessage: String? = null
    private var lastRenderedIndex = Int.MIN_VALUE

    init {
        Log.i(TAG, "LyricsScreen initialized")
        MediaBridge.start(carContext)

        scope.launch {
            MediaBridge.state.collect { nowPlaying ->
                val key = "${nowPlaying.packageName}|${nowPlaying.title}|${nowPlaying.artist}"
                if (key == trackKey) return@collect

                trackKey = key
                lyrics = emptyList()
                errorMessage = null
                loading = nowPlaying.title.isNotBlank()
                lastRenderedIndex = Int.MIN_VALUE
                Log.i(TAG, "Track changed: title='${nowPlaying.title}', artist='${nowPlaying.artist}', access=${nowPlaying.hasAccess}")
                invalidate()

                if (nowPlaying.title.isBlank()) {
                    loading = false
                    errorMessage = if (nowPlaying.hasAccess) {
                        "Mainkan lagu dalam Spotify atau YouTube Music"
                    } else {
                        "Benarkan akses notifikasi DriveDeck pada telefon"
                    }
                    invalidate()
                    return@collect
                }

                launch {
                    val found = LyricsRepository.load(
                        nowPlaying.title,
                        nowPlaying.artist,
                        nowPlaying.durationMs
                    )
                    if (trackKey == key) {
                        lyrics = found
                        loading = false
                        errorMessage = if (found.isEmpty()) {
                            "Lirik terselaras tidak ditemui untuk lagu ini"
                        } else null
                        Log.i(TAG, "Lyrics result: ${found.size} lines")
                        lastRenderedIndex = Int.MIN_VALUE
                        invalidate()
                    }
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(500L)
                val index = currentLyricIndex()
                if (index != lastRenderedIndex) {
                    lastRenderedIndex = index
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val nowPlaying = MediaBridge.state.value
        val pane = Pane.Builder()

        pane.addRow(Row.Builder().setTitle(nowPlaying.title.ifBlank { "Tiada lagu dikesan" }).build())
        if (nowPlaying.artist.isNotBlank()) {
            pane.addRow(Row.Builder().setTitle(nowPlaying.artist).build())
        }

        when {
            loading -> pane.addRow(Row.Builder().setTitle("Sedang mencari lirik terselaras…").build())
            lyrics.isEmpty() -> pane.addRow(
                Row.Builder().setTitle(errorMessage ?: "Lirik belum tersedia").build()
            )
            else -> {
                val center = currentLyricIndex().coerceIn(0, lyrics.lastIndex)
                val start = (center - 1).coerceAtLeast(0)
                val end = (center + 2).coerceAtMost(lyrics.lastIndex)
                for (index in start..end) {
                    val line = lyrics[index].text.ifBlank { "♪" }
                    pane.addRow(
                        Row.Builder()
                            .setTitle(if (index == center) "▶  $line" else line)
                            .build()
                    )
                }
            }
        }

        val actions = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Previous").setOnClickListener { MediaBridge.previous() }.build())
            .addAction(
                Action.Builder()
                    .setTitle(if (nowPlaying.isPlaying) "Pause" else "Play")
                    .setOnClickListener { MediaBridge.togglePlayPause() }
                    .build()
            )
            .addAction(Action.Builder().setTitle("Next").setOnClickListener { MediaBridge.next() }.build())
            .build()

        return PaneTemplate.Builder(pane.build())
            .setTitle("DriveDeck Lyrics")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actions)
            .build()
    }

    private fun currentLyricIndex(): Int {
        if (lyrics.isEmpty()) return -1
        val nowPlaying = MediaBridge.state.value
        val elapsed = if (nowPlaying.isPlaying) {
            (SystemClock.elapsedRealtime() - nowPlaying.updatedAt).coerceAtLeast(0L)
        } else 0L
        val position = nowPlaying.positionMs + elapsed
        val safePosition = if (nowPlaying.durationMs > 0) {
            position.coerceAtMost(nowPlaying.durationMs)
        } else position
        return lyrics.indexOfLast { it.timeMs <= safePosition + 300L }.coerceAtLeast(0)
    }

    override fun onDestroy() {
        Log.i(TAG, "LyricsScreen destroyed")
        scope.cancel()
        super.onDestroy()
    }
}
