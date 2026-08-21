package com.smartboard.teach.domain.model

data class Teacher(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String? = null,
    /**
     * The teacher's id in the ERP system. Populated with seeded fake ids in
     * Phase 1 so that Phase 2 code paths keying off it are exercised from day
     * one rather than first meeting a real value in production.
     */
    val remoteId: String? = null,
)

/**
 * Guest is a first-class state, not "logged out yet". The board must be usable
 * the instant it powers on, so guest is the default and the start destination
 * is always the whiteboard.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object Guest : AuthState
    data class Authenticated(val teacher: Teacher) : AuthState
}

val AuthState.teacherOrNull: Teacher?
    get() = (this as? AuthState.Authenticated)?.teacher

val AuthState.isAuthenticated: Boolean
    get() = this is AuthState.Authenticated
