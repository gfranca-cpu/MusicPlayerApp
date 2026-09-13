
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.components.ExpandableSongItem
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TabMusicColor = Color(0xFF2E86F5)
private val TabArtistColor = Color(0xFF6C3CE9)
private val TabAlbumColor = Color(0xFF00A884)

private class RibbonTabShape(private val skewFraction: Float = 0.22f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val skew = size.width * skewFraction
        val path = Path().apply {
            moveTo(skew, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - skew, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

private const val ARMED_TARJA_TIMEOUT_MS = 60 * 1000L

private fun buildFlatArtistSongs(artists: List<Artist>): List<Song> =
    artists
        .flatMap { artist -> artist.albums.flatMap { album -> album.songs } }
        .distinctBy { it.id }
        .sortedBy { it.title }

private fun songsOfAlbumInOrder(artists: List<Artist>, artistName: String, albumName: String): List<Song> =
    artists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
        ?.albums?.firstOrNull { it.name.equals(albumName, ignoreCase = true) }
        ?.songs ?: emptyList()

@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit,
    onRescopeQueue: (List<Song>) -> Unit = {}
) {
    val allSongs = remember(artists) { buildFlatArtistSongs(artists) }
    val artistNames = remember(allSongs) { allSongs.map { it.artist }.distinct().sorted() }

    val dragOffsets = remember(artists) { mutableStateMapOf<Long, Float>() }
    var activeSongId by remember(artists) { mutableStateOf<Long?>(null) }
    var armedAlbumName by remember(artists) { mutableStateOf<String?>(null) }

    var expandedAlbumFirstSongId by remember(artists) { mutableStateOf<Long?>(null) }
    var expandedAlbumExtraTracks by remember(artists) { mutableStateOf<List<Song>>(emptyList()) }
    var expandedAlbumFullQueue by remember(artists) { mutableStateOf<List<Song>>(emptyList()) }

    val songListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isAnyTarjaOpen = activeSongId != null
    val songCount = allSongs.size
    val artistCount = artistNames.size
    val albumCount = artists.sumOf { it.albums.size }

    val playingSong = remember(currentSongId, allSongs) {
        allSongs.firstOrNull { it.id == currentSongId }
    }
    val playingArtistName = playingSong?.artist
    val playingAlbumName = playingSong?.album

    // ===== Fração de encolhimento progressivo =====
    // 0f = totalmente expandido | 1f = totalmente recolhido
    val extraTracksCollapseFraction by remember(
        expandedAlbumFirstSongId,
        songListState.firstVisibleItemIndex,
        songListState.firstVisibleItemScrollOffset
    ) {
        derivedStateOf {
            val expandedId = expandedAlbumFirstSongId ?: return@derivedStateOf 0f
            val idx = allSongs.indexOfFirst { it.id == expandedId }
            if (idx < 0) return@derivedStateOf 1f

            val firstVisible = songListState.firstVisibleItemIndex
            val scrollOffset = songListState.firstVisibleItemScrollOffset

            when {
                // Ainda não chegou na música expandida → totalmente aberta
                firstVisible < idx -> 0f
                // Já passou da música expandida → totalmente fechada
                firstVisible > idx -> 1f
                // Está exatamente na música expandida → encolhe conforme o scroll
                else -> {
                    val rowHeightApprox = 90f
                    (scrollOffset / rowHeightApprox).coerceIn(0f, 1f)
                }
            }
        }
    }

    // Quando encolhe totalmente, limpa o estado
    LaunchedEffect(extraTracksCollapseFraction) {
        if (extraTracksCollapseFraction >= 0.98f && expandedAlbumFirstSongId != null) {
            expandedAlbumFirstSongId = null
            expandedAlbumExtraTracks = emptyList()
            expandedAlbumFullQueue = emptyList()
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
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy((-16).dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .zIndex(if (!isAnyTarjaOpen) 2f else 0f)
                    .clip(RibbonTabShape())
                    .background(TabMusicColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Músicas($songCount)",
                        color = if (!isAnyTarjaOpen) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = if (!isAnyTarjaOpen) 15.sp else 13.sp,
                        fontWeight = if (!isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                    if (playingSong != null) {
                        Text(
                            text = playingSong.title,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .zIndex(if (isAnyTarjaOpen) 2f else 0f)
                    .clip(RibbonTabShape())
                    .background(TabArtistColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Artistas($artistCount)",
                        color = if (isAnyTarjaOpen) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = if (isAnyTarjaOpen) 15.sp else 13.sp,
                        fontWeight = if (isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                    if (playingArtistName != null) {
                        Text(
                            text = playingArtistName,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RibbonTabShape())
                    .background(TabAlbumColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Álbuns($albumCount)",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (playingAlbumName != null) {
                        Text(
                            text = playingAlbumName,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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

                    val currentSongIdState = rememberUpdatedState(currentSongId)
                    val isBrowsing = activeSongId != null && activeSongId != currentSongIdState.value

                    // Timeout de navegação (60s)
                    LaunchedEffect(activeSongId) {
                        if (isBrowsing) {
                            delay(ARMED_TARJA_TIMEOUT_MS)
                            val realId = currentSongIdState.value
                            val previousActiveId = activeSongId
                            val previousOffsetPx = previousActiveId?.let { prevId ->
                                val prevIndex = allSongs.indexOfFirst { it.id == prevId }
                                songListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == prevIndex }
                                    ?.offset
                            } ?: 0

                            activeSongId?.let { current -> dragOffsets[current] = 0f }
                            armedAlbumName = null
                            expandedAlbumFirstSongId = null
                            expandedAlbumExtraTracks = emptyList()
                            expandedAlbumFullQueue = emptyList()

                            if (realId != null) {
                                activeSongId = realId
                                dragOffsets[realId] = maxDragOffsetPx
                                val targetIndex = allSongs.indexOfFirst { it.id == realId }
                                if (targetIndex >= 0) {
                                    songListState.scrollToItem(targetIndex, 0)
                                    if (previousOffsetPx != 0) {
                                        songListState.scrollBy(-previousOffsetPx.toFloat())
                                    }
                                }
                            } else {
                                activeSongId = null
                            }
                        }
                    }

                    // Segue a tarja no mesmo artista
                    LaunchedEffect(currentSongId) {
                        val activeId = activeSongId
                        if (activeId != null && currentSongId != null && currentSongId != activeId) {
                            val activeSong = allSongs.firstOrNull { it.id == activeId }
                            val newSong = allSongs.firstOrNull { it.id == currentSongId }
                            if (activeSong != null && newSong != null && newSong.artist == activeSong.artist) {
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 280.dp)
                    ) {
                        items(allSongs, key = { it.id }) { song ->
                            val dragOffset = dragOffsets[song.id] ?: 0f
                            val isHighlighted = activeArtistName == null || song.artist == activeArtistName
                            val albumNamesOfArtist = remember(song.artist, allSongs) {
                                allSongs.filter { it.artist == song.artist }
                                    .map { it.album }
                                    .distinct()
                                    .sorted()
                            }

                            val isAlbumExpansionHost = song.id == expandedAlbumFirstSongId
                            val extraTracksForRow =
                                if (isAlbumExpansionHost) expandedAlbumExtraTracks else emptyList()
                            val collapseFraction =
                                if (isAlbumExpansionHost) extraTracksCollapseFraction else 0f

                            ExpandableSongItem(
                                song = song,
                                artistNames = artistNames,
                                albumNames = albumNamesOfArtist,
                                playingArtistName = playingArtistName,
                                playingAlbumName = playingAlbumName,
                                extraAlbumTracks = extraTracksForRow,
                                extraTracksCollapseFraction = collapseFraction,
                                onPlayExtraTrack = { track ->
                                    onSongClick(track, expandedAlbumFullQueue)
                                },
                                isHighlighted = isHighlighted,
                                onPlaySong = {
                                    when {
                                        activeSongId != null && isHighlighted && armedAlbumName != null -> {
                                            val songsOfAlbum = songsOfAlbumInOrder(
                                                artists, song.artist, armedAlbumName!!
                                            )
                                            val startSong = songsOfAlbum.firstOrNull { it.id == song.id }
                                                ?: songsOfAlbum.firstOrNull()
                                            if (startSong != null) onSongClick(startSong, songsOfAlbum)
                                        }
                                        activeSongId != null && isHighlighted -> {
                                            val songsOfArtist = allSongs.filter { it.artist == song.artist }
                                            onSongClick(song, songsOfArtist)
                                        }
                                        else -> onSongClick(song, allSongs)
                                    }
                                },
                                onSelectAlbum = { albumName ->
                                    val songsOfAlbum = songsOfAlbumInOrder(artists, song.artist, albumName)
                                    val firstOfAlbum = songsOfAlbum.firstOrNull()
                                    if (firstOfAlbum != null) {
                                        activeSongId?.let { prev ->
                                            if (prev != firstOfAlbum.id) dragOffsets[prev] = 0f
                                        }
                                        activeSongId = firstOfAlbum.id
                                        dragOffsets[firstOfAlbum.id] = maxDragOffsetPx
                                        armedAlbumName = albumName

                                        // Ativa expansão
                                        expandedAlbumFirstSongId = firstOfAlbum.id
                                        expandedAlbumExtraTracks = songsOfAlbum.drop(1)
                                        expandedAlbumFullQueue = songsOfAlbum

                                        onSongClick(firstOfAlbum, songsOfAlbum)

                                        val targetIndex = allSongs.indexOfFirst { it.id == firstOfAlbum.id }
                                        if (targetIndex >= 0) {
                                            scope.launch {
                                                // Posiciona a primeira faixa logo acima do Mini Player
                                                // para a expansão crescer apenas para baixo
                                                songListState.scrollToItem(targetIndex, 0)

                                                val viewportHeightPx =
                                                    songListState.layoutInfo.viewportSize.height.toFloat()
                                                val rowHeightPx = with(density) { 90.dp.toPx() }
                                                val miniPlayerHeightPx = with(density) { 96.dp.toPx() }
                                                val miniPlayerGapPx = with(density) { 225.dp.toPx() }

                                                // topo desejado da primeira faixa =
                                                // altura da tela - vão inferior - altura do player - altura da faixa
                                                val targetTopY = viewportHeightPx - miniPlayerGapPx -
                                                        miniPlayerHeightPx - rowHeightPx

                                                songListState.scrollBy(-targetTopY)
                                            }
                                        }
                                    }
                                },
                                onRevealArtistClick = { artistName ->
                                    val songsOfArtistPicked = allSongs.filter { it.artist == artistName }
                                    val firstOfArtist = songsOfArtistPicked.firstOrNull()
                                    if (firstOfArtist != null) {
                                        val previousActiveId = activeSongId
                                        val previousOffsetPx = previousActiveId?.let { prevId ->
                                            val prevIndex = allSongs.indexOfFirst { it.id == prevId }
                                            songListState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == prevIndex }
                                                ?.offset
                                        } ?: 0

                                        activeSongId?.let { prev ->
                                            if (prev != firstOfArtist.id) dragOffsets[prev] = 0f
                                        }
                                        activeSongId = firstOfArtist.id
                                        dragOffsets[firstOfArtist.id] = maxDragOffsetPx
                                        armedAlbumName = null
                                        expandedAlbumFirstSongId = null
                                        expandedAlbumExtraTracks = emptyList()
                                        expandedAlbumFullQueue = emptyList()

                                        onSongClick(firstOfArtist, songsOfArtistPicked)

                                        val targetIndex = allSongs.indexOfFirst { it.id == firstOfArtist.id }
                                        if (targetIndex >= 0) {
                                            scope.launch {
                                                songListState.scrollToItem(targetIndex, 0)
                                                if (previousOffsetPx != 0) {
                                                    songListState.scrollBy(-previousOffsetPx.toFloat())
                                                }
                                            }
                                        }
                                    }
                                },
                                dragOffsetX = dragOffset,
                                onDragStart = {
                                    if (activeSongId != song.id) {
                                        activeSongId?.let { dragOffsets[it] = 0f }
                                        activeSongId = song.id
                                        armedAlbumName = null
                                        expandedAlbumFirstSongId = null
                                        expandedAlbumExtraTracks = emptyList()
                                        expandedAlbumFullQueue = emptyList()

                                        if (song.id == currentSongId) {
                                            val songsOfArtist = allSongs.filter { it.artist == song.artist }
                                            onRescopeQueue(songsOfArtist)
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
                                            armedAlbumName = null
                                            expandedAlbumFirstSongId = null
                                            expandedAlbumExtraTracks = emptyList()
                                            expandedAlbumFullQueue = emptyList()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Escotilha
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
                                .padding(end = 6.dp, top = 12.dp, bottom = 240.dp)
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
