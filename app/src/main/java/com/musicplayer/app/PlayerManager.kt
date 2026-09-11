package com.musicplayer.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicplayer.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerManager(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    var isShuffleEnabled: Boolean = false
    var isRepeatEnabled: Boolean = false

    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var positionPollingJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPositionPolling()
                } else {
                    stopPositionPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionPollingJob = scope.launch {
            while (true) {
                _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                delay(300)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
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
        _positionMs.value = 0L
        _durationMs.value = 0L
    }

    // Troca só a FILA usada por playNext()/playPrevious(), sem reiniciar a
    // música que já está tocando. Usado quando o usuário puxa a tarja da
    // própria música que já está tocando: o som continua exatamente de
    // onde estava, mas passa a encadear só o artista daquela tarja.
    fun rescopeQueue(songs: List<Song>) {
        val current = _currentSong.value ?: return
        if (songs.isEmpty()) return
        playlist = songs
        currentIndex = songs.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _positionMs.value = positionMs
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
        stopPositionPolling()
        player.release()
    }
}
