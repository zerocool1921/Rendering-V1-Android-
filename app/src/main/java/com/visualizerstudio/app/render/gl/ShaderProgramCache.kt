package com.visualizerstudio.app.render.gl

import android.opengl.GLES30
import android.util.Log

/**
 * Cache & compiler program shader GLSL ES 3.0. Satu instance dipakai per-EGL-context (per sesi
 * render satu spectrum), supaya program tidak dikompilasi ulang tiap frame — hanya sekali di
 * awal sesi render, lalu dipakai ulang untuk semua frame video (lihat pipeline section 5.2 poin
 * 3 blueprint).
 */
class ShaderProgramCache {

    companion object {
        private const val TAG = "ShaderProgramCache"
    }

    private val programCache = mutableMapOf<String, Int>()

    /**
     * Compile & link program baru (atau kembalikan dari cache kalau source sama).
     * @return id program GL (>0) kalau berhasil, atau -1 kalau gagal kompilasi/link — caller
     * ([GlEsSpectrumRenderer] lewat [GlEsSpectrumRendererAdapter]) WAJIB fallback ke shader
     * bawaan bila -1, JANGAN crash (lihat section 5.2 blueprint: "fallback ke shader default
     * bila gagal compile — anti-crash").
     */
    fun getOrCompile(vertexSrc: String, fragmentSrc: String): Int {
        val cacheKey = "${vertexSrc.hashCode()}_${fragmentSrc.hashCode()}"
        programCache[cacheKey]?.let { return it }

        val program = compileProgram(vertexSrc, fragmentSrc)
        if (program > 0) programCache[cacheKey] = program
        return program
    }

    private fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        if (vertexShader == 0) return -1

        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (fragmentShader == 0) {
            GLES30.glDeleteShader(vertexShader)
            return -1
        }

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            return -1
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)

        // Shader object boleh dihapus setelah link (link menyalin apa yang dibutuhkan ke program)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        if (linkStatus[0] != GLES30.GL_TRUE) {
            Log.e(TAG, "Gagal link program shader: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            return -1
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) return 0

        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val kind = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            Log.e(TAG, "Gagal kompilasi shader $kind: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    /** Hapus semua program yang di-cache. Panggil saat sesi render (EGL context) berakhir. */
    fun releaseAll() {
        programCache.values.forEach { GLES30.glDeleteProgram(it) }
        programCache.clear()
    }
}
