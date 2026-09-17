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
    val albumDragOffsets = remember(artists) { mutableStateMapOf<Long, Float>() }

    var activeSongId by remember(artists) { mutableStateOf<Long?>(null) }
    var armedAlbumName by remember(artists) { mutableStateOf<String?>(null) }

    var expandedAlbumFirstSongId by remember(artists) { mutableStateOf<Long?>(null) }
    var expandedAlbumExtraTracks by remember(artists) { mutableStateOf<List<Song>>(emptyList()) }
    var expandedAlbumFullQueue by remember(artists) { mutableStateOf<List<Song>>(emptyList()) }
    // Espelho base (vão tarja → Mini Player), congelado na abertura
    var frozenMirrorTracks by remember(artists) { mutableStateOf<List<Song>>(emptyList()) }
    // Faixas extras da lista geral que o espelho "puxa" ao alongar no scroll ↑
    var mirrorPullTracks by remember(artists) { mutableStateOf<List<Song>>(emptyList()) }

    val songListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isAnyTarjaOpen = activeSongId != null
    val isAlbumMode = armedAlbumName != null
    val songCount = allSongs.size
    val artistCount = artistNames.size
    val albumCountTotal = artists.sumOf { it.albums.size }

    val playingSong = remember(currentSongId, allSongs) {
        allSongs.firstOrNull { it.id == currentSongId }
    }
    val playingArtistName = playingSong?.artist
    val playingAlbumName = playingSong?.album

    /**
     * Fração única (corrente oposta):
     * - scroll ↑ com álbum aberto → espelho infla / álbum recolhe sob Mini Player
     * - prioridade: zona ~3 faixas abaixo do Mini Player
     * - lista geral continua rolando (nada fica parado)
     */
    val mirrorInflateFraction by remember(
        expandedAlbumFirstSongId,
        frozenMirrorTracks.size,
        songListState.firstVisibleItemIndex,
        songListState.firstVisibleItemScrollOffset
    ) {
        derivedStateOf {
            val expandedId = expandedAlbumFirstSongId ?: return@derivedStateOf 0f
            val idx = allSongs.indexOfFirst { it.id == expandedId }
            if (idx < 0) return@derivedStateOf 1f

            val firstVisible = songListState.firstVisibleItemIndex
            val scrollOffset = songListState.firstVisibleItemScrollOffset.toFloat()
            val rowH = 94f
            // Distância de alongamento: espelho base + margem (~3 faixas zona player)
            val mirrorCount = frozenMirrorTracks.size.coerceAtLeast(1)
            val inflateDistance = rowH * (mirrorCount + 3).coerceAtLeast(4)

            when {
                firstVisible < idx -> 0f
                firstVisible > idx -> 1f
                else -> (scrollOffset / inflateDistance).coerceIn(0f, 1f)
            }
        }
    }

    // Ao alongar, o espelho puxa mais faixas da lista geral (simula saída por trás do Mini Player)
    LaunchedEffect(mirrorInflateFraction, expandedAlbumFirstSongId, frozenMirrorTracks) {
        val hostId = expandedAlbumFirstSongId ?: run {
            mirrorPullTracks = emptyList()
            return@LaunchedEffect
        }
        val hostIndex = allSongs.indexOfFirst { it.id == hostId }
        if (hostIndex < 0) {
            mirrorPullTracks = emptyList()
            return@LaunchedEffect
        }
        val baseCount = frozenMirrorTracks.size
        val afterMirrorStart = hostIndex + 1 + baseCount
        // Até +3 faixas extras conforme inflate (zona prioritária sob o Mini Player)
        val extraPull = (mirrorInflateFraction * 3f).toInt().coerceIn(0, 3)
        mirrorPullTracks = if (extraPull > 0 && afterMirrorStart < allSongs.size) {
            allSongs.drop(afterMirrorStart).take(extraPull)
        } else emptyList()
    }

    LaunchedEffect(mirrorInflateFraction) {
        if (mirrorInflateFraction >= 0.98f && expandedAlbumFirstSongId != null) {
            // Álbum totalmente recolhido → lista geral como antes
            expandedAlbumFirstSongId = null
            expandedAlbumExtraTracks = emptyList()
            expandedAlbumFullQueue = emptyList()
            frozenMirrorTracks = emptyList()
            mirrorPullTracks = emptyList()
            armedAlbumName = null
            albumDragOffsets.clear()
        }
    }

    fun clearAlbumMode() {
        armedAlbumName = null
        expandedAlbumFirstSongId = null
        expandedAlbumExtraTracks = emptyList()
        expandedAlbumFullQueue = emptyList()
        frozenMirrorTracks = emptyList()
        mirrorPullTracks = emptyList()
        albumDragOffsets.clear()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
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
                        text = "Álbuns($albumCountTotal)",
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
                    val maxAlbumDragOffsetPx = maxDragOffsetPx * 0.45f

                    val activeArtistName = activeSongId?.let { id ->
                        allSongs.firstOrNull { it.id == id }?.artist
                    }

                    val currentSongIdState = rememberUpdatedState(currentSongId)
                    val isBrowsing = activeSongId != null && activeSongId != currentSongIdState.value

                    LaunchedEffect(activeSongId) {
                        if (isBrowsing && !isAlbumMode) {
                            delay(ARMED_TARJA_TIMEOUT_MS)
                            val realId = currentSongIdState.value
                            val previousActiveId = activeSongId
                            val previousOffsetPx = previousActiveId?.let { prevId ->
                                val prevIndex = allSongs.indexOfFirst { it.id == prevId }
                                songListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == prevIndex }?.offset
                            } ?: 0

                            activeSongId?.let { dragOffsets[it] = 0f }
                            clearAlbumMode()

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

                    LaunchedEffect(currentSongId) {
                        val activeId = activeSongId
                        if (activeId != null && currentSongId != null && currentSongId != activeId) {
                            val activeSong = allSongs.firstOrNull { it.id == activeId }
                            val newSong = allSongs.firstOrNull { it.id == currentSongId }
                            if (activeSong != null && newSong != null && newSong.artist == activeSong.artist) {
                                dragOffsets[activeId] = 0f
                                activeSongId = currentSongId
                                dragOffsets[currentSongId] = maxDragOffsetPx
                                if (isAlbumMode) {
                                    val prevAlbum = albumDragOffsets[activeId] ?: 0f
                                    if (prevAlbum > 0f) {
                                        albumDragOffsets[activeId] = 0f
                                        albumDragOffsets[currentSongId] = maxAlbumDragOffsetPx
                                    }
                                }
                            }
                        }
                    }

                    // LazyColumn SEMPRE rola — efeitos só mudam altura visual do host
                    LazyColumn(
                        state = songListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 280.dp)
                    ) {
                        items(allSongs, key = { it.id }) { song ->
                            val dragOffset = dragOffsets[song.id] ?: 0f
                            val albumOffset = albumDragOffsets[song.id] ?: 0f

                            val isHighlighted = when {
                                isAlbumMode -> true
                                activeArtistName == null -> true
                                else -> song.artist == activeArtistName
                            }

                            val albumNamesOfArtist = remember(song.artist, allSongs) {
                                allSongs.filter { it.artist == song.artist }
                                    .map { it.album }
                                    .distinct()
                                    .sorted()
                            }

                            val isAlbumExpansionHost = song.id == expandedAlbumFirstSongId
                            val mirrorTracksForRow = if (isAlbumExpansionHost) {
                                frozenMirrorTracks + mirrorPullTracks
                            } else emptyList()
                            val extraTracksForRow =
                                if (isAlbumExpansionHost) expandedAlbumExtraTracks else emptyList()
                            val inflate =
                                if (isAlbumExpansionHost) mirrorInflateFraction else 0f
                            val dragEnabled = !isAlbumMode || song.id == activeSongId

                            ExpandableSongItem(
                                song = song,
                                artistNames = artistNames,
                                albumNames = albumNamesOfArtist,
                                playingArtistName = playingArtistName,
                                playingAlbumName = playingAlbumName,
                                extraAlbumTracks = extraTracksForRow,
                                mirrorGeneralTracks = mirrorTracksForRow,
                                mirrorInflateFraction = inflate,
                                onPlayExtraTrack = { track ->
                                    onSongClick(track, expandedAlbumFullQueue)
                                },
                                albumDragOffsetX = albumOffset,
                                onAlbumDrag = { offset -> albumDragOffsets[song.id] = offset },
                                onAlbumDragEnd = { expanded, target ->
                                    albumDragOffsets[song.id] = if (expanded) target else 0f
                                },
                                dragEnabled = dragEnabled,
                                isHighlighted = isHighlighted,
                                onPlaySong = {
                                    when {
                                        isAlbumMode &&
                                            song.album.equals(armedAlbumName, ignoreCase = true) &&
                                            song.artist.equals(
                                                allSongs.firstOrNull { it.id == expandedAlbumFirstSongId }?.artist
                                                    ?: song.artist,
                                                ignoreCase = true
                                            ) -> {
                                            val songsOfAlbum = songsOfAlbumInOrder(
                                                artists, song.artist, armedAlbumName!!
                                            )
                                            val start = songsOfAlbum.firstOrNull { it.id == song.id }
                                                ?: songsOfAlbum.firstOrNull()
                                            if (start != null) onSongClick(start, songsOfAlbum)
                                        }

                                        activeSongId != null && !isAlbumMode &&
                                            song.artist == activeArtistName -> {
                                            val songsOfArtist =
                                                allSongs.filter { it.artist == song.artist }
                                            onSongClick(song, songsOfArtist)
                                        }

                                        else -> {
                                            activeSongId?.let { dragOffsets[it] = 0f }
                                            activeSongId = null
                                            clearAlbumMode()
                                            onSongClick(song, allSongs)
                                        }
                                    }
                                },
                                onSelectAlbum = { albumName ->
                                    val songsOfAlbum =
                                        songsOfAlbumInOrder(artists, song.artist, albumName)
                                    if (songsOfAlbum.isEmpty()) return@ExpandableSongItem

                                    // Tarja NÃO se move
                                    activeSongId?.let { prev ->
                                        if (prev != song.id) dragOffsets[prev] = 0f
                                    }
                                    activeSongId = song.id
                                    dragOffsets[song.id] = maxDragOffsetPx
                                    armedAlbumName = albumName

                                    albumDragOffsets.clear()
                                    albumDragOffsets[song.id] = maxAlbumDragOffsetPx

                                    expandedAlbumFirstSongId = song.id
                                    expandedAlbumExtraTracks =
                                        songsOfAlbum.filter { it.id != song.id }
                                    expandedAlbumFullQueue = songsOfAlbum

                                    // Espelho = faixas da lista geral no vão até ~Mini Player (0..4)
                                    val hostIndex = allSongs.indexOfFirst { it.id == song.id }
                                    val layoutInfo = songListState.layoutInfo
                                    val hostItem = layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == hostIndex }
                                    val rowH = with(density) { 94.dp.toPx() }
                                    val miniPlayerZonePx = with(density) { (225.dp + 96.dp).toPx() }

                                    val positions = if (hostIndex >= 0 && hostItem != null) {
                                        val spaceBelow = (
                                            layoutInfo.viewportEndOffset -
                                                (hostItem.offset + hostItem.size)
                                            ).toFloat().coerceAtLeast(0f)
                                        val gapToPlayer =
                                            (spaceBelow - miniPlayerZonePx).coerceAtLeast(0f)
                                        (gapToPlayer / rowH).toInt().coerceIn(0, 4)
                                    } else 3 // fallback ~3 faixas (prioridade sob o player)

                                    frozenMirrorTracks = if (hostIndex >= 0 && positions > 0) {
                                        allSongs.drop(hostIndex + 1).take(positions)
                                    } else emptyList()
                                    mirrorPullTracks = emptyList()

                                    onSongClick(songsOfAlbum.firstOrNull() ?: song, songsOfAlbum)
                                },
                                onRevealArtistClick = { artistName ->
                                    val songsOfArtist = allSongs.filter { it.artist == artistName }
                                    val firstOfArtist =
                                        songsOfArtist.firstOrNull() ?: return@ExpandableSongItem

                                    val previousActiveId = activeSongId
                                    val previousOffsetPx = previousActiveId?.let { prevId ->
                                        val prevIndex = allSongs.indexOfFirst { it.id == prevId }
                                        songListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == prevIndex }?.offset
                                    } ?: 0

                                    activeSongId?.let { prev ->
                                        if (prev != firstOfArtist.id) dragOffsets[prev] = 0f
                                    }
                                    activeSongId = firstOfArtist.id
                                    dragOffsets[firstOfArtist.id] = maxDragOffsetPx
                                    clearAlbumMode()
                                    onSongClick(firstOfArtist, songsOfArtist)

                                    val targetIndex =
                                        allSongs.indexOfFirst { it.id == firstOfArtist.id }
                                    if (targetIndex >= 0) {
                                        scope.launch {
                                            songListState.scrollToItem(targetIndex, 0)
                                            if (previousOffsetPx != 0) {
                                                songListState.scrollBy(-previousOffsetPx.toFloat())
                                            }
                                        }
                                    }
                                },
                                dragOffsetX = dragOffset,
                                onDragStart = {
                                    if (isAlbumMode && song.id != activeSongId) {
                                        return@ExpandableSongItem
                                    }
                                    if (activeSongId != song.id) {
                                        activeSongId?.let { dragOffsets[it] = 0f }
                                        activeSongId = song.id
                                        clearAlbumMode()
                                        if (song.id == currentSongId) {
                                            val songsOfArtist =
                                                allSongs.filter { it.artist == song.artist }
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
                                            clearAlbumMode()
                                        }
                                    }
                                }
                            )
                        }
                    }

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
                                            val delta =
                                                dragAmount / viewportHeight * totalContentHeight
                                            scope.launch { songListState.scrollBy(delta) }
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
