package com.mediop.win.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediop.win.core.model.NavSection
import com.mediop.win.core.theme.*

@Composable
fun DesktopSidebar(
    currentSection: NavSection,
    onSelectSection: (NavSection) -> Unit,
    onOpenSearch: () -> Unit,
    onImportFiles: () -> Unit,
    onToggleMiniPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(232.dp)
            .fillMaxHeight()
            .background(DarkSurface)
            .border(width = 1.dp, color = GlassBorder)
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Text(
                text = "MEDIO",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontSize = 20.sp
                ),
                color = PrimaryCyan
            )
            Text(
                text = " PLAYER",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 20.sp
                ),
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenSearch,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search (Ctrl+F)", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        val items = listOf(
            NavSection.HOME to Icons.Rounded.Home,
            NavSection.MUSIC to Icons.Rounded.MusicNote,
            NavSection.VIDEOS to Icons.Rounded.Movie,
            NavSection.ALBUMS to Icons.Rounded.Album,
            NavSection.ARTISTS to Icons.Rounded.Person,
            NavSection.FOLDERS to Icons.Rounded.Folder,
            NavSection.PLAYLISTS to Icons.Rounded.QueueMusic,
            NavSection.RECENTLY_PLAYED to Icons.Rounded.History,
            NavSection.FAVORITES to Icons.Rounded.Favorite,
            NavSection.SETTINGS to Icons.Rounded.Settings
        )

        items.forEach { (section, icon) ->
            SidebarItem(
                title = section.title,
                icon = icon,
                selected = currentSection == section,
                onClick = { onSelectSection(section) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 10.dp))

        TextButton(
            onClick = onImportFiles,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = PrimaryCyan)
        ) {
            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Media File...", fontSize = 13.sp)
        }

        TextButton(
            onClick = onToggleMiniPlayer,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
        ) {
            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Mini Player (Ctrl+M)", fontSize = 13.sp)
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) PrimaryCyan.copy(alpha = 0.16f) else Color.Transparent
    val fg = if (selected) PrimaryCyan else TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = fg,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp
            ),
            color = if (selected) TextPrimary else TextSecondary
        )
    }
}
