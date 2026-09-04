package com.visualizerstudio.app.di

import com.visualizerstudio.app.data.local.RenderTaskDao
import com.visualizerstudio.app.domain.repository.RenderTaskRepository
import com.visualizerstudio.app.domain.repository.RenderTaskRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // Disediakan via @Provides (bukan @Binds ke interface) supaya konsumer yang butuh
    // method tambahan (upsertTask/observeAllTasks di luar kontrak beku A.4.1) tetap bisa
    // inject `RenderTaskRepositoryImpl` langsung, sedangkan konsumer yang cukup kontrak
    // beku inject `RenderTaskRepository` seperti biasa — keduanya resolve ke instance yang
    // sama karena @Singleton.
    @Provides
    @Singleton
    fun provideRenderTaskRepositoryImpl(dao: RenderTaskDao): RenderTaskRepositoryImpl =
        RenderTaskRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideRenderTaskRepository(impl: RenderTaskRepositoryImpl): RenderTaskRepository = impl
}
