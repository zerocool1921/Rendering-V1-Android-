package com.visualizerstudio.app.di

import com.visualizerstudio.app.render.ffmpeg.spectrum.CustomSpectrumRenderer
import com.visualizerstudio.app.render.gl.GlEsSpectrumRendererAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Extension Point Pattern (Appendix A.5 blueprint) — "colok" [GlEsSpectrumRendererAdapter] milik
 * Fase 3 ke `Set<CustomSpectrumRenderer>` yang sudah dideklarasikan KOSONG oleh
 * `CustomSpectrumRendererMultibindModule` (Fase 2). FILE BARU — tidak pernah menyentuh
 * `RenderWorker.kt`, `CustomSpectrumRenderer.kt`, maupun `CustomSpectrumRendererMultibindModule.kt`
 * milik Fase 2 (aturan besi section 0.1).
 *
 * Begitu file ini digabung ke folder project yang sama (section 0.3), Dagger otomatis mendeteksi
 * modul multibind ini saat compile & `Set<CustomSpectrumRenderer>` yang di-inject `RenderWorker`
 * otomatis berisi 1 elemen (implementasi Fase 3 ini) — tanpa sebaris pun kode di `RenderWorker.kt`
 * perlu berubah.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GlEsSpectrumRendererBindModule {

    @Binds
    @IntoSet
    abstract fun bindGlEsSpectrumRenderer(impl: GlEsSpectrumRendererAdapter): CustomSpectrumRenderer
}
