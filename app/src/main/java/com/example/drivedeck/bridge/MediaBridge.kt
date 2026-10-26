package com.example.drivedeck.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
    val durationMs: Long = 0L
)

/**
 * Reads the active media session of other apps (Spotify, YouTube Music, etc.)
 * and forwards play/pause/next/previous to them.
 *
 * Requires Notification Listener access (user grants once in system settings).
 * Call start() from the main thread (MainActivity.onResume is fine).
 */
object MediaBridge {

    private val KNOWN_APPS = mapOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.google.android.youtube" to "YouTube"
    )

    // When nothing is playing, prefer these over random sessions.
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

    fun togglePlayPause() {
        val c = active ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() { active?.transportControls?.skipToNext() }

    fun previous() { active?.transportControls?.skipToPrevious() }

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
        val ctx = appContext ?: return
        val c = active
        if (c == null) {
            _state.value = NowPlaying(hasAccess = true)
            return
        }

        val md = c.metadata
        val ps = c.playbackState
        val pkg = c.packageName

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
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        )
    }

    private fun appLabel(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
