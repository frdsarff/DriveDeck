package com.example.drivedeck

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
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
import androidx.compose.ui.graphics.Brush
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
        val isHeadUnit = resources.configuration.smallestScreenWidthDp >= 600 ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        if (isHeadUnit) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
                if (isHeadUnit) {
                    DriveDeckApp { url, packageName -> openAppOrWeb(url, packageName) }
                } else {
                    PhoneApp(onOpenNotificationSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    })
                }
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



private enum class PhonePage { DASHBOARD, SETTINGS }

@Composable
private fun PhoneApp(onOpenNotificationSettings: () -> Unit) {
    var page by remember { mutableStateOf(PhonePage.DASHBOARD) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("drivedeck_phone_settings", android.content.Context.MODE_PRIVATE) }
    var appearance by remember { mutableStateOf(prefs.getString("appearance", "Dark") ?: "Dark") }
    var language by remember { mutableStateOf(prefs.getString("language", "English") ?: "English") }
    val isLight = appearance == "Light" || (appearance == "System" && !androidx.compose.foundation.isSystemInDarkTheme())
    val bg = if (isLight) Color(0xFFF5F6FA) else Color(0xFF0D101A)
    val panel = if (isLight) Color.White else Color(0xFF1B1E29)
    val text = if (isLight) Color(0xFF191B24) else Color(0xFFF7F7F7)
    val muted = if (isLight) Color(0xFF646875) else Color(0xFFA4AAB5)
    val bm = language == "Bahasa Melayu"

    if (page == PhonePage.SETTINGS) {
        PhoneSettingsScreen(onOpenNotificationSettings = onOpenNotificationSettings, onBack = { page = PhonePage.DASHBOARD })
    } else {
        MaterialTheme(colorScheme = if (isLight) lightColorScheme(
            primary = Color(0xFF26CDB5), background = bg, surface = panel, onBackground = text, onSurface = text
        ) else darkColorScheme(
            primary = Color(0xFF26CDB5), background = bg, surface = panel, onBackground = text, onSurface = text
        )) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(bg),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (bm) "Papan pemuka" else "Dashboard", color = text, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        Surface(onClick = { page = PhonePage.SETTINGS }, shape = CircleShape, color = panel,
                            border = BorderStroke(1.dp, text.copy(alpha = 0.08f))) {
                            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                Text("☷", color = text, fontSize = 26.sp)
                            }
                        }
                    }
                }
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.Transparent,
                        border = BorderStroke(1.dp, Color(0xFF26CDB5).copy(alpha = 0.20f))) {
                        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF125D5B), Color(0xFF101B2B), Color(0xFF123C40)))).padding(24.dp)) {
                            Column {
                                Text(if (bm) "Selamat datang ke" else "Welcome to", color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp)
                                Spacer(Modifier.height(5.dp))
                                Text("DriveDeck", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(if (bm) "Teman pemanduan dan muzik anda" else "Your driving and music companion", color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp)
                            }
                        }
                    }
                }
                item {
                    Text(if (bm) "Akses pantas" else "Quick settings", color = text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhoneDashboardCard(if (bm) "Penampilan" else "Appearance", appearance, "◐", Modifier.weight(1f), panel, text, muted) { page = PhonePage.SETTINGS }
                        PhoneDashboardCard(if (bm) "Lirik" else "Lyrics", if (bm) "Sumber lirik & akses" else "Lyrics source & access", "♫", Modifier.weight(1f), panel, text, muted) { page = PhonePage.SETTINGS }
                    }
                }
                item {
                    Text(if (bm) "Aplikasi" else "Apps", color = text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhoneDashboardCard(if (bm) "Lirik" else "Lyrics", if (bm) "Tetapan sync lirik" else "Lyrics sync settings", "♫", Modifier.fillMaxWidth(), panel, text, muted) { page = PhonePage.SETTINGS }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PhoneDashboardCard(if (bm) "Paparan kereta" else "Car display", if (bm) "Tetapan head unit" else "Head unit settings", "▣", Modifier.weight(1f), panel, text, muted) { page = PhonePage.SETTINGS }
                            PhoneDashboardCard(if (bm) "Tetapan" else "Settings", if (bm) "Konfigurasi DriveDeck" else "Configure DriveDeck", "⚙", Modifier.weight(1f), panel, text, muted) { page = PhonePage.SETTINGS }
                        }
                    }
                }
                item {
                    Text(if (bm) "Dashboard head unit berasingan daripada app telefon." else "The head unit dashboard is separate from the phone app.", color = muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PhoneDashboardCard(
    title: String, subtitle: String, icon: String, modifier: Modifier,
    panel: Color, text: Color, muted: Color, onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = panel),
        border = BorderStroke(1.dp, text.copy(alpha = 0.07f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0x3326CDB5)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text(icon, color = Color(0xFF26CDB5), fontSize = 24.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PhoneSettingsScreen(onOpenNotificationSettings: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("drivedeck_phone_settings", android.content.Context.MODE_PRIVATE) }
    var appearance by remember { mutableStateOf(prefs.getString("appearance", "Dark") ?: "Dark") }
    var language by remember { mutableStateOf(prefs.getString("language", "English") ?: "English") }
    val isLight = appearance == "Light" ||
        (appearance == "System" && androidx.compose.foundation.isSystemInDarkTheme().not())
    val bg = if (isLight) Color(0xFFF5F6FA) else Color(0xFF0D101A)
    val panel = if (isLight) Color(0xFFFFFFFF) else Color(0xFF1B1E29)
    val textColor = if (isLight) Color(0xFF191B24) else Color(0xFFF7F7F7)
    val mutedColor = if (isLight) Color(0xFF646875) else Color(0xFFA4AAB5)
    val accent = Color(0xFF26CDB5)
    val bm = language == "Bahasa Melayu"
    var hasNotificationAccess by remember {
        mutableStateOf(
            android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )?.contains(context.packageName) == true
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationAccess = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )?.contains(context.packageName) == true
            delay(700)
        }
    }

    MaterialTheme(
        colorScheme = if (isLight) lightColorScheme(
            primary = accent, onPrimary = Color.White, background = bg, surface = panel,
            onBackground = textColor, onSurface = textColor
        ) else darkColorScheme(
            primary = accent, background = bg, surface = panel,
            onBackground = textColor, onSurface = textColor
        )
    ) {
        Column(Modifier.fillMaxSize().background(bg)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 38.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = textColor, fontSize = 32.sp, modifier = Modifier.clickable(onClick = onBack))
                Spacer(Modifier.width(18.dp))
                Text(if (bm) "Tetapan" else "Settings", color = textColor, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    SettingsSectionTitle(if (bm) "Penampilan" else "Appearance", textColor)
                    SettingsPanel(panel, textColor) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Dark", "Light", "System").forEach { option ->
                                FilterChip(
                                    selected = appearance == option,
                                    onClick = {
                                        appearance = option
                                        prefs.edit().putString("appearance", option).apply()
                                    },
                                    label = { Text(when (option) {
                                        "Dark" -> if (bm) "Gelap" else "Dark"
                                        "Light" -> if (bm) "Cerah" else "Light"
                                        else -> if (bm) "Sistem" else "System"
                                    }) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = if (isLight) 0.20f else 0.24f),
                                        selectedLabelColor = textColor,
                                        labelColor = mutedColor
                                    )
                                )
                            }
                        }
                    }
                }
                item {
                    SettingsSectionTitle(if (bm) "Bahasa" else "Language", textColor)
                    SettingsPanel(panel, textColor) {
                        listOf("Phone language", "English", "Bahasa Melayu", "中文", "தமிழ்").forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    language = option
                                    prefs.edit().putString("language", option).apply()
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = language == option,
                                    onClick = {
                                        language = option
                                        prefs.edit().putString("language", option).apply()
                                    }
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(option, color = textColor, fontSize = 16.sp)
                            }
                        }
                    }
                }
                item {
                    SettingsSectionTitle(if (bm) "Lirik" else "Lyrics", textColor)
                    SettingsPanel(panel, textColor) {
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onOpenNotificationSettings).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (hasNotificationAccess) "✓" else "!", color = accent, fontSize = 24.sp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (bm) "Akses notifikasi" else "Notification access", color = textColor, fontSize = 17.sp)
                                Text(
                                    if (hasNotificationAccess) { if (bm) "Dibenarkan" else "Granted" }
                                    else { if (bm) "Tekan untuk benarkan akses" else "Tap to grant access" },
                                    color = mutedColor, fontSize = 14.sp
                                )
                            }
                            Text("↗", color = mutedColor, fontSize = 22.sp)
                        }
                        HorizontalDivider(color = textColor.copy(alpha = 0.12f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (bm) "Sumber lirik lalai" else "Default lyrics source", color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(if (bm) "Auto  ▾" else "Auto  ▾", color = mutedColor, fontSize = 15.sp)
                        }
                    }
                }
                item {
                    SettingsSectionTitle(if (bm) "Paparan kereta" else "Car display", textColor)
                    SettingsPanel(panel, textColor) {
                        Text(if (bm) "Latar utama kereta" else "Car home background", color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (bm) "Tetapan rupa dashboard akan ditambah kemudian."
                            else "Dashboard appearance settings will be available here.",
                            color = mutedColor, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(if (bm) "Kad kereta" else "Car card", color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (bm) "Dashboard dan player DriveDeck kekal pada head unit."
                            else "DriveDeck dashboard and player stay on the head unit.",
                            color = mutedColor, fontSize = 14.sp
                        )
                    }
                }
                item {
                    SettingsSectionTitle(if (bm) "Perihal" else "About", textColor)
                    SettingsPanel(panel, textColor) {
                        Text("DriveDeck", color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (bm) "Tetapan telefon • Dashboard head unit"
                            else "Phone settings • Head unit dashboard",
                            color = mutedColor, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, textColor: Color) {
    Text(title, color = textColor, fontSize = 19.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
}

@Composable
private fun SettingsPanel(panelColor: Color, textColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = panelColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.07f))
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

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
            Row(Modifier.weight(0.82f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardTile("PLAYER", if (np.title.isBlank()) "Buka player lirik" else np.title, Modifier.weight(1f).fillMaxHeight(), onOpenPlayer)
                DashboardTile("SHORTCUTS", "Apps & web", Modifier.weight(1f).fillMaxHeight()) {
                    openApp("https://www.youtube.com", null)
                }
            }
            Row(Modifier.weight(1.15f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntertainmentTile("YouTube Web", "Video & music", "▶", Modifier.weight(1f).fillMaxHeight()) {
                    openApp("https://www.youtube.com", null)
                }
                EntertainmentTile("RTMKlik", "TV & radio", "▣", Modifier.weight(1f).fillMaxHeight()) {
                    openApp("https://rtmklik.rtm.gov.my", null)
                }
            }
            Row(Modifier.weight(0.72f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardTile("SPOTIFY", "Open app", Modifier.weight(1f).fillMaxHeight()) {
                    openApp("https://open.spotify.com", "com.spotify.music")
                }
                DashboardTile("YOUTUBE MUSIC", "Open app", Modifier.weight(1f).fillMaxHeight()) {
                    openApp("https://music.youtube.com", "com.google.android.apps.youtube.music")
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
