package com.visualizerstudio.app.render.ffmpeg

import com.visualizerstudio.app.domain.model.TextLine
import com.visualizerstudio.app.domain.model.TextOverlayConfig
import java.io.File
import javax.inject.Inject

/**
 * Menyusun filter `drawtext` gabungan (maks 3 baris, ukuran berbeda per baris) untuk overlay
 * Intro (identitas channel, langkah wizard 19) dan Title (judul lagu, langkah wizard 20).
 * Title otomatis diparsing dari nama file audio (ganti underscore -> spasi) bila user tidak
 * mengisi judul manual. Antar-baris di-stack vertikal otomatis (baris tengah jadi acuan,
 * baris lain digeser proporsional terhadap ukuran fontnya) — porting persis dari blok
 * `render_task_pipeline` versi Python (bagian `useIntro`/`useTitle`).
 */
class IntroTitleFilterBuilder @Inject constructor(
    private val drawTextFilterBuilder: DrawTextFilterBuilder
) {

    /** @return list ekspresi `drawtext=...` (satu per baris teks) siap disambung koma di filter graph. */
    suspend fun buildIntroFilters(config: TextOverlayConfig?): List<String> {
        if (config == null || config.lines.isEmpty()) return emptyList()
        val enableExpr = "lt(t,${config.displayDurationSeconds})"
        return buildStackedLineFilters(config, config.lines, enableExpr, delaySeconds = config.delaySeconds)
    }

    /**
     * @param delayAfterIntro waktu (detik) title mulai tampil (biasanya = akhir durasi intro).
     * @param audioFileNameForFallback nama file audio dipakai sebagai judul otomatis kalau
     *   [config] tidak mengisi teks baris manapun (parsing underscore -> spasi, hapus ekstensi).
     */
    suspend fun buildTitleFilters(
        config: TextOverlayConfig?,
        delayAfterIntro: Float,
        audioFileNameForFallback: String?
    ): List<String> {
        if (config == null) return emptyList()

        val resolvedLines = if (config.lines.any { it.text.isNotBlank() }) {
            config.lines
        } else {
            val fallbackText = audioFileNameForFallback
                ?.let { File(it).nameWithoutExtension }
                ?.replace('_', ' ')
                ?.trim()
                .orEmpty()
            if (fallbackText.isBlank()) return emptyList()
            listOf(TextLine(fallbackText, config.lines.firstOrNull()?.fontSizePx ?: 32))
        }

        val maxT = delayAfterIntro + config.displayDurationSeconds
        val enableExpr = "between(t,$delayAfterIntro,$maxT)"
        return buildStackedLineFilters(config, resolvedLines, enableExpr, delaySeconds = delayAfterIntro)
    }

    /**
     * Susun N baris teks dengan offset vertikal simetris terhadap posisi dasar preset (baris
     * tengah = 0 offset, baris lain digeser +/- berdasarkan indeksnya * 1.4x ukuran fontnya)
     * — sama seperti perhitungan `offset = (i - (N-1)/2.0) * (line_size * 1.4)` di Python.
     */
    private suspend fun buildStackedLineFilters(
        baseConfig: TextOverlayConfig,
        lines: List<TextLine>,
        enableExpr: String,
        delaySeconds: Float
    ): List<String> {
        val n = lines.size
        val (_, baseY) = drawTextFilterBuilder.resolveBasePosition(baseConfig)

        return lines.mapIndexed { i, line ->
            val offset = (i - (n - 1) / 2.0) * (line.fontSizePx * 1.4)
            val stackedY = if (offset == 0.0) baseY else "$baseY + ($offset)"
            val lineConfig = baseConfig.copy(yExpr = stackedY)
            drawTextFilterBuilder.buildDrawtextFilter(
                config = lineConfig,
                text = line.text,
                fontSizePx = line.fontSizePx,
                enableExpr = enableExpr,
                delaySeconds = delaySeconds
            )
        }
    }
}
