package com.smartboard.teach.data.repository

import android.content.Context
import android.util.Base64
import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppError
import com.smartboard.teach.core.util.AppResult
import com.smartboard.teach.core.util.AppText
import com.smartboard.teach.data.local.dao.MaterialDao
import com.smartboard.teach.data.local.entity.StudyMaterialEntity
import com.smartboard.teach.data.remote.erp.ErpApi
import com.smartboard.teach.data.remote.erp.MaterialFileDto
import com.smartboard.teach.di.IoDispatcher
import com.smartboard.teach.domain.model.MaterialKind
import com.smartboard.teach.domain.model.StudyMaterial
import com.smartboard.teach.domain.repository.MaterialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Class materials from the ERP (listed during the roster refresh), downloaded
 * on first open and kept on the board.
 *
 * `GET /api/erp/course-materials/{id}/file` answers JSON, not a stream: the
 * bytes in base64, or an https link when the school stores files in S3.
 */
@Singleton
class ErpMaterialRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val materialDao: MaterialDao,
    private val api: ErpApi,
    private val client: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MaterialRepository {

    override fun materialsForTeacher(teacherId: String): Flow<List<StudyMaterial>> =
        materialDao.observeForTeacher(teacherId).map { rows -> rows.map { it.toDomain() } }

    override fun materialsForClass(classId: String): Flow<List<StudyMaterial>> =
        materialDao.observeForClass(classId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun ensureLocalFile(materialId: String): AppResult<File> {
        val entity = materialDao.getById(materialId) ?: return AppResult.Failure(AppError.NotFound())
        entity.localPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }?.let { return AppResult.Success(it) }
        }
        val url = entity.remoteUrl
            ?: return AppResult.Failure(AppError.NotFound(AppText.get(R.string.error_material_no_file)))

        val file = when (val r = api.get(url, MaterialFileDto.serializer())) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }
        return withContext(ioDispatcher) {
            try {
                val safeName = file.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "material.pdf" }
                val target = File(File(context.filesDir, "materials").apply { mkdirs() }, "${entity.id}_$safeName")
                if (file.fileData.startsWith("https://")) {
                    client.newCall(Request.Builder().url(file.fileData).build()).execute().use { res ->
                        if (!res.isSuccessful) return@withContext AppResult.Failure(AppError.Http(res.code,
                            AppText.get(R.string.error_material_open, res.code.toString())))
                        target.outputStream().use { out -> res.body.byteStream().copyTo(out) }
                    }
                } else {
                    target.writeBytes(Base64.decode(file.fileData, Base64.DEFAULT))
                }
                materialDao.setLocalPath(entity.id, target.absolutePath, target.length())
                AppResult.Success(target)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage(AppText.get(R.string.error_material_open, t.message.orEmpty())))
            }
        }
    }
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
