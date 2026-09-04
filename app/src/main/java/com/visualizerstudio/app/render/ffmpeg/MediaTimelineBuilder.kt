package com.visualizerstudio.app.render.ffmpeg

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.visualizerstudio.app.domain.model.MediaMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Menyusun SEMUA file visual (`RenderTask.mediaFiles`) jadi SATU file timeline video berdurasi
 * persis `targetDurationSec`, sesuai [MediaMode] (langkah wizard 5) — porting dari
 * `build_media_timeline` versi Python. Kalau visual cuma 1 file, mengembalikan null (pipeline
 * filter graph utama langsung memakai file itu apa adanya, tidak perlu timeline tambahan).
 */
class MediaTimelineBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safPathResolver: SafPathResolver,
    private val ffmpegSessionRunner: FfmpegSessionRunner
) {

    private fun tempDir(): File = File(context.cacheDir, "render_tmp").apply { mkdirs() }

    private fun isPictureFile(uri: Uri): Boolean {
        val name = safPathResolver.displayPath(uri).lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
    }

    /** Durasi media asli dalam detik (0 kalau gagal dibaca / file gambar). */
    private suspend fun probeDurationSec(uri: Uri): Float = withContext(Dispatchers.IO) {
        if (isPictureFile(uri)) return@withContext 0f
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ms / 1000f
        } catch (e: Exception) {
            0f
        } finally {
            retriever.release()
        }
    }

    private fun splitEvenly(list: List<Uri>, n: Int): List<List<Uri>> {
        val groups = mutableListOf<List<Uri>>()
        val count = n.coerceAtLeast(1)
        val k = list.size / count
        val m = list.size % count
        var start = 0
        for (i in 0 until count) {
            val size = k + if (i < m) 1 else 0
            if (size > 0) groups.add(list.subList(start, start + size))
            start += size
        }
        return groups
    }

    /**
     * Render satu file visual (gambar atau video) jadi klip berdurasi persis [durationSec],
     * sudah diskalakan ke resolusi target & codec seragam supaya bisa di-concat `-c copy`.
     */
    private suspend fun renderVisualSegment(
        uri: Uri,
        durationSec: Float,
        resWidth: Int,
        resHeight: Int,
        fps: Int,
        outPath: String
    ): Boolean {
        val duration = maxOf(0.1f, durationSec)
        val vf = "scale=$resWidth:$resHeight:force_original_aspect_ratio=increase:eval=init," +
            "crop=$resWidth:$resHeight,fps=$fps,format=yuv420p"

        val args: List<String> = if (isPictureFile(uri)) {
            listOf(
                "-y", "-loop", "1", "-framerate", fps.toString(), "-t", "%.3f".format(duration),
                "-i", safPathResolver.resolveForRead(uri),
                "-vf", vf, "-an", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                outPath
            )
        } else {
            val naturalDur = probeDurationSec(uri).let { if (it <= 0f) duration else it }
            if (naturalDur < duration) {
                listOf(
                    "-y", "-stream_loop", "-1", "-t", "%.3f".format(duration),
                    "-i", safPathResolver.resolveForRead(uri),
                    "-vf", vf, "-an", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                    outPath
                )
            } else {
                listOf(
                    "-y", "-t", "%.3f".format(duration),
                    "-i", safPathResolver.resolveForRead(uri),
                    "-vf", vf, "-an", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                    outPath
                )
            }
        }

        val result = ffmpegSessionRunner.executeSilent(args, "Render segmen visual")
        return result is RenderResult.Success && File(outPath).let { it.exists() && it.length() > 0 }
    }

    /** Gabungkan klip-klip (codec seragam) tanpa re-encode. */
    private suspend fun concatClipsCopy(clipPaths: List<String>, outPath: String): Boolean {
        if (clipPaths.isEmpty()) return false
        val listFile = File(tempDir(), "concat_list_${File(outPath).name}.txt")
        listFile.writeText(clipPaths.joinToString("\n") { "file '${it.replace("'", "'\\''")}'" } + "\n")

        val args = listOf("-y", "-f", "concat", "-safe", "0", "-i", listFile.absolutePath, "-c", "copy", outPath)
        val result = ffmpegSessionRunner.executeSilent(args, "Gabung klip timeline")
        listFile.delete()
        return result is RenderResult.Success && File(outPath).let { it.exists() && it.length() > 0 }
    }

    /** Loop satu klip playlist sampai memenuhi durasi target. */
    private suspend fun loopClipToDuration(clipPath: String, targetDurationSec: Float, outPath: String): Boolean {
        val naturalDur = probeDurationSec(Uri.fromFile(File(clipPath))).let { if (it <= 0f) targetDurationSec else it }
        val loopsNeeded = if (naturalDur < targetDurationSec) {
            maxOf(1, (targetDurationSec / naturalDur).toInt() + 1)
        } else 0

        val args = listOf(
            "-y", "-stream_loop", loopsNeeded.toString(), "-i", clipPath,
            "-t", "%.3f".format(targetDurationSec), "-c", "copy", outPath
        )
        val result = ffmpegSessionRunner.executeSilent(args, "Loop klip timeline")
        return result is RenderResult.Success && File(outPath).let { it.exists() && it.length() > 0 }
    }

    /**
     * @return path file timeline gabungan, atau null kalau visual cuma 1 file (tidak perlu
     * timeline tambahan) ATAU semua langkah gagal.
     */
    suspend fun buildTimeline(
        mediaFiles: List<Uri>,
        audioFiles: List<Uri>,
        mediaMode: MediaMode,
        targetDurationSec: Float,
        resWidth: Int,
        resHeight: Int,
        fps: Int
    ): String? {
        val mediaList = mediaFiles.filterNotNull()
        if (mediaList.size <= 1) return null

        val outFinal = File(tempDir(), "visual_timeline_${System.currentTimeMillis()}.mp4").absolutePath

        if (mediaMode == MediaMode.EQUAL_SPLIT) {
            val slotDuration = targetDurationSec / mediaList.size
            val segPaths = mutableListOf<String>()
            mediaList.forEachIndexed { i, uri ->
                val segOut = File(tempDir(), "seg_eq_$i.mp4").absolutePath
                if (renderVisualSegment(uri, slotDuration, resWidth, resHeight, fps, segOut)) {
                    segPaths += segOut
                }
            }
            if (segPaths.isEmpty()) return null
            return if (concatClipsCopy(segPaths, outFinal)) outFinal else null
        }

        // ---------------- PER_TRACK ----------------
        val numTracks = if (audioFiles.isNotEmpty()) audioFiles.size else 1
        val groups = splitEvenly(mediaList, numTracks)

        val trackDurations = mutableListOf<Float>()
        if (audioFiles.isNotEmpty()) {
            audioFiles.forEach { a ->
                val d = probeDurationSec(a)
                trackDurations += if (d > 0f) d else (targetDurationSec / maxOf(1, audioFiles.size))
            }
        } else {
            trackDurations += targetDurationSec
        }
        while (trackDurations.size < groups.size) {
            trackDurations += targetDurationSec / groups.size
        }

        val trackClipPaths = mutableListOf<String>()
        groups.forEachIndexed { tIdx, group ->
            val tDuration = trackDurations[tIdx]

            if (group.size == 1) {
                val segOut = File(tempDir(), "seg_track_$tIdx.mp4").absolutePath
                if (renderVisualSegment(group[0], tDuration, resWidth, resHeight, fps, segOut)) {
                    trackClipPaths += segOut
                }
                return@forEachIndexed
            }

            val allPictures = group.all { isPictureFile(it) }

            if (allPictures) {
                val perItemDuration = tDuration / group.size
                val segPaths = mutableListOf<String>()
                group.forEachIndexed { i, uri ->
                    val segOut = File(tempDir(), "seg_track_${tIdx}_$i.mp4").absolutePath
                    if (renderVisualSegment(uri, perItemDuration, resWidth, resHeight, fps, segOut)) {
                        segPaths += segOut
                    }
                }
                if (segPaths.isNotEmpty()) {
                    val trackFinal = File(tempDir(), "track_$tIdx.mp4").absolutePath
                    if (concatClipsCopy(segPaths, trackFinal)) trackClipPaths += trackFinal
                }
            } else {
                val segPaths = mutableListOf<String>()
                group.forEachIndexed { i, uri ->
                    val naturalDur = if (!isPictureFile(uri)) probeDurationSec(uri) else (tDuration / group.size)
                    val effectiveDur = if (naturalDur <= 0f) (tDuration / group.size) else naturalDur
                    val segOut = File(tempDir(), "seg_track_${tIdx}_$i.mp4").absolutePath
                    if (renderVisualSegment(uri, effectiveDur, resWidth, resHeight, fps, segOut)) {
                        segPaths += segOut
                    }
                }
                if (segPaths.isNotEmpty()) {
                    val cyclePath = File(tempDir(), "cycle_$tIdx.mp4").absolutePath
                    if (concatClipsCopy(segPaths, cyclePath)) {
                        val trackFinal = File(tempDir(), "track_$tIdx.mp4").absolutePath
                        if (loopClipToDuration(cyclePath, tDuration, trackFinal)) trackClipPaths += trackFinal
                    }
                }
            }
        }

        if (trackClipPaths.isEmpty()) return null
        if (trackClipPaths.size == 1) {
            return if (loopClipToDuration(trackClipPaths[0], targetDurationSec, outFinal)) outFinal else null
        }
        return if (concatClipsCopy(trackClipPaths, outFinal)) outFinal else null
    }
}
