package com.visualizerstudio.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.visualizerstudio.app.render.ffmpeg.RenderProgress
import com.visualizerstudio.app.util.FfmpegUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Membangun & mengelola notifikasi foreground service `RenderWorker` (section 5.2 langkah 7
 * blueprint): progres real-time (persentase, waktu video, FPS, speed) + tombol batal.
 */
@Singleton
class RenderNotifications @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "render_progress_channel"
        const val NOTIFICATION_ID_BASE = 9000
        const val ACTION_CANCEL_RENDER = "com.visualizerstudio.app.action.CANCEL_RENDER"
        const val EXTRA_WORK_ID = "extra_work_id"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Progres Render Video",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan progres render tugas video visualizer musik."
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    fun notificationIdFor(taskId: String): Int = NOTIFICATION_ID_BASE + taskId.hashCode().and(0x0FFFFFFF)

    fun buildQueuedNotification(taskName: String): androidx.core.app.NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Menyiapkan render: $taskName")
            .setContentText("Menggabungkan audio & menyusun timeline visual...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setProgress(100, 0, true)

    fun buildProgressNotification(
        taskName: String,
        progress: RenderProgress,
        workId: java.util.UUID
    ): NotificationCompat.Builder {
        val percentInt = progress.percent.toInt().coerceIn(0, 100)
        val timeLabel = "${FfmpegUtils.formatDuration(progress.currentTimeSec)} / ${FfmpegUtils.formatDuration(progress.totalTimeSec)}"
        val fpsLabel = progress.fps?.let { " | ${it.toInt()} fps" } ?: ""
        val speedLabel = progress.speed?.let { " | $it" } ?: ""

        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workId)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Merender: $taskName ($percentInt%)")
            .setContentText("$timeLabel$fpsLabel$speedLabel")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percentInt, false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batalkan", cancelIntent)
    }

    fun buildResultNotification(taskName: String, success: Boolean, message: String? = null): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (success) "Render selesai: $taskName" else "Render gagal: $taskName")
            .setContentText(message ?: if (success) "Video berhasil disimpan ke folder output." else "Lihat detail log untuk info lebih lanjut.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
}
