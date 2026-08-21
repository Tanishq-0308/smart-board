package com.smartboard.teach.di

import com.smartboard.teach.data.repository.BoardRepositoryImpl
import com.smartboard.teach.data.repository.LocalAttendanceRepository
import com.smartboard.teach.data.repository.LocalAuthRepository
import com.smartboard.teach.data.repository.LocalMaterialRepository
import com.smartboard.teach.data.repository.LocalRosterRepository
import com.smartboard.teach.data.remote.openai.OpenAiClient
import com.smartboard.teach.data.remote.openai.OpenAiLookupClient
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
 * ★ THE PHASE 1 -> PHASE 2 SWAP POINT ★
 *
 * Every ERP-backed capability is bound here to a Local* implementation reading
 * seeded data. When the ERP/LMS integration lands, this file is the diff:
 *
 *     LocalAuthRepository      -> ErpAuthRepository
 *     LocalRosterRepository    -> ErpRosterRepository
 *     LocalAttendanceRepository-> ErpAttendanceRepository
 *     LocalMaterialRepository  -> ErpMaterialRepository
 *
 * If Phase 2 requires edits to any ViewModel or screen, the seam was drawn
 * wrong and the fix belongs in the repository layer, not the UI.
 *
 * Board and Notes repositories are deliberately NOT here — they are
 * device-local by design and never gain a remote variant.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: LocalAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindRosterRepository(impl: LocalRosterRepository): RosterRepository

    @Binds
    @Singleton
    abstract fun bindAttendanceRepository(impl: LocalAttendanceRepository): AttendanceRepository

    @Binds
    @Singleton
    abstract fun bindMaterialRepository(impl: LocalMaterialRepository): MaterialRepository

    // --- Permanently local; not part of the Phase 2 swap ---

    @Binds
    @Singleton
    abstract fun bindBoardRepository(impl: BoardRepositoryImpl): BoardRepository

    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: NotesRepositoryImpl): NotesRepository

    /**
     * Phase 1 calls OpenAI directly with a key compiled into the APK.
     * Phase 2 MUST swap this for a server-proxy implementation so the key
     * stops shipping to devices — one line, exactly like the ERP bindings.
     */
    @Binds
    @Singleton
    abstract fun bindNotesAiService(impl: OpenAiClient): NotesAiService

    /**
     * Visual lookup ("what is this?" on a lassoed region). Same Phase 2
     * obligation as [bindNotesAiService] — both must move behind the school's
     * proxy together, since they share the compiled-in key.
     */
    @Binds
    @Singleton
    abstract fun bindVisualLookupService(impl: OpenAiLookupClient): VisualLookupService
}
