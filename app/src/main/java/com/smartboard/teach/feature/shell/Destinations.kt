package com.smartboard.teach.feature.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * App destinations.
 *
 * `requiresAuth` drives gating in TWO places on purpose:
 *  1. [Sidebar] dims and locks the item (discoverability — a teacher should see
 *     the feature exists rather than have it vanish).
 *  2. [AppNavHost] wraps the destination in an AuthGate, which catches deep
 *     links and the case where a session ends while the screen is open.
 */
sealed class Dest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val requiresAuth: Boolean,
) {
    data object Whiteboard : Dest("whiteboard", "Whiteboard", Icons.Filled.Draw, false)
    data object Notes : Dest("notes", "Notes", Icons.Filled.Description, false)
    data object Classes : Dest("classes", "My Classes", Icons.Filled.Groups, true)
    data object Attendance : Dest("attendance", "Attendance", Icons.Filled.HowToReg, true)
    data object Material : Dest("material", "Study Material", Icons.AutoMirrored.Filled.MenuBook, true)
    data object Settings : Dest("settings", "Settings", Icons.Filled.Settings, false)
    data object Login : Dest("login", "Sign In", Icons.AutoMirrored.Filled.Login, false)

    companion object {
        /** Items rendered in the sidebar, in order. */
        val sidebarItems: List<Dest> = listOf(
            Whiteboard, Notes, Classes, Attendance, Material,
        )

        // Strip both the path arg and any query string, so
        // "whiteboard?backgroundId=..." still resolves to Whiteboard and stays
        // highlighted in the sidebar.
        fun fromRoute(route: String?): Dest? = when (
            route?.substringBefore('/')?.substringBefore('?')
        ) {
            Whiteboard.route -> Whiteboard
            Notes.route -> Notes
            Classes.route -> Classes
            Attendance.route -> Attendance
            Material.route -> Material
            Settings.route -> Settings
            Login.route -> Login
            else -> null
        }
    }
}

/** Detail routes that take arguments. */
object DetailRoutes {
    const val CLASS_DETAIL = "classes/{classId}"
    const val ATTENDANCE_FOR_CLASS = "attendance/{classId}"
    const val NOTE_DETAIL = "notes/{noteId}"
    const val MATERIAL_VIEWER = "material/{materialId}"

    fun classDetail(classId: String) = "classes/$classId"
    fun attendanceForClass(classId: String) = "attendance/$classId"
    fun noteDetail(noteId: String) = "notes/$noteId"
    fun materialViewer(materialId: String) = "material/$materialId"

    /**
     * Optional arg carrying a background id onto the whiteboard.
     *
     * This is how "Annotate on board" reaches the board: the material viewer
     * creates the BoardBackground, then hands over only its id so the board
     * loads it through the normal path rather than the two screens sharing
     * state.
     */
    const val WHITEBOARD_WITH_BACKGROUND = "whiteboard?backgroundId={backgroundId}"
    const val ARG_BACKGROUND_ID = "backgroundId"

    fun whiteboardWithBackground(backgroundId: String) = "whiteboard?backgroundId=$backgroundId"
}
