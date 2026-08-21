package com.smartboard.teach.data.local.seed

import android.content.Context
import android.util.Log
import com.smartboard.teach.core.util.Hashing
import com.smartboard.teach.data.local.SmartBoardDatabase
import com.smartboard.teach.data.local.entity.EnrollmentEntity
import com.smartboard.teach.data.local.entity.SchoolClassEntity
import com.smartboard.teach.data.local.entity.StudentEntity
import com.smartboard.teach.data.local.entity.StudyMaterialEntity
import com.smartboard.teach.data.local.entity.TeacherEntity
import com.smartboard.teach.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the demo roster from assets on first run.
 *
 * This is the Phase 1 stand-in for the ERP. It writes into exactly the tables
 * the ERP will later populate, including `remoteId` values, so the rest of the
 * app cannot tell the difference between seeded and fetched data — which is
 * the point: Phase 2 changes the source, not the shape.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: SmartBoardDatabase,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun seedIfEmpty() = withContext(ioDispatcher) {
        try {
            if (database.authDao().count() > 0) return@withContext
            seed()
            Log.i(TAG, "Seeded demo roster from assets.")
        } catch (t: Throwable) {
            // A seeding failure must not stop the board from opening — guest
            // mode (board + notes) does not depend on any of this data.
            Log.e(TAG, "Seeding failed; continuing without demo roster.", t)
        }
    }

    private suspend fun seed() {
        val teachers = readAsset<List<SeedTeacher>>("seed/teachers.json")
        val students = readAsset<List<SeedStudent>>("seed/students.json")
        val classes = readAsset<List<SeedClass>>("seed/classes.json")
        val materials = readAsset<List<SeedMaterial>>("seed/materials.json")

        database.authDao().insertAll(
            teachers.map {
                TeacherEntity(
                    id = it.id,
                    username = it.username.lowercase(),
                    displayName = it.displayName,
                    passwordHash = Hashing.sha256(it.password),
                    email = it.email,
                    remoteId = it.remoteId,
                )
            },
        )

        val rosterDao = database.rosterDao()
        rosterDao.upsertStudents(
            students.map {
                StudentEntity(
                    id = it.id,
                    rollNumber = it.rollNumber,
                    fullName = it.fullName,
                    remoteId = it.remoteId,
                )
            },
        )
        rosterDao.upsertClasses(
            classes.map {
                SchoolClassEntity(
                    id = it.id,
                    name = it.name,
                    section = it.section,
                    subject = it.subject,
                    teacherId = it.teacherId,
                    remoteId = it.remoteId,
                )
            },
        )
        rosterDao.insertEnrollments(
            classes.flatMap { cls ->
                cls.studentIds.map { EnrollmentEntity(classId = cls.id, studentId = it) }
            },
        )

        database.materialDao().upsertAll(
            materials.map {
                StudyMaterialEntity(
                    id = it.id,
                    teacherId = it.teacherId,
                    classId = it.classId,
                    title = it.title,
                    kind = it.kind,
                    // localPath stays null until the file is actually needed;
                    // MaterialRepository.ensureLocalFile() copies it out of
                    // assets on demand, mirroring the Phase 2 download.
                    localPath = null,
                    remoteUrl = it.remoteUrl,
                    seedAssetFile = it.assetFile,
                    remoteId = it.remoteId,
                )
            },
        )
    }

    private inline fun <reified T> readAsset(path: String): T =
        context.assets.open(path).bufferedReader().use { json.decodeFromString<T>(it.readText()) }

    private companion object {
        const val TAG = "DatabaseSeeder"
    }
}
