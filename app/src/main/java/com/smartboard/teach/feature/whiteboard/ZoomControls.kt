package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import kotlin.math.roundToInt

/**
 * Floating zoom control.
 *
 * Sits over the canvas rather than in the toolbar, which is already at its
 * width limit on a real board. Tapping the percentage resets to 100% — the
 * fastest way back to a known state when a teacher has zoomed somewhere odd
 * mid-lesson.
 */
@Composable
fun ZoomControls(
    state: BoardState,
    onZoomChanged: () -> Unit,
    onFitToContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens
    // Read as state so the label tracks pinch gestures live.
    val zoomPercent = (state.camera.zoom * 100).roundToInt()

    FloatingIsland(modifier = modifier, contentPadding = PaddingValues(4.dp)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomButton(Icons.Filled.Remove, "Zoom out") {
            state.camera.zoomBy(1f / ZOOM_STEP, centreX(state), centreY(state))
            onZoomChanged()
        }

        Box(
            modifier = Modifier
                .width(dimens.touchTarget * 1.15f)
                .height(dimens.touchTarget)
                .clip(RoundedCornerShape(dimens.cornerRadius))
                .clickable {
                    state.camera.setZoom(1f, centreX(state), centreY(state))
                    onZoomChanged()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$zoomPercent%",
                color = TextOnChrome,
                fontSize = dimens.labelSize,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }

        ZoomButton(Icons.Filled.Add, "Zoom in") {
            state.camera.zoomBy(ZOOM_STEP, centreX(state), centreY(state))
            onZoomChanged()
        }

        ZoomButton(Icons.Filled.CenterFocusStrong, "Fit to content", onClick = onFitToContent)
    }
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val dimens = SmartBoardTheme.dimens
    Box(
        modifier = Modifier
            .size(dimens.touchTarget)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextOnChromeMuted,
            modifier = Modifier.size(dimens.iconSize),
        )
    }
}

/**
 * Button zooms anchor on the viewport centre, so the content a teacher is
 * looking at stays put rather than sliding toward a corner.
 */
private fun centreX(state: BoardState) = state.viewportWidth / 2f
private fun centreY(state: BoardState) = state.viewportHeight / 2f

private const val ZOOM_STEP = 1.25f
