
package com.example.drivedeck

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaLibraryService() {

    private var librarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()

        librarySession = MediaLibrarySession.Builder(
            this,
            player,
            DriveDeckLibraryCallback()
        ).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? = librarySession

    override fun onDestroy() {
        librarySession?.release()
        librarySession = null

        if (::player.isInitialized) {
            player.release()
        }

        super.onDestroy()
    }

    private class DriveDeckLibraryCallback :
        MediaLibrarySession.Callback {

        private val demoTrack: MediaItem
            get() = MediaItem.Builder()
                .setMediaId(DEMO_TRACK_ID)
                .setUri(DEMO_TRACK_URL)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("DriveDeck Demo Track")
                        .setArtist("FrdsArff")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()
                )
                .build()

        private val root: MediaItem
            get() = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("DriveDeck")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(
                            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        )
                        .build()
                )
                .build()

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(root, params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {

            val items = if (parentId == ROOT_ID) {
                ImmutableList.of(demoTrack)
            } else {
                ImmutableList.of()
            }

            return Futures.immediateFuture(
                LibraryResult.ofItemList(items, params)
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {

            val resolvedItems = mediaItems.map { requested ->

                // Preserve items that already have a playable URI.
                if (requested.localConfiguration != null) {
                    requested
                } else {
                    when (requested.mediaId) {
                        DEMO_TRACK_ID -> demoTrack

                        else -> {
                            val requestedUri =
                                requested.requestMetadata.mediaUri

                            if (requestedUri != null) {
                                requested.buildUpon()
                                    .setUri(requestedUri)
                                    .build()
                            } else {
                                requested
                            }
                        }
                    }
                }
            }

            return Futures.immediateFuture(resolvedItems)
        }
    }

    companion object {
        const val ROOT_ID = "root"
        const val DEMO_TRACK_ID = "demo-track"

        const val DEMO_TRACK_URL =
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    }
}
