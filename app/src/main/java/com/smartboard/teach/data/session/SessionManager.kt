package com.smartboard.teach.data.session

import android.content.Context
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

/**
 * Persists which teacher is signed in across app restarts.
 *
 * Phase 1 stores only a teacher id — there is no token because there is no
 * server. Phase 2 adds access/refresh tokens here; because the app reads auth
 * through `AuthState` rather than through this class directly, that addition
 * does not reach the UI.
 */
@Singleton
class SessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val teacherIdKey = stringPreferencesKey("teacher_id")

    val teacherId: Flow<String?> =
        context.sessionDataStore.data.map { it[teacherIdKey] }

    suspend fun currentTeacherId(): String? = teacherId.first()

    suspend fun setTeacherId(id: String) = withContext(ioDispatcher) {
        context.sessionDataStore.edit { it[teacherIdKey] = id }
        Unit
    }

    suspend fun clear() = withContext(ioDispatcher) {
        context.sessionDataStore.edit { it.remove(teacherIdKey) }
        Unit
    }
}
