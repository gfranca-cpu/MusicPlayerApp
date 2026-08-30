package com.musicplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.theme.ArtistCard
import com.musicplayer.app.ui.theme.ExpandedPanel
import com.musicplayer.app.ui.theme.TextPlaying
import com.musicplayer.app.ui.theme.TextWhite

@Composable
fun ExpandableArtistItem(
    artist: Artist,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val maxDragOffset = 180f

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ExpandedPanel)
                .padding(8.dp)
                .height(200.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                artist.albums.forEach { album ->
                    item {
                        Text(
                            text = album.name,
                            color = TextWhite.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(album.songs) { song ->
                        val isPlaying = song.id == currentSongId

                        Text(
                            text = song.title,
                            color = if (isPlaying) TextPlaying else TextWhite,
                            fontSize = 14.sp,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val allSongsOfArtist = artist.albums.flatMap { it.songs }
                                    onSongClick(song, allSongsOfArtist)
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }

                    if (album != artist.albums.last()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                .clip(RoundedCornerShape(8.dp))
                .background(ArtistCard)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { },
                        onDragEnd = { },
                        onDragCancel = { },
                        onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-maxDragOffset, maxDragOffset)
                        }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = artist.name.uppercase(),
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = TextWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
