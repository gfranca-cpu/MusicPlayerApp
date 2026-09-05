
package com.musicplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.musicplayer.app.data.Album
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.MusicRepository
import com.musicplayer.app.data.Song
import com.musicplayer.app.player.PlayerManager
import com.musicplayer.app.ui.components.MiniPlayer
import com.musicplayer.app.ui.screens.ArtistsScreen
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {

    private lateinit var playerManager: PlayerManager
    private lateinit var repository: MusicRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            // As permissões foram concedidas → a tela vai recarregar os dados
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerManager = PlayerManager(this)
        repository = MusicRepository(this)

        requestPermissionsIfNeeded()

        setContent {
            MusicPlayerTheme {
                MusicPlayerApp(
                    repository = repository,
                    playerManager = playerManager
                )
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needRequest) {
            permissionLauncher.launch(permissions)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}

@Composable
fun MusicPlayerApp(
    repository: MusicRepository,
    playerManager: PlayerManager
) {
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()

    // Carrega as músicas ao iniciar
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            artists = repository.loadArtists()
            albums = repository.loadAlbums()
            allSongs = repository.loadAllSongs()
            genres = repository.loadGenres()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                isShuffleEnabled = playerManager.isShuffleEnabled,
                isRepeatEnabled = playerManager.isRepeatEnabled,
                onPlayPause = { playerManager.togglePlayPause() },
                onNext = { playerManager.playNext() },
                onShuffle = { playerManager.toggleShuffle() },
                onRepeat = { playerManager.toggleRepeat() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
        ) {
            ArtistsScreen(
                artists = artists,
                isLoading = isLoading,
                currentSongId = currentSong?.id,
                onSongClick = { song, list ->
                    playerManager.playSong(song, list)
                }
            )
        }
    }
}
