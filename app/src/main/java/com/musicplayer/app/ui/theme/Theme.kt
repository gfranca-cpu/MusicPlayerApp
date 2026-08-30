package com.musicplayer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cores baseadas no seu desenho
val DarkBackground = Color(0xFF121212)
val ArtistCard = Color(0xFF5A7A8A)          // cinza-azulado das faixas
val ExpandedPanel = Color(0xFF1E5BB8)       // azul do painel expandido
val TextWhite = Color(0xFFFFFFFF)
val TextPlaying = Color(0xFF4ADE80)        // verde para música tocando
val BottomBar = Color(0xFF1A1A1A)

private val DarkColorScheme = darkColorScheme(
    primary = ExpandedPanel,
    background = DarkBackground,
    surface = DarkBackground,
    onPrimary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
