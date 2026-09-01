package com.musicplayer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.theme.ArtistCard
import com.musicplayer.app.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun ExpandableArtistItem(
    artist: Artist,
    allSongs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    activeArtist: String? = null,
    onRevealSongClick: (Song) -> Unit = {},
    dragOffsetX: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: (Boolean) -> Unit = {}
) {
    var offsetX by remember { mutableStateOf(dragOffsetX) }
    var isDragging by remember { mutableStateOf(false) }
    val offsetAnimation = remember { Animatable(dragOffsetX) }
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val density = LocalDensity.current
        val maxDragOffset = with(density) { (maxWidth * 0.8f).toPx() }
        val dragThreshold = maxDragOffset * 0.5f

        LaunchedEffect(dragOffsetX) {
            if (!isDragging && dragOffsetX != offsetAnimation.value) {
                offsetAnimation.animateTo(dragOffsetX, animationSpec = tween(180))
            }
        }

        val renderedOffset = if (isDragging) offsetX else offsetAnimation.value
        val clampedOffset = renderedOffset.coerceIn(0f, maxDragOffset)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = (-clampedOffset).roundToInt(), y = 0) }
                .clip(RoundedCornerShape(14.dp))
                .background(ArtistCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                onDragStart()
                            },
                            onDragEnd = {
                                val expanded = offsetX >= dragThreshold
                                isDragging = false
                                coroutineScope.launch {
                                    val targetOffset = if (expanded) maxDragOffset else 0f
                                    offsetAnimation.snapTo(offsetX)
                                    offsetAnimation.animateTo(targetOffset, animationSpec = tween(180))
                                    onDragEnd(expanded)
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val nextOffset = (offsetX - dragAmount).coerceIn(0f, maxDragOffset)
                                offsetX = nextOffset
                                onDrag(nextOffset)
                            }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ArtistCard.copy(alpha = 0.75f))
                        .border(1.dp, TextWhite.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = artist.name.take(2).uppercase(),
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = artist.name,
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (clampedOffset > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(with(density) { clampedOffset.toDp() })
                    .fillMaxHeight()
                    .zIndex(1f)
                    .pointerInput(listState, activeArtist, onRevealSongClick) {
                        val touchSlopPx = with(density) { 8.dp.toPx() }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDragY = 0f
                            var dragging = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val matchingChanges = event.changes.filter { it.id == down.id }

                                matchingChanges.forEach { change ->
                                    val deltaY = change.positionChange().y

                                    if (!dragging) {
                                        totalDragY += deltaY
                                        if (kotlin.math.abs(totalDragY) >= touchSlopPx) {
                                            dragging = true
                                        }
                                    }

                                    if (dragging) {
                                        change.consume()
                                        coroutineScope.launch {
                                            listState.scrollBy(-deltaY)
                                        }
                                    }
                                }

                                if (!event.changes.any { it.pressed }) {
                                    if (!dragging) {
                                        val tappedY = down.position.y + listState.layoutInfo.viewportStartOffset
                                        val tappedSong = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { info ->
                                                tappedY >= info.offset.toFloat() &&
                                                    tappedY <= (info.offset + info.size).toFloat()
                                            }
                                            ?.index
                                            ?.let { index -> allSongs.getOrNull(index) }

                                        if (tappedSong != null && activeArtist != null && tappedSong.artist == activeArtist) {
                                            onRevealSongClick(tappedSong)
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
            )
        }
    }
}
