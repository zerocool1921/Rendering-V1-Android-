package com.visualizerstudio.app.di

import com.visualizerstudio.app.render.ffmpeg.scorer.EncoderCompatibilityScorer
import com.visualizerstudio.app.render.ffmpeg.scorer.ExynosEncoderScorer
import com.visualizerstudio.app.render.ffmpeg.scorer.GenericFallbackEncoderScorer
import com.visualizerstudio.app.render.ffmpeg.scorer.MediatekEncoderScorer
import com.visualizerstudio.app.render.ffmpeg.scorer.SnapdragonEncoderScorer
import com.visualizerstudio.app.render.ffmpeg.scorer.TensorEncoderScorer
import com.visualizerstudio.app.render.ffmpeg.scorer.UnisocEncoderScorer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Langkah 2 dari Extension Point Pattern (Appendix A.5) — "colok" tiap implementasi
 * `EncoderCompatibilityScorer` ke dalam Set lewat `@Binds @IntoSet`. File ini murni milik
 * Fase 6, tidak pernah menyentuh `EncoderCompatibilityScorerMultibindModule.kt` di atas.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EncoderCompatibilityScorerBindModule {

    @Binds
    @IntoSet
    abstract fun bindSnapdragon(impl: SnapdragonEncoderScorer): EncoderCompatibilityScorer

    @Binds
    @IntoSet
    abstract fun bindExynos(impl: ExynosEncoderScorer): EncoderCompatibilityScorer

    @Binds
    @IntoSet
    abstract fun bindMediatek(impl: MediatekEncoderScorer): EncoderCompatibilityScorer

    @Binds
    @IntoSet
    abstract fun bindUnisoc(impl: UnisocEncoderScorer): EncoderCompatibilityScorer

    @Binds
    @IntoSet
    abstract fun bindTensor(impl: TensorEncoderScorer): EncoderCompatibilityScorer

    @Binds
    @IntoSet
    abstract fun bindGeneric(impl: GenericFallbackEncoderScorer): EncoderCompatibilityScorer
}
