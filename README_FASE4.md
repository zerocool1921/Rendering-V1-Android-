# Fase 4 — UI Wizard, Antrian, Preset, Home

Dikerjakan sesuai `BLUEPRINT_ANDROID_VISUALIZER_APP-v2.md` section 8 (Roadmap) & Appendix A.2.
Semua file di bawah **BARU** — tidak ada file milik Fase 1/2/3 yang diedit/ditimpa.

## Cara gabung ke project (section 0.3)
Extract folder `app/src/main/java/com/visualizerstudio/app/` di paket ini ke folder project
yang sama tempat Fase 1-3 sudah di-extract sebelumnya. Tidak ada path yang bentrok dengan
manifest A.2 (dicek satu-per-satu, lihat bagian "Manifest file yang dibuat" di bawah).

## Manifest file yang dibuat Fase 4

| Path | Status vs Appendix A.2 |
|---|---|
| `ui/wizard/**` | Sesuai tabel A.2 — milik Fase 4 |
| `ui/queue/**` | Sesuai tabel A.2 — milik Fase 4 |
| `ui/preset/**` | Sesuai tabel A.2 — milik Fase 4 |
| `ui/home/**` | Sesuai tabel A.2 — milik Fase 4 |
| `domain/model/Preset.kt` | **Ekstensi baru**, tidak ada di A.3 (A.3 hanya membekukan `RenderTask.kt`). Preset domain model belum pernah dipatok fase manapun, jadi ditambahkan di sini. Diusulkan masuk revisi Appendix A v3 supaya Fase 5 (galeri preset dgn thumbnail render) pakai struktur yang sama persis. |
| `domain/repository/RenderQueueRepository.kt` | **Ekstensi baru.** `RenderTaskRepository` beku (A.4.1) cuma py `getTaskById`, `updateStatus`, `getNextQueuedTask` — tidak cukup untuk UI Queue (perlu observe semua task, insert, delete, reorder). Interface baru ini melengkapi tanpa mengedit A.4.1. |
| `domain/repository/PresetRepository.kt` | **Ekstensi baru**, pasangan `Preset.kt` di atas. |
| `ui/components/**` (subset dipakai Wizard: `ColorSwatchPicker.kt`, `PositionPresetGrid.kt`, `ValidatedTextField.kt`) | Folder `ui/components` di section 3 ditandai "Komponen Compose bersama", tidak dipatok ke satu fase — dipakai lintas fase. Fase 5 boleh menambah file baru lain di folder yang sama tanpa mengedit 3 file ini. |

## Asumsi implementasi (WAJIB dibaca sebelum Fase 5/6 lanjut)

1. **Implementasi konkret `RenderQueueRepository` & `PresetRepository`** diasumsikan disediakan
   lewat Hilt binding ke `RenderTaskDao`/`PresetDao` (Room, sudah disebut ada di section 3,
   dimiliki Fase 1) dengan method konvensional Room (`@Query SELECT * ...`, `@Insert`, `@Delete`).
   Karena DAO itu sendiri tidak dibekukan field-per-field di Appendix A, Fase 4 TIDAK membuat
   ulang file DAO (menghindari tabrakan) — file `data/repository/RenderQueueRepositoryImpl.kt`
   & `data/repository/PresetRepositoryImpl.kt` disertakan di sini sebagai **implementasi
   referensi** yang mengasumsikan `RenderTaskDao`/`PresetDao` sudah punya method:
   `observeAll(): Flow<List<XEntity>>`, `insert(e: XEntity)`, `delete(id: String)`,
   `updateOrder(ids: List<String>)` (untuk RenderTaskDao), `getById(id): XEntity?`.
   Kalau nama method di DAO asli Fase 1 berbeda, sesi Fase 5/6 tinggal sesuaikan nama panggilan
   di 2 file impl ini saja (bukan ganggu domain layer).
2. **Progress antrian** dibaca lewat WorkManager standar (`WorkInfo` + `Data` progress) dengan
   asumsi `RenderWorker` (Fase 2) memanggil `setProgressAsync` dengan key: `"percent"` (Float
   0-100), `"currentTimeSec"` (Float), `"totalTimeSec"` (Float), dan unique work name = task id.
   Ini API publik WorkManager, jadi tidak perlu membuka file `RenderWorker.kt` — hanya asumsi
   kontrak key `Data`. Kalau Fase 2 pakai key lain, cukup ganti string key di
   `ui/queue/QueueViewModel.kt` (satu tempat).
3. **Reorder antrian** memanggil `RenderQueueRepository.reorderTasks(orderedIds)` yang
   menyimpan urutan (`sortIndex: Int` tambahan diasumsikan ada di Room entity — kalau belum,
   tinggal tambah 1 kolom `sortIndex` di `RenderTaskEntity` Fase 1 dengan migrasi Room).
   `RenderQueueScheduler` (Fase 2) diasumsikan membaca urutan lewat query yg sama
   (`ORDER BY sortIndex`) — tidak ada file Fase 2 yang disentuh di sini.
4. **Live preview Spectrum** (langkah 9-17 wizard) memakai `AndroidView` yang membungkus
   `GLSurfaceView` + memanggil `GlEsSpectrumRenderer` (Fase 3) lewat interface minimal yang
   diasumsikan: `fun attach(surfaceView: GLSurfaceView, spec: SpectrumConfig, sampleAudioPath: String)`.
   Karena method ini TIDAK ada di kontrak beku Appendix A manapun (hanya `CustomSpectrumRenderer.
   renderToAlphaVideo` yang beku, dan itu untuk render final bukan live preview GL), bagian ini
   ditandai ⚠️ di checklist DoD di bawah — perlu diselaraskan dengan API asli Fase 3 begitu
   digabung, atau diusulkan sebagai kontrak baru di Appendix A v3 (`GlPreviewSurfaceBinder`).
5. **Preview Tugas (render 30 detik nyata)** memanggil `FfmpegSessionRunner` (Fase 2) via
   Hilt injection langsung ke ViewModel — nama method diasumsikan `runPreview(task, maxSeconds=30)`
   mengembalikan `Flow<RenderProgress>`/`RenderResult` (pola sama seperti A.4.2). Kalau nama
   beda, cukup ganti satu baris pemanggilan di `WizardViewModel.kt`.
6. **Font**: dropdown 10 font family di step 19/20 hanya menyimpan nama string ke
   `TextOverlayConfig.fontFamily` (sesuai kontrak A.3) — tidak membundel `.ttf` apapun (itu
   tanggung jawab Fase 5 per A.2, kolom font notes).

## Definition of Done — self-check (Appendix C.3 & C.4)

Lihat pesan akhir laporan (bukan file ini) untuk checklist tercentang.
