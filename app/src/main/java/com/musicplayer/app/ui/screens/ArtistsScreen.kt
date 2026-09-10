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

    // ========== TIMEOUT DE 5 MINUTOS ==========
    LaunchedEffect(activeSongId, currentSongId) {
        val activeId = activeSongId
        val playingId = currentSongId

        if (activeId == null || playingId == null) return@LaunchedEffect

        val activeSong = allSongs.firstOrNull { it.id == activeId }
        val playingSong = allSongs.firstOrNull { it.id == playingId }

        if (activeSong != null && playingSong != null && activeSong.artist != playingSong.artist) {
            delay(5 * 60 * 1000L) // 5 minutos

            if (activeSongId == activeId) {
                dragOffsets[activeId] = 0f

                activeSongId = playingId
                dragOffsets[playingId] = with(LocalDensity.current) { 
                    // será recalculado no BoxWithConstraints, mas precisamos do valor
                    0f // placeholder - o valor real é setado abaixo
                }

                val songsOfPlayingArtist = allSongs.filter { it.artist == playingSong.artist }
                onSongClick(playingSong, songsOfPlayingArtist)

                val targetIndex = allSongs.indexOfFirst { it.id == playingId }
                if (targetIndex >= 0) {
                    songListState.animateScrollToItem(targetIndex)
                }
            }
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

                    // Segue a tarja quando a música avança no mesmo artista
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

                    // Atualiza o offset da tarja do timeout (quando o timeout dispara)
                    LaunchedEffect(activeSongId) {
                        activeSongId?.let { id ->
                            if (dragOffsets[id] == 0f) {
                                dragOffsets[id] = maxDragOffsetPx
                            }
                        }
                    }

                    LazyColumn(
                        state = songListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        items(allSongs, key = { it.id }) {
