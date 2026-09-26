package com.smartboard.teach.core.util

import com.smartboard.teach.R

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
        override val message: String = AppText.get(R.string.error_network),
    ) : AppError

    data class Timeout(
        override val message: String = AppText.get(R.string.error_timeout),
    ) : AppError

    data class Http(
        val code: Int,
        override val message: String,
    ) : AppError

    data class InvalidCredentials(
        override val message: String = AppText.get(R.string.error_invalid_credentials),
    ) : AppError

    data class NotAuthenticated(
        override val message: String = AppText.get(R.string.error_not_signed_in),
    ) : AppError

    data class AiNotConfigured(
        override val message: String =
            AppText.get(R.string.error_ai_not_configured),
    ) : AppError

    data class AiResponse(
        override val message: String = AppText.get(R.string.error_ai_response),
    ) : AppError

    data class Storage(
        override val message: String = AppText.get(R.string.error_storage),
    ) : AppError

    data class NotFound(
        override val message: String = AppText.get(R.string.error_not_found),
    ) : AppError

    data class Unknown(
        override val message: String = AppText.get(R.string.error_unknown),
        val cause: Throwable? = null,
    ) : AppError
}
