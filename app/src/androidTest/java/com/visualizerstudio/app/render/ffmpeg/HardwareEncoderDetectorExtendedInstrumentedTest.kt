package com.visualizerstudio.app.render.ffmpeg

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.visualizerstudio.app.render.ffmpeg.scorer.ChipsetIdentifier
import com.visualizerstudio.app.render.ffmpeg.scorer.GenericFallbackEncoderScorer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji instrumented (jalan di perangkat/emulator nyata, bukan JVM murni) — bagian
 * "pengujian perangkat" cakupan Fase 6 (section 8). MediaCodecList hanya tersedia di runtime
 * Android sungguhan, sehingga tidak bisa diuji lewat unit test JVM biasa.
 */
@RunWith(AndroidJUnit4::class)
class HardwareEncoderDetectorExtendedInstrumentedTest {

    @Test
    fun chipsetIdentifier_selaluMengembalikanInfoValid() {
        val chipset = ChipsetIdentifier.identify()
        assertNotNull(chipset.vendor)
        assertTrue(chipset.hardwareString.isNotBlank() || chipset.boardString.isNotBlank())
    }

    @Test
    fun rankAvailableEncoders_terurutMenurunBerdasarkanSkor() {
        val detector = HardwareEncoderDetectorExtended(setOf(GenericFallbackEncoderScorer()))
        val ranked = detector.rankAvailableEncoders(1280, 720, 30)
        for (i in 0 until ranked.size - 1) {
            assertTrue(ranked[i].score >= ranked[i + 1].score)
        }
    }

    @Test
    fun rankAvailableEncoders_tidakCrashUntukResolusiEkstrem() {
        // Sengaja pakai target di luar wajar untuk memastikan perilaku anti-crash (section 9).
        val detector = HardwareEncoderDetectorExtended(setOf(GenericFallbackEncoderScorer()))
        val ranked = detector.rankAvailableEncoders(7680, 4320, 120)
        assertNotNull(ranked)
    }

    @Test
    fun bestEncoderNameOrNull_returnsNullBukanCrash_ketikaSemuaKandidatKosong() {
        val detector = HardwareEncoderDetectorExtended(emptySet())
        val result = detector.bestEncoderNameOrNull(1920, 1080, 30)
        // null itu valid (tandanya caller harus fallback CPU), yang penting tidak melempar exception.
        assertTrue(result == null || result.isNotBlank())
    }
}
