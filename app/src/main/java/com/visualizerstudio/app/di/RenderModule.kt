package com.visualizerstudio.app.di

import com.visualizerstudio.app.render.ffmpeg.FontAssetResolver
import com.visualizerstudio.app.render.ffmpeg.FontAssetResolverImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binding komponen render dasar milik Fase 2. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RenderModule {

    @Binds
    @Singleton
    abstract fun bindFontAssetResolver(impl: FontAssetResolverImpl): FontAssetResolver
}
