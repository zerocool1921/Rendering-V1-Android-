package com.visualizerstudio.app.render.gl

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hasil validasi/konversi satu shader.
 *
 * [glslEs] hanya terisi kalau [isValid] == true. [errors] berisi alasan gagal
 * kalau [isValid] == false (dipakai untuk ditampilkan ke user di UI Shader Studio,
 * BUKAN untuk crash render).
 */
data class ShaderValidationResult(
    val isValid: Boolean,
    val glslEs: String? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Superset uniform yang SAH dipakai oleh ketiga template (section 6.0 blueprint).
 * Nama harus dieja PERSIS seperti tabel — ini yang dipakai ShaderValidator untuk
 * menolak shader yang memakai nama uniform di luar daftar ini.
 */
object ShaderUniformContract {

    /** name -> deklarasi GLSL ES lengkap (dipakai kalau perlu auto-insert deklarasi hilang) */
    val ALLOWED: LinkedHashMap<String, String> = linkedMapOf(
        "v_uv" to "in vec2 v_uv;",
        "u_resolution" to "uniform vec2 u_resolution;",
        "u_time" to "uniform float u_time;",
        "u_bars" to "uniform float u_bars[120];",
        "u_num_bars" to "uniform int u_num_bars;",
        "u_color" to "uniform vec3 u_color;",
        "u_zoom_factor" to "uniform float u_zoom_factor;",
        "u_shear_x" to "uniform float u_shear_x;",
        "u_shear_y" to "uniform float u_shear_y;",
        "u_total_duration" to "uniform float u_total_duration;",
        "u_phase_count" to "uniform int u_phase_count;",
        "u_phase_duration" to "uniform float u_phase_duration[8];",
        "u_phase_line1_chars" to "uniform int u_phase_line1_chars[192];",
        "u_phase_line2_chars" to "uniform int u_phase_line2_chars[192];",
        "u_phase_line1_len" to "uniform int u_phase_line1_len[8];",
        "u_phase_line2_len" to "uniform int u_phase_line2_len[8];",
        "u_loop_duration" to "uniform float u_loop_duration;",
        "u_effect_mode" to "uniform int u_effect_mode;"
    )

    /** uniform yang WAJIB ada di setiap shader, apapun kategorinya */
    val ALWAYS_REQUIRED = setOf("v_uv", "u_resolution", "u_time")

    fun requiredFor(category: ShaderCategory): Set<String> = when (category) {
        ShaderCategory.TIMER_3D -> ALWAYS_REQUIRED + setOf("u_total_duration")
        ShaderCategory.SPECTRUM -> ALWAYS_REQUIRED + setOf("u_bars", "u_num_bars")
        ShaderCategory.OVERLAY_LOOP -> ALWAYS_REQUIRED + setOf("u_loop_duration")
        ShaderCategory.BUILTIN -> ALWAYS_REQUIRED
    }
}

enum class ShaderCategory { TIMER_3D, SPECTRUM, OVERLAY_LOOP, BUILTIN }

/**
 * Fungsi ES yang TIDAK tersedia / berbeda perilaku di GLSL ES 3.0 dibanding desktop GLSL 330.
 * Kalau ditemukan di source, shader GAGAL validasi (langkah 4 di section 6.0) — bukan
 * di-auto-fix, karena penggantinya butuh keputusan semantik yang tidak aman diotomatiskan.
 */
private val ES_INCOMPATIBLE_PATTERNS: List<Regex> = listOf(
    Regex("""\btextureGrad\b"""),
    Regex("""\btextureQueryLod\b"""),
    Regex("""\bgl_FragColor\b"""),
    Regex("""\bgl_FragData\b"""),
    Regex("""#extension\s+GL_ARB_"""),
    Regex("""\bnoperspective\b"""),
    Regex("""\bimageLoad\b"""),
    Regex("""\bimageStore\b""")
)

@Singleton
class ShaderValidator @Inject constructor() {

    /**
     * Jalankan 5 langkah konversi + validasi wajib (section 6.0) terhadap 1 source shader
     * mentah `#version 330`. Dipanggil tiap kali: (a) mode Template mensubstitusi skeleton,
     * (b) mode AI mengembalikan kode, (c) mode Impor menerima file .glsl dari user.
     */
    fun validateAndConvert(rawSource: String, category: ShaderCategory): ShaderValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var src = rawSource

        // --- Langkah 1: ganti header versi desktop -> ES ---
        val hadVersion330 = Regex("""#version\s+330\b""").containsMatchIn(src)
        src = Regex("""#version\s+330(\s+core)?""").replace(src, "#version 300 es")
        if (!Regex("""#version\s+300\s+es""").containsMatchIn(src)) {
            // shader tidak punya header versi sama sekali -> tambahkan di baris pertama
            src = "#version 300 es\n$src"
        }
        if (!hadVersion330) {
            warnings.add("Source tidak memiliki header '#version 330' asli — header ES tetap dipaksa di baris pertama.")
        }

        // --- Langkah 2: precision mediump float; tepat setelah header versi ---
        src = ensurePrecisionAfterVersion(src)

        // --- Langkah 3: pastikan `out vec4 fragColor;` ada ---
        if (!Regex("""out\s+vec4\s+fragColor\s*;""").containsMatchIn(src)) {
            src = insertAfterPrecision(src, "out vec4 fragColor;")
        }

        // --- Langkah 4: cek fungsi/keyword non-ES ---
        for (pattern in ES_INCOMPATIBLE_PATTERNS) {
            if (pattern.containsMatchIn(src)) {
                errors.add("Memakai konstruksi yang tidak didukung GLSL ES 3.0: '${pattern.pattern}'.")
            }
        }
        if (Regex("""\bmainImage\b""").containsMatchIn(src) || Regex("""\biChannel\d""").containsMatchIn(src) || Regex("""\biResolution\b""").containsMatchIn(src)) {
            errors.add("Terdeteksi gaya Shadertoy (mainImage/iChannel/iResolution) — tidak didukung pipeline ini.")
        }
        if (Regex("""\bsampler2D\b""").containsMatchIn(src) || Regex("""\btexture\s*\(""").containsMatchIn(src)) {
            errors.add("Terdeteksi sampling tekstur eksternal — semua bentuk visual wajib prosedural (SDF/noise/hash), tidak ada aset gambar yang di-load saat render.")
        }

        // --- Langkah 5: validasi nama uniform sesuai kontrak superset ---
        val declaredUniforms = Regex("""uniform\s+[\w\[\]]+\s+(\w+)\s*(\[\s*\d+\s*\])?\s*;""")
            .findAll(src)
            .map { it.groupValues[1] }
            .toSet()
        val usedIdentifiers = Regex("""\bu_\w+\b""").findAll(src).map { it.value }.toSet()

        val disallowed = usedIdentifiers.filter { it !in ShaderUniformContract.ALLOWED.keys }
        if (disallowed.isNotEmpty()) {
            errors.add("Memakai nama uniform di luar kontrak section 6.0: ${disallowed.joinToString()}.")
        }

        val required = ShaderUniformContract.requiredFor(category)
        val missingRequired = required.filter { name ->
            name != "v_uv" && name !in declaredUniforms
        }
        if (missingRequired.isNotEmpty()) {
            errors.add("Uniform wajib untuk kategori $category belum dideklarasikan: ${missingRequired.joinToString()}.")
        }
        if (!Regex("""in\s+vec2\s+v_uv\s*;""").containsMatchIn(src)) {
            errors.add("Varying wajib 'in vec2 v_uv;' tidak ditemukan.")
        }

        // area-luar-frame wajib transparan (aturan tambahan section 6.0)
        if (!Regex("""fragColor\s*=\s*vec4\(0\.0\)""").containsMatchIn(src)) {
            warnings.add("Tidak ditemukan 'fragColor = vec4(0.0);' — pastikan area di luar v_uv 0..1 tetap transparan penuh.")
        }

        if (Regex("""#define\s+\w*(DURATION|LOOP)\w*\s+\d""", RegexOption.IGNORE_CASE).containsMatchIn(src)) {
            errors.add("Terdeteksi durasi/loop di-hardcode lewat #define — wajib pakai uniform u_total_duration / u_loop_duration.")
        }

        if (category == ShaderCategory.TIMER_3D) {
            if (!(Regex("""if\s*\(\s*hours\s*>\s*0\s*\)""").containsMatchIn(src))) {
                errors.add("Timer 3D wajib punya cabang if(hours > 0) untuk format HH:MM:SS, dengan else MM:SS.")
            }
        }
        if (category == ShaderCategory.OVERLAY_LOOP) {
            if (!Regex("""mod\s*\(\s*u_time\s*,\s*u_loop_duration\s*\)""").containsMatchIn(src)) {
                errors.add("Overlay Loop wajib pakai mod(u_time, u_loop_duration) supaya seamless loop.")
            }
        }

        return if (errors.isEmpty()) {
            ShaderValidationResult(isValid = true, glslEs = src, warnings = warnings)
        } else {
            ShaderValidationResult(isValid = false, glslEs = null, errors = errors, warnings = warnings)
        }
    }

    private fun ensurePrecisionAfterVersion(source: String): String {
        if (Regex("""precision\s+mediump\s+float\s*;""").containsMatchIn(source)) return source
        val lines = source.lines().toMutableList()
        val versionIdx = lines.indexOfFirst { it.trim().startsWith("#version") }
        val insertAt = if (versionIdx >= 0) versionIdx + 1 else 0
        lines.add(insertAt, "precision mediump float;")
        return lines.joinToString("\n")
    }

    private fun insertAfterPrecision(source: String, declaration: String): String {
        val lines = source.lines().toMutableList()
        val precisionIdx = lines.indexOfFirst { it.trim().startsWith("precision") }
        val insertAt = if (precisionIdx >= 0) precisionIdx + 1 else 1
        lines.add(insertAt, declaration)
        return lines.joinToString("\n")
    }
}
