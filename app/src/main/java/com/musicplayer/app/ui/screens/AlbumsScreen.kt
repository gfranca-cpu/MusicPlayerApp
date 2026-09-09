package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.components.ExpandableSongItem
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite
import kotlinx.coroutines.launch

private fun buildFlatArtistSongs(artists: List<Artist>): List<Song> =
    artists
        .flatMap { artist -> artist.albums.flatMap { album -> album.songs } }
        .distinctBy { it.id }
        .sortedBy { it.title }

@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val allSongs = remember(artists) { buildFlatArtistSongs(artists) }
    val artistNames = remember(allSongs) { allSongs.map { it.artist }.distinct().sorted() }

    val dragOffsets = remember(artists) { mutableStateMapOf<Long, Float>() }
    var activeSongId by remember(artists) { mutableStateOf<Long?>(null) }

    val songListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Text(
            text = "Artistas",
            color = TextWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TextWhite)
                }
            }

            artists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma música encontrada\nno armazenamento",
                        color = TextWhite.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                }
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val itemWidth = maxWidth - 24.dp
                    val maxDragOffsetPx = with(density) { (itemWidth * 0.8f).toPx() }

                    val activeArtistName = activeSongId?.let { id ->
                        allSongs.firstOrNull { it.id == id }?.artist
                    }

                    // Segue a gaveta quando a música avança dentro do mesmo artista
                    LaunchedEffect(currentSongId) {
                        val activeId = activeSongId
                        if (activeId != null && currentSongId != null && currentSongId != activeId) {
                            val activeSong = allSongs.firstOrNull { it.id == activeId }
                            val newSong = allSongs.firstOrNull { it.id == currentSongId }
                            if (activeSong != null && newSong != null && newSong.artist == activeSong.artist) {
                                dragOffsets[activeId] = 0f
                                activeSongId = currentSongId
                                dragOffsets[currentSongId] = maxDragOffsetPx

                                val targetIndex = allSongs.indexOfFirst { it.id == currentSongId }
                                if (targetIndex >= 0) {
                                    songListState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                    }

                    // Lista de tarjas
                    LazyColumn(
                        state = songListState,
                        userScrollEnabled = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        items(allSongs, key = { it.id }) { song ->
                            val dragOffset = dragOffsets[song.id] ?: 0f
                            val isHighlighted = activeArtistName == null || song.artist == activeArtistName

                            ExpandableSongItem(
                                song = song,
                                artistNames = artistNames,
                                isHighlighted = isHighlighted,
                                onPlaySong = {
                                    if (activeSongId != null && isHighlighted) {
                                        val songsOfArtist = allSongs.filter { it.artist == song.artist }
                                        onSongClick(song, songsOfArtist)
                                    } else {
                                        onSongClick(song, allSongs)
                                    }
                                },
                                onRevealArtistClick = { artistName ->
                                    // === SINCRONIZAÇÃO SECUNDÁRIA (mantida intacta) ===
                                    val songsOfArtist = allSongs.filter { it.artist == artistName }
                                    val firstSong = songsOfArtist.firstOrNull()
                                    if (firstSong != null) {
                                        onSongClick(firstSong, songsOfArtist)

                                        activeSongId?.let { previousId ->
                                            if (previousId != firstSong.id) {
                                                dragOffsets[previousId] = 0f
                                            }
                                        }

                                        activeSongId = firstSong.id
                                        dragOffsets[firstSong.id] = maxDragOffsetPx

                                        val targetIndex = allSongs.indexOfFirst { it.id == firstSong.id }
                                        if (targetIndex >= 0) {
                                            scope.launch {
                                                songListState.animateScrollToItem(targetIndex)
                                            }
                                        }
                                    }
                                },
                                dragOffsetX = dragOffset,
                                onDragStart = {
                                    if (activeSongId != song.id) {
                                        activeSongId?.let { previousId ->
                                            dragOffsets[previousId] = 0f
                                        }
                                        activeSongId = song.id
                                    }
                                },
                                onDrag = { nextValue ->
                                    dragOffsets[song.id] = nextValue
                                },
                                onDragEnd = { expanded, targetOffsetPx ->
                                    if (expanded) {
                                        dragOffsets[song.id] = targetOffsetPx
                                    } else {
                                        dragOffsets[song.id] = 0f
                                        if (activeSongId == song.id) {
                                            activeSongId = null
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
