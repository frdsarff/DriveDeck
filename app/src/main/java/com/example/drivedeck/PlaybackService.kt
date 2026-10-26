package com.example.drivedeck

import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.drivedeck.bridge.MediaBridge
import com.example.drivedeck.lyrics.LyricLine
import com.example.drivedeck.lyrics.LyricsRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var librarySession: MediaLibrarySession? = null
    private lateinit var player: BridgePlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var lyricsKey: String? = null
    private var lyrics: List<LyricLine> = emptyList()

    override fun onCreate() {
        super.onCreate()

        MediaBridge.start(this)

        player = BridgePlayer()

        librarySession = MediaLibrarySession.Builder(
            this,
            player,
            DriveDeckLibraryCallback()
        ).build()

        // 1. Bila lagu / state berubah: refresh skrin kereta + cari lirik baru
        scope.launch {
            MediaBridge.state.collect { np ->
                player.refresh()

                val key = "${np.packageName}|${np.title}|${np.artist}"
                if (key != lyricsKey) {
                    lyricsKey = key
                    lyrics = emptyList()
                    player.setLyricLine(null)

                    if (SHOW_LYRICS_ON_CAR && np.title.isNotBlank()) {
                        launch {
                            val result = LyricsRepository.load(
                                np.title, np.artist, np.durationMs
                            )
                            if (lyricsKey == key) {
                                lyrics = result
                                updateLyricLine()
                            }
                        }
                    }
                }
            }
        }

        // 2. Semak baris lirik semasa setiap 300ms
        scope.launch {
            while (isActive) {
                delay(300)
                updateLyricLine()
            }
        }
    }

    private fun updateLyricLine() {
        if (!SHOW_LYRICS_ON_CAR || lyrics.isEmpty()) return
        val pos = player.estimatedPositionMs()
        val idx = lyrics.indexOfLast { it.timeMs <= pos + LYRIC_LEAD_MS }
        player.setLyricLine(if (idx >= 0) lyrics[idx].text else null)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? = librarySession

    override fun onDestroy() {
        scope.cancel()
        librarySession?.release()
        librarySession = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Player yang mirror app yang tengah main kat phone
    // ------------------------------------------------------------------
    @OptIn(UnstableApi::class)
    private class BridgePlayer : SimpleBasePlayer(Looper.getMainLooper()) {

        private var cachedKey: String? = null
        private var cachedArt: ByteArray? = null

        private var lyricLine: String? = null

        /** Posisi lagu sekarang, dikira dari masa update terakhir bridge (jam sama dengan kad phone). */
        fun estimatedPositionMs(): Long {
            val np = MediaBridge.state.value
            val extra = if (np.isPlaying) SystemClock.elapsedRealtime() - np.updatedAt else 0L
            val pos = np.positionMs + extra
            return if (np.durationMs > 0) pos.coerceAtMost(np.durationMs) else pos
        }

        fun refresh() {
            invalidateState()
        }

        fun setLyricLine(text: String?) {
            if (text == lyricLine) return
            lyricLine = text
            invalidateState()
        }

        override fun getState(): State {
            val np = MediaBridge.state.value

            val commands = Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_SET_MEDIA_ITEM,
                    Player.COMMAND_CHANGE_MEDIA_ITEMS,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                )
                .build()

            if (np.title.isBlank()) {
                return State.Builder()
                    .setAvailableCommands(commands)
                    .setPlaybackState(Player.STATE_IDLE)
                    .build()
            }

            // Key ni TAK termasuk lirik, supaya progress bar tak reset bila baris tukar
            val key = "${np.packageName}|${np.title}|${np.artist}"
            val art = artworkFor(key, np.artwork)

            val lyric = lyricLine?.trim().orEmpty()
            val showLyric = SHOW_LYRICS_ON_CAR && lyric.isNotEmpty()

            val metadata = MediaMetadata.Builder()
                .setTitle(if (showLyric) lyric else np.title)
                .setArtist(if (showLyric) "${np.title} • ${np.artist}" else np.artist)
                .setAlbumArtist(np.artist)
                .setAlbumTitle(np.appName)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .apply {
                    if (art != null) {
                        setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                }
                .build()

            val current = MediaItemData.Builder(key)
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(CURRENT_ID)
                        .setMediaMetadata(metadata)
                        .build()
                )
                .setMediaMetadata(metadata)
                .setDurationUs(
                    if (np.durationMs > 0) np.durationMs * 1000 else C.TIME_UNSET
                )
                .setIsSeekable(np.durationMs > 0)
                .build()

            val previous = placeholder("prev", "Lagu sebelumnya")
            val next = placeholder("next", "Lagu seterusnya")

            return State.Builder()
                .setAvailableCommands(commands)
                .setPlaylist(listOf(previous, current, next))
                .setCurrentMediaItemIndex(1)
                .setPlaybackState(Player.STATE_READY)
                .setPlayWhenReady(
                    np.isPlaying,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                )
                .setContentPositionMs(estimatedPositionMs())
                .build()
        }

        private fun placeholder(id: String, title: String): MediaItemData {
            val md = MediaMetadata.Builder().setTitle(title).build()
            return MediaItemData.Builder(id)
                .setMediaItem(
                    MediaItem.Builder().setMediaId(id).setMediaMetadata(md).build()
                )
                .setMediaMetadata(md)
                .build()
        }

        private fun artworkFor(key: String, bitmap: Bitmap?): ByteArray? {
            if (key == cachedKey && cachedArt != null) return cachedArt

            if (bitmap == null) {
                cachedKey = key
                cachedArt = null
                return null
            }

            val maxSide = 512
            val longest = maxOf(bitmap.width, bitmap.height)
            val scaled = if (longest > maxSide) {
                val ratio = maxSide.toFloat() / longest
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else bitmap

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)

            cachedKey = key
            cachedArt = out.toByteArray()
            return cachedArt
        }

        // ---- Arahan dari Android Auto -> dihantar ke app kat phone ----

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) MediaBridge.play() else MediaBridge.pause()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int
        ): ListenableFuture<*> {
            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> MediaBridge.next()

                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> MediaBridge.previous()

                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM ->
                    if (positionMs != C.TIME_UNSET) MediaBridge.seekTo(positionMs)

                Player.COMMAND_SEEK_TO_MEDIA_ITEM -> when (mediaItemIndex) {
                    0 -> MediaBridge.previous()
                    2 -> MediaBridge.next()
                }
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleSetMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<*> {
            MediaBridge.play()
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> =
            Futures.immediateVoidFuture()

        override fun handleStop(): ListenableFuture<*> {
            MediaBridge.pause()
            return Futures.immediateVoidFuture()
        }
    }

    // ------------------------------------------------------------------
    // Browse tree Android Auto
    // ------------------------------------------------------------------
    private class DriveDeckLibraryCallback : MediaLibrarySession.Callback {

        private val root: MediaItem
            get() = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("DriveDeck")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(root, params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val np = MediaBridge.state.value

            val items = if (parentId == ROOT_ID && np.title.isNotBlank()) {
                ImmutableList.of(
                    MediaItem.Builder()
                        .setMediaId(CURRENT_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(np.title)
                                .setArtist(np.artist)
                                .setAlbumTitle(np.appName)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                )
            } else {
                ImmutableList.of()
            }

            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems)
    }

    companion object {
        const val ROOT_ID = "root"
        const val CURRENT_ID = "bridge-current"

        /** Letak false kalau nak skrin kereta tunjuk nama artis macam biasa. */
        const val SHOW_LYRICS_ON_CAR = true

        /** Lirik muncul lebih awal sikit (ms). Naikkan kalau rasa lambat kat kereta. */
        const val LYRIC_LEAD_MS = 300L

        const val DEMO_TRACK_ID = "demo-track"
        const val DEMO_TRACK_URL =
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    }
}
