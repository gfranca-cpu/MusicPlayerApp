package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.theme.ArtistCard
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite

@Composable
fun PlaylistsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit
) {
    var playlistName by remember { mutableStateOf("Minha Playlist") }
    val playlists = remember {
        mutableStateListOf(
            listOf(songs.firstOrNull()).filterNotNull(),
            songs.take(3)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            Text(
                text = "Playlists",
                color = TextWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.hashCode() }) { playlist ->
                    val firstSong = playlist.firstOrNull()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArtistCard)
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = playlistName,
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { firstSong?.let { onSongClick(it, playlist) } }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        playlist.forEach { song ->
                            val isPlaying = song.id == currentSongId
                            Text(
                                text = song.title,
                                color = if (isPlaying) TextWhite else TextWhite.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { onSongClick(song, playlist) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (songs.isNotEmpty()) {
                    playlists.add(songs.take(3))
                }
            },
            containerColor = ArtistCard,
            contentColor = TextWhite,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 130.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Nova playlist")
        }
    }
}
