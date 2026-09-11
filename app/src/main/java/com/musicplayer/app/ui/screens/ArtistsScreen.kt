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
import com.musicplayer.app.ui.components.MiniPlayer
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

@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
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

    val upperListState = rememberLazyListState()
    val lowerListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isAnyTarjaOpen = activeSongId != null
    val songCount = allSongs.size
    val artistCount = artistNames.size
    val albumCount = artists.sumOf { it.albums.size }

    val currentSongId = currentSong?.id
    val playingArtistName = currentSong?.artist
    val playingAlbumName = activeAlbumName ?: currentSong?.album

    // ===== LISTAS =====
    // Quando tem álbum selecionado → lista de baixo = faixas do álbum
    // Quando não tem → lista de baixo = continuação da lista geral
    val (upperSongs, lowerSongs) = remember(allSongs, activeAlbumName, activeAlbumArtist) {
        if (activeAlbumName != null && activeAlbumArtist != null) {
            val albumSongs = artists
                .firstOrNull { it.name.equals(activeAlbumArtist, ignoreCase = true) }
                ?.albums
                ?.firstOrNull { it.name.equals(activeAlbumName, ignoreCase = true) }
                ?.songs
                ?.distinctBy { it.id }
                ?: allSongs.filter {
                    it.artist.equals(activeAlbumArtist, ignoreCase = true) &&
                    it.album.equals(activeAlbumName, ignoreCase = true)
                }

            val albumIds = albumSongs.map { it.id }.toSet()
            val remaining = allSongs.filter { it.id !in albumIds }

            // Lista de cima = músicas que não são do álbum
            // Lista de baixo = álbum reagrupado
            remaining to albumSongs
        } else {
            // Sem álbum: divide a lista no meio para as duas áreas
            val mid = allSongs.size / 2
            allSongs.take(mid) to allSongs.drop(mid)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ========== CABEÇALHO ==========
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
                // ===== LISTA DE CIMA =====
                LazyColumn(
                    state = upperListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(upperSongs, key = { it.id }) { song ->
                        SongItemRow(
                            song = song,
                            artistNames = artistNames,
                            albumNames = albumsByArtist[song.artist] ?: emptyList(),
                            playingArtistName = playingArtistName,
                            playingAlbumName = playingAlbumName,
                            isHighlighted = activeSongId == null || 
                                allSongs.firstOrNull { it.id == activeSongId }?.artist == song.artist,
                            dragOffset = dragOffsets[song.id] ?: 0f,
                            activeSongId = activeSongId,
                            currentSongId = currentSongId,
                            allSongs = allSongs,
                            onPlaySong = { s, list -> onSongClick(s, list) },
                            onRevealArtistClick = { artistName ->
                                val songsOfArtist = allSongs.filter { it.artist == artistName }
                                val firstSong = songsOfArtist.firstOrNull()
                                if (firstSong != null) {
                                    onSongClick(firstSong, songsOfArtist)
                                    activeAlbumName = null
                                    activeAlbumArtist = null
                                    activeSongId?.let { prev ->
                                        if (prev != firstSong.id) dragOffsets[prev] = 0f
                                    }
                                    activeSongId = firstSong.id
                                    dragOffsets[firstSong.id] = 0f // será atualizado no item
                                }
                            },
                            onSelectAlbum = { albumName, artistName ->
                                activeAlbumName = albumName
                                activeAlbumArtist = artistName
                                val songsOfAlbum = artists
                                    .firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                                    ?.albums
                                    ?.firstOrNull { it.name.equals(albumName, ignoreCase = true) }
                                    ?.songs
                                    ?: allSongs.filter {
                                        it.artist.equals(artistName, ignoreCase = true) &&
                                        it.album.equals(albumName, ignoreCase = true)
                                    }
                                val firstSong = songsOfAlbum.firstOrNull()
                                if (firstSong != null) {
                                    onSongClick(firstSong, songsOfAlbum)
                                }
                            },
                            onDragStart = { s ->
                                if (activeSongId != s.id) {
                                    activeSongId?.let { dragOffsets[it] = 0f }
                                    activeSongId = s.id
                                    activeAlbumName = null
                                    activeAlbumArtist = null
                                }
                            },
                            onDrag = { id, offset -> dragOffsets[id] = offset },
                            onDragEnd = { id, expanded, target ->
                                if (expanded) {
                                    dragOffsets[id] = target
                                } else {
                                    dragOffsets[id] = 0f
                                    if (activeSongId == id) {
                                        activeSongId = null
                                        activeAlbumName = null
                                        activeAlbumArtist = null
                                    }
                                }
                            }
                        )
                    }
                }

                // ===== MINI PLAYER =====
                if (currentSong != null) {
                    MiniPlayer(
                        song = currentSong,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeek = onSeek,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // ===== LISTA DE BAIXO =====
                LazyColumn(
                    state = lowerListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    items(lowerSongs, key = { it.id }) { song ->
                        SongItemRow(
                            song = song,
                            artistNames = artistNames,
                            albumNames = albumsByArtist[song.artist] ?: emptyList(),
                            playingArtistName = playingArtistName,
                            playingAlbumName = playingAlbumName,
                            isHighlighted = activeSongId == null || 
                                allSongs.firstOrNull { it.id == activeSongId }?.artist == song.artist,
                            dragOffset = dragOffsets[song.id] ?: 0f,
                            activeSongId = activeSongId,
                            currentSongId = currentSongId,
                            allSongs = allSongs,
                            onPlaySong = { s, list -> onSongClick(s, list) },
                            onRevealArtistClick = { artistName ->
                                val songsOfArtist = allSongs.filter { it.artist == artistName }
                                val firstSong = songsOfArtist.firstOrNull()
                                if (firstSong != null) {
                                    onSongClick(firstSong, songsOfArtist)
                                    activeAlbumName = null
                                    activeAlbumArtist = null
                                    activeSongId?.let { prev ->
                                        if (prev != firstSong.id) dragOffsets[prev] = 0f
                                    }
                                    activeSongId = firstSong.id
                                }
                            },
                            onSelectAlbum = { albumName, artistName ->
                                activeAlbumName = albumName
                                activeAlbumArtist = artistName
                                val songsOfAlbum = artists
                                    .firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                                    ?.albums
                                    ?.firstOrNull { it.name.equals(albumName, ignoreCase = true) }
                                    ?.songs
                                    ?: allSongs.filter {
                                        it.artist.equals(artistName, ignoreCase = true) &&
                                        it.album.equals(albumName, ignoreCase = true)
                                    }
                                val firstSong = songsOfAlbum.firstOrNull()
                                if (firstSong != null) {
                                    onSongClick(firstSong, songsOfAlbum)
                                }
                            },
                            onDragStart = { s ->
                                if (activeSongId != s.id) {
                                    activeSongId?.let { dragOffsets[it] = 0f }
                                    activeSongId = s.id
                                    activeAlbumName = null
                                    activeAlbumArtist = null
                                }
                            },
                            onDrag = { id, offset -> dragOffsets[id] = offset },
                            onDragEnd = { id, expanded, target ->
                                if (expanded) {
                                    dragOffsets[id] = target
                                } else {
                                    dragOffsets[id] = 0f
                                    if (activeSongId == id) {
                                        activeSongId = null
                                        activeAlbumName = null
                                        activeAlbumArtist = null
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

@Composable
private fun SongItemRow(
    song: Song,
    artistNames: List<String>,
    albumNames: List<String>,
    playingArtistName: String?,
    playingAlbumName: String?,
    isHighlighted: Boolean,
    dragOffset: Float,
    activeSongId: Long?,
    currentSongId: Long?,
    allSongs: List<Song>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onRevealArtistClick: (String) -> Unit,
    onSelectAlbum: (String, String) -> Unit,
    onDragStart: (Song) -> Unit,
    onDrag: (Long, Float) -> Unit,
    onDragEnd: (Long, Boolean, Float) -> Unit
) {
    val density = LocalDensity.current
    // maxDragOffset aproximado (o item real calcula o seu)
    val maxDragOffsetPx = with(density) { (300.dp * 0.8f).toPx() }

    ExpandableSongItem(
        song = song,
        artistNames = artistNames,
        albumNames = albumNames,
        playingArtistName = playingArtistName,
        playingAlbumName = playingAlbumName,
        isHighlighted = isHighlighted,
        onPlaySong = {
            if (activeSongId != null && isHighlighted) {
                val songsOfArtist = allSongs.filter { it.artist == song.artist }
                onPlaySong(song, songsOfArtist)
            } else {
                onPlaySong(song, allSongs)
            }
        },
        onRevealArtistClick = onRevealArtistClick,
        onSelectAlbum = { albumName ->
            onSelectAlbum(albumName, song.artist)
        },
        dragOffsetX = dragOffset,
        onDragStart = { onDragStart(song) },
        onDrag = { onDrag(song.id, it) },
        onDragEnd = { expanded, target ->
            onDragEnd(song.id, expanded, target)
        }
    )
}
