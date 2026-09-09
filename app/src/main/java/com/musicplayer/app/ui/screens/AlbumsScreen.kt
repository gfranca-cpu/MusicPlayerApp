package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.components.ExpandableSongItem
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val isAnyTarjaOpen = activeSongId != null
    val songCount = allSongs.size
    val artistCount = artistNames.size
    val albumCount = artists.sumOf { it.albums.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ========== CABEÇALHO DINÂMICO ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Músicas($songCount)",
                color = if (!isAnyTarjaOpen) TextWhite else TextWhite.copy(alpha = 0.45f),
                fontSize = if (!isAnyTarjaOpen) 20.sp else 16.sp,
                fontWeight = if (!isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Artistas($artistCount)",
                color = if (isAnyTarjaOpen) TextWhite else TextWhite.copy(alpha = 0.45f),
                fontSize = if (isAnyTarjaOpen) 20.sp else 16.sp,
                fontWeight = if (isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Álbuns($albumCount)",
                color = TextWhite.copy(alpha = 0.45f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TextWhite)
                }
            }

            artists.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

                    // Segue a gaveta quando a música avança no mesmo artista
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

                    // ========== LISTA DE TARJAS ==========
                    LazyColumn(
                        state = songListState,
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
                                    val songsOfArtist = allSongs.filter { it.artist == artistName }
                                    val firstSong = songsOfArtist.firstOrNull()
                                    if (firstSong != null) {
                                        onSongClick(firstSong, songsOfArtist)
                                        activeSongId?.let { prev ->
                                            if (prev != firstSong.id) dragOffsets[prev] = 0f
                                        }
                                        activeSongId = firstSong.id
                                        dragOffsets[firstSong.id] = maxDragOffsetPx
                                        val targetIndex = allSongs.indexOfFirst { it.id == firstSong.id }
                                        if (targetIndex >= 0) {
                                            scope.launch { songListState.animateScrollToItem(targetIndex) }
                                        }
                                    }
                                },
                                dragOffsetX = dragOffset,
                                onDragStart = {
                                    if (activeSongId != song.id) {
                                        activeSongId?.let { dragOffsets[it] = 0f }
                                        activeSongId = song.id
                                    }
                                },
                                onDrag = { dragOffsets[song.id] = it },
                                onDragEnd = { expanded, target ->
                                    if (expanded) {
                                        dragOffsets[song.id] = target
                                    } else {
                                        dragOffsets[song.id] = 0f
                                        if (activeSongId == song.id) activeSongId = null
                                    }
                                }
                            )
                        }
                    }

                    // ========== ESCOTILHA LATERAL (scrollbar) ==========
                    val layoutInfo = songListState.layoutInfo
                    val totalItems = allSongs.size
                    if (totalItems > 3) {
                        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                        val approxItemHeight = 98f
                        val totalContentHeight = totalItems * approxItemHeight
                        val scrollPixels = songListState.firstVisibleItemIndex * approxItemHeight +
                                songListState.firstVisibleItemScrollOffset
                        val progress = (scrollPixels / totalContentHeight).coerceIn(0f, 1f)
                        val thumbHeight = (viewportHeight / totalContentHeight * viewportHeight)
                            .coerceIn(48f, 140f)
                        val thumbOffset = progress * (viewportHeight - thumbHeight)

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(20.dp)
                                .padding(end = 6.dp, top = 12.dp, bottom = 150.dp)
                        ) {
                            // Trilho
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .align(Alignment.Center)
                                    .background(TextWhite.copy(alpha = 0.12f), RoundedCornerShape(50))
                            )

                            // Escotilha
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(0, thumbOffset.roundToInt()) }
                                    .width(14.dp)
                                    .height(with(density) { thumbHeight.toDp() })
                                    .align(Alignment.TopCenter)
                                    .background(TextWhite.copy(alpha = 0.55f), RoundedCornerShape(50))
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val delta = dragAmount / viewportHeight * totalContentHeight
                                            scope.launch {
                                                songListState.scrollBy(delta)
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
