package com.smartboard.teach.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartboard.teach.core.ui.component.chromeInset
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.core.ui.theme.TextOnSurface
import com.smartboard.teach.core.ui.theme.TextOnSurfaceMuted

@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    onExport: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val dimens = SmartBoardTheme.dimens
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(dimens.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.chromeInset()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text("Notes", color = Accent)
            }
            Spacer(Modifier.weight(1f))
            state.note?.let { note ->
                if (note.markdownPath != null) {
                    TextButton(
                        onClick = { onExport(note.title, state.markdown.orEmpty()) },
                    ) {
                        Icon(Icons.Filled.IosShare, contentDescription = null, tint = Accent)
                        Spacer(Modifier.width(6.dp))
                        Text("Export", color = Accent)
                    }
                }
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            state.markdown == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "This note has no summary yet.",
                    color = TextOnSurfaceMuted,
                    fontSize = dimens.bodySize,
                )
            }

            else -> Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.gutterLarge, vertical = dimens.gutter),
            ) {
                MarkdownText(
                    markdown = state.markdown!!,
                    modifier = Modifier.widthIn(max = 900.dp),
                )
            }
        }
    }
}

/**
 * Minimal Markdown renderer.
 *
 * Deliberately native Compose rather than a WebView: education boards
 * frequently ship an old or missing system WebView, and the Markdown produced
 * here is generated locally by NotesFileStore, so its subset is known exactly
 * — headings, bullets, numbered items, fenced code, and bold spans.
 */
@Composable
private fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val dimens = SmartBoardTheme.dimens
    var inCodeBlock = false

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()

            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEach
            }

            if (inCodeBlock) {
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = dimens.bodySize,
                    color = TextOnSurface,
                )
                return@forEach
            }

            when {
                line.isBlank() -> Spacer(Modifier.height(dimens.gutterSmall))

                line.startsWith("# ") -> Text(
                    text = line.removePrefix("# "),
                    fontSize = dimens.headlineSize,
                    fontWeight = FontWeight.Bold,
                    color = TextOnSurface,
                )

                line.startsWith("## ") -> Text(
                    text = line.removePrefix("## "),
                    fontSize = dimens.titleSize,
                    fontWeight = FontWeight.SemiBold,
                    color = TextOnSurface,
                    modifier = Modifier.padding(top = dimens.gutterSmall),
                )

                line.startsWith("- ") -> BulletLine(line.removePrefix("- "))

                line.firstOrNull()?.isDigit() == true && line.contains(". ") ->
                    Text(
                        text = line,
                        fontSize = dimens.bodySize,
                        color = TextOnSurface,
                        modifier = Modifier.padding(start = dimens.gutterSmall),
                    )

                else -> Text(
                    text = stripEmphasis(line),
                    fontSize = dimens.bodySize,
                    color = TextOnSurface,
                )
            }
        }
    }
}

@Composable
private fun BulletLine(content: String) {
    val dimens = SmartBoardTheme.dimens
    Row(Modifier.padding(start = dimens.gutterSmall)) {
        Text("•  ", fontSize = dimens.bodySize, color = TextOnSurfaceMuted)
        Text(stripEmphasis(content), fontSize = dimens.bodySize, color = TextOnSurface)
    }
}

/** Bold markers are removed rather than styled; the subset stays simple. */
private fun stripEmphasis(text: String): String = text.replace("**", "")
