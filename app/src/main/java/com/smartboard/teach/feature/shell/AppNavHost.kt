package com.smartboard.teach.feature.shell

import com.smartboard.teach.feature.attendance.AttendanceOverviewScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.feature.attendance.AttendanceScreen
import com.smartboard.teach.feature.auth.LoginScreen
import com.smartboard.teach.feature.classes.ClassDetailScreen
import com.smartboard.teach.feature.classes.ClassListScreen
import com.smartboard.teach.feature.maths3d.Maths3DScreen
import com.smartboard.teach.feature.material.MaterialListScreen
import com.smartboard.teach.feature.material.MaterialViewerScreen
import com.smartboard.teach.feature.notes.NoteDetailScreen
import com.smartboard.teach.feature.notes.NotesListScreen
import com.smartboard.teach.feature.settings.DiagnosticsScreen
import com.smartboard.teach.feature.settings.SettingsScreen
import com.smartboard.teach.feature.whiteboard.WhiteboardScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    authState: AuthState,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        // Guest-first: the board must be usable the instant it powers on.
        // Never a login wall at launch.
        // The optional-arg form. Navigating to the bare "whiteboard" route
        // still matches it, so the sidebar needs no special case.
        startDestination = DetailRoutes.WHITEBOARD_WITH_DOCUMENT,
        modifier = modifier,
    ) {
        // --- Guest-accessible ---

        composable(
            route = DetailRoutes.WHITEBOARD_WITH_DOCUMENT,
            arguments = listOf(
                navArgument(DetailRoutes.ARG_DOCUMENT_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(DetailRoutes.ARG_DOCUMENT_PAGE) {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument(DetailRoutes.ARG_DOCUMENT_LAST) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            val pendingInsertImage by entry.savedStateHandle
                .getStateFlow<String?>(DetailRoutes.INSERT_IMAGE_KEY, null)
                .collectAsState()
            // Read through the SavedStateHandle so it can be cleared once used:
            // coming back to the board must not add the document again.
            val pendingDocument by entry.savedStateHandle
                .getStateFlow<String?>(DetailRoutes.ARG_DOCUMENT_PATH, null)
                .collectAsState()
            WhiteboardScreen(
                pendingDocumentPath = pendingDocument,
                // The handle holds the route's arguments, and is overwritten when a
                // document is handed to an existing board.
                pendingDocumentPage = entry.savedStateHandle.get<Int>(DetailRoutes.ARG_DOCUMENT_PAGE) ?: 0,
                pendingDocumentLast = entry.savedStateHandle.get<Int>(DetailRoutes.ARG_DOCUMENT_LAST) ?: 0,
                onDocumentConsumed = { entry.savedStateHandle[DetailRoutes.ARG_DOCUMENT_PATH] = null },
                pendingInsertImage = pendingInsertImage,
                onInsertConsumed = { entry.savedStateHandle[DetailRoutes.INSERT_IMAGE_KEY] = null },
                onOpenNotes = {
                    navController.navigate(Dest.Notes.route) { launchSingleTop = true }
                },
                onOpenNote = { noteId -> navController.navigate(DetailRoutes.noteDetail(noteId)) },
                // Same options as the sidebar, so the board stays at the root of
                // the stack and 3D Maths can hand a snapshot back to it.
                onOpenMaths3D = {
                    navController.navigate(Dest.Maths3D.route) {
                        popUpTo(Dest.Whiteboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                // A 3-D figure selected on the board opens 3D Maths on that solid.
                onOpenSolid = { solid ->
                    navController.navigate(Dest.Maths3D.route + "?solid=" + solid) {
                        popUpTo(Dest.Whiteboard.route) { saveState = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = Dest.Maths3D.route + "?solid={solid}",
            arguments = listOf(navArgument("solid") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            Maths3DScreen(
                initialSolid = entry.arguments?.getString("solid"),
                onInsert = { path ->
                    // The board is always the root of the stack (sidebar
                    // navigation pops up to it), so hand the snapshot to its
                    // entry and return there.
                    navController.getBackStackEntry(DetailRoutes.WHITEBOARD_WITH_DOCUMENT)
                        .savedStateHandle[DetailRoutes.INSERT_IMAGE_KEY] = path
                    navController.popBackStack(DetailRoutes.WHITEBOARD_WITH_DOCUMENT, inclusive = false)
                },
            )
        }

        composable(Dest.Notes.route) {
            NotesListScreen(
                onOpenNote = { noteId -> navController.navigate(DetailRoutes.noteDetail(noteId)) },
            )
        }

        composable(
            route = DetailRoutes.NOTE_DETAIL,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) {
            NoteDetailScreen(
                onBack = { navController.popBackStack() },
                onExport = { _, _ -> },
            )
        }

        composable(Dest.Settings.route) {
            SettingsScreen(onOpenDiagnostics = { navController.navigate(DetailRoutes.DIAGNOSTICS) })
        }
        composable(DetailRoutes.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }

        composable(Dest.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    // Land back on the board, and drop Login from the back
                    // stack so Back does not return to a completed form.
                    navController.navigate(Dest.Whiteboard.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        // --- Requires a signed-in teacher ---

        composable(Dest.Classes.route) {
            AuthGate(authState, navController) {
                ClassListScreen(
                    onOpenClass = { navController.navigate(DetailRoutes.classDetail(it)) },
                    onTakeAttendance = {
                        navController.navigate(DetailRoutes.attendanceForClass(it))
                    },
                )
            }
        }

        composable(
            route = DetailRoutes.CLASS_DETAIL,
            arguments = listOf(navArgument("classId") { type = NavType.StringType }),
        ) {
            AuthGate(authState, navController) {
                ClassDetailScreen(
                    onBack = { navController.popBackStack() },
                    onTakeAttendance = {
                        navController.navigate(DetailRoutes.attendanceForClass(it))
                    },
                )
            }
        }

        composable(Dest.Attendance.route) {
            AuthGate(authState, navController) {
                // Today's register across classes — distinct from My Classes.
                AttendanceOverviewScreen(
                    onTakeAttendance = { navController.navigate(DetailRoutes.attendanceForClass(it)) },
                )
            }
        }

        composable(
            route = DetailRoutes.ATTENDANCE_FOR_CLASS,
            arguments = listOf(navArgument("classId") { type = NavType.StringType }),
        ) {
            AuthGate(authState, navController) {
                AttendanceScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Dest.Material.route) {
            AuthGate(authState, navController) {
                MaterialListScreen(
                    onOpenMaterial = { navController.navigate(DetailRoutes.materialViewer(it)) },
                )
            }
        }

        composable(
            route = DetailRoutes.MATERIAL_VIEWER,
            arguments = listOf(navArgument("materialId") { type = NavType.StringType }),
        ) {
            AuthGate(authState, navController) {
                MaterialViewerScreen(
                    onBack = { navController.popBackStack() },
                    onAnnotateOnBoard = { path, first, last ->
                        // Handed to the EXISTING board, as 3D Maths does, and
                        // popped back to: rebuilding the board here reset the
                        // teacher's pen and colour.
                        val board = runCatching {
                            navController.getBackStackEntry(DetailRoutes.WHITEBOARD_WITH_DOCUMENT)
                        }.getOrNull()
                        if (board != null) {
                            board.savedStateHandle[DetailRoutes.ARG_DOCUMENT_PAGE] = first
                            board.savedStateHandle[DetailRoutes.ARG_DOCUMENT_LAST] = last
                            board.savedStateHandle[DetailRoutes.ARG_DOCUMENT_PATH] = path
                            navController.popBackStack(DetailRoutes.WHITEBOARD_WITH_DOCUMENT, inclusive = false)
                        } else {
                            navController.navigate(DetailRoutes.whiteboardWithDocument(path, first, last)) {
                                popUpTo(Dest.Whiteboard.route) { inclusive = true }
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Second line of gating defence, alongside the sidebar lock.
 *
 * The sidebar prevents a guest *choosing* a locked destination; this catches
 * the two cases it cannot: arriving via a deep link, and a session ending
 * while the screen is already open.
 *
 * While auth state is still [AuthState.Loading] we render nothing rather than
 * redirecting — otherwise a signed-in teacher would be bounced to Login for a
 * frame on every cold start.
 */
@Composable
private fun AuthGate(
    authState: AuthState,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    when (authState) {
        is AuthState.Authenticated -> content()
        AuthState.Loading -> Unit
        AuthState.Guest -> {
            LaunchedEffect(Unit) {
                navController.navigate(Dest.Login.route) {
                    popUpTo(Dest.Whiteboard.route)
                    launchSingleTop = true
                }
            }
        }
    }
}
