package com.smartboard.teach.core.util

/**
 * Errors are modelled explicitly so the UI can say something honest and
 * specific — "you're offline, the snapshot was saved" is a very different
 * message from "the AI key isn't configured", and a board in a classroom
 * deserves the difference.
 */
sealed interface AppError {
    /** Human-readable text safe to show on the board. */
    val message: String

    data class Network(
        override val message: String = "No internet connection.",
    ) : AppError

    data class Timeout(
        override val message: String = "The request took too long.",
    ) : AppError

    data class Http(
        val code: Int,
        override val message: String,
    ) : AppError

    data class InvalidCredentials(
        override val message: String = "Incorrect username or password.",
    ) : AppError

    data class NotAuthenticated(
        override val message: String = "Please sign in to continue.",
    ) : AppError

    data class AiNotConfigured(
        override val message: String =
            "AI notes are not configured. Add an OpenAI API key to local.properties.",
    ) : AppError

    data class AiResponse(
        override val message: String = "The AI response could not be understood.",
    ) : AppError

    data class Storage(
        override val message: String = "Could not read or write local storage.",
    ) : AppError

    data class NotFound(
        override val message: String = "That item no longer exists.",
    ) : AppError

    data class Unknown(
        override val message: String = "Something went wrong.",
        val cause: Throwable? = null,
    ) : AppError
}
