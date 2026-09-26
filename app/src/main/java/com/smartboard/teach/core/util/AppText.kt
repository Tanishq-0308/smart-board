package com.smartboard.teach.core.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * String resources for code that has no Context: ViewModels, repositories,
 * use cases and the [AppError] defaults. Initialised once in SmartBoardApp.
 *
 * ponytail: resolved when the message is created, so a message already on
 * screen keeps its language if the locale changes mid-session. Messages are
 * short-lived, so that is acceptable; move to resolving at display time if
 * in-app language switching lands.
 */
object AppText {
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /** Falls back to the resource id on the JVM (unit tests), where there is no Context. */
    fun get(@StringRes id: Int, vararg args: Any): String =
        context?.getString(id, *args) ?: "#$id"
}
