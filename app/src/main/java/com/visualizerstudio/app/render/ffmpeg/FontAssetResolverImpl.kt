package com.visualizerstudio.app.render.ffmpeg

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi [FontAssetResolver] — copy-once dari `assets/fonts/*.ttf` ke `filesDir/fonts/`
 * (FFmpeg butuh path filesystem nyata, tidak bisa baca langsung dari APK assets), lalu di-
 * cache di memori supaya tidak copy berulang tiap kali render.
 *
 * **Catatan legal (section 5.1c & Appendix A.6):** 10 font family di versi sumber diambil
 * langsung dari `C:/Windows/Fonts` (lisensi Microsoft) — TIDAK boleh dibundel ke APK. Mapping
 * di bawah memakai pengganti bebas lisensi (Liberation Sans/Serif, Carlito, Caladea, dan
 * font Google Fonts berlisensi OFL) dengan NAMA FAMILY yang identik ke daftar 5.1c, supaya
 * UI wizard (Fase 4) tidak perlu tahu bahwa file fisiknya sudah diganti.
 *
 * File `.ttf` fisik pengganti sendiri BELUM di-bundle di fase ini (aset biner) — fase yang
 * membangun UI wizard teks (Fase 4/5, siapa pun duluan) WAJIB menaruh file-file berikut persis
 * dengan nama ini di `app/src/main/assets/fonts/`, sesuai daftar [FONT_ASSET_MAP] di bawah.
 */
@Singleton
class FontAssetResolverImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FontAssetResolver {

    /** family logis (identik section 5.1c) → nama file asset pengganti bebas lisensi per style. */
    private val fontAssetMap: Map<String, Map<String, String>> = mapOf(
        "Arial" to mapOf("r" to "LiberationSans-Regular.ttf", "b" to "LiberationSans-Bold.ttf", "i" to "LiberationSans-Italic.ttf", "bi" to "LiberationSans-BoldItalic.ttf"),
        "Courier New" to mapOf("r" to "LiberationMono-Regular.ttf", "b" to "LiberationMono-Bold.ttf", "i" to "LiberationMono-Italic.ttf", "bi" to "LiberationMono-BoldItalic.ttf"),
        "Georgia" to mapOf("r" to "Gelasio-Regular.ttf", "b" to "Gelasio-Bold.ttf", "i" to "Gelasio-Italic.ttf", "bi" to "Gelasio-BoldItalic.ttf"),
        "Impact" to mapOf("r" to "Anton-Regular.ttf", "b" to "Anton-Regular.ttf", "i" to "Anton-Regular.ttf", "bi" to "Anton-Regular.ttf"),
        "Times New Roman" to mapOf("r" to "LiberationSerif-Regular.ttf", "b" to "LiberationSerif-Bold.ttf", "i" to "LiberationSerif-Italic.ttf", "bi" to "LiberationSerif-BoldItalic.ttf"),
        "Verdana" to mapOf("r" to "DejaVuSans.ttf", "b" to "DejaVuSans-Bold.ttf", "i" to "DejaVuSans-Oblique.ttf", "bi" to "DejaVuSans-BoldOblique.ttf"),
        "Comic Sans MS" to mapOf("r" to "ComicNeue-Regular.ttf", "b" to "ComicNeue-Bold.ttf", "i" to "ComicNeue-Italic.ttf", "bi" to "ComicNeue-BoldItalic.ttf"),
        "Segoe UI" to mapOf("r" to "Carlito-Regular.ttf", "b" to "Carlito-Bold.ttf", "i" to "Carlito-Italic.ttf", "bi" to "Carlito-BoldItalic.ttf"),
        "Century Gothic" to mapOf("r" to "Poppins-Regular.ttf", "b" to "Poppins-Bold.ttf", "i" to "Poppins-Italic.ttf", "bi" to "Poppins-BoldItalic.ttf"),
        "Garamond" to mapOf("r" to "EBGaramond-Regular.ttf", "b" to "EBGaramond-Bold.ttf", "i" to "EBGaramond-Italic.ttf", "bi" to "EBGaramond-BoldItalic.ttf")
    )

    private val fontsDir: File by lazy {
        File(context.filesDir, "fonts").apply { mkdirs() }
    }

    override suspend fun resolveFontFile(fontFamily: String, bold: Boolean, italic: Boolean): String =
        withContext(Dispatchers.IO) {
            val style = when {
                bold && italic -> "bi"
                bold -> "b"
                italic -> "i"
                else -> "r"
            }
            val familyMap = fontAssetMap[fontFamily] ?: fontAssetMap.getValue("Arial")
            val assetFileName = familyMap[style] ?: familyMap.getValue("r")

            val destFile = File(fontsDir, assetFileName)
            if (!destFile.exists() || destFile.length() == 0L) {
                context.assets.open("fonts/$assetFileName").use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            // FFmpeg (fontconfig) di beberapa build sensitif terhadap tanda ':' di path
            // Android (mis. content resolver cache) — filesDir aman, tapi tetap escape.
            destFile.absolutePath.replace(":", "\\:")
        }
}
