package com.smartboard.teach

import com.smartboard.teach.core.util.AppText
import android.app.Application
import com.smartboard.teach.di.ApplicationScope
import com.smartboard.teach.domain.model.AuthState
import com.smartboard.teach.domain.repository.AuthRepository
import com.smartboard.teach.domain.repository.RosterRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartBoardApp : Application() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var rosterRepository: RosterRepository

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        AppText.init(this)
        // Each time a teacher is signed in (at start-up or a fresh sign-in),
        // pull their classes, rosters and materials from the ERP. Off the main
        // thread, and never in the way of guest mode.
        appScope.launch {
            authRepository.authState.filterIsInstance<AuthState.Authenticated>()
                .map { it.teacher.id }
                .distinctUntilChanged()
                .collect { rosterRepository.refresh() }
        }
    }
}
