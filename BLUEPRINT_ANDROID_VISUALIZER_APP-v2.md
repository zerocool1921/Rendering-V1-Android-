# BLUEPRINT PRODUK & TEKNIS — Aplikasi Android "Music Visualizer Studio"

> **v2 — revisi mekanisme lintas-fase.** Perubahan dari v1: (1) section 6 & 10.1 diperbaiki —
> 3 template shader studio (Timer 3D, Spectrum Custom, Overlay Generic Loop) SEMUA sudah
> tersedia, bukan 2; (2) section 0 (baru) & Appendix A (baru) ditambahkan supaya tiap Fase di
> roadmap section 8 bisa dikerjakan di **sesi Claude terpisah, akun terpisah, atau container
> terpisah** — HANYA dengan modal file blueprint ini saja, TANPA perlu upload ulang hasil
> `.zip` fase sebelumnya maupun source code `.py` asli. Baca section 0 dulu sebelum minta
> dikerjakan fase apapun.

> Dokumen ini adalah spesifikasi lengkap untuk membangun aplikasi Android native (Kotlin)
> dari nol. Aplikasi: generator video musik/spectrum visualizer otomatis dengan rendering
> GPU (FFmpeg + OpenGL ES/GLSL), lengkap dengan editor kanvas interaktif, antrian render,
> sistem preset, dan studio pembuat shader kustom. Semua spesifikasi di bawah ditulis
> sebagai target akhir aplikasi Android — bukan hasil porting, langsung dibangun native.

---

## 0. CARA PAKAI BLUEPRINT INI LINTAS SESI/AKUN/CONTAINER (WAJIB DIBACA DULU)

Masalah yang diselesaikan section ini: paket gratis/terbatas sering memaksa tiap Fase
dikerjakan di sesi baru (kadang akun baru, kadang container CI baru) yang **tidak** punya
riwayat percakapan maupun akses ke hasil `.zip` fase sebelumnya. Supaya tetap **nyambung,
tidak mismatch, dan tidak perlu edit file lama**, blueprint ini didesain jadi **satu-satunya
sumber kebenaran (single source of truth)** yang dibawa ke tiap sesi baru — bukan riwayat
chat, bukan `.zip`, bukan `.py` asli.

### 0.1 Tiga aturan besi (non-negotiable, berlaku di SEMUA fase)

1. **Satu fase = hanya file BARU.** Sesi yang mengerjakan Fase N **dilarang** membuka,
   mengedit, menambah baris, atau menimpa file yang sudah dimiliki Fase < N (lihat manifest
   kepemilikan file di Appendix A.2). Kalau Fase N butuh sesuatu dari Fase sebelumnya, ia
   **cukup mengasumsikan** file itu sudah ada persis seperti kontrak di Appendix A.3/A.4 —
   tidak perlu melihat isi file aslinya.
2. **Kontrak (model data, interface, nama package, nama file) dipatok BEKU di Appendix A.**
   Sesi manapun yang mengerjakan fase manapun WAJIB memproduksi ulang file-file kontrak
   tersebut **persis seperti yang tertulis di Appendix A** (nama, path, isi) — supaya walau
   dikerjakan di akun/container berbeda dan digabung belakangan lewat copy-paste folder biasa,
   hasilnya otomatis kompatibel tanpa tabrakan.
3. **Titik sambung antar-fase HANYA lewat Extension Point Pattern (Appendix A.5)** — pola
   Dagger Multibindings supaya Fase lanjutan (mis. Fase 3 nyambung ke hook di Fase 2) bisa
   "colok" implementasi baru dengan **menambah file baru**, tanpa pernah menyentuh file Fase
   sebelumnya. Jangan improvisasi pola sambung lain (mis. edit langsung ke worker/Activity
   fase sebelumnya) — itu yang selama ini bikin hasil antar-sesi gampang mismatch.

### 0.2 Prompt pembuka standar untuk sesi baru (tinggal copy-paste)

Setiap kali mulai sesi/akun/container baru untuk mengerjakan satu fase, upload **HANYA file
blueprint ini** (tidak perlu `.zip` fase lain, tidak perlu `.py` asli), lalu pakai prompt:

> Saya lampirkan blueprint `BLUEPRINT_ANDROID_VISUALIZER_APP-v2.md`. Tolong kerjakan
> **Fase [N]** saja, sesuai cakupan di section 8 (Roadmap) dan manifest kepemilikan file di
> Appendix A.2 untuk Fase [N]. Ikuti 3 aturan besi di section 0.1: buat file BARU saja
> (jangan edit/tambah/timpa file fase lain), reproduksi persis kontrak Appendix A.3/A.4 yang
> dibutuhkan fase ini, dan kalau perlu titik sambung ke fase lain, pakai Extension Point
> Pattern di Appendix A.5. Saya TIDAK melampirkan hasil fase sebelumnya maupun source `.py`
> asli — anggap semua yang dibutuhkan sudah tersedia persis seperti kontrak di Appendix A.

### 0.3 Cara menggabungkan hasil semua fase

Karena tiap fase hanya menambah file baru (tidak pernah mengedit file lain), hasil tiap
sesi tinggal di-**extract ke satu folder project yang sama** secara berurutan (Fase 1 dulu,
lalu Fase 2 ditimpakan di folder yang sama, dst) — tidak akan ada file yang saling menimpa
isi berbeda, karena satu path file cuma "dimiliki" oleh satu fase (lihat Appendix A.2). Kalau
suatu saat proses extract melaporkan file dengan path sama dari 2 fase berbeda, itu tandanya
ada pelanggaran manifest — laporkan balik ke AI di fase yang salah, jangan ditimpa manual.

---

## 1. RINGKASAN PRODUK

Aplikasi mobile untuk membuat video visualizer musik secara otomatis: user memasukkan
audio + media visual (video/gambar), memilih gaya spectrum visualizer & teks overlay,
lalu aplikasi merender video final dengan akselerasi hardware di perangkat.

**Pilar fitur utama:**
1. Pembuatan tugas render lewat wizard multi-langkah
2. Mesin render berbasis FFmpeg (filter graph) + OpenGL ES (shader GLSL kustom)
3. Editor kanvas visual (drag, resize, rotate, warp, keyframe animasi)
4. Studio pembuat shader kustom (3 template siap pakai)
5. Antrian render batch & sistem preset
6. Deteksi & pemilihan hardware encoder otomatis/manual

---

## 2. TECH STACK

| Lapisan | Teknologi |
|---|---|
| Bahasa | Kotlin 100% (Coroutines + Flow) |
| UI | Jetpack Compose (Material 3) + Navigation-Compose |
| Arsitektur | MVVM + Clean Architecture (data / domain / presentation) |
| Database lokal | Room |
| Preferensi | DataStore |
| Background job | WorkManager + Foreground Service |
| Rendering & muxing | FFmpeg (`ffmpegkit-maintained`, lihat section 10 — bukan build native manual) |
| Rendering shader | OpenGL ES 3.0 — `GLSurfaceView` (preview) + EGL Pbuffer offscreen context (render headless per-frame) |
| Analisis audio (FFT/beat) | Implementasi FFT Kotlin/NDK (native C++ untuk performa real-time) |
| Deteksi hardware encoder | `MediaCodecList` + `MediaCodecInfo` |
| Pemilihan file | Storage Access Framework / Photo Picker API |
| Player preview | Media3 (ExoPlayer) |
| Dependency Injection | Hilt |

---

## 3. ARSITEKTUR APLIKASI

**Package root beku (WAJIB dipakai persis, lihat Appendix A.1):** `com.visualizerstudio.app`

```
app/
 ├─ data/
 │   ├─ local/          → Room DAO: RenderTaskDao, PresetDao, ShaderTemplateDao
 │   ├─ datastore/       → HardwarePrefs, AppSettings
 │   └─ repository/      → implementasi repository dari domain
 ├─ domain/
 │   ├─ model/           → RenderTask, SpectrumConfig, KeyframeSet, ShaderTemplate, Preset
 │   └─ usecase/         → BuildFilterGraphUseCase, RenderTaskUseCase, GenerateShaderUseCase,
 │                          AnalyzeAudioBeatUseCase, DetectHardwareEncoderUseCase
 ├─ render/
 │   ├─ ffmpeg/          → FfmpegFilterGraphBuilder, FfmpegSessionRunner, FfmpegProgressParser
 │   ├─ gl/              → EglOffscreenContext, ShaderProgramCache, GlEsSpectrumRenderer,
 │   │                      ShaderValidator
 │   └─ audio/           → AudioFftAnalyzer, BeatEnergyExtractor, WavDecoder
 ├─ worker/              → RenderWorker (CoroutineWorker + ForegroundService + notifikasi progres)
 ├─ ui/
 │   ├─ home/            → Dashboard status hardware & ringkasan antrian
 │   ├─ wizard/          → Layar step-by-step pembuatan tugas
 │   ├─ queue/           → Antrian render (list, reorder, hapus, status)
 │   ├─ preset/          → Manajemen preset visual
 │   ├─ canvaseditor/    → Canvas + KeyframeTimeline
 │   ├─ shaderstudio/    → 3 wizard template shader + galeri shader
 │   ├─ settings/        → Pengaturan hardware/encoder
 │   └─ components/      → Komponen Compose bersama
 └─ shaders/             → assets: shader bawaan (.glsl GLSL ES) + skeleton 3 template
```

> Struktur folder ini beku sejak Fase 1. Fase manapun yang butuh menambah file baru WAJIB
> menaruhnya di sub-folder yang sudah ada di atas (bukan bikin struktur folder alternatif),
> supaya hasil gabungan semua fase tetap satu arsitektur yang konsisten.

---

## 4. DATA MODEL (Room Entities)

> **Kontrak beku.** Definisi lengkap & final field-per-field ada di Appendix A.3 (file Kotlin
> siap-pakai). Tabel di bawah ini ringkasan konseptual saja — kalau ada isi yang beda antara
> tabel ini dengan Appendix A.3, **Appendix A.3 yang berlaku**, karena itu yang jadi sumber
> kode sungguhan.

```kotlin
@Entity
data class RenderTaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mediaFiles: List<Uri>,
    val audioFiles: List<Uri>,
    val mediaMode: MediaMode,              // EQUAL_SPLIT, PER_TRACK
    val resolution: Resolution,            // R720P, R1080P, R2K, R4K
    val fps: Int,
    val loops: Int,
    val spectrums: List<SpectrumConfig>,   // mendukung multi-spectrum sekaligus
    val bgVideoOverlays: List<OverlayAssetConfig>,
    val introConfig: TextOverlayConfig?,
    val titleConfig: TextOverlayConfig?,
    val slowMotionConfig: SlowMotionConfig?,
    val reversePlayConfig: ReversePlayConfig?,
    val overlaySpeedConfig: OverlaySpeedConfig?,   // efek kecepatan overlay berdenyut beat
    val cropConfig: CropConfig?,                    // crop kiri/kanan/atas/bawah (lihat 5.1e)
    val bgVisualEffect: BgVisualEffect,             // NONE, VIGNETTE, BLUR, GRAYSCALE, SEPIA
    val keyframes: KeyframeSet,
    val outputFolder: Uri,
    val status: TaskStatus                 // QUEUED, RENDERING, DONE, FAILED
)

data class SpectrumConfig(
    val shaderRef: String,        // referensi ke ShaderTemplateEntity atau shader bawaan
    val width: Int, val height: Int,
    val posX: String, val posY: String,     // ekspresi posisi, bisa hasil drag Canvas Editor
    val color: String,
    val opacity: Float,
    val bgColor: String?, val bgOpacity: Float,
    val useBeatZoom: Boolean,       // zoom seluruh frame mengikuti bass
    val shearX: Float, val shearY: Float
)

data class OverlayAssetConfig(
    val fileUri: Uri, val opacity: Float, val fadeInSeconds: Float,
    val loop: Boolean, val delaySeconds: Float, val smoothTransition: Boolean,
    val shearX: Float, val shearY: Float,
    val autoLumaKey: Boolean = true    // area gelap otomatis transparan
)

data class OverlaySpeedConfig(
    val active: Boolean, val targetOverlayIndices: List<Int>, val speedMode: String // "auto_beat"
)

data class CropConfig(val left: Int = 0, val right: Int = 0, val top: Int = 0, val bottom: Int = 0)

data class TextOverlayConfig(
    val lines: List<TextLine>,     // maks 3 baris, ukuran berbeda per baris
    val delaySeconds: Float,
    val displayDurationSeconds: Float,
    val fontFamily: String, val bold: Boolean, val italic: Boolean,
    val fontColor: String, val opacity: Float,
    val positionPreset: TextPosition,   // 9 preset posisi, lihat 5.1c
    val animationStyle: TextAnimation,  // FADE_IN_OUT, SLIDE_UP, SLIDE_LEFT, ZOOM_IN
    val animationDurationSeconds: Float,
    val useStroke: Boolean, val strokeColor: String, val strokeWidthPx: Int
)

data class KeyframeSet(
    val bg: List<Keyframe>, val spectrum: List<Keyframe>,
    val intro: List<Keyframe>, val title: List<Keyframe>,
    val overlays: Map<String, List<Keyframe>>
)
data class Keyframe(
    val timeSec: Float, val x: Float, val y: Float,
    val scaleX: Float, val scaleY: Float, val rotation: Float,
    val opacity: Float, val easing: EasingType
)

@Entity
data class PresetEntity(
    @PrimaryKey val id: String, val name: String,
    val fontSettings: TextOverlayConfig, val keyframesTemplate: KeyframeSet
)

@Entity
data class ShaderTemplateEntity(
    @PrimaryKey val id: String, val name: String,
    val category: ShaderCategory,   // TIMER_3D, SPECTRUM, OVERLAY_LOOP, BUILTIN
    val glslCode: String, val thumbnailUri: Uri?, val paramsJson: String
)
```

---

## 5. FITUR LENGKAP & FUNGSI

### 5.1 Wizard Pembuatan Tugas — 22 Langkah Konfigurasi Lengkap

Wizard memiliki **22 langkah konfigurasi** (state machine berurutan, bisa maju/mundur/lompat
lewat opsi skip di tiap langkah opsional). Berikut daftar lengkapnya:

| # | Langkah | Detail |
|---|---|---|
| 1 | Nama Tugas | Nama unik untuk tugas render |
| 2 | Pilih File Media (visual) | Multi-select video/gambar via Photo Picker/SAF |
| 3 | Pilih File Audio | Multi-select audio, digabung otomatis jadi satu track |
| 4 | Pilih Folder Output | Lokasi penyimpanan hasil render |
| 5 | Mode Distribusi Media | **Equal Split** (dibagi rata sepanjang durasi audio) / **Per Track** |
| 6 | Resolusi Video | 720p / 1080p / 2K / 4K |
| 7 | Frame Rate (FPS) | 24 / 30 / 60 |
| 8 | Efek Overlay Video/Gambar | Asset tambahan multi-efek: posisi, opacity, fade-in (bisa dilewati) |
| 9 | Jenis Spectrum & Mode Konfigurasi | Pilih **Wizard Standar** (1 spectrum) atau **Manajer Multi-Spectrum** (tambah/duplikat/edit banyak spectrum sekaligus), pilih shader dari galeri |
| 10 | Opasitas Spectrum | 0.1 (pudar) – 1.0 (jelas) |
| 11 | Lebar Spectrum | dalam pixel |
| 12 | Tinggi Spectrum | dalam pixel |
| 13 | Posisi Spectrum | Geser X/Y (default lewat menu pilihan, presisi lewat Canvas Editor) |
| 14 | Efek Visual Latar Belakang | Pilihan style background |
| 15 | Warna Garis Spectrum | Color picker |
| 16 | Warna Background Box Spectrum | Bisa dimatikan (None) |
| 17 | Opasitas Background Box | Kondisional — hanya muncul bila box aktif, 0.1–1.0 |
| 18 | Looping Video Hasil Akhir | Jumlah pengulangan output |
| 19 | Overlay Identitas Channel (Intro) | Aktif/Nonaktif → jika aktif: teks 1-3 baris ukuran berbeda, gaya font, delay tampil |
| 20 | Overlay Judul Lagu (Title) | Aktif/Nonaktif → otomatis dari nama file audio (parsing underscore) atau manual, delay setelah intro |
| 21 | Slow Motion (Gerak Lambat GPU) | Aktif/Nonaktif → per grup visual atau per file, kecepatan custom |
| 22 | Reverse Play (Putar Terbalik) | Aktif/Nonaktif untuk video utama |

Setelah langkah 22 → opsi simpan sebagai **Preset baru** (opsional), lalu tugas masuk ke Antrian Render.

**Rekomendasi UX Android** (agar 22 langkah tidak terasa melelahkan seperti versi CLI):
- Kelompokkan jadi **5 section tab/accordion** dalam satu alur Stepper, bukan 22 layar linear terpisah:
  1. **Dasar** (langkah 1-7: nama, media, audio, output, mode distribusi, resolusi, fps)
  2. **Overlay & Asset** (langkah 8: efek overlay video/gambar)
  3. **Spectrum** (langkah 9-17: jenis, opasitas, ukuran, posisi, warna, background box) — dengan live preview `GLSurfaceView` di sisi form
  4. **Teks & Identitas** (langkah 18-20: loop, intro, title)
  5. **Gerak Lanjutan** (langkah 21-22: slow motion, reverse play)
- Langkah opsional (8, 17, 19-22) ditampilkan sebagai toggle switch di awal section — form detail baru muncul (expand) kalau diaktifkan, bukan dialog blocking terpisah.
- Semua input tervalidasi instan (bukan tunggu submit), dengan tombol "Lewati" hanya pada langkah yang memang opsional di logika aslinya.

### 5.1b Detail Fitur yang Tersembunyi di Balik Langkah 8 & 9 (penting, sering terlewat)

**Langkah 8 — Multi-Overlay Asset (bisa lebih dari 1 overlay sekaligus):**
- Overlay pertama = utama (opacity, fade-in, loop on/off).
- Overlay ke-2, ke-3, dst muncul **setelah overlay sebelumnya dengan transisi masuk halus
  (smooth transition)** yang durasinya bisa diatur per-overlay (default 2 detik).
- Tiap overlay independen: file (video/gambar), opacity, fade-in, loop, delay, shear X/Y.
- **Auto luma-key**: bagian gelap/hitam pada video overlay otomatis dijadikan transparan
  (threshold luminance + scaling opacity), sehingga aset "efek di atas background hitam"
  otomatis menyatu tanpa background hitam ikut ter-render — wajib direplikasi di Android
  (mis. via shader compositing/`PorterDuff` custom, bukan cuma alpha overlay biasa).
- Asset overlay dipilih dari **folder assets terkurasi** (bukan file picker bebas) — di
  Android sebaiknya jadi **Asset Library** bawaan + opsi impor sendiri.

**Langkah 9 — Spectrum, ternyata 2 tahap + fitur beat tambahan:**
- Tahap 1: aktifkan Spectrum Standar/Custom (bisa dimatikan total → tanpa spectrum).
- Tahap 2 — **Spectrum Overlay Beat Effect** (2 sub-fitur independen, sering terlewat):
  1. **Beat Zoom** — seluruh frame video melakukan zoom in/out halus mengikuti energi bass
     lagu (`zoompan` beat-synced), efeknya di keseluruhan canvas, bukan cuma di spectrum.
  2. **Overlay Speed Beat Sync** — kecepatan playback salah satu/beberapa overlay asset
     (dipilih index-nya) berdenyut mengikuti beat secara otomatis (`speed_mode: auto_beat`),
     bukan kecepatan konstan.
- **Manajer Multi-Spectrum**: mode alternatif tahap 1 di atas — CRUD penuh (Tambah,
  Duplikat dengan auto-offset posisi Y biar tidak numpuk, Edit, Hapus) untuk banyak
  spectrum sekaligus dalam satu tugas, masing-masing dengan konfigurasi independen.

### 5.1c Sistem Font & Teks Overlay (Intro/Title) — Detail Lengkap
- 10 pilihan font family (Arial, Courier New, Georgia, Impact, Times New Roman, Verdana,
  Comic Sans MS, Segoe UI, Century Gothic, Garamond) + toggle Bold & Italic. **Catatan
  legal (lihat juga Appendix A.6):** file `.ttf` asli bersumber dari `C:/Windows/Fonts`
  (berlisensi Microsoft) — TIDAK boleh dibundel langsung ke APK. Fase yang mem-bundling
  font wajib pakai font pengganti bebas lisensi (mis. Liberation Sans/Serif, Carlito,
  Caladea) dengan mapping nama family yang sama persis seperti daftar di atas.
- 11 warna preset (red, yellow, green, blue, gold, black, gray, turquoise, orange, pink,
  white) + input hex custom — dipakai konsisten di font, garis spectrum, background box,
  dan stroke.
- Opacity teks terpisah dari opacity elemen lain.
- **9 preset posisi teks siap pakai**: Tengah-Tengah, Tengah-Bawah, Tengah-Atas, Kiri-Atas,
  Kanan-Atas, Kiri-Tengah, Kanan-Tengah, Bawah-Kiri, Bawah-Kanan (selain drag manual di
  Canvas Editor).
- **4 gaya animasi teks**: Fade In-Out, Slide Up, Slide Left, Zoom In — dengan durasi
  transisi animasi & durasi total tampil yang diatur terpisah.
- **Stroke/outline opsional**: warna & ketebalan (px) terpisah dari warna isi teks.
- Berlaku sama untuk Overlay Intro (identitas channel) maupun Overlay Title (judul lagu).

### 5.1d Preset Posisi Spectrum & Sistem Warna
- 6 preset posisi spectrum siap pakai: Tengah-Bawah, Tengah-Tengah (disarankan untuk tipe
  circular), Tengah-Atas, Kiri-Bawah, Kanan-Bawah, atau **Koordinat Manual** (ekspresi bebas,
  mis. `(W-w)/2`, `H-h-150`) — selain drag langsung di Canvas Editor.
- Palet warna 11 preset yang sama dipakai di seluruh aplikasi (lihat 5.1c) untuk konsistensi UI.

### 5.1e Crop Elemen Latar Belakang (field tersedia di mesin render, TAPI TIDAK PERNAH
diekspos di UI manapun pada versi sumber — crop kiri/kanan/atas/bawah hanya dibaca dari data
task dengan default 0, tanpa wizard atau kontrol Canvas Editor). Di Android, ini sebaiknya
**diperbaiki dan benar-benar diekspos** sebagai crop handle interaktif di Canvas Editor,
bukan dibiarkan jadi fitur mati seperti di sumber.

### 5.1f Strategi Bitrate & Encoding Audio (Standar Kualitas YouTube Live)
- Bitrate video **dihitung otomatis** mengikuti panduan resmi YouTube Live berdasarkan
  resolusi & FPS (mis. 4K ≥30fps → 25 Mbps, 4K >30fps → 35 Mbps, 1080p → 6-8.5 Mbps, dst),
  bukan nilai tetap — GOP = 2× FPS, buffer size = 2× bitrate.
- Audio selalu di-encode AAC 128 kbps, 44.1 kHz, stereo — tetap dipertahankan sebagai
  default standar di Android (`MediaCodec` AAC encoder).

### 5.1g Tools Preview Cepat (terpisah dari render penuh — 3 fitur berbeda, bukan 1)
1. **Preview Tugas** — render nyata 30 detik pertama saja (bukan simulasi), untuk cek hasil
   akhir gabungan semua elemen dengan cepat.
2. **Preview Font Overlay** — render sampel singkat khusus untuk uji gaya/animasi teks saja.
3. **Preview Spectrum Visualizer** — render sampel singkat khusus shader spectrum saja
   (dengan `GLSurfaceView`, tanpa perlu render FFmpeg penuh).

### 5.2 Mesin Render (pipeline lengkap dijalankan di `RenderWorker`)
1. Gabungkan semua audio jadi satu track (concat).
2. Hitung total durasi (`MediaMetadataRetriever`), terapkan jumlah loop.
3. Untuk tiap `SpectrumConfig` yang memakai shader kustom (bukan filter FFmpeg native) →
   render lewat `GlEsSpectrumRenderer`: EGL Pbuffer offscreen sesuai ukuran spectrum,
   compile shader (fallback ke shader default bila gagal compile — anti-crash), loop
   per-frame: `AudioFftAnalyzer` hitung 40-120 bar frekuensi log-scale + smoothing
   attack/release, set semua uniform, `glReadPixels`, tulis ke video alpha (WebM VP9
   alpha channel atau layer compositing langsung). **Titik sambung ke RenderWorker Fase 2
   WAJIB lewat Extension Point Pattern, lihat Appendix A.5 — bukan edit RenderWorker.kt.**
4. Susun timeline media visual sesuai `mediaMode` (scale, setsar, concat).
5. `FfmpegFilterGraphBuilder` menyusun filter graph: overlay spectrum (native filter
   `showfreqs`/`showwaves` ATAU video alpha shader kustom), `drawtext` untuk intro/title
   dengan timing & multi-baris, overlay asset tambahan dengan posisi/opacity/fade.
6. Encode final memakai encoder hardware terdeteksi (`h264_mediacodec`) atau fallback
   software (`libx264`).
7. Progres real-time dikirim ke notifikasi foreground service (persentase, tombol batal).

### 5.3 Deteksi & Pengaturan Hardware
- `DetectHardwareEncoderUseCase` memindai `MediaCodecList` untuk encoder H.264/HEVC
  hardware yang tersedia di chipset perangkat (Snapdragon/Exynos/MediaTek/dll).
- Mode: **Auto** (default, pilih encoder hardware terbaik), **Manual** (user kunci ke
  encoder tertentu), **CPU/Software** (mode darurat anti-crash, `libx264`).
- Layar Pengaturan menampilkan info encoder aktif, kompatibilitas resolusi/fps.

### 5.4 Antrian Render (Queue)
- List tugas tersimpan di Room, status per tugas (Queued/Rendering/Done/Failed).
- Reorder via drag, hapus via swipe, render satu-per-satu berurutan di background.
- Retry otomatis/manual untuk tugas gagal, log error detail per tugas.

### 5.5 Sistem Preset
- Simpan kombinasi gaya font, posisi, keyframe animasi sebagai preset bernama.
- Galeri preset dengan thumbnail visual (preview render mini), bisa langsung diterapkan
  ke tugas baru dari wizard.

### 5.6 Canvas Editor (editor visual interaktif)
- `Canvas` Compose full-gesture: drag posisi, resize (lebar/tinggi presisi), shear X/Y
  (warp/tilt), dipilih dari dropdown "Spectrum aktif" bila ada beberapa spectrum sekaligus.
- Preview background diambil dari frame nyata media (thumbnail video/gambar), elemen
  spectrum digambar sebagai kotak gizmo yang bisa diseleksi & dimanipulasi satu per satu.
- Panel properti sinkron dua arah dengan gizmo: geser di kanvas mengubah angka di panel,
  ubah angka di panel juga menggeser gizmo di kanvas (live sync).
- Tombol **Render Preview** (FFmpeg nyata, bukan simulasi) untuk memvalidasi hasil akhir
  tanpa menutup editor, dan tombol **Simpan Posisi** terpisah agar bisa uji coba dulu.
- **Perluasan untuk versi Android** (peningkatan dibanding sumber): tambahkan drag untuk
  elemen teks (intro/title) dan overlay asset juga (di sumber PyQt hanya spectrum yang
  punya gizmo drag; teks/overlay hanya lewat pilihan posisi preset), tambahkan crop handle
  (lihat 5.1e), timeline keyframe visual di bagian bawah, dan snap-to-grid/snap-to-safe-area.

---

## 6. SHADER TEMPLATE STUDIO (3 Template — SEMUA SUDAH TERSEDIA)

> **Perbaikan dari v1**: dokumen v1 sempat mencatat baru ada 2 dari 3 file template prompt
> (Timer 3D & Overlay Generic Loop, dengan catatan "Spectrum Custom belum ada"). Setelah
> dicek ulang, **`TEMPLATE_PROMPT_SPECTRUM.md` ternyata sudah ada** di antara file yang
> diupload sejak awal — jadi ketiga wizard di section 6.1-6.3 di bawah **semuanya punya
> acuan template lengkap**, tidak ada yang hilang. Catatan v1 soal "belum ada" dinyatakan
> **tidak berlaku lagi**.

Studio berisi galeri shader bawaan + 3 wizard pembuat shader kustom, sehingga user tidak
perlu menulis kode GLSL manual.

### 6.0 Kontrak Uniform Gabungan (SEMUA 3 template, dasar dari `ShaderValidator`)

Ketiga file template prompt (`TEMPLATE_PROMPT_TIMER_3D.md`, `TEMPLATE_PROMPT_SPECTRUM.md`,
`TEMPLATE_PROMPT_OVERLAY_GENERIC_LOOP.md`) ditulis untuk AI generator eksternal dalam
`#version 330` (GLSL desktop) — **bukan** GLSL ES 3.0 yang dipakai `GLSurfaceView`/EGL di
Android. `ShaderValidator` (Fase 5) WAJIB menjalankan langkah konversi berikut ke SETIAP
shader hasil generate sebelum disimpan sebagai asset:

1. Ganti header `#version 330` → `#version 300 es`.
2. Tambahkan `precision mediump float;` tepat setelah header versi.
3. Pastikan deklarasi `out vec4 fragColor;` ada (GLSL ES 3.0 tidak auto-declare `gl_FragColor`).
4. Cek tidak ada fungsi yang tidak tersedia di ES (mis. sebagian varian `textureGrad`,
   sebagian ekstensi desktop-only) — kalau ada, gagal validasi & shader ditolak (bukan
   crash saat render).
5. Uniform WAJIB dideklarasikan persis dengan nama & tipe di tabel gabungan berikut —
   ini superset dari kebutuhan ketiga template, jadi satu file GLSL ES hasil konversi bisa
   mendeklarasikan uniform yang tidak dipakainya (dibiarkan unused, tidak masalah), TAPI
   TIDAK BOLEH memakai nama uniform lain di luar tabel ini:

| Uniform | Tipe | Isi | Dipakai wajib oleh |
|---|---|---|---|
| `v_uv` | vec2 | koordinat pixel 0.0–1.0 | Timer 3D, Spectrum, Overlay Loop |
| `u_resolution` | vec2 | lebar & tinggi output | Timer 3D, Spectrum, Overlay Loop |
| `u_time` | float | waktu berjalan (detik) | Timer 3D, Spectrum, Overlay Loop |
| `u_bars[120]` | float[120] | energi 120 bar frekuensi musik real-time | Spectrum (wajib), Timer 3D & Overlay Loop (opsional, buat pulse/glow beat) |
| `u_num_bars` | int | jumlah bar aktif (selalu 120) | sama seperti `u_bars` |
| `u_color` | vec3 | warna pilihan user | Timer 3D, Spectrum, Overlay Loop |
| `u_zoom_factor` | float | efek zoom saat beat | opsional, ketiganya |
| `u_shear_x` / `u_shear_y` | float | warp/tilt 3D | opsional, ketiganya |
| `u_total_duration` | float | total durasi audio (dari `MediaMetadataRetriever`), diisi otomatis SETELAH compile shader, SEBELUM render frame pertama | **HANYA Timer 3D** — nama harus persis, lihat Aturan #8 di `TEMPLATE_PROMPT_TIMER_3D.md` |
| `u_phase_count` | int | jumlah fase teks intro timer (maks 8) | Timer 3D (fitur opsional teks intro) |
| `u_phase_duration[8]` | float[8] | durasi tiap fase teks (dihitung otomatis aplikasi, BUKAN input manual) | Timer 3D (fitur opsional teks intro) |
| `u_phase_line1_chars[192]` / `u_phase_line2_chars[192]` | int[8×24] | kode ASCII tiap fase, flattened | Timer 3D (fitur opsional teks intro) |
| `u_phase_line1_len[8]` / `u_phase_line2_len[8]` | int[8] | jumlah karakter valid per baris per fase | Timer 3D (fitur opsional teks intro) |
| `u_loop_duration` | float | durasi satu putaran loop (detik), default 120.0 | **HANYA Overlay Loop** — dasar `mod(u_time, u_loop_duration)` |
| `u_effect_mode` | int | 0 = mode `overlay` (arah turun tetap), 1 = mode `effect` (arah alami efek) | **HANYA Overlay Loop**, lihat Aturan Arah Gerakan di `TEMPLATE_PROMPT_OVERLAY_GENERIC_LOOP.md` |

Aturan wajib tiap shader yang dihasilkan/divalidasi oleh `ShaderValidator` (berlaku ke
ketiganya, sudah konsisten di 3 file template):
- Area di luar frame (`v_uv` di luar 0.0–1.0) wajib transparan penuh (`fragColor = vec4(0.0)`).
- Semua bentuk visual wajib prosedural (SDF/noise/hash) — tidak boleh sampling tekstur
  eksternal, karena tidak ada aset gambar yang di-load saat render.
- Tidak boleh ada dependensi durasi hardcode; nilai waktu total (khusus Timer) wajib dari
  uniform `u_total_duration`, bukan angka tertulis di kode; nilai durasi loop (khusus
  Overlay) wajib dari `u_loop_duration`, bukan `#define` hardcode.

### 6.1 Wizard "Timer Countdown 3D" — acuan: `TEMPLATE_PROMPT_TIMER_3D.md`
- Input: gaya angka (referensi gambar opsional), warna, hingga 8 fase teks intro bergantian
  sebelum angka countdown muncul (isi teks saja — durasi tiap fase dihitung otomatis
  proporsional terhadap total durasi audio, lihat rumus `1.5 + total_char*0.06` di file
  template untuk fase teks kalimat panjang, dan 1.0 detik tetap untuk fase angka/kata pendek).
- Format waktu adaptif otomatis: `HH:MM:SS` jika sisa waktu ≥ 1 jam, `MM:SS` jika di bawah —
  KEDUA cabang wajib ada di kode hasil generate, tidak boleh cuma salah satu.
- Titik dua berkedip halus (`sin(u_time * π)`), angka ditampilkan dengan efek timbul 3D
  (lighting diffuse + specular + rim light dari normal vector SDF via `dFdx`/`dFdy`).
- Font angka & huruf fase intro (kalau dipakai) WAJIB satu keluarga visual — SDF prosedural
  16-segment untuk huruf, sama gaya shading-nya dengan digit angka.

### 6.2 Wizard "Spectrum Custom" — acuan: `TEMPLATE_PROMPT_SPECTRUM.md`
- Input: gaya dasar (bar/wave/circular/particle), warna, jumlah bar, sensitivitas beat-zoom,
  gaya glow/reflection opsional.
- Struktur file wajib satu `void main()`, helper `get_bar_height(norm_x)` untuk interpolasi
  halus antar-bar dari array `u_bars[120]` (lihat kerangka wajib di file template).
- Preview langsung (`GLSurfaceView`) dengan data audio sample sebelum disimpan.

### 6.3 Wizard "Overlay Effect Loop" — acuan: `TEMPLATE_PROMPT_OVERLAY_GENERIC_LOOP.md`
- Input: deskripsi efek bebas (hujan, salju, kunang-kunang, api, debu cahaya, dll),
  mode arah gerak — **Overlay** (arah turun tetap, cocok untuk hujan/salju/debu jatuh) atau
  **Effect** (arah alami sesuai sifat efek — naik untuk api, melayang bebas untuk
  kunang-kunang, menyebar radial untuk ledakan/partikel) — dipetakan ke uniform
  `u_effect_mode` (lihat tabel 6.0).
- Durasi loop (default 120 detik, uniform `u_loop_duration`), kepadatan partikel, warna.
- Shader dijamin **seamless loop** — `mod(u_time, u_loop_duration)` sehingga video musik
  berdurasi berapa pun tetap mulus tanpa lompatan visual di titik sambungan. `u_time` sendiri
  berjalan terus otomatis sepanjang encode tanpa perlu tahu total durasi lagu di awal — beda
  dengan Timer 3D yang butuh `u_total_duration` karena menghitung mundur ke satu titik akhir.

### 6.4 Mode Pembuatan Shader
- **Mode Template** (default, tanpa AI): tiap wizard memakai skeleton GLSL ES siap pakai
  (hasil konversi dari 3 file `TEMPLATE_PROMPT_*.md` sesuai langkah 6.0), parameter user
  langsung disubstitusi ke kode — instan, tanpa perlu koneksi internet.
- **Mode Generate via AI** (opsional, terhubung ke API model bahasa): prompt otomatis
  dibangun dari input wizard + isi lengkap file `TEMPLATE_PROMPT_*.md` yang relevan sebagai
  system/context prompt (sama seperti cara pakai manual di bagian "PROMPT PAKAI" tiap file
  template), hasil kode divalidasi otomatis oleh `ShaderValidator` (langkah 6.0) sebelum
  disimpan sebagai `ShaderTemplateEntity` baru.
- **Mode Advanced/Impor**: user power-user bisa tempel/impor file `.glsl` sendiri,
  tetap divalidasi lewat `ShaderValidator` sebelum masuk galeri.

Semua shader (bawaan + hasil studio + impor) tampil di **galeri shader** dengan thumbnail
auto-render, dipilih langsung dari Wizard Buat Tugas (langkah 6) atau Canvas Editor.

---

## 7. DAFTAR LAYAR (SCREENS)

| Layar | Fungsi |
|---|---|
| Dashboard/Home | Status hardware encoder, ringkasan antrian, tombol "Buat Video Baru" |
| Wizard Buat Tugas | 12 langkah sesuai bagian 5.1, dengan progres visual & validasi instan |
| Antrian Render | List card, reorder, swipe-delete, progress bar, status chip |
| Preset Manager | Grid preview visual, duplikat/edit/hapus |
| Canvas Editor | Full-gesture editor + timeline keyframe |
| Shader Template Studio | Galeri shader + 3 wizard pembuat template |
| Preview Player | Pemutaran hasil render/preview 30 detik in-app (Media3) |
| Pengaturan Hardware | Auto/Manual/CPU encoder, info chipset terdeteksi |

---

## 8. ROADMAP PENGEMBANGAN

> **Cara baca tabel ini lintas sesi:** kolom "File dimiliki" menunjuk ke Appendix A.2 —
> WAJIB dicek sebelum mulai fase manapun, supaya tahu persis file mana yang boleh dibuat
> (baru) di fase ini dan file mana milik fase lain yang cukup diasumsikan ada (lewat kontrak
> Appendix A.3/A.4), tanpa perlu upload ulang `.zip` fase itu maupun source `.py` asli
> (lihat section 0).

| Fase | Cakupan | Manifest file |
|---|---|---|
| **Fase 1 — Fondasi** | Setup proyek, Room DB, DataStore, integrasi FFmpeg NDK dasar (transcode sederhana berhasil jalan), **plus semua file kontrak beku Appendix A.3/A.4** (domain model, interface lintas-fase, modul multibinding kosong) | Appendix A.2 baris Fase 1 |
| **Fase 2 — Render Pipeline Inti** | `FfmpegFilterGraphBuilder` (drawtext, overlay, filter native), `RenderWorker` + notifikasi progres, timeline media, hook Extension Point kosong (Appendix A.5) untuk spectrum kustom | Appendix A.2 baris Fase 2 |
| **Fase 3 — GLSL Engine** | `EglOffscreenContext`, `AudioFftAnalyzer`, shader bawaan GLSL ES (hasil konversi 3 template section 6.0), pipeline render-frame-ke-encoder alpha, **colok ke RenderWorker Fase 2 via Extension Point (file BARU, bukan edit)** | Appendix A.2 baris Fase 3 |
| **Fase 4 — UI Wizard & Queue** | Wizard multi-step Compose, layar antrian, preset manager — konsumsi `RenderTaskRepository` & model domain dari Appendix A.3 apa adanya | Appendix A.2 baris Fase 4 |
| **Fase 5 — Canvas Editor & Shader Studio** | Editor drag/warp/keyframe, 3 wizard template shader (section 6.1-6.3) + galeri + `ShaderValidator` (implementasi penuh langkah konversi 6.0) | Appendix A.2 baris Fase 5 |
| **Fase 6 — Polish** | Deteksi hardware encoder lintas chipset (perluas `HardwareEncoderDetector` Fase 2 lewat Extension Point, bukan edit), optimasi performa/baterai, pengujian perangkat | Appendix A.2 baris Fase 6 |

---

## 9. CATATAN TEKNIS & LEGAL PENTING

- **Lisensi FFmpeg**: build FFmpeg dengan encoder GPL (mis. libx264) berarti aplikasi
  tunduk pada lisensi GPL/LGPL — perlu keputusan legal (distribusi source atau linking
  dinamis sesuai skema LGPL) sebelum publikasi ke Play Store.
- **Lisensi Font** (lihat juga 5.1c & Appendix A.6): 10 font family di source Python diambil
  langsung dari folder sistem Windows berlisensi Microsoft — TIDAK boleh dibundel ke APK
  tanpa izin. Fase yang membundling font (biasanya bagian dari Fase 4/5 saat wizard teks
  dibangun) WAJIB pakai pengganti bebas lisensi dengan mapping nama family yang sama.
- **Hardware encoding**: sepenuhnya bergantung pada `MediaCodec` bawaan Android, hasil
  bervariasi antar chipset — wajib ada fallback software (`libx264`) yang stabil di semua
  perangkat.
- **Performa FFT/audio real-time**: pertimbangkan implementasi native C++ (NDK) untuk
  beban analisis frekuensi agar tidak membebani baterai/CPU saat render berjalan lama.
- **Manajemen memori GLSL offscreen**: EGL context, FBO, dan buffer wajib dirilis
  eksplisit setiap selesai render satu spectrum untuk mencegah leak di sesi render panjang.

---

## 10. PAKET FFMPEG UNTUK ANDROID (tidak perlu build manual dari nol)

**Status penting**: `FFmpegKit` versi asli (`arthenica/ffmpeg-kit`) resmi **pensiun sejak
Januari 2025** dan repo di-archive April 2025 — binary lama sudah dihapus dari Maven
Central, jadi dependency Gradle lama (`com.arthenica:ffmpeg-kit-full-gpl`) sudah **tidak
bisa lagi didownload otomatis**. Ada 2 kelanjutan resmi/komunitas per pertengahan 2026:

| Opsi | Cara pakai | Perlu build sendiri? |
|---|---|---|
| **`ffmpegkit-maintained/ffmpeg`** (fork komunitas, drop-in) | Sama persis package `com.arthenica.ffmpegkit`, tinggal ganti baris dependency di Gradle, tetap publish `.aar` siap pakai ke Maven Central | **Tidak** — ini pilihan termudah untuk CI/GitHub Actions |
| **`FFmpegKitNext`** (`arthenica/ffmpeg-kit-next`, kelanjutan resmi) | API kompatibel dengan FFmpegKit 6.0 | **Ya** — sejak versi terbaru didistribusikan **source-only**, harus dibuild lokal via NDK mengikuti script bawaan mereka (lebih rumit untuk CI otomatis) |
| **Build manual dari nol** (`mobile-ffmpeg` style, NDK + shell script) | Full kontrol pustaka eksternal (libx264, dll) | Ya, paling rumit, waktu build NDK bisa >30 menit per run CI |

**Rekomendasi untuk proyek ini**: pakai opsi pertama (`ffmpegkit-maintained`) sebagai
`implementation` dependency di `app/build.gradle.kts` — jadi **tidak perlu download/kompilasi
FFmpeg secara manual sama sekali**, GitHub Actions cukup `gradle assembleRelease` seperti
biasa dan Gradle otomatis menarik `.aar` dari Maven Central saat build. Ini juga yang membuat
build APK lewat GitHub Actions (section 11) bisa selesai dalam hitungan menit tanpa tahap
compile native FFmpeg terpisah.

Catatan lisensi: varian `-gpl` (dengan `libx264`) tunduk GPL v3 (lihat section 9). Varian
non-GPL (`min`/`https`/`audio`/`video`) tidak termasuk `libx264`, dipakai jika encoder
hardware (`h264_mediacodec`) sudah cukup dan software fallback tidak wajib `libx264`.

### 10.1 Bundling shader GLSL (Timer, Spectrum, Effect) jadi satu paket APK

Semua file `.glsl` (shader bawaan hasil generate dari 3 wizard template — Timer Countdown
3D, Spectrum Custom, Overlay Effect Loop) disimpan sebagai **Android assets**
(`app/src/main/assets/shaders/*.glsl`), dibaca via `AssetManager` saat runtime — jadi ikut
terbundle otomatis ke dalam satu file `.apk` akhir, sama seperti FFmpeg native library (`.so`)
yang otomatis ikut ter-package oleh Android App Bundle/Gradle. Tidak perlu proses download
terpisah saat aplikasi pertama dijalankan.

> **Perbaikan dari v1 (sudah diverifikasi ulang)**: ketiga file template prompt —
> `TEMPLATE_PROMPT_TIMER_3D.md`, `TEMPLATE_PROMPT_SPECTRUM.md`, dan
> `TEMPLATE_PROMPT_OVERLAY_GENERIC_LOOP.md` — **semuanya sudah tersedia** (catatan v1 yang
> bilang "Spectrum Custom belum ada" keliru/sudah kedaluwarsa, sudah dicek ulang isinya).
> Yang masih WAJIB dilakukan sebelum bundling ke `assets/shaders/`:
> 1. Semua shader ditulis untuk target **`#version 330` (OpenGL desktop)** di 3 file
>    template — WAJIB dikonversi ke **GLSL ES 3.0** mengikuti 5 langkah di section 6.0
>    (`#version 300 es`, `precision mediump float;`, `out vec4 fragColor;`, cek fungsi
>    non-ES, verifikasi nama uniform sesuai tabel 6.0).
> 2. File `.glsl` **hasil jadi** (bukan cuma template prompt-nya) belum ada di antara file
>    yang diupload — folder `assets/shaders/` diisi belakangan lewat `ShaderValidator`
>    (Fase 5) tiap kali wizard/mode-AI/impor menghasilkan shader baru, BUKAN di-hardcode
>    manual satu-satu di Fase 1.

---

## 11. CI/CD — BUILD OTOMATIS JADI APK VIA GITHUB ACTIONS

Tahap akhir: setiap push/tag rilis, GitHub Actions otomatis compile proyek Kotlin/Gradle
menjadi file `.apk` siap install, lalu diupload sebagai **artifact** (bisa didownload
langsung dari halaman run Actions) sekaligus dilampirkan ke **GitHub Release** kalau push-nya
berupa tag versi (`v1.0.0`, dst).

File workflow: `.github/workflows/build-apk.yml` (lihat file terpisah yang sudah dibuat).
Alur singkat:
1. Checkout kode → setup JDK 17 → setup Android SDK/cache Gradle.
2. `./gradlew assembleRelease` (dependency FFmpeg dari Maven Central ter-download otomatis
   oleh Gradle, lihat section 10 — tidak ada tahap compile native FFmpeg manual di CI).
3. Sign APK release pakai keystore yang disimpan sebagai **GitHub Secrets**
   (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) — kalau secrets ini
   belum diisi, workflow tetap sukses menghasilkan APK **debug** (bisa langsung diinstall
   untuk testing, hanya tidak bisa dipublikasi ke Play Store).
4. Upload APK sebagai artifact run (`actions/upload-artifact`) — didownload manual dari tab
   Actions kapan saja.
5. Kalau trigger-nya tag `v*`, otomatis buat GitHub Release dan lampirkan APK di situ juga,
   supaya link download stabil untuk dibagikan/diinstall langsung di HP.

---

## APPENDIX A — KONTRAK BEKU LINTAS FASE

Ini bagian paling penting untuk mekanisme "kerjakan tiap fase di sesi/akun/container
terpisah tanpa upload ulang apapun" (section 0). Semua yang tertulis di sini adalah
**sumber kebenaran final** — kalau ada bagian body dokumen (section 1-11) yang terkesan
beda, Appendix A yang menang.

### A.1 Nama Package & Namespace (terkunci, tidak boleh diganti fase manapun)

```
namespace / applicationId : com.visualizerstudio.app
minSdk                    : 26
targetSdk/compileSdk      : mengikuti API level terbaru stabil saat Fase 1 dikerjakan
```

Kalau suatu saat memang harus ganti nama package (mis. alasan Play Store), itu HARUS
dilakukan lewat satu revisi Appendix A.1 baru (v3 dst) yang di-broadcast ulang ke semua sesi
— bukan diputuskan sepihak oleh satu sesi fase tertentu.

### A.2 Manifest Kepemilikan File (WAJIB dicek sebelum bikin file apapun)

Aturan baca tabel: kolom "Fase pemilik" = satu-satunya fase yang boleh **membuat** file itu.
Fase lain boleh **mengimpor/memakainya** (via `import` Kotlin biasa) tapi TIDAK BOLEH
membuatnya ulang dengan isi berbeda maupun mengeditnya.

| Path file | Fase pemilik | Isi ringkas |
|---|---|---|
| `app/build.gradle.kts`, `settings.gradle.kts`, `AndroidManifest.xml` | 1 | Skeleton project, dependency dasar |
| `domain/model/RenderTask.kt` | 1 | Seluruh data class (kode lengkap di A.3) |
| `domain/repository/RenderTaskRepository.kt` | 1 | Interface (kode lengkap di A.4.1) |
| `domain/repository/RenderTaskRepositoryImpl.kt` + Room DAO/Entity | 1 | Implementasi konkret di atas Room |
| `data/datastore/AppSettings.kt`, `HardwarePrefs.kt` | 1 | DataStore kosongan (skema key saja) |
| `render/ffmpeg/RenderProgress.kt` | 2 | Model progres & hasil (kode lengkap di A.4.2) |
| `render/ffmpeg/FfmpegProgressParser.kt` | 2 | Parser regex fallback |
| `render/ffmpeg/EncoderParamsProvider.kt` | 2 | `HardwareEncoderMode`, `EncoderSelection`, `HardwareEncoderDetector` dasar (kode lengkap di A.4.3) |
| `render/ffmpeg/DrawTextFilterBuilder.kt`, `IntroTitleFilterBuilder.kt` | 2 | Filter drawtext intro/title |
| `render/ffmpeg/AudioConcatBuilder.kt`, `MediaTimelineBuilder.kt` | 2 | Concat audio, timeline visual |
| `render/ffmpeg/FfmpegFilterGraphBuilder.kt` | 2 | Filter graph multi-layer utama |
| `render/ffmpeg/FfmpegSessionRunner.kt`, `SafPathResolver.kt` | 2 | Eksekusi FFmpegKit, resolusi Uri SAF |
| `render/ffmpeg/FontAssetResolverImpl.kt` | 2 | Copy-once font assets → filesDir |
| `render/ffmpeg/spectrum/CustomSpectrumRenderer.kt` | 2 | **Interface titik sambung** (kode lengkap di A.4.4) — dibuat Fase 2, DIISI Fase 3 |
| `di/CustomSpectrumRendererMultibindModule.kt` | 2 | Modul `@Multibinds` kosong (kode lengkap di A.5) |
| `worker/RenderWorker.kt`, `RenderNotifications.kt`, `RenderQueueScheduler.kt` | 2 | Orkestrasi pipeline + notifikasi + antrian |
| `di/RenderModule.kt` | 2 | Binding `FontAssetResolver` |
| `util/FfmpegUtils.kt` | 2 | Helper murni (ensureEvenInt, dsb) |
| `render/gl/EglOffscreenContext.kt`, `ShaderProgramCache.kt` | 3 | EGL Pbuffer offscreen, cache program shader |
| `render/audio/AudioFftAnalyzer.kt`, `BeatEnergyExtractor.kt`, `WavDecoder.kt` | 3 | Analisis FFT/beat |
| `render/gl/GlEsSpectrumRenderer.kt` | 3 | Render shader → video alpha per-frame |
| `render/gl/GlEsSpectrumRendererAdapter.kt` (implements `CustomSpectrumRenderer`) | 3 | Adapter yang mengimplementasikan interface A.4.4 |
| `di/GlEsSpectrumRendererBindModule.kt` | 3 | `@Binds @IntoSet` — **file baru**, colok ke multibind Fase 2 (lihat A.5) |
| `assets/shaders/*.glsl` (shader bawaan) | 3 | Hasil konversi GLSL ES dari 3 template (section 6.0) |
| `ui/wizard/**`, `ui/queue/**`, `ui/preset/**`, `ui/home/**` | 4 | Layar Compose wizard/antrian/preset/dashboard |
| `ui/canvaseditor/**` | 5 | Canvas Editor full-gesture + timeline keyframe |
| `ui/shaderstudio/**` | 5 | 3 wizard template shader UI + galeri |
| `render/gl/ShaderValidator.kt` | 5 | Validasi & konversi otomatis (langkah 6.0) |
| `assets/fonts/*.ttf` (font pengganti legal) | 5 (atau 4, siapa pun yang bikin wizard teks duluan) | Lihat A.6 soal lisensi |
| `render/ffmpeg/HardwareEncoderDetectorExtended.kt` | 6 | Perluasan skor kompatibilitas per chipset (via Extension Point, TIDAK edit file Fase 2) |

> File yang belum tercantum di tabel ini (mis. detail sub-komponen UI kecil) mengikuti fase
> sesuai cakupan section 8 & folder section 3 — buat file baru bebas selama tidak menabrak
> path yang sudah dimiliki fase lain di tabel di atas.

### A.3 Model Domain Beku — `domain/model/RenderTask.kt` (dimiliki Fase 1)

Fase 1 WAJIB membuat file ini **persis isi berikut** (fase lain cukup `import` dari path
`com.visualizerstudio.app.domain.model.*`, tidak perlu melihat isinya lagi):

```kotlin
package com.visualizerstudio.app.domain.model

import android.net.Uri

enum class MediaMode { EQUAL_SPLIT, PER_TRACK }

enum class Resolution(val width: Int, val height: Int) {
    R720P(1280, 720), R1080P(1920, 1080), R2K(2560, 1440), R4K(3840, 2160)
}

enum class TaskStatus { QUEUED, RENDERING, DONE, FAILED }
enum class BgVisualEffect { NONE, VIGNETTE, BLUR, GRAYSCALE, SEPIA }
enum class SpectrumType { SHOWFREQS, SHOWWAVES, CANDLES, SEGMENTED_FREQ, SHOWFREQS_LOG, NONE, CUSTOM_SHADER }

data class SpectrumConfig(
    val shaderRef: String = "", val specType: SpectrumType = SpectrumType.SHOWFREQS,
    val width: Int = 800, val height: Int = 300,
    val posX: String = "(W-w)/2", val posY: String = "H-h-50",
    val color: String = "white", val opacity: Float = 0.8f,
    val bgColor: String? = null, val bgOpacity: Float = 0.5f,
    val useBeatZoom: Boolean = false, val shearX: Float = 0f, val shearY: Float = 0f
)

data class OverlayAssetConfig(
    val fileUri: Uri, val opacity: Float = 1.0f, val fadeInSeconds: Float = 2.0f,
    val loop: Boolean = false, val delaySeconds: Float = 0f,
    val shearX: Float = 0f, val shearY: Float = 0f, val autoLumaKey: Boolean = true
)

data class OverlaySpeedConfig(
    val active: Boolean = false, val targetOverlayIndices: List<Int> = emptyList(),
    val speedMode: String = "auto_beat"
)

data class CropConfig(val left: Int = 0, val right: Int = 0, val top: Int = 0, val bottom: Int = 0) {
    val isActive: Boolean get() = left > 0 || right > 0 || top > 0 || bottom > 0
}

enum class TextPosition { CENTER_CENTER, CENTER_BOTTOM, CENTER_TOP, TOP_LEFT, TOP_RIGHT, MID_LEFT, MID_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
enum class TextAnimation(val code: Int) { FADE_IN_OUT(1), SLIDE_UP(2), SLIDE_LEFT(3), ZOOM_IN(4) }
data class TextLine(val text: String, val fontSizePx: Int)

data class TextOverlayConfig(
    val lines: List<TextLine>, val delaySeconds: Float = 0f, val displayDurationSeconds: Float = 5.0f,
    val fontFamily: String = "Arial", val bold: Boolean = false, val italic: Boolean = false,
    val fontColor: String = "white", val opacity: Float = 1.0f,
    val positionPreset: TextPosition = TextPosition.CENTER_CENTER,
    val animationStyle: TextAnimation = TextAnimation.FADE_IN_OUT,
    val animationDurationSeconds: Float = 1.0f,
    val useStroke: Boolean = false, val strokeColor: String = "black", val strokeWidthPx: Int = 2,
    val xExpr: String? = null, val yExpr: String? = null
)

enum class EasingType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }
data class Keyframe(
    val timeSec: Float, val x: Float, val y: Float,
    val scaleX: Float = 1f, val scaleY: Float = 1f, val rotation: Float = 0f,
    val opacity: Float = 1f, val easing: EasingType = EasingType.LINEAR
)
data class KeyframeSet(
    val bg: List<Keyframe> = emptyList(), val spectrum: List<Keyframe> = emptyList(),
    val intro: List<Keyframe> = emptyList(), val title: List<Keyframe> = emptyList(),
    val overlays: Map<String, List<Keyframe>> = emptyMap()
)

data class SlowMotionGroupConfig(val active: Boolean = false, val speed: Float = 1.0f)
data class SlowMotionConfig(
    val visualGroup: SlowMotionGroupConfig? = null, val overlayGroup: SlowMotionGroupConfig? = null,
    val perFile: Map<String, SlowMotionGroupConfig> = emptyMap()
)
data class ReversePlayConfig(val active: Boolean = false)

data class RenderTask(
    val id: String, val name: String,
    val mediaFiles: List<Uri>, val audioFiles: List<Uri>,
    val mediaMode: MediaMode = MediaMode.PER_TRACK,
    val resolution: Resolution = Resolution.R720P,
    val fps: Int = 30, val loops: Int = 1,
    val spectrums: List<SpectrumConfig> = emptyList(),
    val bgVideoOverlays: List<OverlayAssetConfig> = emptyList(),
    val introConfig: TextOverlayConfig? = null, val titleConfig: TextOverlayConfig? = null,
    val slowMotionConfig: SlowMotionConfig = SlowMotionConfig(),
    val reversePlayConfig: ReversePlayConfig = ReversePlayConfig(),
    val overlaySpeedConfig: OverlaySpeedConfig = OverlaySpeedConfig(),
    val cropConfig: CropConfig = CropConfig(),
    val bgVisualEffect: BgVisualEffect = BgVisualEffect.NONE,
    val useBeatZoom: Boolean = false,
    val keyframes: KeyframeSet = KeyframeSet(),
    val outputFolder: Uri, val status: TaskStatus = TaskStatus.QUEUED
)
```

### A.4 Interface Lintas-Fase Beku

#### A.4.1 `domain/repository/RenderTaskRepository.kt` (dimiliki Fase 1)
```kotlin
package com.visualizerstudio.app.domain.repository

import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.TaskStatus

interface RenderTaskRepository {
    suspend fun getTaskById(id: String): RenderTask?
    suspend fun updateStatus(id: String, status: TaskStatus, errorMessage: String? = null)
    suspend fun getNextQueuedTask(): RenderTask?
}
```

#### A.4.2 `render/ffmpeg/RenderProgress.kt` (dimiliki Fase 2)
```kotlin
package com.visualizerstudio.app.render.ffmpeg

data class RenderProgress(
    val percent: Float, val currentTimeSec: Float, val totalTimeSec: Float,
    val fps: Float?, val speed: String?
)

sealed class RenderResult {
    data class Success(val outputPath: String) : RenderResult()
    data class Failure(val message: String, val ffmpegLogTail: String? = null) : RenderResult()
    object Cancelled : RenderResult()
}
```

#### A.4.3 `render/ffmpeg/EncoderParamsProvider.kt` — bagian kontrak (dimiliki Fase 2)
```kotlin
package com.visualizerstudio.app.render.ffmpeg

enum class HardwareEncoderMode { AUTO, MANUAL, CPU_SOFTWARE }
data class EncoderSelection(val mediaCodecName: String?, val ffmpegVideoCodec: String)
```
Fase 6 dilarang mengedit `HardwareEncoderDetector` di file ini — perluasan skor kompatibilitas
WAJIB jadi class baru terpisah yang memanggil `HardwareEncoderMode`/`EncoderSelection` di atas
sebagai tipe data, bukan mewarisi/mengubah `HardwareEncoderDetector` yang sudah ada.

#### A.4.4 `render/ffmpeg/spectrum/CustomSpectrumRenderer.kt` — TITIK SAMBUNG Fase 2 ↔ Fase 3 (dimiliki Fase 2)
```kotlin
package com.visualizerstudio.app.render.ffmpeg.spectrum

import com.visualizerstudio.app.domain.model.SpectrumConfig

/**
 * Kontrak render 1 spectrum kustom (shader GLSL, bukan filter FFmpeg native) jadi video
 * alpha siap di-overlay FfmpegFilterGraphBuilder. Fase 2 HANYA mendeklarasikan interface ini
 * (tanpa implementasi nyata — lihat A.5 kenapa). Fase 3 WAJIB membuat implementasi baru yang
 * meng-implement interface ini persis (nama method, parameter, return type), lalu
 * di-"colok" lewat Dagger Multibindings (A.5) — TANPA pernah mengedit file RenderWorker.kt
 * maupun file interface ini sendiri.
 */
interface CustomSpectrumRenderer {
    /** @return true kalau berhasil, file video alpha tertulis di [outputPath]. */
    suspend fun renderToAlphaVideo(
        spec: SpectrumConfig,
        durationSec: Float,
        fps: Int,
        audioSamplePath: String,
        outputPath: String
    ): Boolean
}
```

### A.5 Extension Point Pattern (WAJIB dipakai untuk SEMUA titik sambung antar-fase)

Masalah yang diselesaikan: Fase 3 perlu "menyalakan" render spectrum kustom di dalam
`RenderWorker` (file milik Fase 2), tapi aturan besi 0.1 melarang Fase 3 mengedit file Fase
2. Solusinya **Dagger Hilt Multibindings** — pola resmi Dagger untuk dependency opsional yang
"mungkin ada, mungkin tidak" tanpa tabrakan binding:

**Langkah 1 (dimiliki Fase 2)** — modul multibind KOSONG, cukup mendeklarasikan bahwa
`Set<CustomSpectrumRenderer>` itu valid meski belum ada isinya:
```kotlin
// di/CustomSpectrumRendererMultibindModule.kt
package com.visualizerstudio.app.di

import com.visualizerstudio.app.render.ffmpeg.spectrum.CustomSpectrumRenderer
import dagger.Module
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CustomSpectrumRendererMultibindModule {
    @Multibinds
    abstract fun bindCustomSpectrumRendererSet(): Set<CustomSpectrumRenderer>
}
```
`RenderWorker` (Fase 2) inject `Set<@JvmSuppressWildcards CustomSpectrumRenderer>` lewat
constructor, lalu pakai `.firstOrNull()` — kalau kosong (belum ada Fase 3), otomatis
skip render spectrum kustom (fallback aman, TIDAK crash), persis seperti perilaku Fase 2
yang berdiri sendiri.

**Langkah 2 (dimiliki Fase 3)** — **file BARU**, tidak pernah menyentuh 2 file di atas:
```kotlin
// di/GlEsSpectrumRendererBindModule.kt
package com.visualizerstudio.app.di

import com.visualizerstudio.app.render.ffmpeg.spectrum.CustomSpectrumRenderer
import com.visualizerstudio.app.render.gl.GlEsSpectrumRendererAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class GlEsSpectrumRendererBindModule {
    @Binds
    @IntoSet
    abstract fun bindGlEsSpectrumRenderer(impl: GlEsSpectrumRendererAdapter): CustomSpectrumRenderer
}
```
Begitu file ini digabung ke project (section 0.3 — extract folder Fase 3 ke folder yang
sama), Dagger otomatis mendeteksi 2 modul multibind ini saat compile & `Set<CustomSpectrumRenderer>`
yang di-inject `RenderWorker` otomatis berisi 1 elemen (implementasi Fase 3) — **tanpa
sebaris pun kode di `RenderWorker.kt` perlu berubah**.

**Pola yang sama WAJIB dipakai untuk titik sambung lain**, termasuk (tidak terbatas pada):
- Fase 6 memperluas `HardwareEncoderDetector` (Fase 2) — bikin `Set<EncoderCompatibilityScorer>`
  kosong di Fase 2, diisi Fase 6.
- Fase 5 menyambungkan `ShaderValidator` ke pipeline penyimpanan `ShaderTemplateEntity`
  (Fase 1) — kalau butuh hook serupa, tambahkan interface+multibind kosong baru di file
  Fase 1 yang relevan saat Fase 1 dikerjakan (rencanakan titik sambung SEBELUM fase
  sebelumnya selesai, supaya tidak perlu edit belakangan).

> **Prinsip umum**: setiap kali sebuah fase MERASA perlu mengedit file fase sebelumnya untuk
> "menyalakan" sesuatu, itu tandanya fase sebelumnya seharusnya sudah menyediakan titik
> sambung kosong (interface + `@Multibinds`) di tempat itu. Kalau titik sambung yang
> dibutuhkan ternyata belum ada di Appendix A manapun, tambahkan sebagai revisi Appendix A
> baru (v3) dan pastikan revisi itu diberikan ke SEMUA sesi berikutnya — jangan diakali
> dengan mengedit file lama di satu sesi saja.

### A.6 Checklist Legal Aset (font & shader) — dicek ulang tiap fase yang menyentuh aset

- [ ] Font `.ttf` yang dibundel ke `assets/fonts/` BUKAN diambil mentah dari
      `C:/Windows/Fonts` (lisensi Microsoft) — pakai pengganti bebas lisensi (Liberation
      Sans/Serif, Carlito, Caladea, atau font Google Fonts berlisensi OFL) dengan mapping
      nama family identik dengan daftar di 5.1c.
- [ ] Shader `.glsl` yang dibundel ke `assets/shaders/` sudah lolos 5 langkah konversi
      GLSL ES di section 6.0 (bukan `#version 330` mentah dari file template prompt).
- [ ] Encoder GPL (`libx264` via `ffmpegkit-maintained` varian `-gpl`) — kalau dipakai,
      keputusan lisensi GPL/LGPL sudah diambil sebelum rilis publik (lihat section 9).

---

## APPENDIX C — DEFINITION OF DONE ANTI-DUMMY (WAJIB self-check sebelum fase dianggap selesai)

> Ditambahkan karena kompleksitas UI interaktif (Canvas Editor, Shader Studio, Wizard) rawan
> disederhanakan diam-diam jadi versi "terlihat jadi tapi tidak fungsional penuh" kalau tidak
> ada kriteria konkret. Setiap fase di bawah **WAJIB** memverifikasi checklist-nya sendiri
> (jelaskan per poin: implementasi nyata / masih placeholder) sebelum melapor "fase selesai"
> ke user. Kalau ada poin yang TIDAK bisa diimplementasikan penuh dalam satu sesi (mis. karena
> keterbatasan effort/panjang respons), itu WAJIB dinyatakan eksplisit sebagai "belum lengkap,
> sisanya: [...]" — **DILARANG** melaporkan selesai kalau ada poin checklist yang cuma stub/TODO.

### C.1 Canvas Editor (Fase 5) — tidak boleh dummy di poin manapun berikut:

- [ ] **Drag posisi** spectrum, teks (intro/title), DAN overlay asset — ketiganya, bukan
      cuma spectrum (section 5.6 eksplisit minta ini jadi perluasan dari versi PyQt asli
      yang cuma bisa drag spectrum). Drag harus update `x`/`y` state real-time saat jari
      bergerak, bukan cuma commit di akhir gesture.
- [ ] **Live sync DUA ARAH nyata**: (a) geser gizmo di canvas → angka di panel properti
      berubah live, DAN (b) ubah angka di panel (mis. ketik posisi X manual) → gizmo di
      canvas ikut pindah live. Kalau cuma salah satu arah yang jalan, itu BELUM selesai.
- [ ] **Resize** (lebar/tinggi) via handle di sudut/tepi gizmo, bukan cuma lewat field angka.
- [ ] **Shear X/Y (warp/tilt)** — gizmo harus visual berubah bentuk (jajar genjang) saat
      shear diubah, bukan cuma menyimpan angka tanpa representasi visual.
- [ ] **Crop handle interaktif** (kiri/kanan/atas/bawah) — ini FITUR YANG SENGAJA
      "dihidupkan" dari versi asli (section 5.1e: di source Python field-nya ada tapi TIDAK
      PERNAH diekspos ke UI). Kalau Fase 5 cuma bikin field angka crop tanpa handle visual
      yang bisa diseret, berarti perbaikan yang dijanjikan blueprint gagal terpenuhi.
- [ ] **Timeline keyframe visual** di bagian bawah — minimal bisa: tambah keyframe di waktu
      tertentu, geser keyframe di timeline, preview interpolasi antar-keyframe bergerak
      (bukan cuma list angka waktu tanpa representasi timeline).
- [ ] **Snap-to-grid / snap-to-safe-area** — aktif nyata saat drag (gizmo "nempel" ke garis
      bantu), bukan cuma toggle switch yang tidak berefek.
- [ ] **Tombol Render Preview** memanggil FFmpeg nyata (lewat `FfmpegSessionRunner` milik
      Fase 2) — BUKAN simulasi/progress bar palsu.

### C.2 Shader Template Studio (Fase 5) — tidak boleh dummy:

- [ ] Ketiga wizard (Timer 3D, Spectrum Custom, Overlay Loop) benar-benar men-generate file
      `.glsl` valid mengikuti struktur di section 6.1-6.3, BUKAN cuma UI form tanpa
      men-generate apapun di baliknya.
- [ ] `ShaderValidator` benar-benar menjalankan 5 langkah konversi section 6.0 (bukan
      cuma `return true` tanpa cek apapun) — minimal harus benar-benar gagal-kan shader yang
      melanggar (mis. masih pakai `#version 330` tanpa konversi).
- [ ] Galeri shader menampilkan thumbnail hasil **render nyata** shader itu (via
      `GLSurfaceView`/EGL offscreen dari Fase 3), bukan gambar placeholder statis yang sama
      untuk semua entry.

### C.3 Wizard Buat Tugas (Fase 4) — tidak boleh dummy:

- [ ] Semua 22 langkah (section 5.1) benar-benar tersambung ke `RenderTask` (Appendix A.3) —
      submit di akhir wizard harus menghasilkan objek `RenderTask` lengkap terisi, bukan
      cuma UI form yang tidak pernah menulis ke Room.
- [ ] Live preview `GLSurfaceView` di section Spectrum (langkah 9-17) benar-benar me-render
      shader terpilih dengan sample audio, bukan gambar statis.
- [ ] Validasi instan tiap field (section 5.1: "semua input tervalidasi instan, bukan tunggu
      submit") benar-benar jalan — tombol Lanjut disabled sampai field valid, bukan validasi
      yang cuma dipanggil tapi hasilnya diabaikan.

### C.4 Antrian Render (Fase 4) — tidak boleh dummy:

- [ ] Reorder drag-and-drop benar-benar mengubah urutan eksekusi di `RenderQueueScheduler`
      (Fase 2), bukan cuma reorder visual list tanpa efek ke WorkManager.
- [ ] Progress bar per-item benar-benar terhubung ke `RenderProgress` (Appendix A.4.2) real
      dari `RenderWorker`, bukan animasi progress palsu.

### C.5 Cara melaporkan hasil tiap fase (WAJIB dipakai di akhir respons tiap sesi)

Setiap sesi yang mengerjakan Fase 4 atau 5 WAJIB menutup laporannya dengan checklist di atas
dicentang jujur satu-per-satu (✅ implementasi nyata / ⚠️ implementasi parsial + jelaskan
kurangnya apa / ❌ belum dikerjakan), BUKAN cuma kalimat umum "semua fitur sudah lengkap".
Kalau ada checklist yang ❌/⚠️, user berhak minta sesi itu lanjut menyelesaikannya SEBELUM
pindah ke fase berikutnya — jangan lanjut ke fase baru di atas fondasi UI yang masih dummy.

---
