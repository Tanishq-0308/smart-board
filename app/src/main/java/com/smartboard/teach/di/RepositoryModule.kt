package com.smartboard.teach.di

import com.smartboard.teach.data.remote.erp.ErpLookupService
import com.smartboard.teach.data.remote.erp.ErpNotesAiService
import com.smartboard.teach.data.repository.BoardRepositoryImpl
import com.smartboard.teach.data.repository.ErpAttendanceRepository
import com.smartboard.teach.data.repository.ErpAuthRepository
import com.smartboard.teach.data.repository.ErpMaterialRepository
import com.smartboard.teach.data.repository.ErpRosterRepository
import com.smartboard.teach.data.repository.NotesRepositoryImpl
import com.smartboard.teach.domain.repository.AttendanceRepository
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.BoardRepository
import com.smartboard.teach.domain.repository.MaterialRepository
import com.smartboard.teach.domain.repository.NotesAiService
import com.smartboard.teach.domain.repository.NotesRepository
import com.smartboard.teach.domain.repository.RosterRepository
import com.smartboard.teach.domain.repository.VisualLookupService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Where each capability comes from. Everything school-owned (sign-in, classes,
 * rosters, attendance, materials, AI) is the school's Skolar ERP; screens read
 * a Room cache of it, so they never know. Boards and notes stay on the device.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: ErpAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindRosterRepository(impl: ErpRosterRepository): RosterRepository

    @Binds
    @Singleton
    abstract fun bindAttendanceRepository(impl: ErpAttendanceRepository): AttendanceRepository

    @Binds
    @Singleton
    abstract fun bindMaterialRepository(impl: ErpMaterialRepository): MaterialRepository

    @Binds
    @Singleton
    abstract fun bindBoardRepository(impl: BoardRepositoryImpl): BoardRepository

    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: NotesRepositoryImpl): NotesRepository

    /** Through the ERP, which holds the AI keys and meters each call. */
    @Binds
    @Singleton
    abstract fun bindNotesAiService(impl: ErpNotesAiService): NotesAiService

    @Binds
    @Singleton
    abstract fun bindVisualLookupService(impl: ErpLookupService): VisualLookupService
}
