# Render Visualizer — Termux Edition

Aplikasi CLI (menu teks) untuk membuat video visualizer audio (spectrum, overlay,
intro/title, backsound, dsb) berbasis FFmpeg. Versi ini sudah diadaptasi supaya
jalan native di **Termux (Android)** lewat Python — tanpa perlu compile APK,
tanpa GPU, tanpa dependensi berat.

## Apa yang berubah dari versi asli

- **Canvas Editor GUI** (PyQt6 + OpenGL/ModernGL) — **dihapus**. Fitur ini butuh
  layar desktop & GPU driver, tidak relevan untuk HP.
- **Spectrum Generator GLSL** (ModernGL shader) — **dihapus**. Semua submenu
  pemilihan jenis spectrum sudah dicabut dari wizard & manajer preset. File
  `queue.json`/`presets.json` lama yang masih punya data spectrum tetap aman
  dibuka — entri spectrum-nya cuma dilewati, tidak bikin crash.
- **Deteksi binary FFmpeg/FFprobe/FFplay otomatis**: dicari berurutan di folder
  aplikasi (mode portable) → lokasi baku Termux
  (`/data/data/com.termux/files/usr/bin/`) → PATH sistem. Tidak perlu diatur manual.
- **Mode hardware encoding baru: Android MediaCodec** (`h264_mediacodec`) —
  memakai hardware encoder chip HP (kalau build FFmpeg-nya mendukung
  `--enable-mediacodec`, seperti build Termux `pkg install ffmpeg` standar).
  Jauh lebih hemat baterai & lebih cepat daripada software encode (`libx264`).
  Dites otomatis lewat dry-run; kalau gagal, otomatis fallback ke CPU — anti-crash.
- Sisanya (wizard tugas, antrian render, preset, audio FX, logika FFmpeg)
  **tidak diubah** — semua tetap jalan seperti versi aslinya.

## Instalasi di Termux

```bash
pkg install git -y
git clone <url-repo-github-kamu>
cd <nama-repo>
bash install.sh
```

`install.sh` otomatis meng-install Python, FFmpeg, `numpy`, lalu minta izin
akses penyimpanan HP (`termux-setup-storage`) supaya bisa baca file audio/video
dari luar Termux (Download, Music, dll via `~/storage/`).

## Menjalankan

```bash
bash run.sh
```

atau langsung:

```bash
python main.py
```

Kalau mau ada ikon shortcut di homescreen, install app **Termux:Widget** dari
F-Droid, lalu simpan `run.sh` ke folder `~/.shortcuts/`.

## Upload ke GitHub

```bash
git init
git add .
git commit -m "Render Visualizer - Termux Edition"
git branch -M main
git remote add origin https://github.com/<username>/<nama-repo>.git
git push -u origin main
```

`.gitignore` sudah menyertakan `queue.json`, `presets.json`, `temp/`, `error.txt`,
dan hasil render (`*.mp4`) — jadi tidak ikut ter-commit.

## Struktur menu

1. Tambah Tugas Baru (Wizard)
2. Lihat Antrian Aktif
3. Edit Rincian Tugas
4. Hapus Tugas
5. Manajemen Preset Visual
6. Mulai Render Antrian
7. Ganti Mode Hardware (AUTO / NVENC / QSV / MediaCodec / CPU)

## Soal APK (kenapa tidak dipaketkan sebagai .apk)

Aplikasi ini berbasis `input()`/`print()` di terminal — bukan bug, tapi arsitektur
intinya. Supaya benar-benar jadi APK native, seluruh alur wizard perlu ditulis
ulang total jadi UI tombol/form (mis. pakai Kivy), dan pemanggilan `ffmpeg` lewat
`subprocess` perlu dikemas ulang sebagai native lib (`.so`) karena Android 10+
memblokir eksekusi binary biasa dari storage aplikasi. Itu proyek rewrite
tersendiri, bukan sekadar "compile". Jalur Termux di repo ini jauh lebih stabil
dan mempertahankan 100% logika aslinya.

## Troubleshooting

- **"ffmpeg tidak ditemukan"** → `pkg install ffmpeg`
- **Tidak bisa akses file di Download/Music** → jalankan `termux-setup-storage`,
  lalu pilih file lewat path `~/storage/shared/...` atau `~/storage/downloads/...`
- **Render lambat** → cek Menu [7], coba paksa mode `MediaCodec`; kalau HP tidak
  didukung, aplikasi otomatis fallback ke CPU (`libx264`, aman tapi lebih lambat)
