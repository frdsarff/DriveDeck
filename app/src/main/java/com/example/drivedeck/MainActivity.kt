package com.example.drivedeck

import android.content.ActivityNotFoundException
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
import com.example.drivedeck.bridge.MediaBridge
import com.example.drivedeck.ui.LiveNowPlayingCard
import com.example.drivedeck.ui.LyricsCard

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

    override fun onResume() {
        super.onResume()
        // Reconnect to Spotify / YouTube Music sessions (and pick up
        // access just granted in system settings).
        MediaBridge.start(this)
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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

            // Live now playing: Spotify / YouTube Music / any media app
            LiveNowPlayingCard()
            LyricsCard()

            // Weather placeholder
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        color = White,
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
                ShortcutCard("Waze", Modifier.weight(1f)) {
                    openApp("https://waze.com/ul", "com.waze")
                }

                ShortcutCard("Google Maps", Modifier.weight(1f)) {
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
                ShortcutCard("Spotify", Modifier.weight(1f)) {
                    openApp(
                        "https://open.spotify.com",
                        "com.spotify.music"
                    )
                }

                ShortcutCard("YouTube Music", Modifier.weight(1f)) {
                    openApp(
                        "https://music.youtube.com",
                        "com.google.android.apps.youtube.music"
                    )
                }
            }

            ShortcutCard("YouTube", Modifier.fillMaxWidth()) {
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
