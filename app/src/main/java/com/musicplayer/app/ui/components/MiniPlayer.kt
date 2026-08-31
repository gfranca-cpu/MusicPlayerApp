package com.musicplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.theme.ExpandedPanel
import com.musicplayer.app.ui.theme.TextWhite

@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    isRepeatEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ExpandedPanel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong.title,
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong.artist,
                color = TextWhite.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Embaralhar",
                tint = if (isShuffleEnabled) TextWhite else TextWhite.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onRepeat) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = "Repetir",
                tint = if (isRepeatEnabled) TextWhite else TextWhite.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Tocar",
                tint = TextWhite,
                modifier = Modifier.size(32.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Próxima",
                tint = TextWhite,
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = "Fila",
                tint = TextWhite.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
