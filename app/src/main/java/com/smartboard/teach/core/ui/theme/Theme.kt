package com.smartboard.teach.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val SmartBoardColors = lightColorScheme(
    primary = Accent,
    onPrimary = TextOnChrome,
    primaryContainer = AccentMuted,
    onPrimaryContainer = TextOnSurface,
    secondary = ChromeDarkElevated,
    onSecondary = TextOnChrome,
    background = BoardSurface,
    onBackground = TextOnSurface,
    surface = BoardSurface,
    onSurface = TextOnSurface,
    surfaceVariant = BoardGrid,
    onSurfaceVariant = TextOnSurfaceMuted,
    outline = ChromeBorder,
    error = ErrorRed,
    onError = TextOnChrome,
)

/**
 * The app deliberately ships a single light scheme. A classroom board is a
 * shared, brightly lit display — following a system dark theme would make ink
 * on a dark board unreadable from the back of the room.
 */
@Composable
fun SmartBoardTheme(content: @Composable () -> Unit) {
    val dimens = rememberDimens()

    // Typography is derived from the same screen-size scale as Dimens so text
    // and chrome grow together on a large panel.
    val typography = Typography(
        headlineMedium = TextStyle(fontSize = dimens.headlineSize, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = dimens.titleSize, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = dimens.bodySize, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = dimens.bodySize),
        bodyMedium = TextStyle(fontSize = dimens.bodySize),
        labelLarge = TextStyle(fontSize = dimens.bodySize, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = dimens.labelSize, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = dimens.labelSize),
    )

    CompositionLocalProvider(LocalDimens provides dimens) {
        MaterialTheme(
            colorScheme = SmartBoardColors,
            typography = typography,
            content = content,
        )
    }
}
