package com.visualizerstudio.app.render.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log

/**
 * EGL Pbuffer offscreen context untuk render shader GLSL ES 3.0 headless (tanpa Activity/View),
 * dipakai [GlEsSpectrumRenderer] untuk render frame demi frame ke buffer RGBA lalu di-`glReadPixels`.
 *
 * WAJIB dipanggil [release] setelah selesai satu sesi render spectrum (lihat section 9 blueprint
 * soal manajemen memori GLSL offscreen: "EGL context, FBO, dan buffer wajib dirilis eksplisit
 * setiap selesai render satu spectrum untuk mencegah leak di sesi render panjang") — terutama
 * penting saat antrian render (Fase 4) memproses banyak tugas berurutan.
 */
class EglOffscreenContext(private val width: Int, private val height: Int) {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    var isReady: Boolean = false
        private set

    companion object {
        private const val TAG = "EglOffscreenContext"
        private const val EGL_OPENGL_ES3_BIT = 0x40
    }

    /** @return true kalau EGL berhasil diinisialisasi & context sudah current. */
    fun initialize(): Boolean {
        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.e(TAG, "eglGetDisplay gagal")
                return false
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                Log.e(TAG, "eglInitialize gagal")
                return false
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val configOk = EGL14.eglChooseConfig(
                eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0
            )
            if (!configOk || numConfigs[0] <= 0) {
                Log.e(TAG, "eglChooseConfig gagal, tidak ada EGLConfig ES3 Pbuffer RGBA8 cocok")
                return false
            }
            val eglConfig = configs[0]!!

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.e(TAG, "eglCreateContext gagal")
                return false
            }

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "eglCreatePbufferSurface gagal (width=$width height=$height)")
                return false
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                Log.e(TAG, "eglMakeCurrent gagal")
                return false
            }

            isReady = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Eror inisialisasi EGL offscreen context: ${e.message}", e)
            release()
            return false
        }
    }

    /** Lepas semua resource EGL. Aman dipanggil berkali-kali (idempotent). */
    fun release() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eror saat release EGL context: ${e.message}", e)
        } finally {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            isReady = false
        }
    }
}
