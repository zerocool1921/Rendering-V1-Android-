package com.visualizerstudio.app.render.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Satu-satunya titik eksekusi proses FFmpegKit di seluruh app (dipakai oleh semua builder di
 * atasnya — [AudioConcatBuilder], [MediaTimelineBuilder], [FfmpegFilterGraphBuilder] via
 * [com.visualizerstudio.app.worker.RenderWorker]). Menyediakan 2 mode:
 * - [executeSilent] untuk langkah internal pendek (concat audio, potong klip) tanpa perlu
 *   melaporkan progres ke UI.
 * - [executeWithProgress] untuk render final penuh, mengirim [RenderProgress] tiap statistics
 *   callback FFmpegKit, dengan [FfmpegProgressParser] sebagai fallback kalau statistics API
 *   FFmpegKit tidak mengembalikan data (build tertentu).
 */
@Singleton
class FfmpegSessionRunner @Inject constructor(
    private val progressParser: FfmpegProgressParser
) {

    private val logTailLines = 20

    /** Jalankan FFmpeg tanpa progres, mengembalikan sukses/gagal + tail log kalau gagal. */
    suspend fun executeSilent(args: List<String>, taskLabel: String): RenderResult =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { completed ->
                    if (ReturnCode.isSuccess(completed.returnCode)) {
                        cont.resume(RenderResult.Success(outputPath = args.lastOrNull().orEmpty()))
                    } else if (ReturnCode.isCancel(completed.returnCode)) {
                        cont.resume(RenderResult.Cancelled)
                    } else {
                        val tail = completed.allLogsAsString
                            ?.lines()
                            ?.takeLast(logTailLines)
                            ?.joinToString("\n")
                        cont.resume(
                            RenderResult.Failure(
                                message = "$taskLabel gagal (exit code ${completed.returnCode}).",
                                ffmpegLogTail = tail
                            )
                        )
                    }
                }
                cont.invokeOnCancellation { session.cancel() }
            }
        }

    /**
     * Jalankan FFmpeg dengan progres real-time. [onProgress] dipanggil dari thread callback
     * FFmpegKit — caller (WorkManager Worker) bertanggung jawab marshal ke context yang sesuai
     * kalau perlu update UI langsung (umumnya cukup untuk update notifikasi foreground service).
     */
    suspend fun executeWithProgress(
        args: List<String>,
        totalDurationSec: Float,
        taskLabel: String,
        onProgress: (RenderProgress) -> Unit
    ): RenderResult = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<RenderResult>()
        var lastSession: FFmpegSession? = null

        val session = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            { completed ->
                if (ReturnCode.isSuccess(completed.returnCode)) {
                    deferred.complete(RenderResult.Success(outputPath = args.lastOrNull().orEmpty()))
                } else if (ReturnCode.isCancel(completed.returnCode)) {
                    deferred.complete(RenderResult.Cancelled)
                } else {
                    val tail = completed.allLogsAsString
                        ?.lines()
                        ?.takeLast(logTailLines)
                        ?.joinToString("\n")
                    deferred.complete(
                        RenderResult.Failure(
                            message = "$taskLabel gagal (exit code ${completed.returnCode}).",
                            ffmpegLogTail = tail
                        )
                    )
                }
            },
            { logEntry ->
                // Fallback: FFmpegKit statistics callback kadang tidak dipicu di beberapa build
                // AAR (mis. varian -min) — regex log tetap dipertahankan sebagai jaring pengaman.
                progressParser.parseLine(logEntry.message ?: "", totalDurationSec)?.let(onProgress)
            },
            { statistics: Statistics ->
                val currentTimeSec = statistics.time / 1000f
                val percent = if (totalDurationSec > 0f) {
                    (currentTimeSec / totalDurationSec * 100f).coerceIn(0f, 100f)
                } else 0f
                onProgress(
                    RenderProgress(
                        percent = percent,
                        currentTimeSec = currentTimeSec,
                        totalTimeSec = totalDurationSec,
                        fps = statistics.videoFps,
                        speed = "${statistics.speed}x"
                    )
                )
            }
        )
        lastSession = session

        deferred.invokeOnCompletion { cause ->
            if (cause != null) lastSession?.cancel()
        }
        deferred.await()
    }

    /** Batalkan sesi FFmpeg yang sedang berjalan (dipanggil saat user menekan tombol batal). */
    fun cancelAll() {
        FFmpegKit.cancel()
    }
}
