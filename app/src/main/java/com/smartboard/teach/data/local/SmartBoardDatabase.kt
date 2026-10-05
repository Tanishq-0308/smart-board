package com.smartboard.teach.data.local

import com.smartboard.teach.R
import com.smartboard.teach.core.util.AppText
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.smartboard.teach.domain.model.BoardCanvasStyle
import androidx.sqlite.execSQL
import com.smartboard.teach.data.local.dao.AttendanceDao
import com.smartboard.teach.data.local.dao.AuthDao
import com.smartboard.teach.data.local.dao.BoardDao
import com.smartboard.teach.data.local.dao.MaterialDao
import com.smartboard.teach.data.local.dao.NotesDao
import com.smartboard.teach.data.local.dao.RosterDao
import com.smartboard.teach.data.local.entity.AttendanceRecordEntity
import com.smartboard.teach.data.local.entity.AttendanceSessionEntity
import com.smartboard.teach.data.local.entity.BoardBackgroundEntity
import com.smartboard.teach.data.local.entity.BoardPageEntity
import com.smartboard.teach.data.local.entity.LessonEntity
import com.smartboard.teach.data.local.entity.MediaDwellEntity
import com.smartboard.teach.data.local.entity.TimetableSlotEntity
import com.smartboard.teach.data.local.entity.ContainerCellEntity
import com.smartboard.teach.data.local.entity.ContainerEntity
import com.smartboard.teach.data.local.entity.EnrollmentEntity
import com.smartboard.teach.data.local.entity.NoteDocumentEntity
import com.smartboard.teach.data.local.entity.SchoolClassEntity
import com.smartboard.teach.data.local.entity.StrokeEntity
import com.smartboard.teach.data.local.entity.StudentEntity
import com.smartboard.teach.data.local.entity.StudyMaterialEntity
import com.smartboard.teach.data.local.entity.TeacherEntity
import com.smartboard.teach.data.local.entity.TextBoxEntity

@Database(
    entities = [
        BoardPageEntity::class,
        LessonEntity::class,
        StrokeEntity::class,
        TextBoxEntity::class,
        BoardBackgroundEntity::class,
        ContainerEntity::class,
        ContainerCellEntity::class,
        NoteDocumentEntity::class,
        TeacherEntity::class,
        SchoolClassEntity::class,
        StudentEntity::class,
        EnrollmentEntity::class,
        AttendanceSessionEntity::class,
        AttendanceRecordEntity::class,
        StudyMaterialEntity::class,
        MediaDwellEntity::class,
        TimetableSlotEntity::class,
    ],
    version = 9,
    // Schemas are committed to app/schemas so Phase 2 can write real
    // migrations against a known baseline rather than guessing.
    exportSchema = true,
)
abstract class SmartBoardDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun notesDao(): NotesDao
    abstract fun authDao(): AuthDao
    abstract fun rosterDao(): RosterDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun materialDao(): MaterialDao

    companion object {
        const val NAME = "smartboard.db"

        /**
         * v1 -> v2: camera state per page, for the infinite canvas.
         *
         * A real migration rather than a destructive fallback: boards may
         * already hold a term of lesson pages, and silently wiping them on
         * upgrade would be indefensible. Defaults put existing pages at the
         * origin at 100%, which is exactly where their content already is.
         */
        /** Background placement: position, scale and rotation. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE board_backgrounds ADD COLUMN x REAL NOT NULL DEFAULT 0.0",
                )
                connection.execSQL(
                    "ALTER TABLE board_backgrounds ADD COLUMN y REAL NOT NULL DEFAULT 0.0",
                )
                connection.execSQL(
                    "ALTER TABLE board_backgrounds ADD COLUMN scale REAL NOT NULL DEFAULT 1.0",
                )
                connection.execSQL(
                    "ALTER TABLE board_backgrounds ADD COLUMN rotation REAL NOT NULL DEFAULT 0.0",
                )
            }
        }

        /**
         * v3 -> v4: containers (tables and mindmaps) and the stroke tag that
         * binds handwriting to a cell.
         *
         * `containerId` MUST stay nullable to match `StrokeEntity.containerId:
         * String?` — Room compares the schema hash on open, and a NOT NULL
         * column here throws on upgraded installs while a fresh install works
         * perfectly, which makes it look like anything but a schema problem.
         *
         * `left` and `right` are quoted because SQLite treats them as keywords
         * in some contexts.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE strokes ADD COLUMN containerId TEXT DEFAULT NULL",
                )
                connection.execSQL(
                    "ALTER TABLE strokes ADD COLUMN cellIndex INTEGER NOT NULL DEFAULT -1",
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `containers` (
                      `id` TEXT NOT NULL,
                      `pageId` TEXT NOT NULL,
                      `orderIndex` INTEGER NOT NULL,
                      `kind` TEXT NOT NULL,
                      `x` REAL NOT NULL,
                      `y` REAL NOT NULL,
                      `rows` INTEGER NOT NULL,
                      `cols` INTEGER NOT NULL,
                      `strokeColorArgb` INTEGER NOT NULL,
                      `lineWidthPx` REAL NOT NULL,
                      PRIMARY KEY(`id`),
                      FOREIGN KEY(`pageId`) REFERENCES `board_pages`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_containers_pageId` " +
                        "ON `containers` (`pageId`)",
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `container_cells` (
                      `containerId` TEXT NOT NULL,
                      `cellIndex` INTEGER NOT NULL,
                      `left` REAL NOT NULL,
                      `top` REAL NOT NULL,
                      `right` REAL NOT NULL,
                      `bottom` REAL NOT NULL,
                      `row` INTEGER NOT NULL,
                      `col` INTEGER NOT NULL,
                      PRIMARY KEY(`containerId`, `cellIndex`),
                      FOREIGN KEY(`containerId`) REFERENCES `containers`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_container_cells_containerId` " +
                        "ON `container_cells` (`containerId`)",
                )
            }
        }

        /**
         * v4 -> v5: inserted media.
         *
         * Nullable, so every existing frame container reads back as one with
         * no media rather than needing a backfill.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE containers ADD COLUMN mediaPath TEXT DEFAULT NULL",
                )
            }
        }

        /**
         * v5 -> v6: per-page paper colour and grid.
         *
         * Defaults reproduce the previous fixed board surface with no grid, so
         * every existing page reads back looking exactly as it did.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE board_pages ADD COLUMN canvasColorArgb INTEGER NOT NULL " +
                        "DEFAULT ${BoardCanvasStyle.DEFAULT_COLOR_ARGB}",
                )
                connection.execSQL(
                    "ALTER TABLE board_pages ADD COLUMN gridStyle TEXT NOT NULL DEFAULT 'NONE'",
                )
                connection.execSQL(
                    "ALTER TABLE board_pages ADD COLUMN gridColorArgb INTEGER DEFAULT NULL",
                )
                connection.execSQL(
                    "ALTER TABLE board_pages ADD COLUMN gridSpacing REAL NOT NULL " +
                        "DEFAULT ${BoardCanvasStyle.DEFAULT_SPACING}",
                )
            }
        }

        /**
         * v6 -> v7: named lessons.
         *
         * Sessions already existed as a bare sessionId on pages; this only
         * gives them a name. A session with no row here is an unsaved working
         * session, which is what every existing install has — so nothing is
         * backfilled and the board keeps resuming exactly as before.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS lessons (" +
                        "sessionId TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
            }
        }

        /**
         * v7 -> v8: lessons can be automatically named, and every board that
         * already has content but was never named is recovered as an
         * "Untitled lesson" — before this, such boards were saved but could
         * not be found again from the Open list.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE lessons ADD COLUMN autoNamed INTEGER NOT NULL DEFAULT 0",
                )
                connection.prepare(
                    "INSERT INTO lessons (sessionId, name, createdAt, updatedAt, autoNamed) " +
                        "SELECT p.sessionId, ?, MIN(p.createdAt), MAX(p.updatedAt), 1 " +
                        "FROM board_pages p " +
                        "WHERE p.sessionId NOT IN (SELECT sessionId FROM lessons) AND (" +
                        "EXISTS (SELECT 1 FROM strokes s JOIN board_pages q ON s.pageId = q.id " +
                        "WHERE q.sessionId = p.sessionId) OR " +
                        "EXISTS (SELECT 1 FROM text_boxes t JOIN board_pages q ON t.pageId = q.id " +
                        "WHERE q.sessionId = p.sessionId) OR " +
                        "EXISTS (SELECT 1 FROM containers c JOIN board_pages q ON c.pageId = q.id " +
                        "WHERE q.sessionId = p.sessionId)) " +
                        "GROUP BY p.sessionId",
                ).use { statement ->
                    statement.bindText(1, AppText.get(R.string.lesson_untitled))
                    statement.step()
                }
            }
        }

        /**
         * v8 -> v9: the lesson pack.
         *
         * - `media_dwell`: screen time per PDF page or picture.
         * - `containers.label`: where a picture came from.
         * - `timetable_slots`: the teacher's ERP timetable.
         * - `note_documents.lessonId / packJson`: notes and assignment per lesson.
         *
         * Also drops the pre-ERP demo roster (seed ids like t-001, c-001). The
         * ERP refresh refills the roster tables; boards and notes are untouched.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_dwell` (`targetId` TEXT NOT NULL, " +
                        "`ms` INTEGER NOT NULL, PRIMARY KEY(`targetId`))",
                )
                connection.execSQL("ALTER TABLE containers ADD COLUMN label TEXT")
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timetable_slots` (`rowId` INTEGER PRIMARY KEY " +
                        "AUTOINCREMENT NOT NULL, `classId` TEXT NOT NULL, `subjectId` TEXT, " +
                        "`subjectName` TEXT, `dayOfWeek` INTEGER NOT NULL, `startTime` TEXT, `endTime` TEXT)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_timetable_slots_classId` ON `timetable_slots` (`classId`)",
                )
                connection.execSQL("ALTER TABLE note_documents ADD COLUMN lessonId TEXT")
                connection.execSQL("ALTER TABLE note_documents ADD COLUMN packJson TEXT")

                val seed = "GLOB '[tcsm]-[0-9][0-9][0-9]'"
                connection.execSQL(
                    "DELETE FROM attendance_sessions WHERE classId GLOB 'c-[0-9][0-9][0-9]'",
                )
                connection.execSQL("DELETE FROM enrollments WHERE classId GLOB 'c-[0-9][0-9][0-9]'")
                connection.execSQL("DELETE FROM classes WHERE id $seed")
                connection.execSQL("DELETE FROM students WHERE id $seed")
                connection.execSQL("DELETE FROM study_materials WHERE id $seed")
                connection.execSQL("DELETE FROM teachers WHERE id $seed")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE board_pages ADD COLUMN cameraOffsetX REAL NOT NULL DEFAULT 0.0",
                )
                connection.execSQL(
                    "ALTER TABLE board_pages ADD COLUMN cameraOffsetY REAL NOT NULL DEFAULT 0.0",
                )
                connection.execSQL(
                    "ALTER TABLE board_pages ADD COLUMN cameraZoom REAL NOT NULL DEFAULT 1.0",
                )
            }
        }
    }
}
