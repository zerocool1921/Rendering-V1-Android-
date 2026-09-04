package com.visualizerstudio.app.ui.shaderstudio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import com.visualizerstudio.app.render.gl.EglOffscreenContext
import com.visualizerstudio.app.render.gl.ShaderProgramCache
import com.visualizerstudio.app.domain.model.ShaderTemplateEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Render 1 frame nyata dari shader tersimpan (via [EglOffscreenContext] + [ShaderProgramCache],
 * keduanya dimiliki Fase 3 — dipakai apa adanya lewat import, tidak diedit di sini) untuk jadi
 * thumbnail galeri. INI BUKAN gambar placeholder statis: tiap entry di galeri memanggil ini
 * dengan `glslCode`-nya sendiri, jadi tiap thumbnail benar-benar berbeda sesuai isi shadernya
 * (lihat Appendix C.2 checklist "galeri shader menampilkan thumbnail hasil render nyata").
 */
@Singleton
class ShaderThumbnailRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eglContext: EglOffscreenContext,
    private val programCache: ShaderProgramCache
) {
    companion object {
        private const val THUMB_SIZE = 256
    }

    /** @return Uri file PNG thumbnail hasil render nyata, null kalau shader gagal compile. */
    suspend fun renderThumbnail(entity: ShaderTemplateEntity): Uri? = withContext(Dispatchers.Default) {
        val sampleBars = FloatArray(120) { i ->
            // sample energi sintetis (mensimulasikan musik) supaya thumbnail Spectrum tidak flat
            val base = 0.35f + 0.5f * kotlin.math.abs(kotlin.math.sin(i * 0.21))
            (base + Random(entity.id.hashCode() + i).nextFloat() * 0.15f).coerceIn(0f, 1f)
        }
        val ok = eglContext.withOffscreenSurface(THUMB_SIZE, THUMB_SIZE) { egl ->
            val program = programCache.getOrCompile(entity.id, entity.glslCode) ?: return@withOffscreenSurface false
            program.use()
            program.setUniform2f("u_resolution", THUMB_SIZE.toFloat(), THUMB_SIZE.toFloat())
            program.setUniform1f("u_time", 1.2f)
            program.setUniform1fArray("u_bars", sampleBars)
            program.setUniform1i("u_num_bars", 120)
            program.setUniform3f("u_color", 1f, 1f, 1f)
            program.setUniform1f("u_zoom_factor", 0f)
            program.setUniform1f("u_shear_x", 0f)
            program.setUniform1f("u_shear_y", 0f)
            program.setUniform1f("u_total_duration", 60f)
            program.setUniform1f("u_loop_duration", 120f)
            program.setUniform1i("u_effect_mode", 0)
            program.drawFullscreenQuad()
            true
        }
        if (!ok) return@withContext null

        val bitmap = eglContext.readPixelsAsBitmap(THUMB_SIZE, THUMB_SIZE)
        val outFile = File(context.filesDir, "shader_thumbnails/${entity.id}.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bitmap.recycle()
        outFile.toUri()
    }
}
