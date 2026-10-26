package com.example.drivedeck

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import com.example.drivedeck.bridge.NowPlaying
import com.example.drivedeck.lyrics.LyricLine
import com.example.drivedeck.lyrics.LyricsRepository
import kotlinx.coroutines.delay

private val Ink = Color(0xFF080A0E)
private val Panel = Color(0xD91B1F28)
private val CardColor = Color(0xCC252A34)
private val White = Color(0xFFF7F7F7)
private val Muted = Color(0xFFA4AAB5)
private val Accent = Color(0xFF8A69F5)
private val Mint = Color(0xFF62E6C0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink, surface = Panel, primary = White,
                    onBackground = White, onSurface = White
                )
            ) {
                DriveDeckApp { url, packageName -> openAppOrWeb(url, packageName) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MediaBridge.start(this)
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
                Toast.makeText(this, "Tiada aplikasi atau browser yang sesuai.", Toast.LENGTH_SHORT).show()
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Tak dapat buka aplikasi ini.", Toast.LENGTH_SHORT).show()
        }
    }
}

private enum class DeckPage { HOME, PLAYER }

@Composable
private fun DriveDeckApp(openApp: (String, String?) -> Unit) {
    var page by remember { mutableStateOf(DeckPage.HOME) }
    val np by MediaBridge.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            MediaBridge.start(context)
            delay(1500)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (page == DeckPage.PLAYER && np.artwork != null) {
            Image(
                bitmap = np.artwork!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(42.dp).alpha(0.38f)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xCC080A0E), Color(0xE6080A0E), Color(0xF5080A0E)))
            )
        )

        when (page) {
            DeckPage.HOME -> HomeDashboard(np, openApp) { page = DeckPage.PLAYER }
            DeckPage.PLAYER -> LyricsPlayer(np) { page = DeckPage.HOME }
        }

        Surface(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(48.dp)
                .clickable { page = if (page == DeckPage.HOME) DeckPage.PLAYER else DeckPage.HOME },
            shape = RoundedCornerShape(15.dp), color = Accent, shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (page == DeckPage.HOME) "♫" else "⌂", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeDashboard(np: NowPlaying, openApp: (String, String?) -> Unit, onOpenPlayer: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val time = remember(now) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now)) }
    val date = remember(now) { java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date(now)) }

    Row(
        Modifier.fillMaxSize().padding(start = 66.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1.08f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("DRIVEDECK", color = Muted, fontSize = 12.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Text(time, color = White, fontSize = 62.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light)
                Text(date, color = White.copy(alpha = 0.8f), fontSize = 18.sp)
            }
            NowPlayingTile(np, onOpenPlayer)
            Text("DRIVE SAFE  •  DRIVEDECK", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        }

        Column(Modifier.weight(1.35f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1.15f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntertainmentTile("YouTube Web", "Video & music", "▶", Modifier.weight(1f).fillMaxHeight()) {
                    openApp("https://www.youtube.com", null)
                }
                EntertainmentTile("RTMKlik", "TV & radio", "▣", Modifier.weight(1f).fillMaxHeight()) {
                    openApp("https://rtmklik.rtm.gov.my", null)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTile(np: NowPlaying, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(145.dp),
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (np.artwork != null) {
                Image(np.artwork.asImageBitmap(), null, Modifier.size(76.dp).clip(RoundedCornerShape(13.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(13.dp)).background(CardColor), contentAlignment = Alignment.Center) {
                    Text("♫", fontSize = 30.sp, color = Muted)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("NOW PLAYING", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(5.dp))
                Text(np.title.ifBlank { "Tiada lagu dimainkan" }, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(np.artist.ifBlank { "Tekan untuk buka player" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("↗", color = White, fontSize = 22.sp)
        }
    }
}

@Composable
private fun DashboardTile(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EntertainmentTile(title: String, subtitle: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(shape = RoundedCornerShape(13.dp), color = Color(0x33FFFFFF)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(symbol, color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column {
                Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LyricsPlayer(np: NowPlaying, onBack: () -> Unit) {
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var requestedKey by remember { mutableStateOf("") }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(np.packageName, np.title, np.artist, np.durationMs) {
        val key = "${np.packageName}|${np.title}|${np.artist}"
        requestedKey = key
        lyrics = emptyList()
        loading = np.title.isNotBlank()
        if (np.title.isNotBlank()) {
            val found = LyricsRepository.load(np.title, np.artist, np.durationMs)
            if (requestedKey == key) {
                lyrics = found
                loading = false
            }
        } else loading = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            tick = SystemClock.elapsedRealtime()
            delay(250)
        }
    }

    val position = remember(np.positionMs, np.updatedAt, np.isPlaying, tick) {
        val elapsed = if (np.isPlaying) (tick - np.updatedAt).coerceAtLeast(0L) else 0L
        val value = np.positionMs + elapsed
        if (np.durationMs > 0L) value.coerceAtMost(np.durationMs) else value
    }
    val activeIndex = remember(lyrics, position) { lyrics.indexOfLast { it.timeMs <= position + 250L }.coerceAtLeast(0) }

    Row(
        Modifier.fillMaxSize().padding(start = 66.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.36f), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                if (np.artwork != null) {
                    Image(np.artwork.asImageBitmap(), "Album art", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardColor), contentAlignment = Alignment.Center) {
                        Text("♫", color = Muted, fontSize = 70.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(np.title.ifBlank { "Tiada lagu dimainkan" }, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(np.artist.ifBlank { "Mainkan muzik untuk mula" }, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text("${np.appName}  •  ${if (lyrics.isEmpty()) "Lirik belum tersedia" else "Lirik tersedia"}", color = Mint, fontSize = 11.sp)
            }
            Column {
                LinearProgressIndicator(
                    progress = { if (np.durationMs > 0) (position.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = Mint, trackColor = White.copy(alpha = 0.18f)
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PlaybackButton("◀|", false) { MediaBridge.previous() }
                    PlaybackButton(if (np.isPlaying) "Ⅱ" else "▶", true) { MediaBridge.togglePlayPause() }
                    PlaybackButton("|▶", false) { MediaBridge.next() }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("LYRICS", color = Muted, fontSize = 11.sp, letterSpacing = 3.sp)
                    Text("Sing along", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onBack) { Text("HOME  ⌂", color = White) }
            }
            Spacer(Modifier.height(8.dp))
            when {
                !np.hasAccess -> CenterMessage("Benarkan Notification Access untuk membaca lagu.")
                np.title.isBlank() -> CenterMessage("Mainkan lagu di Spotify atau YouTube Music.")
                loading -> CenterMessage("Sedang mencari lirik terselaras…")
                lyrics.isEmpty() -> CenterMessage("Lirik terselaras tak ditemui untuk lagu ini.")
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    itemsIndexed(lyrics) { index, line ->
                        val active = index == activeIndex
                        Text(
                            text = line.text.ifBlank { "♪" },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            color = if (active) White else White.copy(alpha = if (kotlin.math.abs(index - activeIndex) <= 2) 0.48f else 0.22f),
                            fontSize = if (active) 25.sp else 20.sp,
                            lineHeight = if (active) 32.sp else 27.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.size(if (primary) 58.dp else 46.dp),
        shape = CircleShape, color = if (primary) White else Color(0x552B303A),
        contentColor = if (primary) Ink else White,
        border = if (primary) null else BorderStroke(1.dp, White.copy(alpha = 0.18f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = if (primary) 22.sp else 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, fontSize = 17.sp)
    }
}
