@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    isLoading: Boolean,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val allSongs = remember(artists) { buildFlatArtistSongs(artists) }
    val artistNames = remember(allSongs) { allSongs.map { it.artist }.distinct().sorted() }

    val dragOffsets = remember(artists) { mutableStateMapOf<Long, Float>() }
    var activeSongId by remember(artists) { mutableStateOf<Long?>(null) }

    val songListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isAnyTarjaOpen = activeSongId != null
    val songCount = allSongs.size
    val artistCount = artistNames.size
    val albumCount = artists.sumOf { it.albums.size }

    // ========== TIMEOUT DE 5 MINUTOS ==========
    // Se a tarja aberta não for do artista da música atual, volta após 5 min
    LaunchedEffect(activeSongId, currentSongId) {
        val activeId = activeSongId
        val playingId = currentSongId

        if (activeId == null || playingId == null) return@LaunchedEffect

        val activeSong = allSongs.firstOrNull { it.id == activeId }
        val playingSong = allSongs.firstOrNull { it.id == playingId }

        // Só aplica timeout se os artistas forem diferentes
        if (activeSong != null && playingSong != null && activeSong.artist != playingSong.artist) {
            delay(5 * 60 * 1000L) // 5 minutos

            // Depois de 5 min: volta para a tarja da música que está tocando
            if (activeSongId == activeId) { // ainda é a mesma tarja errada
                dragOffsets[activeId] = 0f

                // Abre a tarja da música atual
                activeSongId = playingId
                dragOffsets[playingId] = maxDragOffsetPx

                // Troca a fila de volta para o artista da música atual
                val songsOfPlayingArtist = allSongs.filter { it.artist == playingSong.artist }
                onSongClick(playingSong, songsOfPlayingArtist)

                // Rola até a música atual
                val targetIndex = allSongs.indexOfFirst { it.id == playingId }
                if (targetIndex >= 0) {
                    songListState.animateScrollToItem(targetIndex)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ========== CABEÇALHO ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Músicas($songCount)",
                color = if (!isAnyTarjaOpen) TextWhite else TextWhite.copy(alpha = 0.45f),
                fontSize = if (!isAnyTarjaOpen) 16.sp else 14.sp,
                fontWeight = if (!isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Artistas($artistCount)",
                color = if (isAnyTarjaOpen) TextWhite else TextWhite.copy(alpha = 0.45f),
                fontSize = if (isAnyTarjaOpen) 16.sp else 14.sp,
                fontWeight = if (isAnyTarjaOpen) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Álbuns($albumCount)",
                color = TextWhite.copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TextWhite)
                }
            }

            artists.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nenhuma música encontrada\nno armazenamento",
                        color = TextWhite.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                }
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val itemWidth = maxWidth - 24.dp
                    val maxDragOffsetPx = with(density) { (itemWidth * 0.8f).toPx() }

                    val activeArtistName = activeSongId?.let { id ->
                        allSongs.firstOrNull { it.id == id }?.artist
                    }

                    // Segue a tarja quando a música avança no mesmo artista
                    LaunchedEffect(currentSongId) {
                        val activeId = activeSongId
                        if (activeId != null && currentSongId != null && currentSongId != activeId) {
                            val activeSong = allSongs.firstOrNull { it.id == activeId }
                            val newSong = allSongs.firstOrNull { it.id == currentSongId }
                            if (activeSong != null && newSong != null && newSong.artist == activeSong.artist) {
                                dragOffsets[activeId] = 0f
                                activeSongId = currentSongId
                                dragOffsets[currentSongId] = maxDragOffsetPx
                                val targetIndex = allSongs.indexOfFirst { it.id == currentSongId }
                                if (targetIndex >= 0) {
                                    songListState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = songListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        items(allSongs, key = { it.id }) { song ->
                            val dragOffset = dragOffsets[song.id] ?: 0f
                            val isHighlighted = activeArtistName == null || song.artist == activeArtistName

                            ExpandableSongItem(
                                song = song,
                                artistNames = artistNames,
                                isHighlighted = isHighlighted,
                                onPlaySong = {
                                    if (activeSongId != null && isHighlighted) {
                                        val songsOfArtist = allSongs.filter { it.artist == song.artist }
                                        onSongClick(song, songsOfArtist)
                                    } else {
                                        onSongClick(song, allSongs)
                                    }
                                },
                                onRevealArtistClick = { artistName ->
                                    val songsOfArtist = allSongs.filter { it.artist == artistName }
                                    val firstSong = songsOfArtist.firstOrNull()
                                    if (firstSong != null) {
                                        // Troca a fila
                                        onSongClick(firstSong, songsOfArtist)

                                        // Fecha anterior e abre a nova
                                        activeSongId?.let { prev ->
                                            if (prev != firstSong.id) dragOffsets[prev] = 0f
                                        }
                                        activeSongId = firstSong.id
                                        dragOffsets[firstSong.id] = maxDragOffsetPx

                                        val targetIndex = allSongs.indexOfFirst { it.id == firstSong.id }
                                        if (targetIndex >= 0) {
                                            scope.launch { songListState.animateScrollToItem(targetIndex) }
                                        }
                                    }
                                },
                                dragOffsetX = dragOffset,

                                // ========== LÓGICA PRINCIPAL DE ARRASTAR ==========
                                onDragStart = {
                                    if (activeSongId != song.id) {
                                        // Fecha a tarja anterior
                                        activeSongId?.let { dragOffsets[it] = 0f }

                                        // Abre a nova
                                        activeSongId = song.id

                                        // Troca a fila para as irmãs deste artista (mesmo sem tocar)
                                        val songsOfArtist = allSongs.filter { it.artist == song.artist }
                                        onSongClick(song, songsOfArtist)
                                    }
                                },
                                onDrag = { dragOffsets[song.id] = it },
                                onDragEnd = { expanded, target ->
                                    if (expanded) {
                                        dragOffsets[song.id] = target
                                    } else {
                                        dragOffsets[song.id] = 0f
                                        if (activeSongId == song.id) activeSongId = null
                                    }
                                }
                            )
                        }
                    }

                    // ========== ESCOTILHA LATERAL (igual) ==========
                    val layoutInfo = songListState.layoutInfo
                    val totalItems = allSongs.size
                    if (totalItems > 3) {
                        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                        val approxItemHeight = 98f
                        val totalContentHeight = totalItems * approxItemHeight
                        val scrollPixels = songListState.firstVisibleItemIndex * approxItemHeight +
                                songListState.firstVisibleItemScrollOffset
                        val progress = (scrollPixels / totalContentHeight).coerceIn(0f, 1f)
                        val thumbHeight = (viewportHeight / totalContentHeight * viewportHeight)
                            .coerceIn(48f, 140f)
                        val thumbOffset = progress * (viewportHeight - thumbHeight)

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(20.dp)
                                .padding(end = 6.dp, top = 12.dp, bottom = 150.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .align(Alignment.Center)
                                    .background(TextWhite.copy(alpha = 0.12f), RoundedCornerShape(50))
                            )

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(0, thumbOffset.roundToInt()) }
                                    .width(14.dp)
                                    .height(with(density) { thumbHeight.toDp() })
                                    .align(Alignment.TopCenter)
                                    .background(TextWhite.copy(alpha = 0.55f), RoundedCornerShape(50))
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val delta = dragAmount / viewportHeight * totalContentHeight
                                            scope.launch {
                                                songListState.scrollBy(delta)
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
