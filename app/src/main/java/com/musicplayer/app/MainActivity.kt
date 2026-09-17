package com.musicplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.musicplayer.app.data.Album
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.MusicRepository
import com.musicplayer.app.data.Song
import com.musicplayer.app.player.PlayerManager
import com.musicplayer.app.ui.components.AlbumExpansionPanel
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
    val positionMs by playerManager.positionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()

    // Faixas extras do álbum selecionado + fila completa pra tocar. O painel
    // que mostra isso é renderizado AQUI, como irmão do Mini Player — nunca
    // dentro da lista que rola — por isso nunca aparece acima do Mini Player.
    var expandedAlbumTracks by remember { mutableStateOf<List<Song>>(emptyList()) }
    var expandedAlbumQueue by remember { mutableStateOf<List<Song>>(emptyList()) }

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

    // O Mini Player fica suspenso, "flutuando" sobre a lista, com um vão de
    // 225dp (≈2,5 tarjas) até a borda de baixo da tela. O painel de faixas
    // extras do álbum ocupa exatamente esse vão — nunca a parte de cima.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        ArtistsScreen(
            artists = artists,
            isLoading = isLoading,
            currentSongId = currentSong?.id,
            onSongClick = { song, list ->
                playerManager.playSong(song, list)
            },
            onRescopeQueue = { songs ->
                playerManager.rescopeQueue(songs)
            },
            onAlbumExpansionChanged = { tracks, fullQueue ->
                expandedAlbumTracks = tracks
                expandedAlbumQueue = fullQueue
            }
        )

        // Painel fixo com as demais faixas do álbum — ancorado direto no vão
        // abaixo do Mini Player, fora da lista que rola.
        AlbumExpansionPanel(
            tracks = expandedAlbumTracks,
            onTrackClick = { track ->
                playerManager.playSong(track, expandedAlbumQueue)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(225.dp)
        )

        MiniPlayer(
            song = currentSong,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onPlayPauseClick = { playerManager.togglePlayPause() },
            onPreviousClick = { playerManager.playPrevious() },
            onNextClick = { playerManager.playNext() },
            onSeek = { newPosition -> playerManager.seekTo(newPosition) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 225.dp)
        )
    }
}

