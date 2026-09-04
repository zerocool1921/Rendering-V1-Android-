package com.visualizerstudio.app.render.gl

/**
 * Skeleton "Mode Template" (tanpa AI, section 6.4) untuk ketiga wizard Shader Studio.
 * Output fungsi-fungsi di sini adalah GLSL **`#version 330` mentah** (persis gaya penulisan
 * di 3 file TEMPLATE_PROMPT_*.md) — WAJIB selalu dilewatkan ke [ShaderValidator.validateAndConvert]
 * sebelum disimpan sebagai [com.visualizerstudio.app.domain.model].ShaderTemplateEntity, karena
 * konversi ke GLSL ES 3.0 (langkah 6.0) belum dilakukan di sini secara sengaja — supaya satu
 * jalur konversi/validasi (ShaderValidator) dipakai konsisten oleh Mode Template, Mode AI,
 * maupun Mode Impor (section 6.4).
 */
object ShaderSkeletons {

    private fun hexToVec3(hex: String): Triple<Float, Float, Float> {
        val clean = hex.removePrefix("#").padStart(6, '0')
        val r = clean.substring(0, 2).toInt(16) / 255f
        val g = clean.substring(2, 4).toInt(16) / 255f
        val b = clean.substring(4, 6).toInt(16) / 255f
        return Triple(r, g, b)
    }

    // ---------------------------------------------------------------------
    // 6.1 Timer Countdown 3D — acuan TEMPLATE_PROMPT_TIMER_3D.md
    // ---------------------------------------------------------------------
    data class TimerPhase(val line1: String, val line2: String = "") {
        val durationSec: Float
            get() {
                val totalChar = line1.length + line2.length
                val isShort = totalChar <= 4 // angka / kata pendek ("3","2","1","GO","Ready")
                return if (isShort) 1.0f
                else (1.5f + totalChar * 0.06f).coerceIn(1.5f, 4.0f)
            }
    }

    fun timer3D(colorHex: String, phases: List<TimerPhase> = emptyList()): String {
        val (r, g, b) = hexToVec3(colorHex)
        val phaseCount = phases.size.coerceAtMost(8)
        val durations = phases.take(8).joinToString(", ") { "%.2f".format(it.durationSec) }
        val phaseComment = phases.take(8).mapIndexed { i, p -> "// fase $i: \"${p.line1}\" / \"${p.line2}\" durasi=${"%.2f".format(p.durationSec)}s" }
            .joinToString("\n")

        return """
            #version 330
            precision highp float;

            in vec2 v_uv;
            out vec4 fragColor;

            uniform vec2 u_resolution;
            uniform float u_bars[120];
            uniform int u_num_bars;
            uniform vec3 u_color;
            uniform float u_time;
            uniform float u_zoom_factor;
            uniform float u_shear_x;
            uniform float u_shear_y;
            uniform float u_total_duration;
            uniform int u_phase_count;
            uniform float u_phase_duration[8];
            uniform int u_phase_line1_chars[192];
            uniform int u_phase_line2_chars[192];
            uniform int u_phase_line1_len[8];
            uniform int u_phase_line2_len[8];

            $phaseComment
            // (u_phase_count aplikasi akan diisi otomatis = $phaseCount, u_phase_duration = [$durations])

            float dfLine(vec2 p, vec2 a, vec2 b) {
                vec2 pa = p - a; vec2 ba = b - a;
                float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
                return length(pa - ba * h);
            }

            float digitSDF(vec2 p, int digit) {
                float w = 0.20; float h = 0.40; float r = 0.045;
                vec2 p0 = vec2(-w, h); vec2 p1 = vec2(w, h); vec2 p2 = vec2(w, 0.0);
                vec2 p3 = vec2(w, -h); vec2 p4 = vec2(-w, -h); vec2 p5 = vec2(-w, 0.0);
                float d = 10.0;
                if (digit == 8) {
                    d = min(d, dfLine(p, p0, p1)); d = min(d, dfLine(p, p1, p2));
                    d = min(d, dfLine(p, p2, p3)); d = min(d, dfLine(p, p3, p4));
                    d = min(d, dfLine(p, p4, p5)); d = min(d, dfLine(p, p5, p0));
                    d = min(d, dfLine(p, p5, p2));
                } else {
                    d = min(d, dfLine(p, p0, p1)); d = min(d, dfLine(p, p1, p2));
                    d = min(d, dfLine(p, p2, p3)); d = min(d, dfLine(p, p5, p2));
                }
                return d - r;
            }

            float charSDF16Segment(vec2 p, int code) {
                // 16-segment sederhana, satu keluarga visual dengan digitSDF (garis lurus SDF)
                float w = 0.18; float h = 0.38; float r = 0.04;
                vec2 a = vec2(-w, h); vec2 bb = vec2(w, h); vec2 c = vec2(w, -h); vec2 d0 = vec2(-w, -h);
                float d = min(dfLine(p, a, bb), dfLine(p, bb, c));
                d = min(d, dfLine(p, c, d0));
                d = min(d, dfLine(p, d0, a));
                return d - r;
            }

            vec3 shade3D(float d, vec2 uv, vec3 baseColor) {
                float eps = 0.001;
                float dx = dFdx(d);
                float dy = dFdy(d);
                vec3 normal = normalize(vec3(-dx, -dy, eps));
                vec3 lightDir = normalize(vec3(0.4, 0.6, 0.8));
                float diffuse = max(dot(normal, lightDir), 0.0);
                vec3 viewDir = vec3(0.0, 0.0, 1.0);
                vec3 halfV = normalize(lightDir + viewDir);
                float specular = pow(max(dot(normal, halfV), 0.0), 24.0);
                float rim = pow(1.0 - max(dot(normal, viewDir), 0.0), 2.0);
                return baseColor * (0.35 + 0.65 * diffuse) + vec3(specular) * 0.5 + rim * baseColor * 0.4;
            }

            void main() {
                vec2 st = v_uv;
                if (st.x < 0.0 || st.x > 1.0 || st.y < 0.0 || st.y > 1.0) {
                    fragColor = vec4(0.0); return;
                }

                vec2 uv = (st - 0.5) * u_resolution / min(u_resolution.x, u_resolution.y);
                uv.x += u_shear_x * uv.y;
                uv.y += u_shear_y * uv.x;

                float intro_total = 0.0;
                for (int i = 0; i < u_phase_count; i++) intro_total += u_phase_duration[i];

                float cutoff = 0.06;
                float d = 10.0;
                vec3 baseColor = u_color; // warna dari uniform pilihan user (fallback default: $colorHex)

                if (u_time < intro_total) {
                    // fase teks intro bergantian (16-segment SDF, satu keluarga visual dgn digit)
                    d = charSDF16Segment(uv, 0);
                } else {
                    float time_left = max(0.0, u_total_duration - u_time);
                    int total_sec = int(floor(time_left));
                    int hours = total_sec / 3600;
                    int minutes = (total_sec % 3600) / 60;
                    int seconds = total_sec % 60;

                    if (hours > 0) {
                        // format HH:MM:SS
                        int h1 = hours / 10; int h2 = hours % 10;
                        d = min(d, digitSDF(uv + vec2(0.9, 0.0), h1));
                        d = min(d, digitSDF(uv + vec2(0.7, 0.0), h2));
                    } else {
                        // format MM:SS
                        int m1 = minutes / 10; int m2 = minutes % 10;
                        int s1 = seconds / 10; int s2 = seconds % 10;
                        d = min(d, digitSDF(uv + vec2(0.3, 0.0), m1));
                        d = min(d, digitSDF(uv + vec2(0.1, 0.0), m2));
                        d = min(d, digitSDF(uv + vec2(-0.15, 0.0), s1));
                        d = min(d, digitSDF(uv + vec2(-0.35, 0.0), s2));
                    }
                    float blink = smoothstep(0.02, 0.0, abs(sin(u_time * 3.14159265)));
                    d = min(d, dfLine(uv, vec2(-0.02, 0.05), vec2(-0.02, -0.05)) - 0.03 * blink);
                }

                if (d > cutoff) { fragColor = vec4(0.0); return; }
                vec3 shaded = shade3D(d, uv, baseColor);
                fragColor = vec4(shaded, 1.0 - smoothstep(0.0, cutoff, d));
            }
        """.trimIndent()
    }

    // ---------------------------------------------------------------------
    // 6.2 Spectrum Custom — acuan TEMPLATE_PROMPT_SPECTRUM.md
    // ---------------------------------------------------------------------
    enum class SpectrumStyle { BAR, WAVE, CIRCULAR, PARTICLE }

    fun spectrumCustom(
        colorHex: String,
        style: SpectrumStyle,
        barCount: Int = 64,
        beatZoomSensitivity: Float = 0.3f,
        glow: Boolean = true
    ): String {
        val (r, g, b) = hexToVec3(colorHex)
        val styleBody = when (style) {
            SpectrumStyle.BAR -> """
                float bar_w = 1.0 / float($barCount);
                int bar_i = int(st.x * float($barCount));
                float bh = get_bar_height(float(bar_i) / float($barCount - 1));
                float local_x = fract(st.x * float($barCount));
                float shape = step(0.08, local_x) * step(local_x, 0.92);
                col = shape * step(st.y, bh) * u_color;
                alpha = shape * step(st.y, bh);
            """.trimIndent()
            SpectrumStyle.WAVE -> """
                float bh = get_bar_height(st.x);
                float lineDist = abs(st.y - (0.5 + (bh - 0.5) * 0.9));
                float shape = smoothstep(0.03, 0.0, lineDist);
                col = shape * u_color;
                alpha = shape;
            """.trimIndent()
            SpectrumStyle.CIRCULAR -> """
                vec2 c = st - 0.5;
                float ang = atan(c.y, c.x) / (2.0 * 3.14159265) + 0.5;
                float bh = get_bar_height(ang);
                float radius = 0.25 + bh * 0.2;
                float ring = smoothstep(0.02, 0.0, abs(length(c) - radius));
                col = ring * u_color;
                alpha = ring;
            """.trimIndent()
            SpectrumStyle.PARTICLE -> """
                float bh = get_bar_height(st.x);
                float pd = abs(st.y - (1.0 - bh));
                float shape = smoothstep(0.015, 0.0, pd);
                col = shape * u_color;
                alpha = shape;
            """.trimIndent()
        }
        val glowBlock = if (glow) """
            float glowAmt = get_bar_height(st.x) * 0.4;
            col += u_color * glowAmt * 0.5;
            alpha = clamp(alpha + glowAmt * 0.2, 0.0, 1.0);
        """.trimIndent() else ""

        return """
            #version 330
            precision mediump float;

            in vec2 v_uv;
            out vec4 fragColor;

            uniform vec2 u_resolution;
            uniform float u_time;
            uniform float u_bars[120];
            uniform int u_num_bars;
            uniform vec3 u_color;
            uniform float u_zoom_factor;
            uniform float u_shear_x;
            uniform float u_shear_y;

            float get_bar_height(float norm_x) {
                float x = clamp(norm_x, 0.0, 1.0);
                float index_f = x * float(u_num_bars - 1);
                int idx = int(floor(index_f));
                int next_idx = min(idx + 1, u_num_bars - 1);
                float frac = smoothstep(0.0, 1.0, fract(index_f));
                return mix(u_bars[idx], u_bars[next_idx], frac);
            }

            void main() {
                vec2 st = v_uv;
                if (st.x < 0.0 || st.x > 1.0 || st.y < 0.0 || st.y > 1.0) {
                    fragColor = vec4(0.0); return;
                }
                st.x += u_shear_x * st.y;
                st.y += u_shear_y * st.x;

                float beatZoom = 1.0 + u_zoom_factor * $beatZoomSensitivity;
                st = (st - 0.5) / beatZoom + 0.5;

                vec3 col = vec3(0.0);
                float alpha = 0.0;
                // u_color: warna dari uniform pilihan user (fallback default: $colorHex)

                $styleBody
                $glowBlock

                fragColor = vec4(col, clamp(alpha, 0.0, 1.0));
            }
        """.trimIndent()
    }

    // ---------------------------------------------------------------------
    // 6.3 Overlay Effect Loop — acuan TEMPLATE_PROMPT_OVERLAY_GENERIC_LOOP.md
    // ---------------------------------------------------------------------
    enum class OverlayEffectKind { RAIN_SNOW_DUST, FIRE, FIREFLY, EXPLOSION_PARTICLE }

    /** mode=0 -> overlay (arah turun tetap), mode=1 -> effect (arah alami) */
    fun overlayEffectLoop(
        colorHex: String,
        kind: OverlayEffectKind,
        density: Float = 40f,
        loopDurationSec: Float = 120f
    ): String {
        val (r, g, b) = hexToVec3(colorHex)
        val effectMode = when (kind) {
            OverlayEffectKind.RAIN_SNOW_DUST -> 0
            else -> 1
        }
        val yVelocitySign = if (effectMode == 0) "+=" else "-="

        return """
            #version 330
            precision highp float;

            in vec2 v_uv;
            out vec4 fragColor;

            uniform vec2 u_resolution;
            uniform float u_bars[120];
            uniform int u_num_bars;
            uniform vec3 u_color;
            uniform float u_time;
            uniform float u_zoom_factor;
            uniform float u_loop_duration;
            uniform int u_effect_mode;

            #define TWO_PI 6.28318530718
            #define DENSITY ${density}.0

            vec2 hash22(vec2 p) {
                float n = sin(dot(p, vec2(41.0, 289.0)));
                return fract(vec2(262144.0, 32768.0) * n) * 2.0 - 1.0;
            }

            float particleLayer(vec2 uv, float t, float cellScale, float sizeFactor, vec2 dir) {
                vec2 grid = uv * cellScale;
                vec2 cellId = floor(grid);
                vec2 cellUv = fract(grid) - 0.5;

                float loopT = mod(t, u_loop_duration) / u_loop_duration;
                vec2 rnd = hash22(cellId);
                float phase = fract(loopT + rnd.x);

                vec2 particlePos = cellUv;
                particlePos.x += rnd.y * 0.3;
                particlePos.y $yVelocitySign phase * 1.4;
                particlePos.y = fract(particlePos.y + 0.5) - 0.5;

                float d = length(particlePos);
                float r = 0.06 * sizeFactor * (0.5 + 0.5 * abs(rnd.x));
                float alpha = smoothstep(r, r * 0.15, d);
                if (rnd.x > 0.55) alpha *= step(0.5 - r, DENSITY / 1000.0 + 0.5);
                return alpha;
            }

            void main() {
                vec2 st = v_uv;
                if (st.x < 0.0 || st.x > 1.0 || st.y < 0.0 || st.y > 1.0) {
                    fragColor = vec4(0.0); return;
                }

                float loopT = mod(u_time, u_loop_duration);
                vec2 dir = u_effect_mode == 0 ? vec2(0.0, -1.0) : vec2(0.0, 1.0);

                float dustLayer = particleLayer(st, loopT, DENSITY, 0.6, dir);
                float glowLayer = particleLayer(st * 0.6 + 3.7, loopT * 0.7, DENSITY * 0.4, 1.6, dir);

                float alpha = clamp(dustLayer * 0.8 + glowLayer * 0.35, 0.0, 1.0);
                if (isnan(alpha)) alpha = 0.0;

                vec3 col = u_color; // fallback default: $colorHex
                fragColor = vec4(col, alpha);
            }
        """.trimIndent()
    }
}
