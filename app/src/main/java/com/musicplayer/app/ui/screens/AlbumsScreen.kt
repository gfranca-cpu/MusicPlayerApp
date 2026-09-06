package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// Escolhe, entre as várias repetições "virtuais" de um artista na lista infinita,
// a que fica mais perto da posição atual de rolagem — evita saltos e nunca fica sem espaço.
private fun nearestVirtualIndexFor(targetName: String, artistNames: List<String>, currentIndex: Int): Int {
    val artistCount = artistNames.size
    if (artistCount == 0) return currentIndex
    val targetLocalIndex = artistNames.indexOf(targetName)
    if (targetLocalIndex < 0) return currentIndex
    val currentCycle = Math.floorDiv(currentIndex, artistCount)
    val candidates = listOf(
        (currentCycle - 1) * artistCount + targetLocalIndex,
        currentCycle * artistCount + targetLocalIndex,
        (currentCycle + 1) * artistCount + targetLocalIndex
    )
    return candidates.minByOrNull { kotlin.math.abs(it - currentIndex) } ?: candidates[1]
}

@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val allSongs = remember(artists) { buildFlatArtistSongs(artists) }
    val artistNames = remember(allSongs) { allSongs.map { it.artist }.distinct().sorted() }
    val artistCount = artistNames.size.coerceAtLeast(1)
    val virtualItemCount = artistCount * 10000

    val dragOffsets = remember(artists) { mutableStateMapOf<Long, Float>() }
    var activeSongId by remember(artists) { mutableStateOf<Long?>(null) }

    val initialVirtualIndex = remember(artistNames) {
        ((virtualItemCount / 2) / artistCount) * artistCount
    }
    val namesListState = rememberLazyListState(initialFirstVisibleItemIndex = initialVirtualIndex)
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
                    val activeDragOffsetPx = activeSongId?.let { dragOffsets[it] } ?: 0f
                    val revealedWidthDp = with(density) { activeDragOffsetPx.toDp() }
                    val highlightStartPadding = if (activeSongId != null) {
                        (itemWidth - revealedWidthDp).coerceAtLeast(0.dp)
                    } else {
                        0.dp
                    }

                    val maxDragOffsetPx = with(density) { (itemWidth * 0.8f).toPx() }
                    val activeArtistName = activeSongId?.let { id ->
                        allSongs.firstOrNull { it.id == id }?.artist
                    }

                    // Segue automaticamente a gaveta quando a música avança dentro do mesmo artista
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

                                val targetArtistIndex = nearestVirtualIndexFor(
                                    newSong.artist,
                                    artistNames,
                                    namesListState.firstVisibleItemIndex
                                )
                                namesListState.animateScrollToItem(targetArtistIndex)
                            }
                        }
                    }

                    // Lista de nomes de artistas (atrás) — rolagem infinita/circular
                    LazyColumn(
                        state = namesListState,
                        userScrollEnabled = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(virtualItemCount, key = { it }) { virtualIndex ->
                            val name = artistNames[virtualIndex % artistCount]
                            val isHighlighted = name == activeArtistName

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = highlightStartPadding)
                                    .alpha(if (isHighlighted) 1f else 0.4f)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isHighlighted) Color(0xFFB7F7C1) else TextWhite.copy(alpha = 0.55f),
                                    modifier = Modifier.size(14.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = name,
                                    color = if (isHighlighted) TextWhite else TextWhite.copy(alpha = 0.65f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Lista de tarjas (frente)
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
                                namesListState = namesListState,
                                ownListState = songListState,
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

                                        val targetArtistIndex = nearestVirtualIndexFor(
                                            song.artist,
                                            artistNames,
                                            namesListState.firstVisibleItemIndex
                                        )
                                        scope.launch {
                                            namesListState.animateScrollToItem(targetArtistIndex)
                                        }
                                    }
                                },
                                onDragStart = {
    if (activeSongId != song.id) {
        activeSongId?.let { previousId ->
            dragOffsets[previousId] = 0f
        }
        activeSongId = song.id

        val targetArtistIndex = nearestVirtualIndexFor(
            song.artist,
            artistNames,
            namesListState.firstVisibleItemIndex
        )
        val songOffsetInViewport = songListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == song.id }
            ?.offset ?: 0
        scope.launch {
            namesListState.animateScrollToItem(targetArtistIndex, songOffsetInViewport)
        }
    }
},
                                    
                               
                               
                        
                                      
                
