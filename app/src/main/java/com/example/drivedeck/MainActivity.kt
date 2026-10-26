
package com.example.drivedeck

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

private val Ink = Color(0xFF080A0E)
private val Panel = Color(0xFF171A20)
private val CardColor = Color(0xFF22262E)
private val White = Color(0xFFF7F7F7)
private val Muted = Color(0xFFA4AAB5)
private val Accent = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink,
                    surface = Panel,
                    primary = Accent,
                    onBackground = White,
                    onSurface = White
                )
            ) {
                DriveDeckScreen(
                    openApp = { url, packageName ->
                        openAppOrWeb(url, packageName)
                    }
                )
            }
        }
    }

    private fun openAppOrWeb(
        url: String,
        packageName: String? = null
    ) {
        try {
            val appIntent = packageName?.let {
                packageManager.getLaunchIntentForPackage(it)
            }

            if (appIntent != null) {
                startActivity(appIntent)
                return
            }

            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )

            if (webIntent.resolveActivity(packageManager) != null) {
                startActivity(webIntent)
            } else {
                Toast.makeText(
                    this,
                    "No compatible app found.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Unable to open this app.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
private fun DriveDeckScreen(
    openApp: (String, String?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var controller by remember {
        mutableStateOf<MediaController?>(null)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var trackTitle by remember { mutableStateOf("No track playing") }
    var playerError by remember { mutableStateOf<String?>(null) }

    val sessionToken = remember(context) {
        SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
    }

    val controllerFuture = remember(sessionToken) {
        MediaController.Builder(context, sessionToken).buildAsync()
    }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get()
                    playerError = null
                } catch (e: Exception) {
                    playerError = "Unable to connect to media player."
                }
            },
            context.mainExecutor
        )

        onDispose {
            val future = controllerFuture

            if (future.isDone && !future.isCancelled) {
                try {
                    MediaController.releaseFuture(future)
                } catch (_: Exception) {
                    // Controller may already have been released.
                }
            } else {
                future.cancel(false)
            }
        }
    }

    DisposableEffect(controller) {
        val currentController = controller

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                trackTitle =
                    mediaItem?.mediaMetadata?.title?.toString()
                        ?: "No track playing"
            }

            override fun onPlayerError(
                error: androidx.media3.common.PlaybackException
            ) {
                playerError = "Playback failed: ${error.errorCodeName}"
            }
        }

        currentController?.addListener(listener)

        isPlaying = currentController?.isPlaying == true
        trackTitle =
            currentController?.currentMediaItem
                ?.mediaMetadata?.title?.toString()
                ?: "No track playing"

        onDispose {
            currentController?.removeListener(listener)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "DRIVEDECK",
                        color = Muted,
                        fontSize = 12.sp,
                        letterSpacing = 3.sp
                    )
                    Text(
                        "Your drive, simplified.",
                        color = White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Created by FrdsArff",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = White,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "D",
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 10.dp
                        ),
                        color = Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Functional media player
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "NOW PLAYING",
                        color = Muted,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )

                    Text(
                        trackTitle,
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        if (isPlaying) "Playing" else "Ready",
                        color = Muted,
                        fontSize = 13.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val mediaController = controller

                                if (mediaController == null) {
                                    playerError =
                                        "Media service is still connecting."
                                    return@Button
                                }

                                playerError = null

                                if (mediaController.isPlaying) {
                                    mediaController.pause()
                                } else {
                                    if (mediaController.currentMediaItem == null) {
                                        mediaController.setMediaItem(
                                            MediaItem.Builder()
                                                .setMediaId(
                                                    PlaybackService.DEMO_TRACK_ID
                                                )
                                                .setUri(
                                                    PlaybackService.DEMO_TRACK_URL
                                                )
                                                .setMediaMetadata(
                                                    androidx.media3.common.MediaMetadata
                                                        .Builder()
                                                        .setTitle(
                                                            "DriveDeck Demo Track"
                                                        )
                                                        .setArtist("FrdsArff")
                                                        .build()
                                                )
                                                .build()
                                        )
                                        mediaController.prepare()
                                    }

                                    mediaController.play()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = White,
                                contentColor = Ink
                            )
                        ) {
                            Text(
                                if (isPlaying) "Pause" else "Play"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                controller?.stop()
                                controller?.clearMediaItems()
                                isPlaying = false
                                trackTitle = "No track playing"
                                playerError = null
                            }
                        ) {
                            Text("Stop")
                        }
                    }

                    playerError?.let {
                        Text(
                            it,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        "Demo audio for testing. Requires internet access.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            // Weather placeholder
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "WEATHER",
                        color = Muted,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "Weather not connected",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Live weather provider not configured.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }

            Text(
                "QUICK SHORTCUTS",
                color = Muted,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShortcutCard(
                    "Waze",
                    Modifier.weight(1f)
                ) {
                    openApp("https://waze.com/ul", "com.waze")
                }

                ShortcutCard(
                    "Google Maps",
                    Modifier.weight(1f)
                ) {
                    openApp(
                        "https://maps.google.com",
                        "com.google.android.apps.maps"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShortcutCard(
                    "Spotify",
                    Modifier.weight(1f)
                ) {
                    openApp(
                        "https://open.spotify.com",
                        "com.spotify.music"
                    )
                }

                ShortcutCard(
                    "YouTube Music",
                    Modifier.weight(1f)
                ) {
                    openApp(
                        "https://music.youtube.com",
                        "com.google.android.apps.youtube.music"
                    )
                }
            }

            ShortcutCard(
                "YouTube",
                Modifier.fillMaxWidth()
            ) {
                openApp(
                    "https://www.youtube.com",
                    "com.google.android.youtube"
                )
            }

            Text(
                "DRIVE SAFE • DriveDeck by FrdsArff",
                color = Muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title,
                color = White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
