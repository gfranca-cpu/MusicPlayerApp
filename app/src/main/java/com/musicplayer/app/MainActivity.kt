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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.musicplayer.app.data.Album
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.MusicRepository
import com.musicplayer.app.data.Song
import com.musicplayer.app.player.PlayerManager
import com.musicplayer.app.ui.components.BottomNavBar
import com.musicplayer.app.ui.components.BottomNavItem
import com.musicplayer.app.ui.components.MiniPlayer
import com.musicplayer.app.ui.screens.AlbumsScreen
import com.musicplayer.app.ui.screens.ArtistsScreen
import com.musicplayer.app.ui.screens.SongsScreen
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.launch

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
    var isLoading by remember { mutableStateOf(true) }

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val pagerState = rememberPagerState(initialPage = BottomNavItem.ARTISTS.ordinal) { 3 }
    val selectedTab = BottomNavItem.entries[pagerState.currentPage]
    val scope = rememberCoroutineScope()

    // Carrega as músicas ao iniciar
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            artists = repository.loadArtists()
            albums = repository.loadAlbums()
            allSongs = repository.loadAllSongs()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            Column {
                // Mini player (só aparece quando tem música tocando)
                MiniPlayer(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onPlayPause = { playerManager.togglePlayPause() },
                    onNext = { playerManager.playNext() }
                )

                BottomNavBar(
                    selected = selectedTab,
                    onItemSelected = { item ->
                        scope.launch {
                            pagerState.animateScrollToPage(item.ordinal)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
                userScrollEnabled = true,
                beyondViewportPageCount = 1
            ) { page ->
                when (BottomNavItem.entries[page]) {
                    BottomNavItem.ARTISTS -> {
                        ArtistsScreen(
                            artists = artists,
                            isLoading = isLoading,
                            currentSongId = currentSong?.id,
                            onSongClick = { song, list ->
                                playerManager.playSong(song, list)
                            }
                        )
                    }

                    BottomNavItem.ALBUMS -> {
                        AlbumsScreen(
                            albums = albums,
                            currentSongId = currentSong?.id,
                            onSongClick = { song, list ->
                                playerManager.playSong(song, list)
                            }
                        )
                    }

                    BottomNavItem.SONGS -> {
                        SongsScreen(
                            songs = allSongs,
                            currentSongId = currentSong?.id,
                            onSongClick = { song, list ->
                                playerManager.playSong(song, list)
                            }
                        )
                    }
                }
            }
        }
    }
}
