package com.smartboard.teach.data.repository

import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.AppText
import com.smartboard.teach.data.local.dao.AuthDao
import com.smartboard.teach.data.local.entity.TeacherEntity
import com.smartboard.teach.data.remote.erp.ContextSwitchRequest
import com.smartboard.teach.data.remote.erp.ErpApi
import com.smartboard.teach.data.remote.erp.LoginByIdRequest
import com.smartboard.teach.data.remote.erp.LoginRequest
import com.smartboard.teach.data.remote.erp.MembershipDto
import com.smartboard.teach.data.remote.erp.RefreshRequest
import com.smartboard.teach.data.remote.erp.ScopedToken
import com.smartboard.teach.data.remote.erp.StaffDto
import com.smartboard.teach.data.remote.erp.TokenPair
import com.smartboard.teach.data.session.ErpSession
import com.smartboard.teach.data.session.SessionManager
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.model.Teacher
import com.smartboard.teach.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Teacher sign-in against the school's Skolar ERP (ERP_SMS/docs/smart-board-api.md):
 * login, pick the teacher membership, switch into that school, read the staff
 * record. The staff id becomes the teacher id the rest of the app keys on.
 */
@Singleton
class ErpAuthRepository @Inject constructor(
    private val api: ErpApi,
    private val authDao: AuthDao,
    private val sessionManager: SessionManager,
) : AuthRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val authState: Flow<AuthState> =
        sessionManager.teacherId.flatMapLatest { id ->
            if (id == null) flowOf(AuthState.Guest)
            else authDao.observeById(id).map { entity ->
                entity?.let { AuthState.Authenticated(it.toDomain()) } ?: AuthState.Guest
            }
        }

    override suspend fun login(username: String, password: String, schoolCode: String?): AppResult<Teacher> {
        val id = username.trim()
        if (id.isEmpty() || password.isEmpty()) return AppResult.Failure(AppError.InvalidCredentials())

        val tokens = when (val r = if (schoolCode.isNullOrBlank()) {
            api.postPublic("/api/auth/login", LoginRequest(id, password), LoginRequest.serializer(), TokenPair.serializer())
        } else {
            api.postPublic("/api/auth/login-by-id", LoginByIdRequest(schoolCode.trim(), id, password),
                LoginByIdRequest.serializer(), TokenPair.serializer())
        }) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }

        val memberships = when (val r = api.getWith("/api/tenancy/memberships/me", tokens.accessToken,
            ListSerializer(MembershipDto.serializer()))) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }
        // A teacher signs the board in; a principal may too (the ERP allows both).
        val membership = memberships.filter { it.status == "active" }
            .firstOrNull { it.role == "teacher" } ?: memberships.firstOrNull { it.role == "principal" }
            ?: return AppResult.Failure(AppError.InvalidCredentials(AppText.get(R.string.error_not_a_teacher)))

        val scoped = when (val r = api.postWith("/api/tenancy/context/switch", tokens.accessToken,
            ContextSwitchRequest(membership.schoolId, membership.role), ContextSwitchRequest.serializer(),
            ScopedToken.serializer())) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }

        val staff = when (val r = api.getWith("/api/erp/staff/me", scoped.accessToken, StaffDto.serializer())) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }

        val teacher = TeacherEntity(
            id = staff.id,
            username = staff.email ?: id,
            displayName = "${staff.firstName} ${staff.lastName}".trim().ifBlank { id },
            passwordHash = "",
            email = staff.email,
            remoteId = staff.userId,
        )
        authDao.insertAll(listOf(teacher))
        sessionManager.saveErpSession(
            ErpSession(scoped.accessToken, tokens.refreshToken, membership.schoolId, membership.schoolName, membership.role),
        )
        sessionManager.setTeacherId(teacher.id)
        return AppResult.Success(teacher.toDomain())
    }

    override suspend fun logout() {
        sessionManager.erpSession()?.let {
            // Best effort: a board signing out offline must still sign out.
            api.postPublic("/api/auth/logout", RefreshRequest(it.refreshToken), RefreshRequest.serializer(),
                kotlinx.serialization.json.JsonElement.serializer())
        }
        sessionManager.clear()
    }

    override suspend fun currentTeacher(): Teacher? =
        sessionManager.currentTeacherId()?.let { authDao.findById(it)?.toDomain() }
}

internal fun TeacherEntity.toDomain() = Teacher(
    id = id,
    username = username,
    displayName = displayName,
    email = email,
    remoteId = remoteId,
)
