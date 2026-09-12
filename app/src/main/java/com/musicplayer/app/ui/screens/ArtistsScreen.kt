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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun buildFlatArtistSongs(artists: List<Artist>): List<Song> =
    artists
        .flatMap { artist -> artist.albums.flatMap { album -> album.songs } }
        .distinctBy { it.id }
        .sortedBy { it.title }

/**
 * Reagrupa a lista colocando o bloco do álbum
 * em uma posição que tende a ficar abaixo do Mini Player.
 */
private fun rebuildListForAlbum(
    allSongs: List<Song>,
    artists: List<Artist>,
    artistName: String,
    albumName: String
): List<Song> {
    val albumSongs = artists
        .firstOrNull { it.name.equals(artistName, ignoreCase = true) }
        ?.albums
        ?.firstOrNull { it.name.equals(albumName, ignoreCase = true) }
        ?.songs
        ?.distinctBy { it.id }
        ?: allSongs.filter {
            it.artist.equals(artistName, ignoreCase = true) &&
            it.album.equals(albumName, ignoreCase = true)
        }

    if (albumSongs.isEmpty()) return allSongs

    val albumIds = albumSongs.map { it.id }.toSet()
    val remaining = allSongs.filter { it.id !in albumIds }

    // Coloca uma quantidade de músicas antes para empurrar o bloco do álbum
    // para a região mais baixa da tela (abaixo do Mini Player)
    val preCount = (remaining.size * 0.40f).toInt().coerceAtLeast(3)
    val before = remaining.take(preCount)
    val after = remaining.drop(preCount)

    return before + albumSongs + after
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

    val albumsByArtist = remember(artists) {
        artists.associate { artist ->
            artist.name to artist.albums.map { it.name }.distinct().sorted()
        }
    }

    val dragOffsets = remember(artists) { mutableStateMapOf<Long, Float>() }
    var activeSongId by remember(artists) { mutableStateOf<Long?>(null) }
    var activeAlbumName by remember { mutableStateOf<String?>(null) }
    var activeAlbumArtist by remember { mutableStateOf<String?>(null) }

    var displaySongs by remember(artists) { mutableStateOf(allSongs) }

    val songListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isAnyTarjaOpen = activeSongId != null
    val songCount = allSongs.size
    val artistCount = artistNames.size
    val albumCount = artists.sumOf { it.albums.size }

    val currentSong = allSongs.firstOrNull { it.id == currentSongId }
    val playingArtistName = currentSong?.artist
    val playingAlbumName = activeAlbumName ?: currentSong?.album

    LaunchedEffect(allSongs) {
        if (activeAlbumName == null) {
            displaySongs = allSongs
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Cabeçalho
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Músicas($songCount)",
                color = if (!isAnyTarjaOpen) TextWhite else TextWhite.copy(alpha = 0.45f),
                fontSize = if (!isAnyTarjaOpen) 16.sp else 14.sp,
                fontWeight = if (!isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Artistas($artistCount)",
                color = if (isAnyTarjaOpen) TextWhite else TextWhite.copy(alpha = 0.45f),
                fontSize = if (isAnyTarjaOpen) 16.sp else 14.sp,
                fontWeight = if (isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Álbuns($albumCount)",
                color = TextWhite.copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
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

                        // Timeout 2 min
                        LaunchedEffect(activeSongId, currentSongId, maxDragOffsetPx) {
                            val activeId = activeSongId
                            val playingId = currentSongId
                            if (activeId == null || playingId == null) return@LaunchedEffect

                            val activeSong = allSongs.firstOrNull { it.id == activeId }
                            val playingSong = allSongs.firstOrNull { it.id == playingId }

                            if (activeSong != null && playingSong != null &&
                                activeSong.artist != playingSong.artist
                            ) {
                                delay(2 * 60 * 1000L)
                                if (activeSongId == activeId) {
                                    dragOffsets[activeId] = 0f
                                    activeSongId = playingId
                                    dragOffsets[playingId] = maxDragOffsetPx
                                    activeAlbumName = null
                                    activeAlbumArtist = null
                                    displaySongs = allSongs

                                    val songsOfPlayingArtist =
                                        allSongs.filter { it.artist == playingSong.artist }
                                    onSongClick(playingSong, songsOfPlayingArtist)
                                }
                            }
                        }

                        // Segue tarja no mesmo artista (sem rolar)
                        LaunchedEffect(currentSongId, maxDragOffsetPx) {
                            val activeId = activeSongId
                            if (activeId != null && currentSongId != null && currentSongId != activeId) {
                                val activeSong = allSongs.firstOrNull { it.id == activeId }
                                val newSong = allSongs.firstOrNull { it.id == currentSongId }
                                if (activeSong != null && newSong != null &&
                                    newSong.artist == activeSong.artist
                                ) {
                                    dragOffsets[activeId] = 0f
                                    activeSongId = currentSongId
                                    dragOffsets[currentSongId] = maxDragOffsetPx
                                }
                            }
                        }

                        LazyColumn(
                            state = songListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(bottom = 280.dp)
                        ) {
                            items(
                                items = displaySongs,
                                key = { it.id }
                            ) { song ->
                                val dragOffset = dragOffsets[song.id] ?: 0f
                                val isHighlighted =
                                    activeArtistName == null || song.artist == activeArtistName
                                val songAlbumNames = albumsByArtist[song.artist] ?: emptyList()

                                // animateItem faz o reagrupamento ter animação de movimento
                                Box(
                                    modifier = Modifier.animateItem()
                                ) {
                                    ExpandableSongItem(
                                        song = song,
                                        artistNames = artistNames,
                                        albumNames = songAlbumNames,
                                        playingArtistName = playingArtistName,
                                        playingAlbumName = playingAlbumName,
                                        isHighlighted = isHighlighted,
                                        onPlaySong = {
                                            when {
                                                activeAlbumName != null && isHighlighted -> {
                                                    val songsOfAlbum = allSongs.filter {
                                                        it.artist.equals(song.artist, ignoreCase = true) &&
                                                        it.album.equals(activeAlbumName, ignoreCase = true)
                                                    }
                                                    onSongClick(song, songsOfAlbum)
                                                }
                                                activeSongId != null && isHighlighted -> {
                                                    val songsOfArtist =
                                                        allSongs.filter { it.artist == song.artist }
                                                    onSongClick(song, songsOfArtist)
                                                }
                                                else -> onSongClick(song, allSongs)
                                            }
                                        },
                                        onRevealArtistClick = { artistName ->
                                            val songsOfArtist =
                                                allSongs.filter { it.artist == artistName }
                                            val firstSong = songsOfArtist.firstOrNull()
                                            if (firstSong != null) {
                                                onSongClick(firstSong, songsOfArtist)
                                                activeAlbumName = null
                                                activeAlbumArtist = null
                                                displaySongs = allSongs

                                                activeSongId?.let { prev ->
                                                    if (prev != firstSong.id) dragOffsets[prev] = 0f
                                                }
                                                activeSongId = firstSong.id
                                                dragOffsets[firstSong.id] = maxDragOffsetPx
                                            }
                                        },
                                        onSelectAlbum = { albumName ->
                                            activeAlbumName = albumName
                                            activeAlbumArtist = song.artist

                                            // Reagrupa com animação
                                            displaySongs = rebuildListForAlbum(
                                                allSongs = allSongs,
                                                artists = artists,
                                                artistName = song.artist,
                                                albumName = albumName
                                            )

                                            val songsOfAlbum = artists
                                                .firstOrNull { it.name.equals(song.artist, ignoreCase = true) }
                                                ?.albums
                                                ?.firstOrNull { it.name.equals(albumName, ignoreCase = true) }
                                                ?.songs
                                                ?.distinctBy { it.id }
                                                ?: allSongs.filter {
                                                    it.artist.equals(song.artist, ignoreCase = true) &&
                                                    it.album.equals(albumName, ignoreCase = true)
                                                }

                                            val firstSong = songsOfAlbum.firstOrNull() ?: song
                                            onSongClick(firstSong, songsOfAlbum)
                                        },
                                        dragOffsetX = dragOffset,
                                        onDragStart = {
                                            if (activeSongId != song.id) {
                                                activeSongId?.let { dragOffsets[it] = 0f }
                                                activeSongId = song.id
                                                dragOffsets[song.id] = maxDragOffsetPx
                                                activeAlbumName = null
                                                activeAlbumArtist = null
                                                displaySongs = allSongs

                                                if (song.id == currentSongId) {
                                                    val songsOfArtist =
                                                        allSongs.filter { it.artist == song.artist }
                                                    onSongClick(song, songsOfArtist)
                                                }
                                            }
                                        },
                                        onDrag = { dragOffsets[song.id] = it },
                                        onDragEnd = { expanded, target ->
                                            if (expanded) {
                                                dragOffsets[song.id] = target
                                            } else {
                                                dragOffsets[song.id] = 0f
                                                if (activeSongId == song.id) {
                                                    activeSongId = null
                                                    activeAlbumName = null
                                                    activeAlbumArtist = null
                                                    displaySongs = allSongs
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Escotilha
                        val layoutInfo = songListState.layoutInfo
                        val totalItems = displaySongs.size
                        if (totalItems > 3) {
                            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                            val approxItemHeight = 98f
                            val totalContentHeight = totalItems * approxItemHeight
                            val scrollPixels =
                                songListState.firstVisibleItemIndex * approxItemHeight +
                                        songListState.firstVisibleItemScrollOffset
                            val progress = (scrollPixels / totalContentHeight).coerceIn(0f, 1f)
                            val thumbHeight =
                                (viewportHeight / totalContentHeight * viewportHeight)
                                    .coerceIn(48f, 140f)
                            val thumbOffset = progress * (viewportHeight - thumbHeight)

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(20.dp)
                                    .padding(end = 6.dp, top = 12.dp, bottom = 290.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .align(Alignment.Center)
                                        .background(TextWhite.copy(alpha = 0.12f), RoundedCornerShape(50))
                                )
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
}
