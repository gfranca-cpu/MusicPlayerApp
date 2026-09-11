package com.musicplayer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.theme.TextWhite
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SongCardColor = Color(0xFF14213D)

@Composable
fun ExpandableSongItem(
    song: Song,
    artistNames: List<String>,
    albumNames: List<String>,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = true,
    onPlaySong: () -> Unit = {},
    onRevealArtistClick: (String) -> Unit = {},
    onSelectAlbum: (String) -> Unit = {},
    dragOffsetX: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: (Boolean, Float) -> Unit = { _, _ -> }
) {
    var isDragging by remember { mutableStateOf(false) }
    val offsetAnimation = remember { Animatable(dragOffsetX) }
    val coroutineScope = rememberCoroutineScope()

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnPlaySong by rememberUpdatedState(onPlaySong)
    val currentOnRevealArtistClick by rememberUpdatedState(onRevealArtistClick)
    val currentOnSelectAlbum by rememberUpdatedState(onSelectAlbum)

    var offsetX by remember { mutableFloatStateOf(dragOffsetX) }
    var measuredTextWidthPx by remember { mutableStateOf(0f) }

    // Estado da lista local de artistas (sempre vivo)
    val localArtistsState = rememberLazyListState()
    // Estado da lista local de álbuns (sempre vivo)
    val localAlbumsState = rememberLazyListState()

    // Estado da segunda tarja (álbuns) — só existe enquanto a tarja do artista está aberta
    var isAlbumDragging by remember { mutableStateOf(false) }
    var albumOffsetX by remember(song.id) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        val density = LocalDensity.current
        val maxDragOffset = with(density) { (maxWidth * 0.8f).toPx() }
        val dragThreshold = maxDragOffset * 0.5f

        // A tarja do álbum abre menos que a do artista, pra sempre sobrar
        // uma parte do nome do artista visível.
        val maxAlbumDragOffset = maxDragOffset * 0.45f
        val albumDragThreshold = maxAlbumDragOffset * 0.5f

        val textStartX = with(density) { (16.dp + 56.dp + 12.dp).toPx() }
        val textEndX = textStartX + measuredTextWidthPx

        LaunchedEffect(dragOffsetX) {
            if (!isDragging && dragOffsetX != offsetAnimation.value) {
                offsetAnimation.animateTo(dragOffsetX, animationSpec = tween(180))
                offsetX = dragOffsetX
            }
        }

        val renderedOffset = if (isDragging) offsetX else offsetAnimation.value
        val clampedOffset = renderedOffset.coerceIn(0f, maxDragOffset)
        val isExpanded = clampedOffset > 0f
        val isAlbumExpanded = albumOffsetX >= maxAlbumDragOffset

        // Se a tarja do artista fecha, a tarja do álbum fecha junto —
        // álbum nunca pode ficar aberto sem o artista visível.
        LaunchedEffect(isExpanded) {
            if (!isExpanded) {
                isAlbumDragging = false
                albumOffsetX = 0f
            }
        }

        // Posiciona no artista correto quando a tarja abre
        LaunchedEffect(isExpanded, song.artist) {
            if (isExpanded && artistNames.isNotEmpty()) {
                delay(50) // espera o layout assentar

                val targetIndex = artistNames.indexOfFirst {
                    it.equals(song.artist, ignoreCase = true)
                }

                if (targetIndex >= 0) {
                    localArtistsState.scrollToItem(targetIndex)
                }
            }
        }

        // Posiciona no álbum correto quando a tarja de álbum abre
        LaunchedEffect(albumOffsetX > 0f, song.album) {
            if (albumOffsetX > 0f && albumNames.isNotEmpty()) {
                delay(50)

                val targetIndex = albumNames.indexOfFirst {
                    it.equals(song.album, ignoreCase = true)
                }

                if (targetIndex >= 0) {
                    localAlbumsState.scrollToItem(targetIndex)
                }
            }
        }

        // CARD DA MÚSICA
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = (-clampedOffset).roundToInt(), y = 0) }
                .background(SongCardColor)
                .border(1.dp, TextWhite.copy(alpha = 0.25f))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            currentOnDragStart()
                        },
                        onDragEnd = {
                            val expanded = offsetX >= dragThreshold
                            isDragging = false
                            coroutineScope.launch {
                                val target = if (expanded) maxDragOffset else 0f
                                offsetAnimation.snapTo(offsetX)
                                offsetAnimation.animateTo(target, tween(180))
                                offsetX = target
                                currentOnDragEnd(expanded, target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val nextOffset = (offsetX - dragAmount).coerceIn(0f, maxDragOffset)
                            offsetX = nextOffset
                            currentOnDrag(nextOffset)
                        }
                    )
                }
                .pointerInput(isHighlighted, textStartX, textEndX) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (isHighlighted && offset.x in textStartX..textEndX) {
                                currentOnPlaySong()
                            }
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(SongCardColor.copy(alpha = 0.6f), CircleShape)
                        .border(1.dp, TextWhite.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = song.title,
                    color = if (isHighlighted) TextWhite else TextWhite.copy(alpha = 0.35f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        measuredTextWidthPx = result.size.width.toFloat()
                    },
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ÁREA REVELADA — lista local de artistas
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(with(density) { clampedOffset.toDp() })
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .zIndex(1f)
            ) {
                LazyColumn(
                    state = localArtistsState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(artistNames) { name ->
                        val isCurrent = name.equals(song.artist, ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(name) {
                                    detectTapGestures {
                                        currentOnRevealArtistClick(name)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isCurrent) Color(0xFFB7F7C1) else TextWhite.copy(alpha = 0.55f),
                                modifier = Modifier.size(14.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = name,
                                color = if (isCurrent) TextWhite else TextWhite.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ===== Alça de arraste da tarja do álbum =====
                // Só existe aqui dentro, ou seja, só pode ser puxada com o
                // artista já visível na tela (isExpanded == true).
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(28.dp)
                        .fillMaxHeight()
                        .zIndex(1.5f)
                        .pointerInput(maxAlbumDragOffset) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isAlbumDragging = true
                                },
                                onDragEnd = {
                                    isAlbumDragging = false
                                    albumOffsetX = if (albumOffsetX >= albumDragThreshold) {
                                        maxAlbumDragOffset
                                    } else {
                                        0f
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    albumOffsetX = (albumOffsetX - dragAmount)
                                        .coerceIn(0f, maxAlbumDragOffset)
                                }
                            )
                        }
                )

                // ===== Painel da tarja do álbum (menor, sobrepõe parte do painel do artista) =====
                // Mesma estrutura da lista de artistas: nomes, ordem e destaque do atual.
                if (albumOffsetX > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(with(density) { albumOffsetX.toDp() })
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.97f))
                            .zIndex(2f)
                    ) {
                        LazyColumn(
                            state = localAlbumsState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(albumNames) { name ->
                                val isCurrent = name.equals(song.album, ignoreCase = true)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(name) {
                                            detectTapGestures {
                                                currentOnSelectAlbum(name)
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color(0xFFB7F7C1) else TextWhite.copy(alpha = 0.55f),
                                        modifier = Modifier.size(13.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = name,
                                        color = if (isCurrent) TextWhite else TextWhite.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

