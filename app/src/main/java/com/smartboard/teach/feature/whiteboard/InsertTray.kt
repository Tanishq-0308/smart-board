package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.component.SetSquare45Icon
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted

/** One slot in the insert tray. */
private data class InsertItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * Everything that can be placed on the board, behind the + button.
 *
 * A 2x5 icon grid rather than a list of labelled rows, matching the reference
 * panel: ten destinations as a vertical list would run most of the height of
 * the board, and a teacher picks these by shape rather than by reading.
 *
 * Items not yet built are shown DIMMED rather than hidden, so the tray's shape
 * stays constant as they land and nobody has to re-learn where things sit.
 */
@Composable
fun InsertTray(
    onImage: () -> Unit,
    onTable: () -> Unit,
    onGeometry: () -> Unit,
    onMindmap: () -> Unit,
    onPdf: () -> Unit,
    onVideo: () -> Unit,
    onWeb: () -> Unit,
    onLabs: () -> Unit,
    onTimer: () -> Unit,
    onText: () -> Unit,
    onSnapshot: () -> Unit,
    onBackground: () -> Unit,
    onLessons: () -> Unit,
    modifier: Modifier = Modifier,
    geometryEnabled: Boolean = false,
    mindmapEnabled: Boolean = false,
    pdfEnabled: Boolean = false,
    videoEnabled: Boolean = false,
    webEnabled: Boolean = false,
    labsEnabled: Boolean = false,
    timerEnabled: Boolean = false,
) {
    val dimens = SmartBoardTheme.dimens

    val rows = listOf(
        listOf(
            InsertItem(Icons.Filled.Image, "Image", true, onImage),
            InsertItem(Icons.Filled.GridOn, "Table", true, onTable),
            InsertItem(SetSquare45Icon, "Geometry", geometryEnabled, onGeometry),
            InsertItem(Icons.Filled.TextFields, "Text", true, onText),
            InsertItem(Icons.Filled.AccountTree, "Mindmap", mindmapEnabled, onMindmap),
            InsertItem(Icons.Filled.Science, "Labs", labsEnabled, onLabs),
        ),
        listOf(
            InsertItem(Icons.Filled.PictureAsPdf, "PDF", pdfEnabled, onPdf),
            InsertItem(Icons.Filled.Movie, "Video", videoEnabled, onVideo),
            InsertItem(Icons.Filled.Public, "Web search", webEnabled, onWeb),
            InsertItem(Icons.Filled.PhotoCamera, "Snapshot", true, onSnapshot),
            InsertItem(Icons.Filled.AccessTime, "Timer", timerEnabled, onTimer),
            InsertItem(Icons.Filled.Gradient, "Background", true, onBackground),
            InsertItem(Icons.Filled.FolderOpen, "Lessons", true, onLessons),
        ),
    )

    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(dimens.gutterSmall)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { item -> TrayButton(item) }
                }
            }
        }
    }
}

@Composable
private fun TrayButton(item: InsertItem) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .size(dimens.chromeButton + dimens.gutterSmall)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
            .background(Color.Transparent)
            .alpha(if (item.enabled) 1f else 0.3f)
            .clickable(enabled = item.enabled, onClick = item.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (item.enabled) TextOnChrome else TextOnChromeMuted,
            modifier = Modifier.size(dimens.chromeIcon),
        )
    }
}
