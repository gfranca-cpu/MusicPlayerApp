package com.musicplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Song

@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},          // abre o player full
    modifier: Modifier = Modifier
) {
    if (song == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E88E5))          // azul do seu print
            .clickable { onPlayerClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ========== Título + Artista ==========
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ========== Controles ==========
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Música Anterior (novo)
            IconButton(
                onClick = onPreviousClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Shuffle (opcional – pode remover se não quiser)
            IconButton(
                onClick = onShuffleClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Aleatório",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play / Pause
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Tocar",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Próxima
            IconButton(
                onClick = onNextClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Próxima",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
