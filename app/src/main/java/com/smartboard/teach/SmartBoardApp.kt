package com.smartboard.teach

import android.app.Application
import com.smartboard.teach.data.local.seed.DatabaseSeeder
import com.smartboard.teach.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartBoardApp : Application() {

    @Inject lateinit var seeder: DatabaseSeeder

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Seeding runs off the main thread and is a no-op after first launch.
        // It must never block startup: guest mode (board + notes) does not
        // depend on any of the seeded roster.
        appScope.launch { seeder.seedIfEmpty() }
    }
}
