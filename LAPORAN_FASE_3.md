# Laporan Fase 3 — GLSL Engine

Sesuai section 0.1 (aturan besi): **hanya file BARU** dibuat di sesi ini, tidak ada file milik
Fase 1/2 yang dibuka/diedit. Struktur folder di ZIP ini sudah path-relatif ke root project
(`app/src/main/...`) — tinggal di-extract ke folder project yang sama menimpa/menambah di atas
hasil Fase 1 & 2 (section 0.3), tidak akan ada tabrakan path (lihat manifest Appendix A.2 baris
Fase 3).

## File yang dibuat

| Path | Isi |
|---|---|
| `render/gl/EglOffscreenContext.kt` | EGL Pbuffer offscreen context headless (ES 3.0) |
| `render/gl/ShaderProgramCache.kt` | Compile & cache program shader per sesi render |
| `render/gl/GlEsSpectrumRenderer.kt` | Render 1 frame shader → buffer RGBA (`glReadPixels`) |
| `render/gl/GlEsSpectrumRendererAdapter.kt` | Implementasi `CustomSpectrumRenderer` (A.4.4), orkestrasi decode audio → render tiap frame → mux WebM VP9 alpha |
| `render/audio/FftMath.kt` | FFT radix-2 murni Kotlin (dipakai bersama 2 file di bawah) |
| `render/audio/WavDecoder.kt` | Pembaca WAV multi-format (PCM 8/16/24/32-bit, float32) |
| `render/audio/AudioFftAnalyzer.kt` | 120 bar frekuensi log-scale + smoothing (isi `u_bars[120]`) |
| `render/audio/BeatEnergyExtractor.kt` | Faktor zoom dari energi bass (`useBeatZoom`) |
| `di/GlEsSpectrumRendererBindModule.kt` | Extension Point (A.5) — colok adapter ke multibind Fase 2, **file baru**, tidak mengedit `RenderWorker.kt` |
| `assets/shaders/spectrum_vertex.glsl` | Vertex shader bawaan, GLSL ES 3.0 |
| `assets/shaders/spectrum_default.glsl` | Fragment shader bawaan, 14 gaya visual (id 0 + 6–19), GLSL ES 3.0 |

## Asumsi terdokumentasi (WAJIB dibaca sebelum fase lanjutan menyambung ke sini)

1. **Sumber konversi shader bawaan**: dua file `.glsl` di atas dikonversi dari kode shader ASLI
   di `spectrum_generator.py` (`SPECTRUM_VERTEX_SHADER` & `SPECTRUM_FRAGMENT_SHADER`, `#version
   330`), BUKAN dari isi 3 file `TEMPLATE_PROMPT_*.md`. Ketiga file template tersebut adalah
   *prompt* acuan untuk AI generator eksternal (dipakai user lewat wizard Shader Studio Fase 5),
   bukan kode GLSL siap pakai — sudah dikonfirmasi lewat isi `TEMPLATE_PROMPT_SPECTRUM.md` yang
   isinya aturan+kerangka prompt, bukan implementasi visual. Shader bawaan (built-in gallery
   default) yang dibutuhkan RenderWorker supaya tidak crash saat `Set<CustomSpectrumRenderer>`
   dipanggil justru datang dari kode Python asli yang sudah ada di repo.
2. **Konversi 5 langkah section 6.0** diterapkan manual (bukan lewat `ShaderValidator` otomatis,
   itu baru dibangun Fase 5): `#version 300 es`, `precision highp float;` (deviasi sengaja dari
   literal "mediump" di section 6.0 — dijelaskan di komentar dalam file `.glsl`-nya sendiri),
   `out vec4 fragColor;` eksplisit, tidak ada fungsi non-ES, uniform sesuai tabel gabungan.
3. **Resolusi shader kustom user** (`SpectrumConfig.shaderRef` non-blank & bukan nama gaya
   bawaan): diasumsikan file `.glsl`-nya sudah ada di `filesDir/shaders/<shaderRef>.glsl` —
   `GlEsSpectrumRendererAdapter` TIDAK query Room `ShaderTemplateDao` langsung karena skema DAO
   itu belum dibekukan resmi di Appendix A (baru disebut naratif di section 3). Kalau Fase
   1/5 membekukan kontrak DAO tsb di revisi Appendix A berikutnya, cukup `resolveShaderSource()`
   di `GlEsSpectrumRendererAdapter.kt` yang perlu diganti (file milik Fase 3, aman direvisi).
4. **FFT murni Kotlin**, bukan native NDK C++. Section 9 blueprint hanya menyebut "pertimbangkan"
   (opsional) — baseline fungsional Fase 3 memakai radix-2 Cooley-Tukey Kotlin biasa di
   `FftMath.kt`. Kandidat optimasi native untuk Fase 6 (Polish) kalau ada bottleneck performa.
5. **Mux video alpha**: rawvideo RGBA ditulis ke file sementara di `cacheDir` lalu di-mux jadi
   WebM VP9 (`libvpx-vp9`, `yuva420p`) lewat `FFmpegKit.execute()` langsung di dalam
   `GlEsSpectrumRendererAdapter` (bukan lewat `FfmpegSessionRunner` milik Fase 2 — memanggil
   library FFmpegKit langsung tidak melanggar aturan besi karena tidak mengedit file Fase 2
   manapun, hanya memakai library yang sama).

## Self-check (semangat Appendix C, meski wajib formalnya baru untuk Fase 4/5)

- ✅ EGL offscreen context + release eksplisit (anti-leak, section 9).
- ✅ Fallback anti-crash: kompilasi shader kustom gagal → otomatis pakai shader bawaan
  (`spectrum_default.glsl`), bukan crash render.
- ✅ Extension Point Pattern diikuti persis: `GlEsSpectrumRendererBindModule.kt` adalah file
  baru, tidak menyentuh `RenderWorker.kt`/`CustomSpectrumRenderer.kt` Fase 2.
- ✅ Shader bawaan lolos 5 langkah konversi section 6.0 (dicek manual, dijelaskan di komentar
  file `.glsl`).
- ⚠️ FFT: pure-Kotlin, bukan NDK native (lihat asumsi #4 di atas — bukan pelanggaran spesifikasi,
  hanya deviasi dari saran opsional performa).
- ⚠️ Integrasi shader kustom user: bergantung pada asumsi lokasi file `filesDir/shaders/*.glsl`
  (lihat asumsi #3) karena kontrak DAO shader belum dibekukan Appendix A — perlu diverifikasi
  ulang saat Fase 5 (Shader Studio) benar-benar menulis file di lokasi yang sama.
