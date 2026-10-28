
package com.example.drivedeck.car

import android.content.Intent
import android.os.SystemClock
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

class LyricsCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Untuk ujian sideload/developer sahaja.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session = LyricsSession()
}


private class LyricsSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return LyricsScreen(carContext)
    }
}



private class LyricsScreen(
    carContext: androidx.car.app.CarContext
) : Screen(carContext) {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var trackKey: String? = null
    private var lyrics: List<LyricLine> = emptyList()
    private var loading = false
    private var lastRenderedIndex = Int.MIN_VALUE

    init {
        MediaBridge.start(carContext)

        // Pantau lagu semasa dan dapatkan lirik apabila lagu berubah.
        scope.launch {
            MediaBridge.state.collect { np ->
                val newKey =
                    "${np.packageName}|${np.title}|${np.artist}"

                if (newKey != trackKey) {
                    trackKey = newKey
                    lyrics = emptyList()
                    loading = np.title.isNotBlank()
                    lastRenderedIndex = Int.MIN_VALUE
                    invalidate()

                    if (np.title.isNotBlank()) {
                        launch {
                            val result = LyricsRepository.load(
                                np.title,
                                np.artist,
                                np.durationMs
                            )

                            if (trackKey == newKey) {
                                lyrics = result
                                loading = false
                                lastRenderedIndex = Int.MIN_VALUE
                                invalidate()
                            }
                        }
                    } else {
                        loading = false
                    }
                }
            }
        }

        // Kemas kini paparan apabila baris lirik berubah.
        scope.launch {
            while (isActive) {
                delay(250L)

                val index = currentLyricIndex()
                if (index != lastRenderedIndex) {
                    lastRenderedIndex = index
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val np = MediaBridge.state.value
        val pane = Pane.Builder()

        // Keep the car-safe PaneTemplate, but make the limited space work harder:
        // one compact track-info row plus up to three synchronized lyric rows.
        if (np.title.isNotBlank()) {
            val trackInfo = buildString {
                append(np.title)
                if (np.artist.isNotBlank()) append("  •  ").append(np.artist)
                if (np.appName.isNotBlank()) append("  ·  ").append(np.appName)
            }
            pane.addRow(Row.Builder().setTitle(trackInfo).build())
        }

        when {
            !np.hasAccess -> {
                pane.addRow(
                    Row.Builder()
                        .setTitle("Benarkan akses notifikasi untuk mengesan muzik")
                        .build()
                )
            }

            np.title.isBlank() -> {
                pane.addRow(
                    Row.Builder()
                        .setTitle("Mainkan lagu di Spotify atau YouTube Music")
                        .build()
                )
                pane.addRow(
                    Row.Builder()
                        .setTitle("DriveDeck akan memaparkan lirik di sini")
                        .build()
                )
            }

            loading -> {
                pane.addRow(
                    Row.Builder()
                        .setTitle("Sedang mencari lirik terselaras…")
                        .build()
                )
            }

            lyrics.isEmpty() -> {
                pane.addRow(
                    Row.Builder()
                        .setTitle("Lirik terselaras tidak ditemui")
                        .build()
                )
            }

            else -> {
                val center = currentLyricIndex().coerceIn(0, lyrics.lastIndex)
                val start = (center - 1).coerceAtLeast(0)
                val end = (center + 1).coerceAtMost(lyrics.lastIndex)

                for (i in start..end) {
                    val line = lyrics[i].text.ifBlank { "♪" }
                    val display = if (i == center) "▶  $line" else line
                    pane.addRow(Row.Builder().setTitle(display).build())
                }
            }
        }

        val controls = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Previous")
                    .setOnClickListener { MediaBridge.previous() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (np.isPlaying) "Pause" else "Play")
                    .setOnClickListener { MediaBridge.togglePlayPause() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Next")
                    .setOnClickListener { MediaBridge.next() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane.build())
            .setTitle("DriveDeck Lyrics")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(controls)
            .build()
    }

    private fun currentLyricIndex(): Int {
        if (lyrics.isEmpty()) return -1

        val np = MediaBridge.state.value

        // MediaBridge.positionMs is a snapshot; advance it using the active
        // player's playback speed so lyrics stay aligned at 1.25x/1.5x/etc.
        val elapsed = if (np.isPlaying) {
            (SystemClock.elapsedRealtime() - np.updatedAt)
                .coerceAtLeast(0L)
        } else {
            0L
        }
        val speed = np.playbackSpeed.takeIf { it > 0f } ?: 1f
        val position = np.positionMs + (elapsed * speed).toLong()
        val safePosition = if (np.durationMs > 0) {
            position.coerceAtMost(np.durationMs)
        } else {
            position
        }

        return lyrics.indexOfLast {
            it.timeMs <= safePosition + 300L
        }.coerceAtLeast(0)
    }

    fun dispose() {
        // Stop lyric polling and media observers when this screen is explicitly disposed.
        scope.cancel()
    }
}
