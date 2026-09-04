package com.visualizerstudio.app.render.ffmpeg.scorer

import android.media.MediaCodecInfo

/**
 * TITIK SAMBUNG (Extension Point) baru untuk Fase 6, mengikuti pola resmi Appendix A.5.
 *
 * CATATAN LINTAS FASE (WAJIB dibaca sebelum menggabung folder ini ke project):
 * Kontrak `Set<EncoderCompatibilityScorer>` ini TIDAK tercantum secara eksplisit di Appendix A
 * versi yang diberikan ke sesi ini (hanya disebut sebagai contoh pola di catatan A.5: "Fase 6
 * memperluas HardwareEncoderDetector (Fase 2) — bikin Set<EncoderCompatibilityScorer> kosong di
 * Fase 2, diisi Fase 6"). Karena definisi interface-nya belum pernah dipatok di Appendix A
 * manapun, interface ini didefinisikan di sesi Fase 6 ini SEBAGAI USULAN REVISI APPENDIX A v3 —
 * sesuai instruksi eksplisit di blueprint (section A.5, paragraf "Prinsip umum"): "kalau titik
 * sambung yang dibutuhkan ternyata belum ada di Appendix A manapun, tambahkan sebagai revisi
 * Appendix A baru (v3) dan pastikan revisi itu diberikan ke SEMUA sesi berikutnya."
 *
 * File ini TIDAK menabrak manifest kepemilikan Fase 2 manapun (bukan file yang sudah ada di
 * Appendix A.2) — jadi aman digabung sebagai file baru murni.
 *
 * DILARANG dipakai untuk mewarisi/mengubah `HardwareEncoderDetector` di
 * `render/ffmpeg/EncoderParamsProvider.kt` (milik Fase 2, lihat A.4.3) — scorer ini murni
 * lapisan tambahan yang dikonsumsi oleh `HardwareEncoderDetectorExtended` (class BARU, milik
 * Fase 6).
 */
interface EncoderCompatibilityScorer {

    /** true kalau scorer ini relevan untuk vendor chipset yang sedang berjalan. */
    fun appliesTo(chipset: ChipsetInfo): Boolean

    /**
     * Beri skor kompatibilitas 0..100 (100 = paling direkomendasikan) untuk satu kandidat
     * encoder hardware, terhadap target resolusi/fps render yang diminta user.
     */
    fun score(
        chipset: ChipsetInfo,
        codecInfo: MediaCodecInfo,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int
    ): EncoderScoreResult
}

/** Identitas chipset perangkat, hasil deteksi `ChipsetIdentifier` (Fase 6). */
data class ChipsetInfo(
    val vendor: ChipsetVendor,
    val hardwareString: String,
    val boardString: String,
    val socModel: String?,
    val sdkInt: Int
)

enum class ChipsetVendor { QUALCOMM, SAMSUNG_EXYNOS, MEDIATEK, UNISOC, GOOGLE_TENSOR, UNKNOWN }

data class EncoderScoreResult(
    val score: Int,
    /** true = encoder ini WAJIB dihindari untuk kombinasi target ini, dikonversi jadi skor 0. */
    val mustAvoid: Boolean = false,
    val reason: String
)
