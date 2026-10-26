
package com.example.drivedeck.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivedeck.bridge.MediaBridge
import com.example.drivedeck.lyrics.LyricLine
import com.example.drivedeck.lyrics.LyricsRepository

/** Laraskan nilai ini jika lirik terlalu awal atau terlalu lambat. */
private const val SYNC_LEAD_MS = 250L

private val LyricsBg = Color(0xFF1B1E25)
private val Active = Color(0xFFF7F7F7)
private val Upcoming = Color(0xFF7C8491)
private val Past = Color(0xFF454B55)
private val HeaderMuted = Color(0xFF6F7580)

@Composable
fun LyricsCard(modifier: Modifier = Modifier) {
    val np by MediaBridge.state.collectAsState()

    var lines by remember { mutableStateOf<List<LyricLine>?>(null) }
    val nowMs = remember { mutableStateOf(0L) }
    val listState = rememberLazyListState()

    // 1. Cari lirik apabila lagu bertukar.
    LaunchedEffect(np.title, np.artist) {
        if (np.title.isBlank()) {
            lines = emptyList()
            return@LaunchedEffect
        }

        lines = null
        lines = LyricsRepository.load(np.title, np.artist, np.durationMs)
    }

    // 2. Kemas kini posisi lagu untuk penyegerakan lirik.
    LaunchedEffect(np) {
        if (np.isPlaying) {
            while (true) {
                nowMs.value = np.positionMs +
                    (SystemClock.elapsedRealtime() - np.updatedAt) +
                    SYNC_LEAD_MS

                withFrameNanos { }
            }
        } else {
            nowMs.value = np.positionMs + SYNC_LEAD_MS
        }
    }

    val list = lines

    val currentIndex by remember(list) {
        derivedStateOf {
            list?.indexOfLast { it.timeMs <= nowMs.value } ?: -1
        }
    }

    // 3. Tatal automatik supaya baris semasa mudah dilihat.
    LaunchedEffect(currentIndex, list) {
        if (list.isNullOrEmpty()) return@LaunchedEffect

        val offset = -(listState.layoutInfo.viewportSize.height / 3)
        listState.animateScrollToItem(
            currentIndex.coerceAtLeast(0),
            offset
        )
    }

    if (np.title.isBlank()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = LyricsBg)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 24.dp,
                vertical = 20.dp
            )
        ) {
            Text(
                text = "LYRICS",
                color = HeaderMuted,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(12.dp))

            when {
                list == null -> {
                    Text(
                        text = "Mencari lirik...",
                        color = HeaderMuted,
                        fontSize = 15.sp
                    )
                }

                list.isEmpty() -> {
                    Text(
                        text = "Lirik tak dijumpai untuk lagu ni.",
                        color = HeaderMuted,
                        fontSize = 15.sp
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        itemsIndexed(list) { i, line ->
                            KaraokeLine(
                                index = i,
                                line = line,
                                nextTimeMs = list.getOrNull(i + 1)?.timeMs,
                                currentIndex = currentIndex,
                                nowMs = nowMs
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KaraokeLine(
    index: Int,
    line: LyricLine,
    nextTimeMs: Long?,
    currentIndex: Int,
    nowMs: State<Long>
) {
    val text = line.text.ifBlank { "♪" }

    val rowModifier = Modifier
        .fillMaxWidth()
        .clickable(
            interactionSource = remember {
                MutableInteractionSource()
            },
            indication = null
        ) {
            MediaBridge.seekTo(line.timeMs)
        }
        .padding(vertical = 8.dp)

    if (index == currentIndex) {
        // Isi teks bergerak dari kiri ke kanan mengikut masa lirik.
        val duration = (
            (nextTimeMs ?: (line.timeMs + 4000)) - line.timeMs
        ).coerceIn(600L, 8000L)

        val progress = (
            (nowMs.value - line.timeMs).toFloat() /
                (duration * 0.9f)
        ).coerceIn(0f, 1f)

        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Active,
                progress to Active,
                (progress + 0.03f).coerceAtMost(1f) to Upcoming,
                1f to Upcoming
            )
        )

        Text(
            text = text,
            modifier = rowModifier,
            style = TextStyle(
                brush = brush,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        )
    } else {
        Text(
            text = text,
            modifier = rowModifier,
            color = if (index < currentIndex) Past else Upcoming,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
