#version 300 es
// spectrum_default.glsl — shader bawaan (built-in) default, Fase 3.
// Hasil konversi GLSL ES 3.0 dari SPECTRUM_FRAGMENT_SHADER (spectrum_generator.py, #version 330)
// mengikuti 5 langkah konversi section 6.0 blueprint:
//  1) header #version 330 -> #version 300 es (baris di atas)
//  2) precision default ditambahkan tepat setelah header versi (baris di bawah). Dipertahankan
//     `highp` (bukan `mediump` literal) karena source asli memang butuh highp untuk stabilitas
//     trigonometri u_time pada durasi render panjang — deviasi ini didokumentasikan, ganti ke
//     mediump di sini kapan saja kalau ShaderValidator (Fase 5) mewajibkan mediump persis.
//  3) `out vec4 fragColor;` sudah dideklarasikan eksplisit (baris di bawah), tidak pakai
//     gl_FragColor implisit.
//  4) dicek: tidak ada fungsi non-ES (tidak ada textureGrad/ekstensi desktop-only apapun,
//     semua fungsi yang dipakai — dot, length, atan(y,x), mix, smoothstep, clamp, fract, floor,
//     abs, sin, cos, pow, min, max — tersedia penuh di GLSL ES 3.0).
//  5) semua uniform dipakai persis nama & tipe di tabel gabungan section 6.0 (superset boleh
//     dideklarasikan tanpa dipakai; TIDAK ADA nama uniform lain di luar tabel itu di file ini).
precision highp float;

in vec2 v_uv;
out vec4 fragColor;

uniform vec2 u_resolution;
uniform float u_bars[120];
uniform int u_num_bars;
uniform vec3 u_color;
uniform float u_time;
uniform int u_spec_type;
// Dideklarasikan sesuai tabel section 6.0 (superset uniform), tidak dipakai langsung di file
// ini (dibaca di vertex shader) — deklarasi ganda antar vertex/fragment tidak masalah di GLSL ES.
uniform float u_zoom_factor;
uniform float u_shear_x;
uniform float u_shear_y;
uniform float u_total_duration;

float get_bar_height(float norm_x) {
    float x = clamp(norm_x, 0.0, 1.0);
    float index_f = x * float(u_num_bars - 1);
    int idx = int(floor(index_f));
    int next_idx = min(idx + 1, u_num_bars - 1);
    float frac = smoothstep(0.0, 1.0, fract(index_f));
    return mix(u_bars[idx], u_bars[next_idx], frac);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

float drawNote(vec2 p, float scale) {
    p /= scale;
    vec2 head_pos = p + vec2(0.08, 0.12);
    float head = length(head_pos * vec2(1.2, 0.9)) - 0.08;
    float stem = max(abs(p.x - 0.02) - 0.012, max(p.y - 0.15, -p.y - 0.05));
    float flag = max(abs(p.x - 0.06) - 0.04, abs(p.y - 0.12) - 0.02);
    return min(head, min(stem, flag));
}

void main() {
    vec2 st = v_uv;

    if (st.x < 0.0 || st.x > 1.0 || st.y < 0.0 || st.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }

    vec4 finalColor = vec4(0.0, 0.0, 0.0, 0.0);
    float aspect = u_resolution.x / u_resolution.y;

    // ID 6: PINK OSCILLOSCOPE WAVEFORM
    if (u_spec_type == 6) {
        float mid_y = 0.5;
        float wave_multi = sin(st.x * 45.0 + u_time * 6.0) * 0.50
                         + sin(st.x * 95.0 - u_time * 10.0) * 0.30
                         + sin(st.x * 180.0 + u_time * 14.0) * 0.20;

        float amp = (get_bar_height(st.x) * 0.38 + 0.012);
        float wave_y = mid_y + wave_multi * amp;

        float dist = abs(st.y - wave_y);
        float line_alpha = smoothstep(0.007, 0.001, dist);
        float glow_alpha = smoothstep(0.035, 0.0, dist) * 0.75;
        float total_alpha = clamp(line_alpha + glow_alpha, 0.0, 1.0);

        vec3 pink_core = vec3(1.0, 0.85, 0.98);
        vec3 magenta_glow = vec3(0.92, 0.18, 0.82);

        vec3 col = mix(magenta_glow, pink_core, line_alpha);
        finalColor = vec4(col * total_alpha, total_alpha);
    }
    // ID 7: CIRCULAR GRADIENT BLOOM SPECTRUM
    else if (u_spec_type == 7) {
        vec2 pos = (st - vec2(0.5)) * vec2(aspect, 1.0);
        float dist = length(pos);
        float angle = atan(pos.y, pos.x);
        float norm_angle = (angle + 3.14159265359) / (2.0 * 3.14159265359);

        float base_r = 0.22;
        float bar_h = get_bar_height(abs(norm_angle * 2.0 - 1.0)) * 0.20;

        float total_bars = 64.0;
        float angle_dist = abs(fract(norm_angle * total_bars) - 0.5);
        float width_mask = smoothstep(0.42, 0.28, angle_dist);

        vec3 bar_color;
        if (norm_angle < 0.5) {
            bar_color = mix(vec3(0.12, 0.92, 0.95), vec3(0.78, 0.22, 0.95), norm_angle * 2.0);
        } else {
            bar_color = mix(vec3(0.78, 0.22, 0.95), vec3(0.12, 0.92, 0.95), (norm_angle - 0.5) * 2.0);
        }

        float bar_length = base_r + bar_h;
        float radial_mask = smoothstep(base_r - 0.002, base_r + 0.004, dist) * smoothstep(bar_length + 0.004, bar_length - 0.004, dist);

        float bar_alpha = radial_mask * width_mask;
        float glow = smoothstep(bar_length + 0.10, base_r, dist) * 0.45 * width_mask;
        float total_alpha = clamp(bar_alpha + glow, 0.0, 1.0);

        finalColor = vec4(bar_color * total_alpha, total_alpha);
    }
    // ID 8: RAINBOW CIRCLE WITH INNER EQUALIZER
    else if (u_spec_type == 8) {
        vec2 pos = (st - vec2(0.5)) * vec2(aspect, 1.0);
        float dist = length(pos);
        float angle = atan(pos.y, pos.x);
        float norm_angle = (angle + 3.14159265359) / (2.0 * 3.14159265359);

        float base_r = 0.24;
        float bar_h = get_bar_height(abs(norm_angle * 2.0 - 1.0)) * 0.20;

        float total_bars = 72.0;
        float angle_dist = abs(fract(norm_angle * total_bars) - 0.5);
        float width_mask = smoothstep(0.45, 0.28, angle_dist);

        vec3 rainbow_color = hsv2rgb(vec3(fract(norm_angle + 0.25), 0.92, 1.0));

        float bar_len = base_r + bar_h;
        float radial_mask = smoothstep(base_r, base_r + 0.003, dist) * smoothstep(bar_len + 0.004, bar_len - 0.004, dist);
        float outer_bar_alpha = radial_mask * width_mask;

        float white_ring = smoothstep(0.005, 0.002, abs(dist - base_r));

        float inner_eq_alpha = 0.0;
        vec3 inner_pink = vec3(0.96, 0.25, 0.58);

        if (dist < (base_r - 0.015)) {
            vec2 inner_uv = pos / (base_r - 0.015);
            float eq_x = inner_uv.x * 0.5 + 0.5;
            if (eq_x >= 0.15 && eq_x <= 0.85) {
                float num_eq_bars = 22.0;
                float eq_bar_idx = floor(eq_x * num_eq_bars);
                float eq_bar_x = eq_bar_idx / num_eq_bars;
                float eq_gap = smoothstep(0.45, 0.3, abs(fract(eq_x * num_eq_bars) - 0.5));

                float h_top = get_bar_height(eq_bar_x) * 0.30;
                float h_bot = get_bar_height(1.0 - eq_bar_x) * 0.30;

                if (inner_uv.y >= 0.10 && inner_uv.y <= (0.10 + h_top)) {
                    inner_eq_alpha = eq_gap;
                }
                if (inner_uv.y <= -0.10 && inner_uv.y >= (-0.10 - h_bot)) {
                    inner_eq_alpha = eq_gap;
                }
            }
        }

        if (white_ring > 0.0) {
            finalColor = vec4(vec3(1.0) * white_ring, white_ring);
        } else if (outer_bar_alpha > 0.0) {
            finalColor = vec4(rainbow_color * outer_bar_alpha, outer_bar_alpha);
        } else if (inner_eq_alpha > 0.0) {
            finalColor = vec4(inner_pink * inner_eq_alpha, inner_eq_alpha);
        }
    }
    // ID 9: GLOWING PARTICLE WAVE
    else if (u_spec_type == 9) {
        float h = get_bar_height(st.x) * 0.88;
        vec3 rainbow_col = hsv2rgb(vec3(st.x * 0.85, 0.85, 1.0));

        float bar_alpha = (st.y <= h) ? smoothstep(h, h - 0.015, st.y) : 0.0;

        vec2 p_grid = fract(st * vec2(35.0, 18.0) + vec2(0.0, -u_time * 0.30)) - 0.5;
        float p_dist = length(p_grid);
        float p_rand = rand(floor(st * vec2(35.0, 18.0)));
        float p_glow = 0.0;
        if (p_dist < 0.22 * p_rand && st.y > h * 0.2) {
            p_glow = smoothstep(0.22 * p_rand, 0.0, p_dist) * 0.75;
        }

        float total_a = clamp(bar_alpha + p_glow, 0.0, 1.0);
        finalColor = vec4(rainbow_col * total_a, total_a);
    }
    // ID 10: RETRO RED/YELLOW LED BLOCKS
    else if (u_spec_type == 10) {
        float h = get_bar_height(st.x) * 0.90;
        float blocks = 20.0;
        float block_idx = floor(st.y * blocks) / blocks;

        float gap_x = step(0.15, fract(st.x * float(u_num_bars)));
        float gap_y = step(0.18, fract(st.y * blocks));

        if (st.y <= h) {
            vec3 col = mix(vec3(1.0, 0.8, 0.0), vec3(1.0, 0.05, 0.0), block_idx * 1.1);
            float a = gap_x * gap_y;
            finalColor = vec4(col * a, a);
        }

        float cap_y = floor((h + 0.04) * blocks) / blocks;
        if (abs(st.y - cap_y) < (1.0 / blocks) && st.y > h) {
            finalColor = vec4(vec3(1.0, 0.1, 0.1) * gap_x, gap_x);
        }
    }
    // ID 11: DOUBLE WAVE BARS
    else if (u_spec_type == 11) {
        float h = get_bar_height(st.x) * 0.85;
        vec3 col = hsv2rgb(vec3(st.x * 0.9, 0.85, 1.0));

        float gap_x = step(0.12, fract(st.x * float(u_num_bars)));
        float bar_mask = step(st.y, h) * gap_x * 0.85;

        float line_alpha = 0.0;
        for (int i = 1; i <= 3; i++) {
            float wave = 0.15 + 0.35 * sin(st.x * 12.0 + float(i) * 1.5 + u_time * 2.0) * (get_bar_height(st.x) + 0.2);
            float line_dist = abs(st.y - wave);
            line_alpha = max(line_alpha, smoothstep(0.006, 0.001, line_dist));
        }

        vec3 final_rgb = mix(col * bar_mask, vec3(1.0), line_alpha);
        float total_a = clamp(bar_mask + line_alpha, 0.0, 1.0);
        finalColor = vec4(final_rgb, total_a);
    }
    // ID 12: HALFTONE DOTS AUDIO WAVE
    else if (u_spec_type == 12) {
        vec2 grid_uv = st * vec2(60.0, 30.0);
        vec2 id = floor(grid_uv);
        vec2 f = fract(grid_uv) - 0.5;

        float norm_x = id.x / 60.0;
        float wave_h = get_bar_height(norm_x) * 0.90;
        float norm_y = id.y / 30.0;

        float max_radius = 0.45 * smoothstep(wave_h, 0.0, norm_y);
        float dot_dist = length(f);
        float dot_alpha = (norm_y <= wave_h) ? smoothstep(max_radius, max_radius - 0.05, dot_dist) : 0.0;

        finalColor = vec4(vec3(1.0) * dot_alpha, dot_alpha);
    }
    // ID 13: CLEAN MINIMAL WHITE BARS
    else if (u_spec_type == 13) {
        float h = get_bar_height(st.x) * 0.88;
        float bar_gap = step(0.2, fract(st.x * float(u_num_bars)));

        if (st.y <= h) {
            float fade_bottom = smoothstep(0.0, 0.25, st.y) * bar_gap;
            finalColor = vec4(vec3(1.0) * fade_bottom, fade_bottom);
        }
    }
    // ID 14: BAR REFLECTION WITH FLOATING CAPS
    else if (u_spec_type == 14) {
        float baseline = 0.05;
        float h = get_bar_height(st.x) * 0.80;
        vec3 col = hsv2rgb(vec3(0.8 - st.x * 0.6, 0.85, 1.0));
        float gap_x = step(0.12, fract(st.x * float(u_num_bars)));

        if (st.y >= baseline && st.y <= (baseline + h)) {
            float a = gap_x;
            finalColor = vec4(col * a, a);
        }

        float cap_y = baseline + h + 0.02;
        if (abs(st.y - cap_y) < 0.006 && st.y > baseline) {
            finalColor = vec4(col * gap_x, gap_x);
        }
    }
    // ID 15: GLITCH CYAN/BLUE GRID SPECTRUM
    else if (u_spec_type == 15) {
        float h = get_bar_height(st.x) * 0.88;
        float gap_x = step(0.1, fract(st.x * float(u_num_bars)));
        float gap_y = step(0.15, fract(st.y * 22.0));
        vec3 cyan_glow = vec3(0.1, 0.85, 1.0);

        if (st.y <= h) {
            float a = gap_x * gap_y * 0.95;
            finalColor = vec4(cyan_glow * a, a);
        }

        if (abs(st.y - (h + 0.02)) < 0.008 && st.y > 0.02) {
            finalColor = vec4(vec3(0.95, 0.2, 0.8) * gap_x, gap_x);
        }
    }
    // ID 16: NEON SINE MESH WAVES
    else if (u_spec_type == 16) {
        float base_y = 0.15;
        vec3 mesh_col = vec3(0.0);
        float total_alpha = 0.0;

        for (int i = 0; i < 5; i++) {
            float freq = 8.0 + float(i) * 3.0;
            float speed = u_time * (1.5 + float(i) * 0.5);
            float amp = get_bar_height(st.x) * (0.15 + float(i) * 0.05);

            float wave_y = base_y + abs(sin(st.x * freq + speed)) * amp * 2.5;
            float dist = abs(st.y - wave_y);

            vec3 col_i = hsv2rgb(vec3(0.5 + float(i) * 0.1, 0.85, 1.0));
            float alpha_i = smoothstep(0.012, 0.0, dist);

            mesh_col += col_i * alpha_i;
            total_alpha += alpha_i;
        }

        float a = clamp(total_alpha, 0.0, 1.0);
        finalColor = vec4(mesh_col * a, a);
    }
    // ID 17: RADIAL CONCENTRIC SQUARES
    else if (u_spec_type == 17) {
        vec2 grid = fract(st * vec2(45.0, 25.0)) - 0.5;
        vec2 id = floor(st * vec2(45.0, 25.0));
        float norm_x = id.x / 45.0;
        float h = get_bar_height(norm_x) * 0.90;
        float norm_y = id.y / 25.0;

        vec3 col = hsv2rgb(vec3(0.08 + norm_x * 0.65, 0.85, 1.0));
        float block_box = max(abs(grid.x), abs(grid.y));

        if (norm_y <= h && block_box < 0.4) {
            float glow_center = smoothstep(0.4, 0.0, block_box);
            vec3 final_rgb = col + vec3(0.3 * glow_center);
            finalColor = vec4(final_rgb, 1.0);
        }
    }
    // ID 18: MUSIC NOTES FLOATING SPECTRUM
    else if (u_spec_type == 18) {
        float h = get_bar_height(st.x) * 0.80;
        vec3 rainbow = hsv2rgb(vec3(st.x * 0.9, 0.85, 1.0));

        float line_gap = step(0.3, fract(st.x * 90.0));
        float line_a = (st.y <= h) ? 0.85 * line_gap : 0.0;

        vec2 note_grid = fract(st * vec2(14.0, 7.0) + vec2(u_time * 0.2, -u_time * 0.35)) - 0.5;
        float note_dist = drawNote(note_grid, 1.0);
        float note_a = (note_dist < 0.0 && st.y > (h * 0.2)) ? 1.0 : 0.0;

        float total_a = clamp(line_a + note_a, 0.0, 1.0);
        finalColor = vec4(rainbow * total_a, total_a);
    }
    // ID 19: NEON GLOW REFLECTION
    else if (u_spec_type == 19) {
        float baseline = 0.02;
        float h = get_bar_height(st.x) * 0.85;
        vec3 neon_purple = vec3(0.82, 0.25, 1.0);
        float gap_x = step(0.12, fract(st.x * float(u_num_bars)));

        float bar_a = 0.0;
        if (st.y >= baseline && st.y <= (baseline + h)) {
            bar_a = gap_x;
        }

        float cap_y = baseline + h + 0.015;
        float cap_a = (abs(st.y - cap_y) < 0.006) ? gap_x : 0.0;

        float glow_a = smoothstep(baseline + h + 0.12, baseline, st.y) * 0.35 * gap_x;
        float total_a = clamp(max(bar_a, cap_a) + glow_a, 0.0, 1.0);

        vec3 col = (cap_a > 0.0) ? vec3(0.4, 0.8, 1.0) : neon_purple;
        finalColor = vec4(col * total_a, total_a);
    }
    // ID 0 (default): BAR SEDERHANA MEMAKAI u_color PILIHAN USER
    else {
        float h = get_bar_height(st.x);
        float bar_gap = step(0.1, fract(st.x * float(u_num_bars)));
        if (st.y <= h) {
            finalColor = vec4(u_color * bar_gap, bar_gap);
        }
    }

    fragColor = finalColor;
}
