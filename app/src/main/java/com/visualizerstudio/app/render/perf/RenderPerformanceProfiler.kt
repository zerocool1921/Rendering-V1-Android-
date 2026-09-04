package com.visualizerstudio.app.render.perf

import java.util.ArrayDeque

/**
 * Profiler ringan berbasis sliding-window untuk mengukur waktu render per-frame (mis. di dalam
 * loop `GlEsSpectrumRenderer`, milik Fase 3). Class ini berdiri sendiri dan TIDAK dipanggil
 * otomatis dari file Fase 3 manapun — Fase 6 dilarang mengedit file itu (aturan besi 0.1).
 * Disediakan sebagai util publik yang bisa diimpor & dipanggil manual pada sesi lanjutan yang
 * ingin menyambungkan integrasi ini (`onFrameStart()`/`onFrameEnd()` di sekitar `glReadPixels`).
 */
class RenderPerformanceProfiler(private val windowSize: Int = 60) {

    private val frameTimesMs = ArrayDeque<Long>()
    private var lastFrameStartNs: Long = 0L

    fun onFrameStart() {
        lastFrameStartNs = System.nanoTime()
    }

    fun onFrameEnd() {
        if (lastFrameStartNs == 0L) return
        val elapsedMs = (System.nanoTime() - lastFrameStartNs) / 1_000_000
        if (frameTimesMs.size >= windowSize) frameTimesMs.poll()
        frameTimesMs.add(elapsedMs)
        lastFrameStartNs = 0L
    }

    fun averageFrameTimeMs(): Double =
        if (frameTimesMs.isEmpty()) 0.0 else frameTimesMs.average()

    fun estimatedFps(): Double {
        val avg = averageFrameTimeMs()
        return if (avg <= 0.0) 0.0 else 1000.0 / avg
    }

    /** true kalau fps rata-rata sudah turun >20% dari target — sinyal untuk menurunkan kualitas. */
    fun isBelowTarget(targetFps: Int): Boolean =
        frameTimesMs.isNotEmpty() && estimatedFps() < targetFps * 0.8

    fun reset() {
        frameTimesMs.clear()
        lastFrameStartNs = 0L
    }
}
