package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.unit.sp
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
 * A 2x5 grid rather than a vertical list: ten destinations stacked would run
 * most of the height of the board, while a grid stays a single glanceable block.
 * Each icon carries its name underneath — shape alone is ambiguous between
 * things like Mindmap and Table, and a teacher mid-lesson should not have to
 * guess or hunt.
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
    onTimer: () -> Unit,
    onText: () -> Unit,
    onBackground: () -> Unit,
    onLessons: () -> Unit,
    modifier: Modifier = Modifier,
    geometryEnabled: Boolean = false,
    mindmapEnabled: Boolean = false,
    pdfEnabled: Boolean = false,
    videoEnabled: Boolean = false,
    timerEnabled: Boolean = false,
) {
    val dimens = SmartBoardTheme.dimens

    // Five and five. Web and Snapshot moved to the tools drawer on the right
    // edge: neither puts an object on the board, which is what this tray is
    // for — they go and fetch something from outside it.
    val rows = listOf(
        listOf(
            InsertItem(Icons.Filled.Image, "Image", true, onImage),
            InsertItem(Icons.Filled.GridOn, "Table", true, onTable),
            InsertItem(SetSquare45Icon, "Geometry", geometryEnabled, onGeometry),
            InsertItem(Icons.Filled.TextFields, "Text", true, onText),
            InsertItem(Icons.Filled.AccountTree, "Mindmap", mindmapEnabled, onMindmap),
        ),
        listOf(
            InsertItem(Icons.Filled.PictureAsPdf, "PDF", pdfEnabled, onPdf),
            InsertItem(Icons.Filled.Movie, "Video", videoEnabled, onVideo),
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
    Column(
        modifier = Modifier
            // Wide enough for the longest label at this size, so every cell is
            // the same width and the grid stays square whatever it is called.
            .width(dimens.chromeButton * 1.9f)
            .clip(RoundedCornerShape(dimens.cornerRadius * 0.6f))
            .background(Color.Transparent)
            .alpha(if (item.enabled) 1f else 0.3f)
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = item.icon,
            // The label is on screen now, so it no longer has to be announced
            // twice to a screen reader.
            contentDescription = null,
            tint = if (item.enabled) TextOnChrome else TextOnChromeMuted,
            modifier = Modifier.size(dimens.chromeIcon),
        )
        Text(
            text = item.label,
            color = if (item.enabled) TextOnChrome else TextOnChromeMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}
