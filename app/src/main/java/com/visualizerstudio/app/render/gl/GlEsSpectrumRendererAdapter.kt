package com.visualizerstudio.app.render.gl

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.visualizerstudio.app.domain.model.SpectrumConfig
import com.visualizerstudio.app.render.audio.AudioFftAnalyzer
import com.visualizerstudio.app.render.audio.BeatEnergyExtractor
import com.visualizerstudio.app.render.audio.WavDecoder
import com.visualizerstudio.app.render.ffmpeg.spectrum.CustomSpectrumRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Implementasi nyata [CustomSpectrumRenderer] — interface titik sambung A.4.4 milik Fase 2.
 * Instance ini "dicolok" ke `Set<CustomSpectrumRenderer>` milik `RenderWorker` Fase 2 lewat
 * Dagger Multibindings ([com.visualizerstudio.app.di.GlEsSpectrumRendererBindModule]) — file ini
 * TIDAK PERNAH mengedit `RenderWorker.kt` maupun `CustomSpectrumRenderer.kt` (aturan besi
 * section 0.1 & Extension Point Pattern Appendix A.5).
 *
 * Alur render satu spectrum kustom:
 * 1. Decode `audioSamplePath` (WAV hasil concat Fase 2) lewat [WavDecoder].
 * 2. Resolusi source GLSL: shader kustom user (lihat [resolveShaderSource]) atau shader bawaan
 *    (`assets/shaders/spectrum_default.glsl`) — fallback otomatis ke bawaan kalau kustom gagal
 *    compile (anti-crash, section 5.2 poin 3).
 * 3. Render tiap frame lewat [GlEsSpectrumRenderer] → tulis rawvideo RGBA ke file sementara.
 * 4. Mux rawvideo jadi WebM VP9 dengan alpha channel via FFmpegKit → [outputPath].
 * 5. Bersihkan semua resource GL/EGL & file sementara di blok `finally`.
 *
 * ASUMSI TERDOKUMENTASI (baca sebelum revisi lintas-fase):
 * [SpectrumConfig.shaderRef] untuk shader KUSTOM buatan user (Shader Studio, Fase 5) diasumsikan
 * sudah tersedia sebagai file teks GLSL di `filesDir/shaders/<shaderRef>.glsl` — Adapter ini
 * SENGAJA tidak query langsung ke Room `ShaderTemplateDao`, karena skema/nama method DAO
 * tersebut belum dibekukan sebagai kontrak resmi di Appendix A blueprint (baru disebut naratif
 * di section 3), jadi Fase 3 tidak boleh menebak-nebak isinya (melanggar semangat "kontrak beku"
 * section 0.1 aturan #2). Kalau kontrak `ShaderTemplateDao` dibekukan di revisi Appendix A
 * berikutnya (v3+), cukup ganti isi [resolveShaderSource] di file INI (file milik Fase 3, aman
 * direvisi ulang tanpa menyentuh file fase lain).
 */
class GlEsSpectrumRendererAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) : CustomSpectrumRenderer {

    companion object {
        private const val TAG = "GlEsSpectrumRendererAdapter"
        private const val BUILTIN_VERTEX_ASSET = "shaders/spectrum_vertex.glsl"
        private const val BUILTIN_FRAGMENT_ASSET = "shaders/spectrum_default.glsl"

        // Mapping nama gaya bawaan (BUILTIN_SPECTRUM_TYPES di spectrum_generator.py asli) ke
        // uniform u_spec_type di dalam spectrum_default.glsl (lihat section 6.0 & konversi id).
        private val BUILTIN_STYLE_IDS = mapOf(
            "custom_oscilloscope" to 6,
            "custom_circular_gradient" to 7,
            "custom_circular_inner_bars" to 8,
            "custom_glowing_particle_wave" to 9,
            "custom_retro_red_blocks" to 10,
            "custom_double_wave_bars" to 11,
            "custom_halftone_dots" to 12,
            "custom_clean_minimal_bars" to 13,
            "custom_bar_reflection" to 14,
            "custom_glitch_grid" to 15,
            "custom_sine_mesh" to 16,
            "custom_radial_squares" to 17,
            "custom_music_notes" to 18,
            "custom_neon_glow_reflection" to 19
        )
    }

    override suspend fun renderToAlphaVideo(
        spec: SpectrumConfig,
        durationSec: Float,
        fps: Int,
        audioSamplePath: String,
        outputPath: String
    ): Boolean = withContext(Dispatchers.Default) {
        val renderer = GlEsSpectrumRenderer(spec.width, spec.height)
        val rawTempFile = File(context.cacheDir, "spectrum_raw_${System.currentTimeMillis()}.rgba")

        try {
            val vertexSrc = readAsset(BUILTIN_VERTEX_ASSET)
            if (vertexSrc == null) {
                Log.e(TAG, "Vertex shader bawaan tidak ditemukan di assets — batal render")
                return@withContext false
            }

            val decoded = WavDecoder.read(audioSamplePath)

            var fragmentSrc = resolveShaderSource(spec.shaderRef)
            var specTypeId = builtinStyleIdFor(spec.shaderRef)
            var ready = fragmentSrc != null && renderer.initialize(vertexSrc, fragmentSrc!!)

            if (!ready) {
                Log.w(TAG, "Shader '${spec.shaderRef}' gagal dipakai, fallback ke shader bawaan default")
                fragmentSrc = readAsset(BUILTIN_FRAGMENT_ASSET)
                specTypeId = 0
                if (fragmentSrc == null) {
                    Log.e(TAG, "Shader bawaan pun tidak ditemukan di assets — batal render spectrum kustom")
                    return@withContext false
                }
                ready = renderer.initialize(vertexSrc, fragmentSrc)
            }
            if (!ready) {
                Log.e(TAG, "Gagal inisialisasi EGL/shader bawaan sekalipun — batal render spectrum kustom")
                return@withContext false
            }

            val totalFrames = (durationSec * fps).toInt().coerceAtLeast(1)
            val colorRgb = parseColorToRgbFloat(spec.color)
            val fftAnalyzer = AudioFftAnalyzer()
            val beatExtractor = BeatEnergyExtractor()

            BufferedOutputStream(FileOutputStream(rawTempFile)).use { out ->
                for (frameIdx in 0 until totalFrames) {
                    val bars = fftAnalyzer.computeBars(decoded.samples, decoded.sampleRateHz, fps, frameIdx)
                    val zoomFactor = if (spec.useBeatZoom) beatExtractor.zoomFactorFromBars(bars) else 1.0f
                    val timeSec = frameIdx.toFloat() / fps.toFloat()

                    val frameBytes = renderer.renderFrame(
                        bars = bars,
                        timeSec = timeSec,
                        colorRgb = colorRgb,
                        zoomFactor = zoomFactor,
                        shearX = spec.shearX,
                        shearY = spec.shearY,
                        specTypeId = specTypeId,
                        totalDurationSec = durationSec
                    )
                    out.write(frameBytes)
                }
            }

            muxRawToAlphaWebm(rawTempFile.absolutePath, spec.width, spec.height, fps, outputPath)
        } catch (e: Exception) {
            Log.e(TAG, "Eror render spectrum kustom GLSL: ${e.message}", e)
            false
        } finally {
            renderer.release()
            if (rawTempFile.exists()) rawTempFile.delete()
        }
    }

    /**
     * Mux rawvideo RGBA jadi WebM VP9 dengan alpha channel (`yuva420p`) via FFmpegKit — sama
     * seperti pipeline `ffmpeg_cmd` di `generate_custom_spectrum_gpu` Python, tapi lewat file
     * rawvideo sementara (bukan stdin pipe) supaya lebih stabil di lingkungan Android/FFmpegKit.
     */
    private fun muxRawToAlphaWebm(
        rawPath: String, width: Int, height: Int, fps: Int, outputPath: String
    ): Boolean {
        val cmd = "-y -f rawvideo -pix_fmt rgba -s ${width}x${height} -r $fps -i \"$rawPath\" " +
            "-c:v libvpx-vp9 -pix_fmt yuva420p -auto-alt-ref 0 -b:v 0 -crf 30 \"$outputPath\""
        val session = FFmpegKit.execute(cmd)
        val success = ReturnCode.isSuccess(session.returnCode)
        if (!success) {
            Log.e(TAG, "FFmpeg mux alpha video gagal (rc=${session.returnCode}): ${session.failStackTrace}")
        }
        return success
    }

    /**
     * Resolusi source fragment shader: kosong → shader bawaan; nama gaya bawaan (lihat
     * [BUILTIN_STYLE_IDS]) → tetap shader bawaan (beda hanya `u_spec_type`); selain itu →
     * dianggap id shader kustom hasil Shader Studio (lihat ASUMSI TERDOKUMENTASI di kelas ini).
     */
    private fun resolveShaderSource(shaderRef: String): String? {
        if (shaderRef.isBlank() || BUILTIN_STYLE_IDS.containsKey(shaderRef)) {
            return readAsset(BUILTIN_FRAGMENT_ASSET)
        }
        val customFile = File(context.filesDir, "shaders/$shaderRef.glsl")
        if (customFile.exists()) {
            return try {
                customFile.readText()
            } catch (e: Exception) {
                Log.w(TAG, "Gagal baca shader kustom '$shaderRef': ${e.message}")
                null
            }
        }
        Log.w(TAG, "Shader kustom '$shaderRef' tidak ditemukan di filesDir/shaders/ — pakai bawaan")
        return readAsset(BUILTIN_FRAGMENT_ASSET)
    }

    private fun builtinStyleIdFor(shaderRef: String): Int = BUILTIN_STYLE_IDS[shaderRef] ?: 0

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        Log.e(TAG, "Gagal baca asset $path: ${e.message}")
        null
    }

    /** Parsing warna: hex (#RRGGBB) atau nama dari 11 preset warna section 5.1c blueprint. */
    private fun parseColorToRgbFloat(colorStr: String): FloatArray {
        return try {
            val hex = if (colorStr.startsWith("#")) colorStr else namedColorToHex(colorStr)
            val c = Color.parseColor(hex)
            floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)
        } catch (e: Exception) {
            floatArrayOf(1f, 1f, 1f) // fallback putih, anti-crash
        }
    }

    private fun namedColorToHex(name: String): String = when (name.lowercase()) {
        "red" -> "#FF0000"
        "yellow" -> "#FFFF00"
        "green" -> "#00FF00"
        "blue" -> "#0000FF"
        "gold" -> "#FFD700"
        "black" -> "#000000"
        "gray", "grey" -> "#808080"
        "turquoise" -> "#40E0D0"
        "orange" -> "#FFA500"
        "pink" -> "#FFC0CB"
        "white" -> "#FFFFFF"
        else -> "#FFFFFF"
    }
}
