package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.data.Artist
import com.musicplayer.app.data.Song
import com.musicplayer.app.ui.components.ExpandableArtistItem
import com.musicplayer.app.ui.theme.DarkBackground
import com.musicplayer.app.ui.theme.TextWhite

@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit
) {
    var expandedArtistName by remember { mutableStateOf<String?>(null) }
    val allSongs = artists.flatMap { artist -> artist.albums.flatMap { album -> album.songs } }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Text(
            text = "Artistas",
            color = TextWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TextWhite)
                }
            }

            artists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma música encontrada\nno armazenamento",
                        color = TextWhite.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(allSongs, key = { it.id }) { song ->
                            val isHighlighted = expandedArtistName == null || song.artist == expandedArtistName
                            Text(
                                text = "${song.artist} • ${song.title}",
                                color = if (isHighlighted) TextWhite else TextWhite.copy(alpha = 0.25f),
                                fontSize = 12.sp,
                                letterSpacing = 1.2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp)
                                    .background(DarkBackground)
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(artists, key = { it.name }) { artist ->
                            ExpandableArtistItem(
                                artist = artist,
                                currentSongId = currentSongId,
                                isExpanded = expandedArtistName == artist.name,
                                onExpandedChange = { expanded ->
                                    expandedArtistName = if (expanded) artist.name else null
                                },
                                onSongClick = onSongClick
                            )
                        }
                    }
                }
            }
        }
    }
}
