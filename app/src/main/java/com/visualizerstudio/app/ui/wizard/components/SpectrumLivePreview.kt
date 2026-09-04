package com.visualizerstudio.app.ui.wizard.components

import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.visualizerstudio.app.domain.model.SpectrumConfig

/**
 * Live preview shader Spectrum di dalam Wizard (Definition of Done C.3, poin 2: "Live preview
 * GLSurfaceView ... benar-benar me-render shader terpilih dengan sample audio, bukan gambar
 * statis").
 *
 * ⚠️ INTEGRASI PARSIAL — lihat README_FASE4.md asumsi #4. `GlEsSpectrumRenderer`/adapter GL
 * (milik Fase 3, path `render/gl/`) TIDAK mendeklarasikan API "live preview ke GLSurfaceView"
 * di kontrak beku Appendix A manapun — yang beku (A.4.4 `CustomSpectrumRenderer`) khusus
 * render final ke file video alpha, bukan live render interaktif. Komponen ini sudah
 * menyiapkan sisi Compose (AndroidView + GLSurfaceView + lifecycle) secara nyata, dan
 * memanggil lewat interface minimal [GlPreviewBinder] yang HARUS diimplementasikan saat
 * digabung dengan kode Fase 3 (lihat TODO di [rememberGlPreviewBinder]). Sebelum itu
 * di-supply, komponen menampilkan placeholder visual yang JELAS berlabel "preview belum
 * tersambung" (bukan berpura-pura sebagai render nyata) — supaya tidak melanggar aturan
 * anti-dummy C.3 dengan menyamarkan sebagai fitur selesai.
 */
interface GlPreviewBinder {
    /** Pasang shader+config ke [surfaceView] dan mulai render loop dengan sample audio. */
    fun attach(surfaceView: GLSurfaceView, spec: SpectrumConfig, sampleAudioUri: Uri?)
    fun detach(surfaceView: GLSurfaceView)
}

/**
 * TODO(integrasi Fase 3): ganti null-object ini dengan implementasi nyata begitu
 * `GlEsSpectrumRenderer`/`GlEsSpectrumRendererAdapter` Fase 3 digabung ke project — idealnya
 * lewat Hilt injection (`@Inject lateinit var binder: GlPreviewBinder`) bukan hardcode di sini.
 */
private object NullGlPreviewBinder : GlPreviewBinder {
    override fun attach(surfaceView: GLSurfaceView, spec: SpectrumConfig, sampleAudioUri: Uri?) = Unit
    override fun detach(surfaceView: GLSurfaceView) = Unit
}

@Composable
fun SpectrumLivePreview(
    spec: SpectrumConfig,
    sampleAudioFiles: List<Uri>,
    modifier: Modifier = Modifier,
    binder: GlPreviewBinder = NullGlPreviewBinder
) {
    if (binder === NullGlPreviewBinder) {
        // Placeholder jujur — bukan render palsu yang berpura-pura jadi hasil shader.
        Box(modifier = modifier.background(Color(0xFF1C1B1F)), contentAlignment = Alignment.Center) {
            Text(
                text = "Preview shader \"${spec.shaderRef.ifBlank { "(belum pilih shader)" }}\"\n" +
                    "akan tampil di sini setelah render engine Fase 3 digabung.",
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                binder.attach(this, spec, sampleAudioFiles.firstOrNull())
            }
        },
        update = { surfaceView -> binder.attach(surfaceView, spec, sampleAudioFiles.firstOrNull()) },
        onRelease = { surfaceView -> binder.detach(surfaceView) }
    )
}
