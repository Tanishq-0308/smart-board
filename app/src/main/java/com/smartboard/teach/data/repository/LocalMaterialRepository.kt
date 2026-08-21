package com.smartboard.teach.data.repository

import android.content.Context
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.data.local.dao.MaterialDao
import com.smartboard.teach.data.local.entity.StudyMaterialEntity
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.MaterialKind
import com.smartboard.teach.domain.model.StudyMaterial
import com.smartboard.teach.domain.repository.MaterialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMaterialRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val materialDao: MaterialDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MaterialRepository {

    override fun materialsForTeacher(teacherId: String): Flow<List<StudyMaterial>> =
        materialDao.observeForTeacher(teacherId).map { rows -> rows.map { it.toDomain() } }

    override fun materialsForClass(classId: String): Flow<List<StudyMaterial>> =
        materialDao.observeForClass(classId).map { rows -> rows.map { it.toDomain() } }

    /**
     * Materializes a study material onto disk and returns the file.
     *
     * Phase 1 copies it out of assets; Phase 2 downloads it from the LMS and
     * caches it at the same path. Both are "make sure this file exists locally
     * and hand it back", which is why the signature does not change — the
     * material viewer and the board's PDF background import both call this and
     * neither will need editing.
     */
    override suspend fun ensureLocalFile(materialId: String): AppResult<File> =
        withContext(ioDispatcher) {
            try {
                val entity = materialDao.getById(materialId)
                    ?: return@withContext AppResult.Failure(AppError.NotFound())

                // Already materialized on a previous open.
                entity.localPath?.let { path ->
                    val existing = File(path)
                    if (existing.exists() && existing.length() > 0) {
                        return@withContext AppResult.Success(existing)
                    }
                }

                val assetName = entity.seedAssetFile
                    ?: return@withContext AppResult.Failure(
                        AppError.NotFound("This material has no file attached."),
                    )

                val target = File(materialsDir(), "${entity.id}_$assetName")
                context.assets.open("seed/files/$assetName").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }

                materialDao.setLocalPath(entity.id, target.absolutePath, target.length())
                AppResult.Success(target)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage("Could not open the material: ${t.message}"))
            }
        }

    private fun materialsDir(): File =
        File(context.filesDir, "materials").apply { mkdirs() }
}

internal fun StudyMaterialEntity.toDomain() = StudyMaterial(
    id = id,
    teacherId = teacherId,
    classId = classId,
    title = title,
    kind = runCatching { MaterialKind.valueOf(kind) }.getOrDefault(MaterialKind.PDF),
    localPath = localPath,
    remoteUrl = remoteUrl,
    sizeBytes = sizeBytes,
    remoteId = remoteId,
)
