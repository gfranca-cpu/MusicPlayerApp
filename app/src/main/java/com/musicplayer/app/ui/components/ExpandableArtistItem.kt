package com.musicplayer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.musicplayer.app.data.Artist
import com.musicplayer.app.ui.theme.ArtistCard
import com.musicplayer.app.ui.theme.TextWhite
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ExpandableArtistItem(
    artist: Artist,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    dragOffsetX: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: (Boolean) -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    val offsetAnimation = remember { Animatable(dragOffsetX) }
    val coroutineScope = rememberCoroutineScope()

    // Mantém as callbacks atualizadas
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    // Offset local usado apenas durante o arrasto
    var offsetX by remember { mutableFloatStateOf(dragOffsetX) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val density = LocalDensity.current
        val maxDragOffset = with(density) { (maxWidth * 0.8f).toPx() }
        val dragThreshold = maxDragOffset * 0.5f

        // Sincroniza com o valor externo quando não está arrastando
        LaunchedEffect(dragOffsetX) {
            if (!isDragging && dragOffsetX != offsetAnimation.value) {
                offsetAnimation.animateTo(
                    targetValue = dragOffsetX,
                    animationSpec = tween(180)
                )
                offsetX = dragOffsetX
            }
        }

        val renderedOffset = if (isDragging) offsetX else offsetAnimation.value
        val clampedOffset = renderedOffset.coerceIn(0f, maxDragOffset)

        // Card principal (artista)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = (-clampedOffset).roundToInt(), y = 0) }
                .clip(RoundedCornerShape(14.dp))
                .background(ArtistCard)
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
                                currentOnDragEnd(expanded)
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar com iniciais
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

        // Área revelada (permite scroll da lista)
        if (clampedOffset > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(with(density) { clampedOffset.toDp() })
                    .fillMaxHeight()
                    .zIndex(1f)
                    .pointerInput(listState) {
                        val touchSlop = with(density) { 8.dp.toPx() }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDragY = 0f
                            var isVerticalDrag = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes.filter { it.id == down.id }

                                changes.forEach { change ->
                                    val deltaY = change.positionChange().y

                                    if (!isVerticalDrag) {
                                        totalDragY += deltaY
                                        if (kotlin.math.abs(totalDragY) >= touchSlop) {
                                            isVerticalDrag = true
                                        }
                                    }

                                    if (isVerticalDrag) {
                                        change.consume()
                                        coroutineScope.launch {
                                            listState.scrollBy(-deltaY)
                                        }
                                    }
                                }

                                if (changes.none { it.pressed }) break
                            }
                        }
                    }
            )
        }
    }
}
