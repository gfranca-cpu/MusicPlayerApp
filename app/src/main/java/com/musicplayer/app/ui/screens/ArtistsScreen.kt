
package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.components.ExpandableArtistItem
import com.musicplayer.app.ui.theme.ArtistCard
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite

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
    val dragOffsets = remember(artists) { mutableStateMapOf<String, Float>() }
    var activeArtist by remember(artists) { mutableStateOf<String?>(null) }
    val backgroundListState = rememberLazyListState()
    val artistListState = rememberLazyListState()

    LaunchedEffect(artistListState) {
        snapshotFlow { artistListState.firstVisibleItemIndex to artistListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (activeArtist == null &&
                    (backgroundListState.firstVisibleItemIndex != index || backgroundListState.firstVisibleItemScrollOffset != offset)
                ) {
                    backgroundListState.scrollToItem(index, offset)
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
                    val activeDragOffsetPx = activeArtist?.let { dragOffsets[it] } ?: 0f
                    val revealedWidthDp = with(density) { activeDragOffsetPx.toDp() }
                    val highlightStartPadding = if (activeArtist != null) {
                        (itemWidth - revealedWidthDp).coerceAtLeast(0.dp)
                    } else {
                        0.dp
                    }

                    val fillerItemHeight = 90.dp
                    val fillerItemSpacing = 3.dp
                    val reservedBottomSpace = 140.dp
                    val fillerCount = kotlin.math.ceil(
                        reservedBottomSpace / (fillerItemHeight + fillerItemSpacing)
                    ).toInt().coerceAtLeast(1)

                    LazyColumn(
                        state = backgroundListState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allSongs, key = { it.id }) { song ->
                            val isHighlightedSong = song.artist == activeArtist
                            val enabled = isHighlightedSong && activeArtist != null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = highlightStartPadding)
                                    .alpha(if (isHighlightedSong) 1f else 0.3f)
                                    .clickable(enabled = enabled) {
                                        if (enabled) {
                                            val artistSongs = artists
                                                .firstOrNull { it.name == activeArtist }
                                                ?.let { artistSongList(it) }
                                                .orEmpty()
                                            onSongClick(song, artistSongs)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isHighlightedSong) {
                                        Color(0xFFB7F7C1)
                                    } else {
                                        TextWhite.copy(alpha = 0.55f)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = song.title,
                                    color = if (isHighlightedSong) TextWhite else TextWhite.copy(alpha = 0.6f),
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
                        state = artistListState,
                        userScrollEnabled = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = 0.dp,
                            end = 0.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(artists, key = { it.name }) { artist ->
                            val dragOffset = dragOffsets[artist.name] ?: 0f
                            ExpandableArtistItem(
                                artist = artist,
                                allSongs = allSongs,
                                currentSongId = currentSongId,
                                onSongClick = onSongClick,
                                listState = backgroundListState,
                                artistListState = artistListState,
                                activeArtist = activeArtist,
                                onRevealSongClick = { song ->
                                    if (activeArtist == artist.name) {
                                        val artistSongs = artistSongList(artist)
                                        onSongClick(song, artistSongs)
                                    }
                                },
                                dragOffsetX = dragOffset,
                                onDragStart = {
                                    if (activeArtist != artist.name) {
                                        activeArtist?.let { previousArtist ->
                                            dragOffsets[previousArtist] = 0f
                                        }
                                        activeArtist = artist.name
                                    }
                                },
                                onDrag = { nextValue ->
                                    dragOffsets[artist.name] = nextValue
                                },
                                onDragEnd = { expanded, targetOffsetPx ->
                                    if (expanded) {
                                        dragOffsets[artist.name] = targetOffsetPx
                                    } else {
                                        dragOffsets[artist.name] = 0f
                                        if (activeArtist == artist.name) {
                                            activeArtist = null
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
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(ArtistCard)
                            )
                        }
                    }
                }
            }
        }
    }
}
