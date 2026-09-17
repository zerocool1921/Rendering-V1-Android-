#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# INSTALLER OTOMATIS - RENDER VISUALIZER (TERMUX EDITION)
# =============================================================================
# Jalankan sekali saja setelah clone repo ini di Termux:
#   bash install.sh
# =============================================================================
set -e

echo "========================================================="
echo "  INSTALL RENDER VISUALIZER - TERMUX EDITION"
echo "========================================================="

echo "[1/5] Update paket Termux..."
pkg update -y && pkg upgrade -y

echo "[2/5] Install Python & FFmpeg (ffmpeg, ffprobe, ffplay)..."
pkg install -y python ffmpeg

echo "[3/5] Install dependensi Python (numpy)..."
pip install --upgrade pip
pip install -r requirements.txt

echo "[4/5] Minta izin akses penyimpanan HP (untuk baca/simpan file di luar Termux)..."
termux-setup-storage || true

echo "[5/5] Cek instalasi FFmpeg..."
ffmpeg -version | head -n 1
ffprobe -version | head -n 1

echo ""
echo "========================================================="
echo "  INSTALASI SELESAI"
echo "========================================================="
echo "  Jalankan aplikasi dengan:"
echo "      bash run.sh"
echo "  atau langsung:"
echo "      python main.py"
echo "========================================================="
