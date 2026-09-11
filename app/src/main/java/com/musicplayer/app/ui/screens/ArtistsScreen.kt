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

// Tempo livre pra navegar (trocar artista/álbum armado) sem que nada mude
// na tela. Se passar esse tempo sem apertar (tocar) em nada, a lista
// desiste da navegação e volta sozinha pra música que está tocando de fato.
private const val ARMED_TARJA_TIMEOUT_MS = 60 * 1000L

private fun buildFlatArtistSongs(artists: List<Artist>): List<Song> =
    artists
        .flatMap { artist -> artist.albums.flatMap { album -> album.songs } }
        .distinctBy { it.id }
        .sortedBy { it.title }

// Busca as músicas do álbum na ordem ORIGINAL (ex.: número da faixa),
// e não na ordem alfabética usada pela lista geral (allSongs).
private fun songsOfAlbumInOrder(artists: List<Artist>, artistName: String, albumName: String): List<Song> =
    artists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
        ?.albums?.firstOrNull { it.name.equals(albumName, ignoreCase = true) }
        ?.songs ?: emptyList()

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

    // Contexto "armado" — só existe internamente, nunca move a tarja aberta na tela.
    var armedArtistName by remember(artists) { mutableStateOf<String?>(null) }
    var armedAlbumName by remember(artists) { mutableStateOf<String?>(null) }

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
        // ========== CABEÇALHO DINÂMICO (fonte ajustada) ==========
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

                    // Artista "efetivo": o que foi armado por dentro (trocando na lista
                    // revelada) tem prioridade; sem isso, cai no artista da própria
                    // música que está com a tarja aberta.
                    val rowArtistOfActive = activeSongId?.let { id ->
                        allSongs.firstOrNull { it.id == id }?.artist
                    }
                    val activeArtistName = armedArtistName ?: rowArtistOfActive

                    val currentSongIdState = rememberUpdatedState(currentSongId)

                    // Enquanto o usuário está "navegando" (tarja aberta em música/artista/
                    // álbum diferente do que está tocando de verdade), ele tem 1 minuto
                    // livre pra trocar de artista/álbum à vontade sem nada mudar na tela.
                    // Qualquer nova troca reinicia esse minuto do zero. Se ficar parado
                    // por 1 minuto sem apertar (tocar) em nada, desiste da navegação e a
                    // lista volta sozinha pra música que está tocando, abrindo a tarja dela.
                    val isBrowsing = activeSongId != null &&
                        (activeSongId != currentSongIdState.value ||
                            armedArtistName != null ||
                            armedAlbumName != null)

                    LaunchedEffect(activeSongId, armedArtistName, armedAlbumName) {
                        if (isBrowsing) {
                            delay(ARMED_TARJA_TIMEOUT_MS)
                            // Chegou até aqui sem a chave do efeito mudar = ninguém tocou
                            // em nada durante o minuto inteiro. Desiste da navegação e
                            // toma controle total: localiza a música que está tocando e
                            // abre a tarja dela na MESMA posição de tela que a tarja
                            // armada ocupava (sem saltar pro topo).
                            val realId = currentSongIdState.value
                            val previousActiveId = activeSongId
                            val previousOffsetPx = previousActiveId?.let { prevId ->
                                val prevIndex = allSongs.indexOfFirst { it.id == prevId }
                                songListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == prevIndex }
                                    ?.offset
                            } ?: 0

                            activeSongId?.let { current -> dragOffsets[current] = 0f }
                            armedArtistName = null
                            armedAlbumName = null
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

                    // Segue a gaveta quando a música avança dentro do mesmo artista efetivo
                    // (ex.: fila de álbum/artista tocando sozinha, sem ação do usuário).
                    LaunchedEffect(currentSongId) {
                        val activeId = activeSongId
                        if (activeId != null && currentSongId != null && currentSongId != activeId) {
                            val newSong = allSongs.firstOrNull { it.id == currentSongId }
                            if (newSong != null && activeArtistName != null && newSong.artist == activeArtistName) {
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
                            // A tarja aberta continua sempre destacada/tocável, mesmo que o
                            // contexto armado tenha mudado pra outro artista por dentro.
                            val isHighlighted = activeArtistName == null ||
                                song.artist == activeArtistName ||
                                song.id == activeSongId
                            val albumNamesOfArtist = remember(song.artist, allSongs) {
                                allSongs.filter { it.artist == song.artist }
                                    .map { it.album }
                                    .distinct()
                                    .sorted()
                            }

                            ExpandableSongItem(
                                song = song,
                                artistNames = artistNames,
                                albumNames = albumNamesOfArtist,
                                isHighlighted = isHighlighted,
                                onPlaySong = {
                                    val effectiveArtist = activeArtistName ?: song.artist
                                    when {
                                        activeSongId != null && isHighlighted && armedAlbumName != null -> {
                                            // Álbum armado (do artista efetivo, que pode ter sido
                                            // trocado por dentro sem mover a tarja).
                                            val songsOfAlbum = songsOfAlbumInOrder(
                                                artists, effectiveArtist, armedAlbumName!!
                                            )
                                            val startSong = songsOfAlbum.firstOrNull { it.id == song.id }
                                                ?: songsOfAlbum.firstOrNull()
                                            if (startSong != null) onSongClick(startSong, songsOfAlbum)
                                        }
                                        activeSongId != null && isHighlighted && effectiveArtist != song.artist -> {
                                            // Artista foi trocado por dentro (sem álbum armado):
                                            // toca a partir do primeiro do artista escolhido.
                                            val songsOfArtist = allSongs.filter { it.artist == effectiveArtist }
                                            val startSong = songsOfArtist.firstOrNull()
                                            if (startSong != null) onSongClick(startSong, songsOfArtist)
                                        }
                                        activeSongId != null && isHighlighted -> {
                                            // Comportamento normal: toca a partir da própria
                                            // música aberta, encadeando o resto do artista.
                                            val songsOfArtist = allSongs.filter { it.artist == song.artist }
                                            onSongClick(song, songsOfArtist)
                                        }
                                        else -> onSongClick(song, allSongs)
                                    }
                                },
                                onSelectAlbum = { albumName ->
                                    // Pressionar o álbum É o gatilho de som: toca a partir da
                                    // primeira faixa, na ordem real do álbum.
                                    val effectiveArtist = armedArtistName ?: song.artist
                                    val songsOfAlbum = songsOfAlbumInOrder(artists, effectiveArtist, albumName)
                                    val firstOfAlbum = songsOfAlbum.firstOrNull()
                                    if (firstOfAlbum != null) {
                                        // Guarda a posição exata na tela da tarja atual, pra abrir
                                        // a nova bem ali — sem saltar pro topo.
                                        val previousActiveId = activeSongId
                                        val previousOffsetPx = previousActiveId?.let { prevId ->
                                            val prevIndex = allSongs.indexOfFirst { it.id == prevId }
                                            songListState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == prevIndex }
                                                ?.offset
                                        } ?: 0

                                        activeSongId?.let { prev ->
                                            if (prev != firstOfAlbum.id) dragOffsets[prev] = 0f
                                        }
                                        activeSongId = firstOfAlbum.id
                                        dragOffsets[firstOfAlbum.id] = maxDragOffsetPx
                                        armedAlbumName = albumName

                                        onSongClick(firstOfAlbum, songsOfAlbum)

                                        val targetIndex = allSongs.indexOfFirst { it.id == firstOfAlbum.id }
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
                                onRevealArtistClick = { artistName ->
                                    // Só arma o artista por dentro — não move a tarja, não toca.
                                    armedArtistName = artistName
                                    armedAlbumName = null
                                },
                                dragOffsetX = dragOffset,
                                onDragStart = {
                                    if (activeSongId != song.id) {
                                        activeSongId?.let { dragOffsets[it] = 0f }
                                        activeSongId = song.id
                                        armedArtistName = null
                                        armedAlbumName = null
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
                                            armedArtistName = null
                                            armedAlbumName = null
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // ========== ESCOTILHA LATERAL ==========
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
