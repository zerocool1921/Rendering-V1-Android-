package com.visualizerstudio.app.render.ffmpeg

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.visualizerstudio.app.domain.model.Resolution
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------------------
// KONTRAK BEKU — dimiliki Fase 2 (Appendix A.4.3 blueprint). Fase 6 DILARANG mengedit
// HardwareEncoderDetector di bawah — perluasan skor kompatibilitas per chipset WAJIB jadi
// class baru terpisah (render/ffmpeg/HardwareEncoderDetectorExtended.kt) yang memanggil
// HardwareEncoderMode/EncoderSelection di sini sebagai tipe data, bukan mewarisi/mengedit
// HardwareEncoderDetector yang sudah ada.
// ---------------------------------------------------------------------------------------

enum class HardwareEncoderMode { AUTO, MANUAL, CPU_SOFTWARE }

data class EncoderSelection(val mediaCodecName: String?, val ffmpegVideoCodec: String)

/**
 * Bitrate video (kbps) mengikuti panduan resmi YouTube Live (H.264, standard vs high frame
 * rate), porting 1:1 dari `get_youtube_live_bitrate_kbps` versi Python sumber.
 */
object YoutubeLiveBitrate {
    fun kbps(resWidth: Int, resHeight: Int, fps: Int): Int {
        val highFps = fps > 30
        val longEdge = maxOf(resWidth, resHeight)
        return when {
            longEdge >= 3840 -> if (highFps) 35000 else 25000
            longEdge >= 2560 -> if (highFps) 16000 else 12000
            longEdge >= 1920 -> if (highFps) 8500 else 6000
            longEdge >= 1280 -> if (highFps) 5500 else 4000
            else -> 2500
        }
    }
}

/**
 * Deteksi encoder H.264/HEVC hardware yang tersedia lewat `MediaCodecList`, dengan fallback
 * software (`libx264`) selalu tersedia sebagai jalur anti-crash (section 5.3 & 9 blueprint).
 */
@Singleton
class HardwareEncoderDetector @Inject constructor() {

    /** Daftar nama encoder H.264 hardware yang terdeteksi di chipset perangkat ini. */
    fun listAvailableH264HardwareEncoders(): List<String> {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos
            .asSequence()
            .filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } }
            .map { it.name }
            .filter { name ->
                // Encoder software bawaan AOSP diawali "c2.android." / "omx.google." — bukan
                // hardware vendor sesungguhnya, jadi dikeluarkan dari daftar "hardware".
                !name.startsWith("c2.android.", ignoreCase = true) &&
                    !name.startsWith("omx.google.", ignoreCase = true)
            }
            .toList()
    }

    fun hasHardwareEncoder(): Boolean = listAvailableH264HardwareEncoders().isNotEmpty()

    /**
     * Resolusi mode encoder → pilihan encoder aktual + codec name FFmpeg yang dipakai
     * `FfmpegFilterGraphBuilder`/`FfmpegSessionRunner`.
     */
    fun resolveEncoderSelection(
        mode: HardwareEncoderMode,
        manualMediaCodecName: String? = null
    ): EncoderSelection {
        return when (mode) {
            HardwareEncoderMode.CPU_SOFTWARE -> EncoderSelection(
                mediaCodecName = null,
                ffmpegVideoCodec = "libx264"
            )
            HardwareEncoderMode.MANUAL -> {
                if (manualMediaCodecName != null) {
                    EncoderSelection(manualMediaCodecName, "h264_mediacodec")
                } else {
                    EncoderSelection(null, "libx264")
                }
            }
            HardwareEncoderMode.AUTO -> {
                val hw = listAvailableH264HardwareEncoders().firstOrNull()
                if (hw != null) {
                    EncoderSelection(hw, "h264_mediacodec")
                } else {
                    EncoderSelection(null, "libx264")
                }
            }
        }
    }

    /**
     * Susun argumen encoder FFmpeg lengkap (GOP, bitrate, buffer, pix_fmt) mengikuti standar
     * kualitas YouTube Live — porting dari `get_optimal_encoder` versi Python, disesuaikan ke
     * 2 jalur nyata di Android: `h264_mediacodec` (hardware) dan `libx264` (software fallback).
     */
    fun buildEncoderArgs(
        selection: EncoderSelection,
        resolution: Resolution,
        fps: Int
    ): List<String> {
        val gop = maxOf(2, Math.round(fps.toFloat())) * 2
        val bitrate = YoutubeLiveBitrate.kbps(resolution.width, resolution.height, fps)
        val bufsize = bitrate * 2

        return if (selection.ffmpegVideoCodec == "h264_mediacodec") {
            listOf(
                "-c:v", "h264_mediacodec",
                "-b:v", "${bitrate}k",
                "-maxrate", "${bitrate}k",
                "-bufsize", "${bufsize}k",
                "-g", gop.toString(),
                "-pix_fmt", "yuv420p"
            )
        } else {
            listOf(
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-b:v", "${bitrate}k",
                "-minrate", "${bitrate}k",
                "-maxrate", "${bitrate}k",
                "-bufsize", "${bufsize}k",
                "-g", gop.toString(),
                "-keyint_min", gop.toString(),
                "-sc_threshold", "0",
                "-threads", "0",
                "-pix_fmt", "yuv420p"
            )
        }
    }

    /** Argumen audio standar — AAC 128 kbps, 44.1 kHz, stereo (section 5.1f blueprint). */
    fun buildAudioArgs(): List<String> =
        listOf("-c:a", "aac", "-profile:a", "aac_low", "-b:a", "128k", "-ar", "44100", "-ac", "2")
}
