package com.example.drivedeck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink, surface = Panel, primary = Blue,
                onBackground = Color.White, onSurface = Color.White
            )) {
                Dashboard(
                    openWaze = { openAppOrWeb("https://waze.com/ul") },
                    openMaps = { openAppOrWeb("https://maps.google.com") },
                    openSpotify = { openAppOrWeb("https://open.spotify.com") }
                )
            }
        }
    }

    private fun openAppOrWeb(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }
}

@Composable
private fun Dashboard(openWaze: () -> Unit, openMaps: () -> Unit, openSpotify: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("DRIVEDECK", color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                    Text("Your drive, simplified.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text("●", color = Blue, fontSize = 22.sp)
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NOW PLAYING", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text("DriveDeck Demo Stream", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sample audio for testing the Android Auto media service", color = Muted, fontSize = 13.sp)
                    Text("Playback controls appear in Android Auto media UI.", color = Blue, fontSize = 12.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("WEATHER", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                        Text("Weather provider not connected", fontWeight = FontWeight.SemiBold)
                        Text("Add an API provider to show live data.", color = Muted, fontSize = 12.sp)
                    }
                    Text("☁", color = Blue, fontSize = 32.sp)
                }
            }

            Text("QUICK SHORTCUTS", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Shortcut("Waze", "Navigation", Modifier.weight(1f), openWaze)
                Shortcut("Maps", "Navigation", Modifier.weight(1f), openMaps)
                Shortcut("Spotify", "Music", Modifier.weight(1f), openSpotify)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Phone companion dashboard • Keep interactions simple while driving.",
                color = Muted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun Shortcut(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF24354A))
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}
