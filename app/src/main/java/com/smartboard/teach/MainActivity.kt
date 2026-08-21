package com.smartboard.teach

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.smartboard.teach.core.ui.theme.SmartBoardTheme
import com.smartboard.teach.feature.shell.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A board at the front of a classroom must not sleep mid-lesson.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive: the panel IS the lesson surface, so the status and
        // navigation bars are noise on it — a clock, a battery icon and a
        // back button projected in front of a class. They stay swipe-
        // reachable (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) rather than being
        // locked away, so an installer can still get to the launcher.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            SmartBoardTheme {
                AppRoot()
            }
        }
    }
}
