package com.example.drivedeck.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val hasAccess: Boolean = false,
    val packageName: String? = null,
    val appName: String = "",
    val title: String = "",
    val artist: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val updatedAt: Long = android.os.SystemClock.elapsedRealtime()
)

object MediaBridge {

    private val KNOWN_APPS = mapOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.google.android.youtube" to "YouTube"
    )
    private val PREFERRED = listOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music"
    )

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var active: MediaController? = null
    private var registered = false

    // Web media exposed by DriveDeck's own WebView (inspired by Jeda's web-media bridge).
    private var webNowPlaying: NowPlaying? = null
    @Volatile private var webMediaActive = false
    @Volatile private var webControlHandler: ((String) -> Unit)? = null

    fun attachWebControlHandler(handler: ((String) -> Unit)?) {
        webControlHandler = handler
    }

    fun publishWebState(
        title: String,
        artist: String,
        durationMs: Long,
        positionMs: Long,
        playbackSpeed: Float,
        isPlaying: Boolean,
        isActive: Boolean
    ) {
        if (!isActive || title.isBlank()) {
            webMediaActive = false
            webNowPlaying = null
            publish()
            return
        }
        val ctx = appContext
        webMediaActive = true
        webNowPlaying = NowPlaying(
            hasAccess = true,
            packageName = ctx?.packageName ?: "com.example.drivedeck",
            appName = "DriveDeck Web",
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            durationMs = durationMs.coerceAtLeast(0L),
            positionMs = positionMs.coerceAtLeast(0L),
            playbackSpeed = playbackSpeed.takeIf { it > 0f } ?: 1f,
            updatedAt = SystemClock.elapsedRealtime()
        )
        _state.value = webNowPlaying!!
    }

    private fun webCommand(command: String): Boolean {
        val handler = webControlHandler
        if (!webMediaActive || handler == null) return false
        handler.invoke(command)
        return true
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            pick(list.orEmpty())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun openAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx

        if (!hasAccess(ctx)) {
            _state.value = NowPlaying(hasAccess = false)
            return
        }

        val mgr = manager ?: (ctx.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as MediaSessionManager).also { manager = it }
        val comp = component ?: ComponentName(ctx, MediaBridgeListener::class.java)
            .also { component = it }

        try {
            if (!registered) {
                mgr.addOnActiveSessionsChangedListener(sessionsListener, comp)
                registered = true
            }
            pick(mgr.getActiveSessions(comp))
        } catch (e: SecurityException) {
            registered = false
            _state.value = NowPlaying(hasAccess = false)
        }
    }

    // ---- Controls -------------------------------------------------------

    fun play() { if (!webCommand("play")) active?.transportControls?.play() }

    fun pause() { if (!webCommand("pause")) active?.transportControls?.pause() }

    fun togglePlayPause() {
        if (webCommand("toggle")) return
        val c = active ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() { if (!webCommand("next")) active?.transportControls?.skipToNext() }

    fun previous() { if (!webCommand("previous")) active?.transportControls?.skipToPrevious() }

    fun seekTo(positionMs: Long) {
        if (!webCommand("seek:" + positionMs)) active?.transportControls?.seekTo(positionMs)
    }

    // ---- Internals ------------------------------------------------------

    private fun refresh() {
        val mgr = manager ?: return
        val comp = component ?: return
        try {
            pick(mgr.getActiveSessions(comp))
        } catch (e: SecurityException) {
            _state.value = NowPlaying(hasAccess = false)
        }
    }

    private fun pick(sessions: List<MediaController>) {
        val ctx = appContext ?: return
        val candidates = sessions.filter { it.packageName != ctx.packageName }

        val chosen =
            candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: candidates.firstOrNull { it.packageName == active?.packageName }
                ?: candidates.firstOrNull { it.packageName in PREFERRED }
                ?: candidates.firstOrNull()

        if (chosen?.sessionToken != active?.sessionToken) {
            active?.unregisterCallback(controllerCallback)
            active = chosen
            chosen?.registerCallback(controllerCallback)
        }
        publish()
    }

    private fun publish() {
        if (webMediaActive) {
            val web = webNowPlaying
            if (web != null) {
                _state.value = web.copy(updatedAt = SystemClock.elapsedRealtime())
                return
            }
        }
        val ctx = appContext ?: return
        val c = active
        if (c == null) {
            _state.value = NowPlaying(hasAccess = true)
            return
        }

        val md = c.metadata
        val ps = c.playbackState
        val pkg = c.packageName
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        var position = ps?.position ?: 0L
        if (ps != null && ps.state == PlaybackState.STATE_PLAYING && ps.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) *
                    ps.playbackSpeed).toLong()
        }
        position = position.coerceAtLeast(0L)
        if (duration > 0) position = position.coerceAtMost(duration)

        _state.value = NowPlaying(
            hasAccess = true,
            packageName = pkg,
            appName = KNOWN_APPS[pkg] ?: appLabel(ctx, pkg),
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            artwork = md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            isPlaying = ps?.state == PlaybackState.STATE_PLAYING,
            durationMs = duration,
            positionMs = position,
            playbackSpeed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        )
    }

    private fun appLabel(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
