package com.visualizerstudio.app.render.ffmpeg

import com.visualizerstudio.app.domain.model.TextAnimation
import com.visualizerstudio.app.domain.model.TextOverlayConfig
import com.visualizerstudio.app.util.FfmpegUtils
import javax.inject.Inject

/** Resolusi nama font family logis (section 5.1c) → path file `.ttf` fisik di `filesDir`. */
interface FontAssetResolver {
    /** @return path absolut ke file `.ttf` yang sudah di-copy ke penyimpanan aplikasi. */
    suspend fun resolveFontFile(fontFamily: String, bold: Boolean, italic: Boolean): String
}

/**
 * Menyusun satu ekspresi filter `drawtext=...` FFmpeg lengkap dengan animasi (fade in-out,
 * slide up, slide left, zoom in), stroke opsional, dan delay tampil — porting 1:1 dari
 * `get_ffmpeg_drawtext_filter` versi Python sumber, disesuaikan ke [TextOverlayConfig]
 * (Appendix A.3) dan resolusi font lewat [FontAssetResolver] (bukan `C:/Windows/Fonts`).
 */
class DrawTextFilterBuilder @Inject constructor(
    private val fontAssetResolver: FontAssetResolver
) {

    /**
     * @param text teks satu baris (SUDAH di-escape lewat [FfmpegUtils.escapeDrawtext]
     *   sebelum dipanggil, atau boleh mentah — builder ini akan escape ulang secara aman).
     * @param enableExpr ekspresi `enable=` FFmpeg, mis. `lt(t,5.0)` atau `between(t,2.0,7.0)`.
     * @param delaySeconds waktu (detik) sebelum animasi masuk mulai dihitung dari t=0 timeline.
     * @param fontSizePx ukuran font dasar dalam px (dari [com.visualizerstudio.app.domain.model.TextLine]).
     */
    suspend fun buildDrawtextFilter(
        config: TextOverlayConfig,
        text: String,
        fontSizePx: Int,
        enableExpr: String,
        delaySeconds: Float = 0f
    ): String {
        val fontFile = fontAssetResolver.resolveFontFile(config.fontFamily, config.bold, config.italic)
        val escapedText = FfmpegUtils.escapeDrawtext(text)

        val animDur = config.animationDurationSeconds
        val dispDur = config.displayDurationSeconds
        val opacity = config.opacity

        val tVar = if (delaySeconds > 0f) "(t-$delaySeconds)" else "t"

        val alphaExpr = if (delaySeconds > 0f) {
            "if(lt(t,$delaySeconds),0," +
                "if(lt($tVar,$animDur),($tVar/$animDur)*$opacity," +
                "if(lt($tVar,$dispDur),$opacity," +
                "max(0,$opacity*(1-($tVar-$dispDur)/$animDur)))))"
        } else {
            "if(lt($tVar,$animDur),($tVar/$animDur)*$opacity," +
                "if(lt($tVar,$dispDur),$opacity," +
                "max(0,$opacity*(1-($tVar-$dispDur)/$animDur))))"
        }

        val (baseX, baseY) = resolveBasePosition(config)
        var xExpr = baseX
        var yExpr = baseY
        var sizeExpr = fontSizePx.toString()

        when (config.animationStyle) {
            TextAnimation.SLIDE_UP -> {
                yExpr = "if(lt($tVar,$animDur),$baseY+(1-$tVar/$animDur)*50,$baseY)"
                if (delaySeconds > 0f) yExpr = "if(lt(t,$delaySeconds),$baseY+50,$yExpr)"
            }
            TextAnimation.SLIDE_LEFT -> {
                xExpr = "if(lt($tVar,$animDur),$baseX+(1-$tVar/$animDur)*150,$baseX)"
                if (delaySeconds > 0f) xExpr = "if(lt(t,$delaySeconds),$baseX+150,$xExpr)"
            }
            TextAnimation.ZOOM_IN -> {
                sizeExpr = "if(lt($tVar,$animDur),$fontSizePx*(0.5+0.5*($tVar/$animDur)),$fontSizePx)"
                if (delaySeconds > 0f) sizeExpr = "if(lt(t,$delaySeconds),$fontSizePx*0.5,$sizeExpr)"
            }
            TextAnimation.FADE_IN_OUT -> { /* posisi & ukuran tetap, hanya alpha yang beranimasi */ }
        }

        val strokeExpr = if (config.useStroke) {
            ":borderw=${config.strokeWidthPx}:bordercolor=${config.strokeColor}"
        } else ""

        return "drawtext=fontfile='$fontFile':text='$escapedText':fontsize='$sizeExpr':" +
            "fontcolor=${config.fontColor}:alpha='$alphaExpr':x='$xExpr':y='$yExpr':" +
            "enable='$enableExpr'$strokeExpr"
    }

    /**
     * 9 preset posisi (section 5.1c) → ekspresi x/y FFmpeg, kecuali sudah di-override manual.
     * Dibuka non-private supaya [IntroTitleFilterBuilder] bisa menghitung offset stacking
     * vertikal antar-baris di atas posisi dasar yang sama.
     */
    internal fun resolveBasePosition(config: TextOverlayConfig): Pair<String, String> {
        val preset = when (config.positionPreset) {
            com.visualizerstudio.app.domain.model.TextPosition.CENTER_CENTER -> "(w-tw)/2" to "(h-th)/2"
            com.visualizerstudio.app.domain.model.TextPosition.CENTER_BOTTOM -> "(w-tw)/2" to "h-th-40"
            com.visualizerstudio.app.domain.model.TextPosition.CENTER_TOP -> "(w-tw)/2" to "40"
            com.visualizerstudio.app.domain.model.TextPosition.TOP_LEFT -> "40" to "40"
            com.visualizerstudio.app.domain.model.TextPosition.TOP_RIGHT -> "w-tw-40" to "40"
            com.visualizerstudio.app.domain.model.TextPosition.MID_LEFT -> "40" to "(h-th)/2"
            com.visualizerstudio.app.domain.model.TextPosition.MID_RIGHT -> "w-tw-40" to "(h-th)/2"
            com.visualizerstudio.app.domain.model.TextPosition.BOTTOM_LEFT -> "40" to "h-th-40"
            com.visualizerstudio.app.domain.model.TextPosition.BOTTOM_RIGHT -> "w-tw-40" to "h-th-40"
        }
        // xExpr/yExpr manual (drag di Canvas Editor Fase 5, atau offset stacking antar-baris
        // dari IntroTitleFilterBuilder) meng-override preset SECARA INDEPENDEN per sumbu.
        return (config.xExpr ?: preset.first) to (config.yExpr ?: preset.second)
    }
}
