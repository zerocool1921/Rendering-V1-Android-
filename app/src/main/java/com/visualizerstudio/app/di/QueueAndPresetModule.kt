package com.visualizerstudio.app.di

import com.visualizerstudio.app.data.repository.PresetRepositoryImpl
import com.visualizerstudio.app.data.repository.RenderQueueRepositoryImpl
import com.visualizerstudio.app.domain.repository.PresetRepository
import com.visualizerstudio.app.domain.repository.RenderQueueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * File BARU milik Fase 4 (tidak mengedit `di/RenderModule.kt` milik Fase 2). Hanya mem-bind
 * 2 interface ekstensi ke implementasinya — bukan Extension Point Pattern (A.5), karena ini
 * bukan titik sambung opsional lintas-fase, melainkan dependency biasa yang Fase 4 sendiri
 * definisikan dan konsumsi.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class QueueAndPresetModule {

    @Binds
    abstract fun bindRenderQueueRepository(impl: RenderQueueRepositoryImpl): RenderQueueRepository

    @Binds
    abstract fun bindPresetRepository(impl: PresetRepositoryImpl): PresetRepository
}
