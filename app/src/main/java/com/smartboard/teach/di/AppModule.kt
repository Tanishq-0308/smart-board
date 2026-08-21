package com.smartboard.teach.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartboard.teach.data.local.SmartBoardDatabase
import com.smartboard.teach.data.local.dao.AttendanceDao
import com.smartboard.teach.data.local.dao.AuthDao
import com.smartboard.teach.data.local.dao.BoardDao
import com.smartboard.teach.data.local.dao.MaterialDao
import com.smartboard.teach.data.local.dao.NotesDao
import com.smartboard.teach.data.local.dao.RosterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmartBoardDatabase =
        Room.databaseBuilder(context, SmartBoardDatabase::class.java, SmartBoardDatabase.NAME)
            // Foreign keys drive the CASCADE deletes that keep strokes and
            // text boxes from outliving their page.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Real migration, never a destructive fallback: a board may hold a
            // term of lesson pages and wiping them on upgrade is indefensible.
            .addMigrations(
                SmartBoardDatabase.MIGRATION_1_2,
                SmartBoardDatabase.MIGRATION_2_3,
                SmartBoardDatabase.MIGRATION_3_4,
                SmartBoardDatabase.MIGRATION_4_5,
                SmartBoardDatabase.MIGRATION_5_6,
                SmartBoardDatabase.MIGRATION_6_7,
            )
            .build()

    @Provides fun provideBoardDao(db: SmartBoardDatabase): BoardDao = db.boardDao()
    @Provides fun provideNotesDao(db: SmartBoardDatabase): NotesDao = db.notesDao()
    @Provides fun provideAuthDao(db: SmartBoardDatabase): AuthDao = db.authDao()
    @Provides fun provideRosterDao(db: SmartBoardDatabase): RosterDao = db.rosterDao()
    @Provides fun provideAttendanceDao(db: SmartBoardDatabase): AttendanceDao = db.attendanceDao()
    @Provides fun provideMaterialDao(db: SmartBoardDatabase): MaterialDao = db.materialDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Vision requests are genuinely slow; a short read timeout would fail
        // perfectly good calls.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
