package com.visualizerstudio.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Bukti "integrasi FFmpeg NDK dasar (transcode sederhana berhasil jalan)" yang diminta
 * roadmap Fase 1 (section 8). Dijalankan sebagai instrumented test karena FFmpegKit butuh
 * konteks Android nyata (filesystem app, native lib `.so`).
 *
 * Tidak memakai file media eksternal — dipakai sumber sintetik bawaan FFmpeg (`lavfi`,
 * `testsrc`/`sine`) supaya smoke test ini mandiri (tidak butuh asset tambahan) sekaligus
 * tetap membuktikan pipeline decode-filter-encode-mux FFmpegKit berjalan penuh di perangkat/
 * emulator, persis alur yang nanti dipakai `FfmpegSessionRunner` (Fase 2) untuk render
 * sungguhan.
 *
 * File output ini BUKAN pengganti pipeline render nyata Fase 2 — hanya validasi bahwa
 * dependency `ffmpeg-kit-full-gpl` di `app/build.gradle.kts` (Fase 1) sudah tertaut & bisa
 * dieksekusi dari kode Kotlin, sebelum Fase 2 membangun `FfmpegFilterGraphBuilder` di atasnya.
 */
@RunWith(AndroidJUnit4::class)
class FfmpegFoundationSmokeTest {

    @Test
    fun ffmpegKit_dapat_transcode_sumber_sintetik_ke_mp4() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputFile = File(context.cacheDir, "fase1_smoke_test_output.mp4")
        if (outputFile.exists()) outputFile.delete()

        // 2 detik video 320x240@30fps (testsrc) + audio sine sintetik, di-mux jadi mp4.
        // Encoder software (mpeg4) dipakai di sini khusus untuk smoke test dasar Fase 1 —
        // pemilihan encoder hardware/CPU sungguhan adalah tanggung jawab
        // `HardwareEncoderDetector` di Fase 2 (Appendix A.4.3), bukan file ini.
        val command = "-y -f lavfi -i testsrc=duration=2:size=320x240:rate=30 " +
            "-f lavfi -i sine=frequency=440:duration=2 " +
            "-c:v mpeg4 -c:a aac -shortest ${outputFile.absolutePath}"

        val session = FFmpegKit.execute(command)

        assertTrue(
            "FFmpegKit gagal mengeksekusi command transcode dasar. State: ${session.state}, " +
                "ReturnCode: ${session.returnCode}, Log: ${session.allLogsAsString}",
            ReturnCode.isSuccess(session.returnCode)
        )
        assertTrue(
            "File output transcode tidak ditemukan setelah eksekusi FFmpegKit sukses.",
            outputFile.exists() && outputFile.length() > 0
        )

        outputFile.delete()
    }
}
