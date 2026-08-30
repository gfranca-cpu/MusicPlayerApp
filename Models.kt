package com.musicplayer.app.data

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // em milissegundos
    val uri: String,
    val albumArtUri: String? = null
)

data class Artist(
    val name: String,
    val albums: List<Album>
)

data class Album(
    val name: String,
    val songs: List<Song>
)
