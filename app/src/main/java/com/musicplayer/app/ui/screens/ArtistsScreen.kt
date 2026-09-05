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
import com.musicplayer.app.ui.theme.ArtistCard
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite
import kotlinx.coroutines.launch

private fun buildFlatArtistSongs(artists: List<Artist>): List<Song> =
    artists
        .flatMap { artist -> artist.albums.flatMap { album -> album.songs } }
        .distinctBy { it.id }
        .sortedBy { it.title }

private fun artistSongList(artist: Artist): List<Song> =
    artist.albums
        .flatMap { it.songs }
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
    val artistNames = remember(artists) { artists.map { it.name }.distinct().sorted() }

    val dragOffsets = remember(artists) { mutableStateMapOf<Long, Float>() }
    var activeSongId by remember(artists) { mutableStateOf<Long?>(null) }

    val namesListState = rememberLazyListState()
    val songListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(songListState) {
        snapshotFlow { songListState.firstVisibleItemIndex to songListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (activeSongId == null &&
                    (namesListState.firstVisibleItemIndex != index || namesListState.firstVisibleItemScrollOffset != offset)
                ) {
                    namesListState.scrollToItem(index, offset)
                }
            }
    }

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

                    val fillerItemHeight = 90.dp
                    val fillerItemSpacing = 0.dp
                    val reservedBottomSpace = 140.dp
                    val fillerCount = kotlin.math.ceil(
                        reservedBottomSpace / (fillerItemHeight + fillerItemSpacing)
                    ).toInt().coerceAtLeast(1)

                    val maxDragOffsetPx = with(density) { (itemWidth * 0.8f).toPx() }

                    val activeArtistName = activeSongId?.let { id -> allSongs.firstOrNull { it.id == id }?.artist }

                    LazyColumn(
                        state = namesListState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(artistNames, key = { it }) { name ->
                            val isHighlighted = name == activeArtistName

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = highlightStartPadding)
                                    .alpha(if (isHighlighted) 1f else 0.3f)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isHighlighted) {
                                        Color(0xFFB7F7C1)
                                    } else {
                                        TextWhite.copy(alpha = 0.55f)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = name,
                                    color = if (isHighlighted) TextWhite else TextWhite.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state = songListState,
                        userScrollEnabled = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = 0.dp,
                            end = 0.dp,
                            bottom = 0.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
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
                                    onSongClick(song, allSongs)
                                },
                                onRevealArtistClick = { artistName ->
                                    val artist = artists.firstOrNull { it.name == artistName }
                                    val firstSong = artist?.let { artistSongList(it).firstOrNull() }
                                    if (artist != null && firstSong != null) {
                                        onSongClick(firstSong, artistSongList(artist))
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

                        items(fillerCount) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(fillerItemHeight)
                                    .background(ArtistCard)
                            )
                        }
                    }
                }
            }
        }
    }
}
