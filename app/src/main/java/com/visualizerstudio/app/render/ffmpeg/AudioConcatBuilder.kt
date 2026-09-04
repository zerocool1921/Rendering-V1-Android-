package com.visualizerstudio.app.render.ffmpeg

import android.net.Uri
import javax.inject.Inject

/**
 * Menggabungkan seluruh `audioFiles` sebuah [com.visualizerstudio.app.domain.model.RenderTask]
 * menjadi satu track PCM WAV (langkah 3 wizard: multi-select audio digabung otomatis).
 * Porting dari `concat_audios` versi Python: 1 file → re-encode langsung ke PCM; >1 file →
 * dirangkai dengan `acrossfade` (durasi transisi 2 detik, curve `qsin` di kedua sisi) supaya
 * sambungan antar-lagu tidak "kepotong" kasar.
 */
class AudioConcatBuilder @Inject constructor(
    private val safPathResolver: SafPathResolver,
    private val ffmpegSessionRunner: FfmpegSessionRunner
) {

    private val transitionDurationSec = 2.0

    /** @return [RenderResult.Success] dengan path WAV hasil gabungan, atau [RenderResult.Failure]. */
    suspend fun concatAudios(audioFiles: List<Uri>, outputWavPath: String): RenderResult {
        if (audioFiles.isEmpty()) {
            return RenderResult.Failure("Tidak ada file audio di dalam tugas render.")
        }

        val args = mutableListOf("-y")

        if (audioFiles.size == 1) {
            args += listOf(
                "-thread_queue_size", "16384",
                "-vn",
                "-i", safPathResolver.resolveForRead(audioFiles[0]),
                "-c:a", "pcm_s16le",
                "-threads", "0",
                outputWavPath
            )
        } else {
            audioFiles.forEach { uri ->
                args += listOf("-thread_queue_size", "16384", "-vn", "-i", safPathResolver.resolveForRead(uri))
            }

            val filters = StringBuilder()
            var lastLabel = "[0:a]"
            val n = audioFiles.size
            for (i in 0 until n - 1) {
                val nextLabel = "[${i + 1}:a]"
                val outLabel = if (i == n - 2) "[outa]" else "[a${i + 1}]"
                if (filters.isNotEmpty()) filters.append(";")
                filters.append("$lastLabel$nextLabel")
                    .append("acrossfade=d=$transitionDurationSec:c1=qsin:c2=qsin")
                    .append(outLabel)
                lastLabel = outLabel
            }

            args += listOf(
                "-filter_complex", filters.toString(),
                "-map", "[outa]",
                "-vn",
                "-c:a", "pcm_s16le",
                "-threads", "0",
                outputWavPath
            )
        }

        return ffmpegSessionRunner.executeSilent(args, taskLabel = "Penggabungan Audio")
    }
}
