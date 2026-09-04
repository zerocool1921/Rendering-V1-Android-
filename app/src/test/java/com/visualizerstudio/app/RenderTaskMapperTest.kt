package com.visualizerstudio.app

import android.net.Uri
import com.visualizerstudio.app.data.local.RenderTaskMapper
import com.visualizerstudio.app.domain.model.BgVisualEffect
import com.visualizerstudio.app.domain.model.CropConfig
import com.visualizerstudio.app.domain.model.MediaMode
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.Resolution
import com.visualizerstudio.app.domain.model.SpectrumConfig
import com.visualizerstudio.app.domain.model.TaskStatus
import com.visualizerstudio.app.domain.model.TextLine
import com.visualizerstudio.app.domain.model.TextOverlayConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Membuktikan `RenderTaskMapper` (data/local) bisa mengubah `RenderTask` domain (Appendix
 * A.3, kontrak beku) pulang-pergi lewat representasi Room/JSON tanpa kehilangan data —
 * termasuk field bertingkat (spectrums, introConfig, cropConfig) yang paling rawan salah
 * kalau mapping dibuat asal-asalan/stub.
 */
@RunWith(RobolectricTestRunner::class)
class RenderTaskMapperTest {

    @Test
    fun `toEntity lalu toDomain menghasilkan RenderTask yang sama persis`() {
        val original = RenderTask(
            id = "task-1",
            name = "Lagu Uji Coba",
            mediaFiles = listOf(Uri.parse("content://media/video1"), Uri.parse("content://media/video2")),
            audioFiles = listOf(Uri.parse("content://media/audio1")),
            mediaMode = MediaMode.EQUAL_SPLIT,
            resolution = Resolution.R1080P,
            fps = 60,
            loops = 2,
            spectrums = listOf(
                SpectrumConfig(shaderRef = "builtin_bars", width = 900, height = 320, color = "gold")
            ),
            introConfig = TextOverlayConfig(
                lines = listOf(TextLine("Selamat Datang", 48), TextLine("Channel Musik", 32))
            ),
            cropConfig = CropConfig(left = 10, right = 10, top = 0, bottom = 20),
            bgVisualEffect = BgVisualEffect.VIGNETTE,
            outputFolder = Uri.parse("content://tree/output"),
            status = TaskStatus.QUEUED
        )

        val entity = RenderTaskMapper.toEntity(original)
        val roundTripped = RenderTaskMapper.toDomain(entity)

        assertEquals(original, roundTripped)
    }
}
