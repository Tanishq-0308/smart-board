package com.smartboard.teach.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/**
 * Single-threaded dispatcher for PdfRenderer.
 *
 * PdfRenderer is not thread-safe and permits only one open page at a time, so
 * every call must be serialized. Confining it to one thread is simpler and
 * harder to get wrong than scattering mutexes at each call site.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PdfDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides @PdfDispatcher @Singleton
    fun providePdfDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1)

    /** Outlives any single screen — used for debounced board saves. */
    @Provides @ApplicationScope @Singleton
    fun provideApplicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
