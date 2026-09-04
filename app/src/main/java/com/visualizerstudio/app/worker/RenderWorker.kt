package com.visualizerstudio.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.SpectrumType
import com.visualizerstudio.app.domain.model.TaskStatus
import com.visualizerstudio.app.domain.repository.RenderTaskRepository
import com.visualizerstudio.app.render.ffmpeg.AudioConcatBuilder
import com.visualizerstudio.app.render.ffmpeg.EncoderParamsProvider
import com.visualizerstudio.app.render.ffmpeg.FfmpegFilterGraphBuilder
import com.visualizerstudio.app.render.ffmpeg.FfmpegSessionRunner
import com.visualizerstudio.app.render.ffmpeg.HardwareEncoderDetector
import com.visualizerstudio.app.render.ffmpeg.HardwareEncoderMode
import com.visualizerstudio.app.render.ffmpeg.MediaTimelineBuilder
import com.visualizerstudio.app.render.ffmpeg.RenderResult
import com.visualizerstudio.app.render.ffmpeg.SafPathResolver
import com.visualizerstudio.app.render.ffmpeg.spectrum.CustomSpectrumRenderer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import javax.inject.Provider

/**
 * Orkestrator penuh pipeline render (section 5.2 blueprint) di dalam `CoroutineWorker` +
 * foreground service (langkah 7: notifikasi progres). Ini SATU-SATUNYA tempat yang boleh
 * memakai `Set<CustomSpectrumRenderer>` (Appendix A.5, Extension Point Pattern) — kalau set
 * kosong (Fase 3 belum digabung ke project), spectrum ber-`CUSTOM_SHADER` otomatis di-skip
 * dengan aman lewat fallback di [FfmpegFilterGraphBuilder], TIDAK CRASH.
 *
 * Alur: 1) tandai RENDERING -> 2) concat audio -> 3) susun timeline visual (kalau media > 1)
 * -> 4) pre-render spectrum kustom (kalau ada & Fase 3 tersedia) -> 5) susun filter graph ->
 * 6) eksekusi FFmpeg dengan progres -> 7) tandai DONE/FAILED -> 8) bersihkan file sementara ->
 * 9) tarik tugas `QUEUED` berikutnya dari antrian.
 */
@HiltWorker
class RenderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RenderTaskRepository,
    private val audioConcatBuilder: AudioConcatBuilder,
    private val mediaTimelineBuilder: MediaTimelineBuilder,
    private val filterGraphBuilder: FfmpegFilterGraphBuilder,
    private val sessionRunner: FfmpegSessionRunner,
    private val safPathResolver: SafPathResolver,
    private val encoderDetector: HardwareEncoderDetector,
    private val notifications: RenderNotifications,
    private val queueScheduler: RenderQueueScheduler,
    // Titik sambung Fase 2 <-> Fase 3 (Appendix A.5): Provider dipakai (bukan Set langsung)
    // supaya Hilt tidak wajib resolve isinya sampai benar-benar dibutuhkan saat runtime.
    private val customSpectrumRenderers: Provider<Set<@JvmSuppressWildcards CustomSpectrumRenderer>>
) : CoroutineWorker(context, params) {

    private val appContext = context

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(RenderQueueScheduler.KEY_TASK_ID)
            ?: return Result.failure(workDataOf("error" to "task_id kosong"))

        val task = repository.getTaskById(taskId)
            ?: return Result.failure(workDataOf("error" to "Tugas $taskId tidak ditemukan di Room"))

        setForeground(foregroundInfoQueued(task.name))
        repository.updateStatus(taskId, TaskStatus.RENDERING)

        val tempFiles = mutableListOf<File>()
        val result = try {
            runRenderPipeline(task, tempFiles)
        } catch (t: Throwable) {
            RenderResult.Failure(message = t.message ?: "Eksepsi tak terduga saat render.")
        } finally {
            tempFiles.forEach { it.delete() }
        }

        return when (result) {
            is RenderResult.Success -> {
                repository.updateStatus(taskId, TaskStatus.DONE)
                notifications.buildResultNotification(task.name, success = true)
                queueScheduler.scheduleNextQueuedTaskIfAny()
                Result.success()
            }
            is RenderResult.Failure -> {
                repository.updateStatus(taskId, TaskStatus.FAILED, errorMessage = result.message)
                notifications.buildResultNotification(task.name, success = false, message = result.message)
                queueScheduler.scheduleNextQueuedTaskIfAny()
                Result.failure(workDataOf("error" to result.message))
            }
            is RenderResult.Cancelled -> {
                repository.updateStatus(taskId, TaskStatus.QUEUED)
                Result.success()
            }
        }
    }

    private suspend fun runRenderPipeline(task: RenderTask, tempFiles: MutableList<File>): RenderResult {
        // 1. Concat audio.
        val concatAudioFile = File(appContext.cacheDir, "render_tmp/audio_concat_${task.id}.wav").also { it.parentFile?.mkdirs() }
        tempFiles += concatAudioFile
        val audioResult = audioConcatBuilder.concatAudios(task.audioFiles, concatAudioFile.absolutePath)
        if (audioResult !is RenderResult.Success) return audioResult

        val audioDurationSec = probeLocalDurationSec(concatAudioFile.absolutePath).let { if (it > 0f) it else 30f }
        val loopCount = task.loops.coerceAtLeast(1)
        val renderDurationSec = audioDurationSec * loopCount

        val resolution = task.resolution
        val fps = task.fps

        // 2. Timeline visual (kalau media > 1).
        val timelinePath = mediaTimelineBuilder.buildTimeline(
            mediaFiles = task.mediaFiles,
            audioFiles = task.audioFiles,
            mediaMode = task.mediaMode,
            targetDurationSec = audioDurationSec,
            resWidth = resolution.width,
            resHeight = resolution.height,
            fps = fps
        )
        timelinePath?.let { tempFiles += File(it) }

        val isTimeline = timelinePath != null
        val mainVisualUri = task.mediaFiles.firstOrNull()
        if (timelinePath == null && mainVisualUri == null) {
            return RenderResult.Failure("Media visual utama tidak ditemukan di dalam tugas.")
        }

        val mainVisualReadParam = timelinePath ?: safPathResolver.resolveForRead(mainVisualUri!!)
        val mainVisualIsPicture = !isTimeline && mainVisualUri != null && isPictureUri(mainVisualUri)
        val mainVisualDurationSec = if (isTimeline) audioDurationSec else if (mainVisualUri != null) probeUriDurationSec(mainVisualUri) else 0f
        val mainVisualKey = timelinePath ?: mainVisualUri.toString()

        // 3. Pre-render spectrum kustom (Extension Point ke Fase 3, kalau tersedia).
        val renderers = customSpectrumRenderers.get()
        val customSpecPaths = mutableMapOf<Int, String>()
        if (renderers.isNotEmpty()) {
            val renderer = renderers.first()
            task.spectrums.forEachIndexed { idx, spec ->
                if (spec.specType == SpectrumType.CUSTOM_SHADER) {
                    val outFile = File(appContext.cacheDir, "render_tmp/custom_spec_${task.id}_$idx.mov")
                    outFile.parentFile?.mkdirs()
                    val ok = renderer.renderToAlphaVideo(
                        spec = spec,
                        durationSec = renderDurationSec,
                        fps = fps,
                        audioSamplePath = concatAudioFile.absolutePath,
                        outputPath = outFile.absolutePath
                    )
                    if (ok && outFile.exists() && outFile.length() > 0) {
                        customSpecPaths[idx] = outFile.absolutePath
                        tempFiles += outFile
                    }
                    // Gagal render shader kustom -> di-skip dengan aman (fallback ke tanpa spectrum
                    // itu), TIDAK menggagalkan seluruh render (anti-crash, sama seperti sumber).
                }
            }
        }

        // 4. Encoder selection.
        val encoderSelection = encoderDetector.resolveEncoderSelection(HardwareEncoderMode.AUTO)

        // 5. Output file (SAF).
        val outputUri = safPathResolver.createOutputFile(task.outputFolder, "${task.name}.mp4")
            ?: return RenderResult.Failure("Gagal membuat file output di folder yang dipilih.")
        val outputWriteParam = safPathResolver.resolveForWrite(outputUri)

        // 6. Susun filter graph + argumen final.
        val args = filterGraphBuilder.buildArgs(
            FfmpegFilterGraphBuilder.BuildInput(
                task = task,
                mainVisualReadParam = mainVisualReadParam,
                mainVisualIsPicture = mainVisualIsPicture,
                mainVisualIsTimeline = isTimeline,
                mainVisualDurationSec = mainVisualDurationSec,
                mainVisualKeyForSlowMotion = mainVisualKey,
                concatAudioReadParam = concatAudioFile.absolutePath,
                renderDurationSec = renderDurationSec,
                resWidth = resolution.width,
                resHeight = resolution.height,
                fps = fps,
                customSpecVideoPaths = customSpecPaths,
                encoderSelection = encoderSelection,
                outputWriteParam = outputWriteParam
            )
        )

        // 7. Eksekusi dengan progres real-time -> notifikasi foreground service.
        return sessionRunner.executeWithProgress(
            args = args,
            totalDurationSec = renderDurationSec,
            taskLabel = "Render ${task.name}"
        ) { progress ->
            setForegroundAsyncSafely(foregroundInfoProgress(task.name, progress))
        }
    }

    private fun setForegroundAsyncSafely(info: ForegroundInfo) {
        // setForegroundAsync tidak suspend -> aman dipanggil dari callback statistics FFmpegKit
        // yang berjalan di thread non-coroutine milik FFmpegKit sendiri.
        setForegroundAsync(info)
    }

    private fun foregroundInfoQueued(taskName: String): ForegroundInfo {
        val notification = notifications.buildQueuedNotification(taskName).build()
        return ForegroundInfo(RenderNotifications.NOTIFICATION_ID_BASE, notification)
    }

    private fun foregroundInfoProgress(taskName: String, progress: com.visualizerstudio.app.render.ffmpeg.RenderProgress): ForegroundInfo {
        val notification = notifications.buildProgressNotification(taskName, progress, id).build()
        return ForegroundInfo(RenderNotifications.NOTIFICATION_ID_BASE, notification)
    }

    private fun probeLocalDurationSec(path: String): Float {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000f
        } catch (e: Exception) {
            0f
        } finally {
            retriever.release()
        }
    }

    private fun probeUriDurationSec(uri: android.net.Uri): Float {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000f
        } catch (e: Exception) {
            0f
        } finally {
            retriever.release()
        }
    }

    private fun isPictureUri(uri: android.net.Uri): Boolean {
        val name = safPathResolver.displayPath(uri).lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
    }

    private fun workDataOf(vararg pairs: Pair<String, Any?>): androidx.work.Data =
        androidx.work.Data.Builder().apply {
            pairs.forEach { (k, v) -> if (v is String) putString(k, v) }
        }.build()
}
