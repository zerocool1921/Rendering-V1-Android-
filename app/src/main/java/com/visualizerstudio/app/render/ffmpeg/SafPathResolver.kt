package com.visualizerstudio.app.render.ffmpeg

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKitConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menjembatani `content://` Uri hasil Storage Access Framework / Photo Picker (langkah wizard
 * 2-4, tidak ada path filesystem langsung di Android modern) ke parameter path yang bisa
 * dipakai `-i` / output FFmpegKit, memakai `FFmpegKitConfig.getSafParameterForRead/Write`
 * bawaan `ffmpegkit-maintained` (section 10 blueprint) — bukan menyalin manual ke cache
 * kalau tidak perlu, supaya hemat I/O untuk file media besar.
 */
@Singleton
class SafPathResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** @return parameter siap dipakai sebagai argumen `-i <hasil>` FFmpegKit. */
    fun resolveForRead(uri: Uri): String =
        FFmpegKitConfig.getSafParameterForRead(context, uri)

    /** @return parameter siap dipakai sebagai path OUTPUT terakhir di argumen FFmpegKit. */
    fun resolveForWrite(uri: Uri): String =
        FFmpegKitConfig.getSafParameterForWrite(context, uri)

    /**
     * Membuat file output baru di dalam folder output (Uri hasil SAF folder picker, langkah
     * wizard 4) dengan nama & mime type tertentu, lalu mengembalikan Uri file yang baru dibuat
     * supaya bisa langsung diresolusi lewat [resolveForWrite].
     */
    fun createOutputFile(outputFolderUri: Uri, displayName: String, mimeType: String = "video/mp4"): Uri? {
        val folder = DocumentFile.fromTreeUri(context, outputFolderUri) ?: return null
        // Hindari nama bentrok dengan hasil render sebelumnya di folder yang sama.
        folder.findFile(displayName)?.delete()
        return folder.createFile(mimeType, displayName)?.uri
    }

    /** Human-readable path (untuk ditampilkan di notifikasi/log), bukan untuk argumen FFmpeg. */
    fun displayPath(uri: Uri): String =
        DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: uri.toString()
}
