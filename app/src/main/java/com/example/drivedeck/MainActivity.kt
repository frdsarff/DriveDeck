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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0B1018)
private val Panel = Color(0xFF1A2535)
private val Blue = Color(0xFF8CBFFF)
private val Muted = Color(0xFFAAB8CC)
private val CardBlue = Color(0xFF24354A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink,
                    surface = Panel,
                    primary = Blue,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Dashboard(
                    openWaze = { openAppOrWeb("https://waze.com/ul", "com.waze") },
                    openMaps = { openAppOrWeb("https://maps.google.com", "com.google.android.apps.maps") },
                    openSpotify = { openAppOrWeb("https://open.spotify.com", "com.spotify.music") },
                    openYouTubeMusic = {
                        openAppOrWeb(
                            "https://music.youtube.com",
                            "com.google.android.apps.youtube.music"
                        )
                    },
                    openYouTube = {
                        openAppOrWeb("https://www.youtube.com", "com.google.android.youtube")
                    }
                )
            }
        }
    }

    private fun openAppOrWeb(url: String, packageName: String? = null) {
        try {
            val appIntent = packageName?.let { packageManager.getLaunchIntentForPackage(it) }
            if (appIntent != null) {
                startActivity(appIntent)
                return
            }

            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (webIntent.resolveActivity(packageManager) != null) {
                startActivity(webIntent)
            } else {
                Toast.makeText(this, "No compatible app found to open this shortcut.", Toast.LENGTH_SHORT).show()
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Unable to open this app right now.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun Dashboard(
    openWaze: () -> Unit,
    openMaps: () -> Unit,
    openSpotify: () -> Unit,
    openYouTubeMusic: () -> Unit,
    openYouTube: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DRIVEDECK", color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                    Text("Your drive, simplified.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Your apps, one dashboard.", color = Muted, fontSize = 13.sp)
                }
                Surface(shape = RoundedCornerShape(16.dp), color = CardBlue) {
                    Text("DD", modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        color = Blue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ANDROID AUTO MEDIA", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text("DriveDeck Demo Stream", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "The Android Auto media screen is provided by DriveDeck's MediaLibraryService.",
                        color = Muted, fontSize = 13.sp
                    )
                    Text(
                        "Note: this is a test stream, not a YouTube or YouTube Music player.",
                        color = Blue, fontSize = 12.sp
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("☁", color = Blue, fontSize = 30.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("WEATHER", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                        Text("Weather not connected", fontWeight = FontWeight.SemiBold)
                        Text("Live weather needs a provider and API key.", color = Muted, fontSize = 12.sp)
                    }
                }
            }

            Text("QUICK SHORTCUTS", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Shortcut("Waze", "Navigation", Modifier.weight(1f), openWaze)
                Shortcut("Google Maps", "Navigation", Modifier.weight(1f), openMaps)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Shortcut("Spotify", "Music", Modifier.weight(1f), openSpotify)
                Shortcut("YouTube Music", "Music", Modifier.weight(1f), openYouTubeMusic)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Shortcut("YouTube", "Video / audio", Modifier.fillMaxWidth(), openYouTube)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202B3A)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DRIVE SAFE", color = Blue, fontWeight = FontWeight.Bold)
                    Text(
                        "Use audio and voice controls while driving. YouTube video playback and phone interaction should be used only when safely parked; Android Auto restricts video apps while moving.",
                        color = Muted, fontSize = 12.sp
                    )
                }
            }

            Text(
                "Phone companion dashboard • DriveDeck is an early-stage project.",
                color = Muted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun Shortcut(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlue)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}
