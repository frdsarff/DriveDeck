package com.example.drivedeck.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivedeck.bridge.MediaBridge

private val CardBg = Color(0xFF1B1E25)
private val Muted = Color(0xFF9AA0AA)

@Composable
fun LiveNowPlayingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val np by MediaBridge.state.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("NOW PLAYING", color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))

            when {
                !np.hasAccess -> {
                    Text("Perlu akses media", color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Bagi akses sekali supaya DriveDeck boleh baca dan kawal Spotify / YouTube Music.",
                        color = Muted, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { MediaBridge.openAccessSettings(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5F5F5),
                            contentColor = Color.Black
                        )
                    ) { Text("Bagi akses") }
                }

                np.title.isBlank() -> {
                    Text("Tiada media dimainkan", color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Buka Spotify atau YouTube Music dan mula main lagu.",
                        color = Muted, fontSize = 13.sp)
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        np.artwork?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(np.title, color = Color.White, fontSize = 20.sp,
                                fontWeight = FontWeight.Bold, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            if (np.artist.isNotBlank()) {
                                Text(np.artist, color = Muted, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("via ${np.appName}", color = Muted, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { MediaBridge.previous() }) {
                            Text("Prev", color = Color.White)
                        }
                        Button(
                            onClick = { MediaBridge.togglePlayPause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF5F5F5),
                                contentColor = Color.Black
                            )
                        ) { Text(if (np.isPlaying) "Pause" else "Play") }
                        OutlinedButton(onClick = { MediaBridge.next() }) {
                            Text("Next", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
