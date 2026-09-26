package com.smartboard.teach.di

import com.smartboard.teach.data.ink.MlKitInkRecognizer
import com.smartboard.teach.domain.engine.InkRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Recognition/AI engines. Features see only the domain interfaces; swapping a
 * vendor is a one-line change here, like the repository swaps next door.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds
    @Singleton
    abstract fun bindInkRecognizer(impl: MlKitInkRecognizer): InkRecognizer
}
