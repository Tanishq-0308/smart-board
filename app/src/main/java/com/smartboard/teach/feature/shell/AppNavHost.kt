package com.smartboard.teach.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.smartboard.teach.feature.material.MaterialListScreen
import com.smartboard.teach.feature.material.MaterialViewerScreen
import com.smartboard.teach.feature.notes.NoteDetailScreen
import com.smartboard.teach.feature.notes.NotesListScreen
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
        startDestination = DetailRoutes.WHITEBOARD_WITH_BACKGROUND,
        modifier = modifier,
    ) {
        // --- Guest-accessible ---

        composable(
            route = DetailRoutes.WHITEBOARD_WITH_BACKGROUND,
            arguments = listOf(
                navArgument(DetailRoutes.ARG_BACKGROUND_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            WhiteboardScreen(
                pendingBackgroundId = entry.arguments
                    ?.getString(DetailRoutes.ARG_BACKGROUND_ID),
                onOpenNotes = {
                    navController.navigate(Dest.Notes.route) { launchSingleTop = true }
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

        composable(Dest.Settings.route) { SettingsScreen() }

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
                // Attendance needs a class; send the teacher to pick one.
                ClassListScreen(
                    onOpenClass = { navController.navigate(DetailRoutes.attendanceForClass(it)) },
                    onTakeAttendance = {
                        navController.navigate(DetailRoutes.attendanceForClass(it))
                    },
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
                    onAnnotateOnBoard = { backgroundId ->
                        navController.navigate(
                            DetailRoutes.whiteboardWithBackground(backgroundId),
                        ) {
                            popUpTo(Dest.Whiteboard.route) { inclusive = true }
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
