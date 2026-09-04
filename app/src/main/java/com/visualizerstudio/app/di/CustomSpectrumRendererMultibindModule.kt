package com.visualizerstudio.app.di

import com.visualizerstudio.app.render.ffmpeg.spectrum.CustomSpectrumRenderer
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * KONTRAK BEKU — dimiliki Fase 2 (Appendix A.5 langkah 1, Extension Point Pattern).
 *
 * Modul multibind KOSONG: hanya mendeklarasikan bahwa `Set<CustomSpectrumRenderer>` adalah
 * dependency yang valid untuk di-inject meski belum ada isinya. Fase 3 akan "mengisi" set ini
 * lewat modul BARU (`di/GlEsSpectrumRendererBindModule.kt`) tanpa pernah menyentuh file ini.
 *
 * [com.visualizerstudio.app.worker.RenderWorker] meng-inject
 * `Set<@JvmSuppressWildcards CustomSpectrumRenderer>` lewat constructor lalu memanggil
 * `.firstOrNull()` — kalau set kosong (Fase 3 belum digabung), render spectrum kustom
 * otomatis di-skip dengan aman (fallback ke filter native FFmpeg), TIDAK crash.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CustomSpectrumRendererMultibindModule {
    @Multibinds
    abstract fun bindCustomSpectrumRendererSet(): Set<CustomSpectrumRenderer>
}
