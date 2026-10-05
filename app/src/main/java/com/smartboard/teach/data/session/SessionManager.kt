package com.smartboard.teach.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartboard.teach.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** The signed-in teacher's ERP tokens and school, as the ERP issued them. */
data class ErpSession(
    val accessToken: String,
    val refreshToken: String,
    val schoolId: String,
    val schoolName: String,
    val role: String,
)

/**
 * Persists who is signed in across app restarts: the teacher id the app keys
 * its data on, and the ERP session behind it.
 *
 * ponytail: tokens sit in app-private DataStore, not the Android Keystore. A
 * board is a shared device whose real protection is Sign Out; move them to
 * Keystore-backed storage if boards ever leave the school's control.
 */
@Singleton
class SessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val teacherIdKey = stringPreferencesKey("teacher_id")
    private val accessKey = stringPreferencesKey("erp_access")
    private val refreshKey = stringPreferencesKey("erp_refresh")
    private val schoolIdKey = stringPreferencesKey("erp_school_id")
    private val schoolNameKey = stringPreferencesKey("erp_school_name")
    private val roleKey = stringPreferencesKey("erp_role")

    /**
     * Null unless there is also an ERP session: a teacher id left over from the
     * pre-ERP demo sign-in has no tokens behind it, so that board is a guest.
     */
    val teacherId: Flow<String?> =
        context.sessionDataStore.data.map { prefs -> prefs[teacherIdKey]?.takeIf { prefs[accessKey] != null } }

    suspend fun currentTeacherId(): String? = teacherId.first()

    suspend fun erpSession(): ErpSession? = context.sessionDataStore.data.first().toErpSession()

    suspend fun setTeacherId(id: String) = withContext(ioDispatcher) {
        context.sessionDataStore.edit { it[teacherIdKey] = id }
        Unit
    }

    suspend fun saveErpSession(s: ErpSession) = withContext(ioDispatcher) {
        context.sessionDataStore.edit {
            it[accessKey] = s.accessToken
            it[refreshKey] = s.refreshToken
            it[schoolIdKey] = s.schoolId
            it[schoolNameKey] = s.schoolName
            it[roleKey] = s.role
        }
        Unit
    }

    suspend fun clear() = withContext(ioDispatcher) {
        context.sessionDataStore.edit { it.clear() }
        Unit
    }

    private fun Preferences.toErpSession(): ErpSession? {
        val access = this[accessKey] ?: return null
        return ErpSession(
            accessToken = access,
            refreshToken = this[refreshKey].orEmpty(),
            schoolId = this[schoolIdKey].orEmpty(),
            schoolName = this[schoolNameKey].orEmpty(),
            role = this[roleKey] ?: "teacher",
        )
    }
}
