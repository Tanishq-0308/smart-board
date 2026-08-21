package com.smartboard.teach.data.repository

import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.Hashing
import com.smartboard.teach.data.local.dao.AuthDao
import com.smartboard.teach.data.local.entity.TeacherEntity
import com.smartboard.teach.data.session.SessionManager
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.model.Teacher
import com.smartboard.teach.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 authentication against the seeded local roster.
 *
 * Phase 2 replaces this class with an ErpAuthRepository that calls the real
 * login endpoint. The binding in RepositoryModule is the only thing that
 * changes; this class is deleted, and nothing that consumes AuthRepository is
 * touched.
 */
@Singleton
class LocalAuthRepository @Inject constructor(
    private val authDao: AuthDao,
    private val sessionManager: SessionManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val authState: Flow<AuthState> =
        sessionManager.teacherId.flatMapLatest { id ->
            if (id == null) {
                flowOf(AuthState.Guest)
            } else {
                authDao.observeById(id).map { entity ->
                    // A stored id pointing at a teacher that no longer exists
                    // (seed changed, data cleared) degrades to Guest rather
                    // than leaving the app in a broken half-signed-in state.
                    entity?.let { AuthState.Authenticated(it.toDomain()) } ?: AuthState.Guest
                }
            }
        }

    override suspend fun login(username: String, password: String): AppResult<Teacher> =
        withContext(ioDispatcher) {
            val normalized = username.trim().lowercase()
            if (normalized.isEmpty() || password.isEmpty()) {
                return@withContext AppResult.Failure(AppError.InvalidCredentials())
            }

            val teacher = authDao.findByUsername(normalized)
                ?: return@withContext AppResult.Failure(AppError.InvalidCredentials())

            if (teacher.passwordHash != Hashing.sha256(password)) {
                return@withContext AppResult.Failure(AppError.InvalidCredentials())
            }

            sessionManager.setTeacherId(teacher.id)
            AppResult.Success(teacher.toDomain())
        }

    override suspend fun logout() = sessionManager.clear()

    override suspend fun currentTeacher(): Teacher? = withContext(ioDispatcher) {
        sessionManager.currentTeacherId()?.let { authDao.findById(it)?.toDomain() }
    }
}

internal fun TeacherEntity.toDomain() = Teacher(
    id = id,
    username = username,
    displayName = displayName,
    email = email,
    remoteId = remoteId,
)
