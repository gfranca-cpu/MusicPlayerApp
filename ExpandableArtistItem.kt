package com.musicplayer.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "arrowRotation"
    )

    Column(modifier = modifier.fillMaxWidth()) {

        // === FAIXA CINZA DO ARTISTA (a parte principal do seu desenho) ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ArtistCard)
                .clickable { expanded = !expanded }
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
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }

        // === PAINEL EXPANDIDO (álbum + músicas) — animação bem lisa ===
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 250)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ExpandedPanel)
                    .padding(12.dp)
            ) {
                artist.albums.forEach { album ->
                    // Nome do álbum
                    Text(
                        text = album.name,
                        color = TextWhite.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Lista de músicas
                    album.songs.forEach { song ->
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
                                    // Toca sem mudar de tela, só muda a cor
                                    val allSongsOfArtist = artist.albums.flatMap { it.songs }
                                    onSongClick(song, allSongsOfArtist)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }

                    if (album != artist.albums.last()) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
