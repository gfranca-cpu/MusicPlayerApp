package com.musicplayer.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.BottomBar
import com.musicplayer.app.ui.theme.ExpandedPanel
import com.musicplayer.app.ui.theme.TextWhite

enum class BottomNavItem(
    val title: String,
    val icon: ImageVector
) {
    ARTISTS("Artistas", Icons.Default.Person),
    ALBUMS("Álbuns", Icons.Default.Album),
    SONGS("Músicas", Icons.Default.MusicNote)
}

@Composable
fun BottomNavBar(
    selected: BottomNavItem,
    onItemSelected: (BottomNavItem) -> Unit
) {
    NavigationBar(
        containerColor = BottomBar,
        tonalElevation = 0.dp
    ) {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onItemSelected(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ExpandedPanel,
                    selectedTextColor = ExpandedPanel,
                    unselectedIconColor = TextWhite.copy(alpha = 0.6f),
                    unselectedTextColor = TextWhite.copy(alpha = 0.6f),
                    indicatorColor = ExpandedPanel.copy(alpha = 0.15f)
                )
            )
        }
    }
}
