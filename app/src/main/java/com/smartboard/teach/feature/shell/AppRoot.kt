package com.smartboard.teach.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartboard.teach.core.ui.theme.BoardSurface
import com.smartboard.teach.core.ui.theme.SmartBoardTheme

/**
 * Root layout.
 *
 * The content area is FULL-BLEED: it fills the entire window and no chrome
 * reserves layout space from it. Navigation, the clock and every board control
 * float on top as islands.
 *
 * The previous layout kept a fixed sidebar and header, which cost roughly 18%
 * of width and 34% of height on a 1920x1080 board — the drawing surface was
 * about 1566x706. Now the whiteboard gets the whole 1920x1080 and the teacher
 * only gives up space when they deliberately open the menu.
 */
@Composable
fun AppRoot(
    shellViewModel: ShellViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by shellViewModel.authState.collectAsStateWithLifecycle()
    val use24HourClock by shellViewModel.use24HourClock.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDest = Dest.fromRoute(currentRoute)
    val dimens = SmartBoardTheme.dimens

    var menuOpen by remember { mutableStateOf(false) }

    // The board's menu button lives in its own bottom-right page bar, so the
    // board needs a handle on this state. Published through a composition
    // local rather than threaded through the NavHost, which would mean a
    // parameter on every destination that does not want one.
    

    // The whiteboard is the one screen that wants every pixel. Other screens
    // are dense content (a 40-student roster, a PDF page) and read better with
    // the chrome out of the way, so the clock is hidden there too.
    val isBoard = currentDest == Dest.Whiteboard
    val hideClock = remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(BoardSurface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Content fills the window. Screens other than the board draw their
        // own headers and take the full area.
        CompositionLocalProvider(
            LocalOpenBoardMenu provides { menuOpen = true },
            LocalHideClock provides hideClock,
        ) {
            AppNavHost(
                navController = navController,
                authState = authState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // --- floating chrome ---

        // On the BOARD the menu lives inside the page bar (bottom-right),
        // where the reference panel puts it — every control the teacher
        // touches sits along the bottom edge. Other screens are ordinary
        // content and keep a conventional top-left menu button.
        if (!isBoard) {
            MenuButton(
                isOpen = menuOpen,
                onClick = { menuOpen = !menuOpen },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimens.gutterSmall),
            )
        } else if (!hideClock.value) {
            ClockIsland(
                use24HourClock = use24HourClock,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.gutterSmall),
            )
        }

        SidebarOverlay(
            isOpen = menuOpen,
            authState = authState,
            currentRoute = currentRoute,
            onNavigate = { dest ->
                if (currentRoute != dest.route) {
                    navController.navigate(dest.route) {
                        popUpTo(Dest.Whiteboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            onLogout = {
                shellViewModel.logout()
                navController.navigate(Dest.Whiteboard.route) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onDismiss = { menuOpen = false },
        )
    }
}

/**
 * Opens the navigation sidebar from inside a destination.
 *
 * The board puts its menu button in the bottom-right page bar rather than the
 * top-left corner, so it needs to reach the shell's menu state. A composition
 * local keeps that out of every other destination's signature.
 */
val LocalOpenBoardMenu = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Lets the board hide the clock island.
 *
 * The clock is drawn HERE, after the board screen, so it paints over anything
 * the board docks against the top-right corner however opaque that is. The
 * web search pane needs that corner, so it reports in through this rather than
 * having its state plumbed up through navigation.
 */
val LocalHideClock = staticCompositionLocalOf<androidx.compose.runtime.MutableState<Boolean>> {
    androidx.compose.runtime.mutableStateOf(false)
}
