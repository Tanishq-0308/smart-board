package com.smartboard.teach.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.smartboard.teach.data.local.entity.EnrollmentEntity
import com.smartboard.teach.data.local.entity.SchoolClassEntity
import com.smartboard.teach.data.local.entity.StudentEntity
import com.smartboard.teach.data.local.entity.TeacherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthDao {
    @Query("SELECT * FROM teachers WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): TeacherEntity?

    @Query("SELECT * FROM teachers WHERE id = :id")
    suspend fun findById(id: String): TeacherEntity?

    @Query("SELECT * FROM teachers WHERE id = :id")
    fun observeById(id: String): Flow<TeacherEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(teachers: List<TeacherEntity>)

    @Query("SELECT COUNT(*) FROM teachers")
    suspend fun count(): Int
}

/**
 * Class list rows carry a computed student count so the classes grid doesn't
 * need a second query per row.
 */
data class ClassWithCount(
    val id: String,
    val name: String,
    val section: String?,
    val subject: String?,
    val teacherId: String,
    val remoteId: String?,
    val studentCount: Int,
)

@Dao
interface RosterDao {

    @Query(
        """
        SELECT c.id, c.name, c.section, c.subject, c.teacherId, c.remoteId,
               (SELECT COUNT(*) FROM enrollments e WHERE e.classId = c.id) AS studentCount
        FROM classes c
        WHERE c.teacherId = :teacherId
        ORDER BY c.name ASC, c.section ASC
        """,
    )
    fun observeClassesForTeacher(teacherId: String): Flow<List<ClassWithCount>>

    @Query(
        """
        SELECT c.id, c.name, c.section, c.subject, c.teacherId, c.remoteId,
               (SELECT COUNT(*) FROM enrollments e WHERE e.classId = c.id) AS studentCount
        FROM classes c WHERE c.id = :classId
        """,
    )
    fun observeClass(classId: String): Flow<ClassWithCount?>

    @Query(
        """
        SELECT s.* FROM students s
        INNER JOIN enrollments e ON e.studentId = s.id
        WHERE e.classId = :classId
        ORDER BY CAST(s.rollNumber AS INTEGER) ASC, s.rollNumber ASC
        """,
    )
    fun observeStudentsInClass(classId: String): Flow<List<StudentEntity>>

    @Query(
        """
        SELECT s.* FROM students s
        INNER JOIN enrollments e ON e.studentId = s.id
        WHERE e.classId = :classId
        ORDER BY CAST(s.rollNumber AS INTEGER) ASC, s.rollNumber ASC
        """,
    )
    suspend fun getStudentsInClass(classId: String): List<StudentEntity>

    @Upsert
    suspend fun upsertClasses(classes: List<SchoolClassEntity>)

    @Upsert
    suspend fun upsertStudents(students: List<StudentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollments(enrollments: List<EnrollmentEntity>)

    @Query("SELECT COUNT(*) FROM classes")
    suspend fun classCount(): Int
}
