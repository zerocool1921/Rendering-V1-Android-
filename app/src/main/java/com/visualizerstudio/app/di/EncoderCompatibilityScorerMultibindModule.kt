package com.visualizerstudio.app.di

import com.visualizerstudio.app.render.ffmpeg.scorer.EncoderCompatibilityScorer
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Langkah 1 dari Extension Point Pattern (Appendix A.5) untuk `EncoderCompatibilityScorer` —
 * deklarasi bahwa `Set<EncoderCompatibilityScorer>` itu valid meski kosong, mengikuti pola
 * PERSIS sama dengan `CustomSpectrumRendererMultibindModule` (Fase 2 ↔ Fase 3).
 *
 * CATATAN LINTAS FASE: langkah ini idealnya dideklarasikan bersamaan dengan
 * `EncoderParamsProvider.kt` di Fase 2 (lihat komentar di `EncoderCompatibilityScorer.kt`).
 * Karena kontrak ini belum tercantum di Appendix A yang diberikan ke sesi ini, modul ini dibuat
 * sebagai USULAN REVISI Appendix A v3 — file BARU, tidak menabrak/mengedit satu pun file yang
 * sudah dimiliki Fase 2 di manifest A.2.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EncoderCompatibilityScorerMultibindModule {
    @Multibinds
    abstract fun bindEncoderCompatibilityScorerSet(): Set<EncoderCompatibilityScorer>
}
