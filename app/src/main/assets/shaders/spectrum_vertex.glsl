#version 300 es
// spectrum_vertex.glsl — shader bawaan (built-in), Fase 3.
// Hasil konversi GLSL ES 3.0 dari SPECTRUM_VERTEX_SHADER (spectrum_generator.py, #version 330)
// mengikuti 5 langkah konversi section 6.0 blueprint:
//  1) header #version 330 -> #version 300 es (baris di atas)
//  2) precision default ditambahkan tepat setelah header versi (baris di bawah)
//  3) tidak ada gl_FragColor di sini (vertex shader tidak butuh fragColor)
//  4) tidak memakai fungsi non-ES apapun (in/out/uniform biasa, kompatibel penuh)
//  5) uniform dipakai persis sesuai nama & tipe di tabel gabungan section 6.0
precision highp float;

in vec2 in_vert;
out vec2 v_uv;

uniform float u_zoom_factor;
uniform float u_shear_x;
uniform float u_shear_y;

void main() {
    vec2 uv = (in_vert + 1.0) * 0.5;
    // FLIP KOORDINAT Y UNTUK MEMASTIKAN ST.Y = 0 TERLETAK DI DASAR BAWAH VIDEO
    vec2 flipped_uv = vec2(uv.x, 1.0 - uv.y);
    vec2 center = vec2(0.5, 0.5);
    vec2 p = center + (flipped_uv - center) / u_zoom_factor;

    // Transformasi Matrix Shear 3D/Tilt langsung pada koordinat UV
    vec2 centered = p - center;
    vec2 sheared;
    sheared.x = centered.x - u_shear_x * centered.y;
    sheared.y = centered.y - u_shear_y * centered.x;
    v_uv = center + sheared;

    gl_Position = vec4(in_vert.x, in_vert.y, 0.0, 1.0);
}
