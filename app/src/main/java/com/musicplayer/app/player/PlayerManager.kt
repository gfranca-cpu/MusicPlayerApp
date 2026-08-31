package com.musicplayer.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicplayer.app.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerManager(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    var isShuffleEnabled: Boolean = false
    var isRepeatEnabled: Boolean = false

    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })
    }

    fun playSong(song: Song, songs: List<Song> = listOf(song)) {
        playlist = songs
        currentIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        val mediaItem = MediaItem.fromUri(song.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        _currentSong.value = song
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
    }

    fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val nextIndex = when {
            isShuffleEnabled -> playlist.indices.random()
            isRepeatEnabled && currentIndex == playlist.lastIndex -> 0
            else -> (currentIndex + 1) % playlist.size
        }
        currentIndex = nextIndex
        val next = playlist[currentIndex]
        playSong(next, playlist)
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playlist.lastIndex else currentIndex - 1
        val prev = playlist[currentIndex]
        playSong(prev, playlist)
    }

    fun release() {
        player.release()
    }
}
