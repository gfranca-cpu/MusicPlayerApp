package com.musicplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier,
    dragOffsetX: Float = 0f,
    onDrag: (Float) -> Unit = {},
    onDragEnd: (Boolean) -> Unit = {}
) {
    val maxDragOffset = 260f
    val dragThreshold = 120f
    val clampedOffset = dragOffsetX.coerceIn(0f, maxDragOffset)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .offset { IntOffset(x = (-clampedOffset).roundToInt(), y = 0) }
            .clip(RoundedCornerShape(14.dp))
            .background(ArtistCard)
            .pointerInput(artist.name) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        onDragEnd(clampedOffset > dragThreshold)
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val nextOffset = (clampedOffset + dragAmount).coerceIn(0f, maxDragOffset)
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
