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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted

/**
 * Insert → Area: the six ready-made layouts in a 3 x 2 grid under a title,
 * as on the reference board. A tap puts one on the board and closes this.
 */
@Composable
fun AreaPicker(onPick: (AreaTemplate) -> Unit, onDismiss: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Dialog(onDismissRequest = onDismiss) {
        FloatingIsland(contentPadding = PaddingValues(dimens.gutter)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.board_insert_area),
                    color = TextOnChrome,
                    fontSize = dimens.bodySize * 1.15f,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = dimens.gutter),
                )
                AreaTemplate.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                        row.forEach { template -> AreaTile(template) { onPick(template) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaTile(template: AreaTemplate, onClick: () -> Unit) {
    val dimens = SmartBoardTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(dimens.chromeButton * 3.4f)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .clickable(onClick = onClick)
            .padding(vertical = dimens.gutterSmall),
    ) {
        Box(
            Modifier
                .size(width = dimens.chromeButton * 2.6f, height = dimens.chromeButton * 1.6f)
                .clip(RoundedCornerShape(dimens.cornerRadius))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (template) {
                    AreaTemplate.BADMINTON -> Icons.Filled.SportsTennis
                    AreaTemplate.BASEBALL -> Icons.Filled.SportsBaseball
                    AreaTemplate.BASKETBALL -> Icons.Filled.SportsBasketball
                    AreaTemplate.GO -> Icons.Filled.Apps
                    AreaTemplate.HANDBALL -> Icons.Filled.SportsHandball
                    AreaTemplate.ICE_RINK -> Icons.Filled.SportsHockey
                },
                contentDescription = null,
                tint = TextOnChromeMuted,
                modifier = Modifier.size(dimens.chromeIcon * 1.8f),
            )
        }
        Text(
            stringResource(template.label),
            color = TextOnChrome,
            fontSize = dimens.bodySize,
            modifier = Modifier.padding(top = dimens.gutterSmall),
        )
    }
}
