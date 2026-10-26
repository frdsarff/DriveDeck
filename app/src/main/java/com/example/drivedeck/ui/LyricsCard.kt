package com.example.drivedeck.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivedeck.bridge.MediaBridge
import com.example.drivedeck.lyrics.LyricLine
import com.example.drivedeck.lyrics.LyricsRepository
import kotlinx.coroutines.delay

private val LyricsBg = Color(0xFF1B1E25)
private val LyricsMuted = Color(0xFF6F7580)
private val LyricsWhite = Color(0xFFF7F7F7)

@Composable
fun LyricsCard(modifier: Modifier = Modifier) {
    val np by MediaBridge.state.collectAsState()

    // null = tengah cari, empty = tak jumpa
    var lines by remember { mutableStateOf<List<LyricLine>?>(null) }
    var nowMs by remember { mutableStateOf(0L) }

    LaunchedEffect(np.title, np.artist) {
        if (np.title.isBlank()) {
            lines = emptyList()
            return@LaunchedEffect
        }
        lines = null
        lines = LyricsRepository.load(np.title, np.artist, np.durationMs)
    }

    // Kira posisi lagu sebenar, update 4 kali sesaat
    LaunchedEffect(np.positionMs, np.isPlaying, np.title) {
        val anchor = SystemClock.elapsedRealtime()
        val base = np.positionMs
        nowMs = base
        while (np.isPlaying) {
            delay(250)
            nowMs = base + (SystemClock.elapsedRealtime() - anchor)
        }
    }

    if (np.title.isBlank()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = LyricsBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("LYRICS", color = LyricsMuted, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(14.dp))

            val list = lines
            when {
                list == null -> Text("Mencari lirik...", color = LyricsMuted, fontSize = 15.sp)

                list.isEmpty() -> Text(
                    "Lirik tak dijumpai untuk lagu ni.",
                    color = LyricsMuted,
                    fontSize = 15.sp
                )

                else -> {
                    val idx = list.indexOfLast { it.timeMs <= nowMs + 300 }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        for (i in (idx - 1)..(idx + 2)) {
                            val line = list.getOrNull(i)
                            val current = i == idx
                            Text(
                                text = when {
                                    line == null -> ""
                                    line.text.isBlank() -> "♪"
                                    else -> line.text
                                },
                                color = if (current) LyricsWhite else LyricsMuted,
                                fontSize = if (current) 22.sp else 15.sp,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}
