package com.musicplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.theme.ArtistCard
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun ExpandableArtistItem(
    artist: Artist,
    allSongs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val maxDragOffset = 220f
    val revealProgress = (dragOffsetX / maxDragOffset).coerceIn(0f, 1f)
    val visibleSongs = allSongs.sortedBy { it.title }
    val artistSongs = visibleSongs.filter { it.artist == artist.name }
    val drawerVisible = expanded || dragOffsetX > 0f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        val maxDrawerWidth = maxWidth * 0.68f
        val drawerWidth = maxDrawerWidth * revealProgress

        if (drawerVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(drawerWidth)
                    .height(160.dp)
                    .alpha(revealProgress)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBackground.copy(alpha = 0.96f))
                    .border(1.dp, TextWhite.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleSongs, key = { it.id }) { song ->
                        val isFromArtist = song.artist == artist.name
                        val enabled = isFromArtist
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isFromArtist) 1f else 0.3f)
                                .clickable(enabled = enabled) {
                                    if (enabled) {
                                        onSongClick(song, artistSongs)
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isFromArtist) Color(0xFFB7F7C1) else TextWhite.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = song.title,
                                color = if (isFromArtist) TextWhite else TextWhite.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ArtistCard)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            expanded = dragOffsetX > maxDragOffset * 0.5f
                            dragOffsetX = if (expanded) maxDragOffset else 0f
                        },
                        onDragCancel = {
                            expanded = false
                            dragOffsetX = 0f
                        },
                        onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, maxDragOffset)
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
}
