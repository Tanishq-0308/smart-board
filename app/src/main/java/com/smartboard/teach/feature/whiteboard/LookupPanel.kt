package com.smartboard.teach.feature.whiteboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import com.smartboard.teach.R
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
    /** Null hides the action: nothing on this board can receive an image. */
    onShareToLens: (() -> Unit)?,
    /** Null hides the action: no browser on this board. */
    onSearchWeb: ((String) -> Unit)?,
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
                        contentDescription = stringResource(R.string.panel_close),
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
                            stringResource(R.string.panel_lookup_reading),
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
                        stringResource(R.string.panel_lookup_not_configured),
                        fontSize = dimens.bodySize,
                        color = TextOnChrome,
                    )
                }
            }

            Spacer(Modifier.height(dimens.gutterSmall))

            // Actions. "Search with Lens" appears in EVERY state that has a
            // crop on disk, failure included, because that is exactly when the
            // teacher still needs an answer from somewhere. FlowRow: three
            // actions do not fit the panel's width on one line.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (onShareToLens != null && shareUriOf(state) != null) {
                    TextButton(onClick = onShareToLens) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.panel_lookup_lens), fontSize = dimens.labelSize)
                    }
                }

                if (onSearchWeb != null && state is LookupState.Ready && !state.lookup.isUnreadable) {
                    TextButton(onClick = { onSearchWeb(state.lookup.searchQuery) }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.panel_search_web), fontSize = dimens.labelSize)
                    }
                }

                // Saves the explanation and the crop as a real note, then
                // opens Notes. Independent of the browser check above.
                if (state is LookupState.Ready && !state.lookup.isUnreadable) {
                    TextButton(onClick = onSaveToNotes) {
                        Text(stringResource(R.string.lookup_save_to_notes), fontSize = dimens.labelSize)
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
                stringResource(R.string.panel_lookup_read_from_board),
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
                stringResource(R.string.panel_lookup_related, lookup.relatedTerms.joinToString(", ")),
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

@Composable
private fun headerTitle(state: LookupState): String = when (state) {
    is LookupState.Working -> stringResource(R.string.panel_lookup_working)
    is LookupState.Ready -> state.lookup.title
    is LookupState.Failed -> stringResource(R.string.panel_lookup_failed)
    is LookupState.NotConfigured -> stringResource(R.string.panel_lookup_visual_search)
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
