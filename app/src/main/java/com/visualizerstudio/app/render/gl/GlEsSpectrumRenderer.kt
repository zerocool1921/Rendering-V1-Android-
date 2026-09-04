package com.visualizerstudio.app.render.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Render 1 frame shader spectrum GLSL ES 3.0 ke buffer RGBA lewat EGL Pbuffer offscreen
 * ([EglOffscreenContext]) + program shader ([ShaderProgramCache]). Dipanggil per-frame di dalam
 * loop total-frame video oleh [GlEsSpectrumRendererAdapter] (implementasi
 * [com.visualizerstudio.app.render.ffmpeg.spectrum.CustomSpectrumRenderer] milik Fase 2, lihat
 * Appendix A.4.4 & A.5 blueprint).
 *
 * Bukan thread-safe — satu instance dipakai sekuensial untuk satu sesi render satu
 * `SpectrumConfig`. Uniform yang di-set mengikuti tabel kontrak section 6.0 blueprint.
 */
class GlEsSpectrumRenderer(private val width: Int, private val height: Int) {

    companion object {
        private const val TAG = "GlEsSpectrumRenderer"

        // Quad fullscreen (triangle strip) — sama persis dengan `vertices` di spectrum_generator.py
        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
        )
    }

    private val eglContext = EglOffscreenContext(width, height)
    private val programCache = ShaderProgramCache()

    private var program: Int = -1
    private var vertexBufferId: Int = 0
    private var fboId: Int = 0
    private var colorTextureId: Int = 0
    private var attribLocation: Int = -1

    /**
     * Inisialisasi EGL + compile/link shader. @return true kalau siap dipakai render frame.
     * Kalau false, caller WAJIB fallback ke shader bawaan (jangan crash, lihat section 5.2).
     */
    fun initialize(vertexSrc: String, fragmentSrc: String): Boolean {
        if (!eglContext.initialize()) {
            Log.e(TAG, "Gagal inisialisasi EGL offscreen context (${width}x$height)")
            return false
        }

        program = programCache.getOrCompile(vertexSrc, fragmentSrc)
        if (program <= 0) {
            Log.e(TAG, "Gagal kompilasi/link shader — caller wajib fallback ke shader bawaan")
            return false
        }

        return try {
            setUpQuadAndFbo()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal setup quad/FBO: ${e.message}", e)
            false
        }
    }

    private fun setUpQuadAndFbo() {
        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        vertexBufferId = vbo[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferId)
        val fb = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(QUAD_VERTICES).position(0)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, QUAD_VERTICES.size * 4, fb, GLES30.GL_STATIC_DRAW
        )

        attribLocation = GLES30.glGetAttribLocation(program, "in_vert")

        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        colorTextureId = tex[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTextureId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        fboId = fbo[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, colorTextureId, 0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO tidak lengkap, status=0x${Integer.toHexString(status)}")
        }
        GLES30.glViewport(0, 0, width, height)
    }

    /**
     * Render 1 frame dengan uniform yang sudah dihitung caller (bars dari
     * [com.visualizerstudio.app.render.audio.AudioFftAnalyzer], zoomFactor dari
     * [com.visualizerstudio.app.render.audio.BeatEnergyExtractor], dst — lihat tabel uniform
     * section 6.0 blueprint).
     *
     * @return byte RGBA mentah (width*height*4), siap ditulis sebagai 1 frame rawvideo ke FFmpeg.
     */
    fun renderFrame(
        bars: FloatArray,
        timeSec: Float,
        colorRgb: FloatArray,
        zoomFactor: Float,
        shearX: Float,
        shearY: Float,
        specTypeId: Int,
        totalDurationSec: Float
    ): ByteArray {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glUseProgram(program)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        setUniforms(bars, timeSec, colorRgb, zoomFactor, shearX, shearY, specTypeId, totalDurationSec)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferId)
        if (attribLocation >= 0) {
            GLES30.glEnableVertexAttribArray(attribLocation)
            GLES30.glVertexAttribPointer(attribLocation, 2, GLES30.GL_FLOAT, false, 0, 0)
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        if (attribLocation >= 0) GLES30.glDisableVertexAttribArray(attribLocation)

        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()
        val out = ByteArray(width * height * 4)
        buffer.get(out)
        return out
    }

    private fun setUniforms(
        bars: FloatArray, timeSec: Float, colorRgb: FloatArray, zoomFactor: Float,
        shearX: Float, shearY: Float, specTypeId: Int, totalDurationSec: Float
    ) {
        setUniform1f("u_time", timeSec)
        setUniform2f("u_resolution", width.toFloat(), height.toFloat())
        setUniform3f(
            "u_color",
            colorRgb.getOrElse(0) { 1f }, colorRgb.getOrElse(1) { 1f }, colorRgb.getOrElse(2) { 1f }
        )
        setUniform1i("u_num_bars", 120)
        setUniform1f("u_zoom_factor", zoomFactor)
        setUniform1f("u_shear_x", shearX)
        setUniform1f("u_shear_y", shearY)
        setUniform1i("u_spec_type", specTypeId)
        setUniform1f("u_total_duration", totalDurationSec)

        val loc = GLES30.glGetUniformLocation(program, "u_bars")
        if (loc >= 0) GLES30.glUniform1fv(loc, bars.size, bars, 0)
    }

    private fun setUniform1f(name: String, value: Float) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform1f(loc, value)
    }

    private fun setUniform1i(name: String, value: Int) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform1i(loc, value)
    }

    private fun setUniform2f(name: String, x: Float, y: Float) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform2f(loc, x, y)
    }

    private fun setUniform3f(name: String, x: Float, y: Float, z: Float) {
        val loc = GLES30.glGetUniformLocation(program, name)
        if (loc >= 0) GLES30.glUniform3f(loc, x, y, z)
    }

    /**
     * WAJIB dipanggil setelah sesi render 1 spectrum selesai (lihat section 9 blueprint —
     * anti-leak EGL context/FBO/buffer). Aman dipanggil meski [initialize] gagal di tengah jalan.
     */
    fun release() {
        try {
            if (fboId != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            if (colorTextureId != 0) GLES30.glDeleteTextures(1, intArrayOf(colorTextureId), 0)
            if (vertexBufferId != 0) GLES30.glDeleteBuffers(1, intArrayOf(vertexBufferId), 0)
            programCache.releaseAll()
        } catch (e: Exception) {
            Log.e(TAG, "Eror saat release resource GL: ${e.message}", e)
        } finally {
            fboId = 0
            colorTextureId = 0
            vertexBufferId = 0
            eglContext.release()
        }
    }
}
