package com.visualizerstudio.app.di

import android.content.Context
import androidx.room.Room
import com.visualizerstudio.app.data.local.AppDatabase
import com.visualizerstudio.app.data.local.RenderTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration() // Fase 1 saja; fase lanjutan wajib migrasi eksplisit
            .build()

    @Provides
    @Singleton
    fun provideRenderTaskDao(db: AppDatabase): RenderTaskDao = db.renderTaskDao()
}
