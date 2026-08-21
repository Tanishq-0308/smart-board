package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartboard.teach.core.ui.component.FloatingIsland
import com.smartboard.teach.core.ui.theme.Dimens
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.core.ui.theme.WarningAmber
import com.smartboard.teach.domain.model.LookupKind

/**
 * Result panel for a visual lookup.
 *
 * A floating island, NOT a dialog. A dialog would scrim the board and hide the
 * very region being explained, and a teacher needs to look at the circled
 * equation while reading what it is. That is also why the panel is bounded in
 * height and scrolls internally rather than growing to fit its content.
 */
@Composable
fun LookupPanel(
    state: LookupState,
    onDismiss: () -> Unit,
    onShareToLens: () -> Unit,
    onSearchWeb: (String) -> Unit,
    onSaveToNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SmartBoardTheme.dimens

    FloatingIsland(
        modifier = modifier.widthIn(min = 320.dp, max = 420.dp),
        contentPadding = PaddingValues(dimens.gutter),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = headerIcon(state),
                    contentDescription = null,
                    tint = TextOnChrome,
                    modifier = Modifier.size(dimens.iconSize),
                )
                Spacer(Modifier.width(dimens.gutterSmall))
                Text(
                    text = headerTitle(state),
                    fontSize = dimens.titleSize,
                    fontWeight = FontWeight.SemiBold,
                    color = TextOnChrome,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(dimens.touchTarget)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = TextOnChromeMuted,
                        modifier = Modifier.size(dimens.iconSize),
                    )
                }
            }

            Spacer(Modifier.height(dimens.gutterSmall))

            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (state) {
                    is LookupState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimens.iconSize),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(dimens.gutter))
                        Text(
                            "Reading the selected region…",
                            fontSize = dimens.bodySize,
                            color = TextOnChromeMuted,
                        )
                    }

                    is LookupState.Ready -> ReadyBody(state, dimens)

                    is LookupState.Failed -> Text(
                        state.message,
                        fontSize = dimens.bodySize,
                        color = TextOnChrome,
                    )

                    is LookupState.NotConfigured -> Text(
                        "AI lookup is not configured on this board. You can still " +
                            "send the selected region to Google Lens or another " +
                            "visual search app.",
                        fontSize = dimens.bodySize,
                        color = TextOnChrome,
                    )
                }
            }

            Spacer(Modifier.height(dimens.gutterSmall))

            // Actions. "Search with Lens" appears in EVERY state that has a
            // crop on disk, failure included, because that is exactly when the
            // teacher still needs an answer from somewhere.
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (shareUriOf(state) != null) {
                    TextButton(onClick = onShareToLens) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Search with Lens", fontSize = dimens.labelSize)
                    }
                }

                if (state is LookupState.Ready && !state.lookup.isUnreadable) {
                    TextButton(onClick = { onSearchWeb(state.lookup.searchQuery) }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Search the web", fontSize = dimens.labelSize)
                    }
                    // Labelled "Open notes", NOT "Save to notes". This only
                    // navigates; persisting a lookup as a note would need the
                    // notes flow proper (snapshot on disk + markdown), and a
                    // button that claims to save while doing nothing is worse
                    // than no button at all.
                    TextButton(onClick = onSaveToNotes) {
                        Text("Open notes", fontSize = dimens.labelSize)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyBody(state: LookupState.Ready, dimens: Dimens) {
    val lookup = state.lookup

    if (lookup.isUnreadable) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Visibility,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(dimens.iconSize),
            )
            Spacer(Modifier.width(dimens.gutterSmall))
            Text(lookup.explanation, fontSize = dimens.bodySize, color = TextOnChrome)
        }
        return
    }

    Column {
        Text(lookup.explanation, fontSize = dimens.bodySize, color = TextOnChrome)

        if (lookup.transcription.isNotBlank()) {
            Spacer(Modifier.height(dimens.gutter))
            Text(
                "Read from the board",
                fontSize = dimens.labelSize,
                fontWeight = FontWeight.SemiBold,
                color = TextOnChromeMuted,
            )
            Spacer(Modifier.height(2.dp))
            // Monospace: this is a verbatim transcription, and a teacher is
            // checking it character by character against their own writing.
            Text(
                lookup.transcription,
                fontSize = dimens.labelSize,
                fontFamily = FontFamily.Monospace,
                color = TextOnChrome,
            )
        }

        if (lookup.relatedTerms.isNotEmpty()) {
            Spacer(Modifier.height(dimens.gutter))
            Text(
                "Related: " + lookup.relatedTerms.joinToString(", "),
                fontSize = dimens.labelSize,
                color = TextOnChromeMuted,
            )
        }
    }
}

private fun shareUriOf(state: LookupState) = when (state) {
    is LookupState.Working -> state.previewUri
    is LookupState.Ready -> state.shareUri
    is LookupState.Failed -> state.shareUri
    is LookupState.NotConfigured -> state.shareUri
}

private fun headerTitle(state: LookupState): String = when (state) {
    is LookupState.Working -> "Looking up"
    is LookupState.Ready -> state.lookup.title
    is LookupState.Failed -> "Lookup failed"
    is LookupState.NotConfigured -> "Visual search"
}

private fun headerIcon(state: LookupState): ImageVector = when (state) {
    is LookupState.Working -> Icons.Filled.Search
    is LookupState.Failed -> Icons.Filled.CloudOff
    is LookupState.NotConfigured -> Icons.Outlined.HelpOutline
    is LookupState.Ready -> when (state.lookup.kind) {
        LookupKind.EQUATION -> Icons.Filled.Functions
        LookupKind.CHEMISTRY -> Icons.Filled.Science
        LookupKind.DIAGRAM -> Icons.Filled.Image
        LookupKind.TEXT -> Icons.Filled.TextFields
        LookupKind.GEOMETRY -> Icons.Filled.Image
        LookupKind.OTHER -> Icons.Filled.Search
    }
}
