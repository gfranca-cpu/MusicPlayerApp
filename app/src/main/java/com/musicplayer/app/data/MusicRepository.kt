package com.musicplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun loadArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val songs = loadAllSongs()
        if (songs.isEmpty()) return@withContext emptyList()

        // Agrupa por artista → álbum → músicas
        songs
            .groupBy { it.artist.ifBlank { "Artista Desconhecido" } }
            .map { (artistName, artistSongs) ->
                val albums = artistSongs
                    .groupBy { it.album.ifBlank { "Álbum Desconhecido" } }
                    .map { (albumName, albumSongs) ->
                        Album(
                            name = albumName,
                            songs = albumSongs.sortedBy { it.title }
                        )
                    }
                    .sortedBy { it.name }

                Artist(name = artistName, albums = albums)
            }
            .sortedBy { it.name }
    }

    suspend fun loadAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Sem título"
                val artist = cursor.getString(artistCol) ?: "Artista Desconhecido"
                val album = cursor.getString(albumCol) ?: "Álbum Desconhecido"
                val duration = cursor.getLong(durationCol)
                val albumId = cursor.getLong(albumIdCol)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()

                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri,
                        albumArtUri = albumArtUri
                    )
                )
            }
        }

        songs
    }

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val songs = loadAllSongs()
        songs
            .groupBy { it.album.ifBlank { "Álbum Desconhecido" } }
            .map { (albumName, albumSongs) ->
                Album(name = albumName, songs = albumSongs.sortedBy { it.title })
            }
            .sortedBy { it.name }
    }
}
