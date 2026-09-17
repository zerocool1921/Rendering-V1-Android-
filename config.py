# --- START OF FILE config.py ---

import os
import sys
import re
import time
import subprocess
import shutil

# -------------------------------------------------------------------------
# DETEKSI PATH ABSOLUT LINGKUNGAN KERJA APLIKASI
# -------------------------------------------------------------------------
if getattr(sys, 'frozen', False):
    SCRIPT_ROOT = os.path.dirname(sys.executable)
else:
    SCRIPT_ROOT = os.path.dirname(os.path.abspath(__file__))

QueueFile = os.path.join(SCRIPT_ROOT, "queue.json")
PresetFile = os.path.join(SCRIPT_ROOT, "presets.json")
TempFolder = os.path.join(SCRIPT_ROOT, "temp")
ErrorLog = os.path.join(SCRIPT_ROOT, "error.txt")

# -------------------------------------------------------------------------
# NAMA BINARY FFMPEG DISESUAIKAN OTOMATIS SESUAI OS (WINDOWS vs LINUX/COLAB)
# Di Linux/Google Colab, binary TIDAK memakai akhiran ".exe".
# -------------------------------------------------------------------------
IS_WINDOWS = (os.name == "nt")

# -------------------------------------------------------------------------
# DETEKSI LINGKUNGAN TERMUX (ANDROID)
# Termux selalu punya env var PREFIX berisi ".../com.termux/files/usr".
# Ini dipakai sebagai sinyal paling pasti (lebih akurat daripada menebak
# dari os.name, karena Termux tetap melapor sebagai "posix" biasa).
# -------------------------------------------------------------------------
IS_TERMUX = "com.termux" in os.environ.get("PREFIX", "")
TERMUX_BIN = "/data/data/com.termux/files/usr/bin"

_ffmpeg_bin_name = "ffmpeg.exe" if IS_WINDOWS else "ffmpeg"
_ffplay_bin_name = "ffplay.exe" if IS_WINDOWS else "ffplay"
_ffprobe_bin_name = "ffprobe.exe" if IS_WINDOWS else "ffprobe"


def _locate_binary(bin_name, local_fallback):
    """
    Mencari lokasi binary (ffmpeg/ffplay/ffprobe) dengan urutan prioritas:
    1. Folder lokal aplikasi (mode portable, mis. dibundel bareng .exe di Windows)
    2. Lokasi baku Termux: /data/data/com.termux/files/usr/bin/<nama>
       (dicek langsung tanpa bergantung shutil.which, karena PATH shell
       Termux kadang belum ter-load penuh saat script dipanggil dari cron/
       Tasker/shortcut lain).
    3. shutil.which() -- mencari di PATH sistem manapun (Linux/Mac/Colab/dll).
    Kalau semua gagal, kembalikan path lokal apa adanya (biar pesan
    peringatan di initialize_app() tetap konsisten menyebut nama filenya).
    """
    if os.path.exists(local_fallback):
        return local_fallback
    termux_path = os.path.join(TERMUX_BIN, bin_name)
    if os.path.exists(termux_path):
        return termux_path
    found = shutil.which(bin_name)
    if found:
        return found
    return local_fallback


FFmpeg = _locate_binary(_ffmpeg_bin_name, os.path.join(SCRIPT_ROOT, _ffmpeg_bin_name))
FFplay = _locate_binary(_ffplay_bin_name, os.path.join(SCRIPT_ROOT, _ffplay_bin_name))
FFprobe = _locate_binary(_ffprobe_bin_name, os.path.join(SCRIPT_ROOT, _ffprobe_bin_name))

AssetsFolder = os.path.join(SCRIPT_ROOT, "assets")
ShadersFolder = os.path.join(SCRIPT_ROOT, "shaders")

# -------------------------------------------------------------------------
# LOKASI FOLDER FONT DISESUAIKAN OTOMATIS SESUAI OS
# -------------------------------------------------------------------------
if IS_WINDOWS:
    FontsFolder = "C:/Windows/Fonts"
else:
    _linux_font_candidates = [
        os.path.join(os.path.dirname(TERMUX_BIN), "share/fonts"),  # font paket Termux (fontconfig dkk)
        "/system/fonts",                                            # font sistem Android (Roboto, dll)
        "/usr/share/fonts",
        "/usr/share/fonts/truetype",
        "/usr/local/share/fonts",
        os.path.expanduser("~/.fonts"),
        os.path.expanduser("~/storage/shared/Fonts"),               # font custom user, via termux-setup-storage
    ]
    FontsFolder = next((p for p in _linux_font_candidates if os.path.isdir(p)), "/usr/share/fonts")

# -------------------------------------------------------------------------
# STATUS GLOBAL AKSELERASI HARDWARE GPU & MEMORI VRAM
# -------------------------------------------------------------------------
STATE = {
    "gpu_accelerator_active": False,
    "gpu_vendor": "Unknown",             # NVIDIA, INTEL, AMD, CPU
    "gpu_name": "CPU Fallback / Software Render",
    "gpu_memory_total_mb": 0,
    "cuda_device_id": 0,
    "cuda_available": False,
    "cupy_available": False,
    "torch_cuda_available": False,
    "opencv_cuda_available": False,
    "moderngl_active": False,
    "ffmpeg_hwaccel_type": None,         # cuda, qsv, d3d11va
    "ffmpeg_vcodec": "libx264",          # h264_nvenc, h264_qsv, h264_amf, libx264
    "ffmpeg_hw_input_args": [],          # Argumen input HWAccel GPU
    "ffmpeg_hw_extra_args": [],          # Argumen output encoder GPU
    "last_ffmpeg_exit_code": 0,
    "canvas_editor_open": False,
    "user_hardware_mode": "AUTO"
}

# -------------------------------------------------------------------------
# PRESET KUALITAS ENCODE VIDEO (dipilih user di wizard SEBELUM render)
# Dua MODE:
#   CBR = bitrate video KONSTAN sepanjang durasi (ukuran file bisa dipastikan
#         akurat sebelum render -> estimasi size = bitrate x durasi).
#   CRF = kualitas visual yang dijaga konstan, bitrate MENYESUAIKAN kompleksitas
#         konten per frame (ukuran file bisa lebih kecil dari CBR di adegan
#         statis, atau lebih besar di adegan ramai) -> estimasi size di sini
#         SIFATNYA PERKIRAAN, dihitung dari aturan umum encoding: tiap naik/
#         turun 6 poin CRF, bitrate kira-kira dibagi/dikali 2 dari titik acuan
#         CRF 23 (setara mode CBR "Sedang").
# 4 tingkat di kedua mode: very_low, low, med (rekomendasi), high.
# -------------------------------------------------------------------------
QUALITY_PRESETS = {
    "CBR": {
        "very_low": {"label": "Sangat Rendah", "bitrate_factor": 0.40},
        "low":      {"label": "Rendah",         "bitrate_factor": 0.70},
        "med":      {"label": "Sedang (Rekomendasi)", "bitrate_factor": 1.00},
        "high":     {"label": "Tinggi",         "bitrate_factor": 1.50},
    },
    "CRF": {
        "very_low": {"label": "Sangat Rendah", "crf": 32, "cq": 32},
        "low":      {"label": "Rendah",         "crf": 28, "cq": 28},
        "med":      {"label": "Sedang (Rekomendasi)", "crf": 23, "cq": 23},
        "high":     {"label": "Tinggi",         "crf": 18, "cq": 18},
    },
}
QUALITY_LEVEL_ORDER = ["very_low", "low", "med", "high"]


def check_gpu_dependencies():
    """
    Inisialisasi Pustaka GPU (PyTorch CUDA, CuPy, OpenCV CUDA)
    secara mendalam di VRAM memori.
    """
    try:
        import torch
        if torch.cuda.is_available():
            STATE["torch_cuda_available"] = True
            STATE["cuda_available"] = True
            STATE["cuda_device_id"] = 0
            torch.cuda.set_device(0)
            
            STATE["gpu_name"] = torch.cuda.get_device_name(0)
            STATE["gpu_memory_total_mb"] = int(torch.cuda.get_device_properties(0).total_memory / (1024 * 1024))
            
            torch.backends.cudnn.benchmark = True
            torch.backends.cuda.matmul.allow_tf32 = True
            torch.backends.cudnn.allow_tf32 = True
    except BaseException:
        pass

    try:
        import cupy as cp
        if cp.cuda.is_available():
            STATE["cupy_available"] = True
            cp.cuda.Device(0).use()
    except BaseException:
        pass

    try:
        import cv2
        if cv2.cuda.getCudaEnabledDeviceCount() > 0:
            STATE["opencv_cuda_available"] = True
            cv2.cuda.setDevice(0)
    except BaseException:
        pass


def test_encoder_available(vcodec, log_reason=False):
    """
    Melakukan tes dry-run inisialisasi encoder GPU dengan FFmpeg.
    Mencegah error 'nvcuda.dll missing' pada GPU Intel HD Graphics.

    CATATAN PENTING: Resolusi tes dipakai 256x256 (bukan 16x16).
    NVENC pada sebagian driver/GPU MENOLAK frame yang terlalu kecil
    (batas minimum encode NVENC bisa di kisaran 33x33 s/d 145x49
    tergantung generasi & versi driver). Kalau tesnya pakai frame
    16x16, GPU T4 yang sebenarnya normal & mendukung NVENC bisa
    salah terdeteksi sebagai 'tidak didukung' (false negative),
    lalu aplikasi keliru fallback ke CPU padahal GPU-nya sanggup.
    256x256 aman jauh di atas batas minimum manapun, dan tetap
    sangat cepat (durasi cuma 0.2 detik).
    """
    if not os.path.exists(FFmpeg):
        return False
    try:
        cmd = [
            FFmpeg, "-hide_banner", "-loglevel", "error",
            "-f", "lavfi", "-i", "color=c=black:s=256x256:d=0.2",
            "-c:v", vcodec, "-f", "null", "-"
        ]
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        )
        _, stderr = proc.communicate()
        ok = proc.returncode == 0
        if not ok and log_reason:
            reason = stderr.decode(errors="ignore").strip().splitlines()
            reason_msg = reason[-1] if reason else "(tidak ada pesan eror dari FFmpeg)"
            print(f"[INFO] Encoder '{vcodec}' tidak tersedia -> {reason_msg}")
        return ok
    except Exception:
        return False



def set_manual_hardware_mode(mode="AUTO"):
    """
    Mengunci mode akselerasi hardware pilihan pengguna.
    """
    STATE["user_hardware_mode"] = mode
    detect_ffmpeg_hwaccel()


def detect_ffmpeg_hwaccel():
    """
    Mendeteksi dan Mengoptimalkan Argumen GPU NVENC / QSV / CPU.
    DIOPTIMALKAN UNTUK KECEPATAN TERTINGGI (SPEED & FPS MAXIMAL).
    """
    if not os.path.exists(FFmpeg):
        return

    mode = STATE.get("user_hardware_mode", "AUTO")

    if mode == "CPU":
        STATE["gpu_vendor"] = "CPU"
        STATE["ffmpeg_hwaccel_type"] = None
        STATE["ffmpeg_vcodec"] = "libx264"
        STATE["ffmpeg_hw_input_args"] = []
        STATE["ffmpeg_hw_extra_args"] = [
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-tune", "zerolatency",
            "-threads", "0",
            "-pix_fmt", "yuv420p"
        ]
        return

    try:
        # 1. TES NYATA NVIDIA NVENC 
        if mode in ["AUTO", "NVENC"] and test_encoder_available("h264_nvenc", log_reason=True):
            STATE["gpu_vendor"] = "NVIDIA"
            STATE["ffmpeg_hwaccel_type"] = "cuda"
            STATE["ffmpeg_vcodec"] = "h264_nvenc"
            # PERBAIKAN: -extra_hw_frames 32 terlalu besar -- ditambah pool
            # default NVDEC bisa melebihi batas aman 32 surface per sesi
            # decode di Tesla T4 ("Using more than 32 (43) decode surfaces"),
            # bikin decoder gagal init secara diam-diam (proses lain yang
            # memakainya, mis. pra-render alpha overlay, gagal tanpa exit
            # code yang jelas). 8 extra frame sudah cukup sebagai headroom.
            STATE["ffmpeg_hw_input_args"] = ["-hwaccel", "cuda", "-extra_hw_frames", "8"]
            
            # PENGOPTIMALAN MAX SPEED NVIDIA: Preset P1 (Paling Cepat) + Low Latency
            STATE["ffmpeg_hw_extra_args"] = [
                "-c:v", "h264_nvenc",
                "-preset", "p1",
                "-tune", "ll",
                "-rc", "cbr",
                "-spatial-aq", "1",
                "-temporal-aq", "1",
                "-rc-lookahead", "10", 
                "-surfaces", "16",     
                "-no-scenecut", "1",
                "-forced-idr", "1",
                "-delay", "0",
                "-pix_fmt", "yuv420p"
            ]
            STATE["gpu_accelerator_active"] = True
            return

        # 2. TES NYATA INTEL QUICKSYNC (QSV) 
        if mode in ["AUTO", "QSV"] and test_encoder_available("h264_qsv", log_reason=True):
            STATE["gpu_vendor"] = "INTEL"
            STATE["ffmpeg_hwaccel_type"] = "qsv"
            STATE["ffmpeg_vcodec"] = "h264_qsv"
            STATE["ffmpeg_hw_input_args"] = ["-hwaccel", "qsv"]
            STATE["ffmpeg_hw_extra_args"] = [
                "-c:v", "h264_qsv",
                "-preset", "veryfast",
                "-pix_fmt", "nv12"
            ]
            STATE["gpu_accelerator_active"] = True
            return

        # 3. TES NYATA ANDROID MEDIACODEC (Hardware Encoder HP via NdkMediaCodec)
        # Relevan untuk build FFmpeg Termux (--enable-mediacodec) -- jauh lebih
        # hemat baterai & lebih cepat daripada libx264 software di CPU HP.
        if mode in ["AUTO", "MEDIACODEC"] and IS_TERMUX and test_encoder_available("h264_mediacodec", log_reason=True):
            STATE["gpu_vendor"] = "ANDROID"
            STATE["ffmpeg_hwaccel_type"] = "mediacodec"
            STATE["ffmpeg_vcodec"] = "h264_mediacodec"
            STATE["ffmpeg_hw_input_args"] = []
            STATE["ffmpeg_hw_extra_args"] = [
                "-c:v", "h264_mediacodec",
                "-b:v", "6M",
                "-pix_fmt", "nv12"
            ]
            STATE["gpu_accelerator_active"] = True
            return

    except BaseException:
        pass

    # Fallback CPU Engine (Sangat Aman & Bebas Crash)
    STATE["gpu_vendor"] = "CPU"
    STATE["ffmpeg_hwaccel_type"] = None
    STATE["ffmpeg_vcodec"] = "libx264"
    STATE["ffmpeg_hw_input_args"] = []
    STATE["ffmpeg_hw_extra_args"] = [
        "-c:v", "libx264",
        "-preset", "ultrafast",
        "-tune", "zerolatency",
        "-threads", "0",
        "-pix_fmt", "yuv420p"
    ]


def initialize_app():
    """
    Inisialisasi Lingkungan Kerja, Direktori, dan Hardware Encoding Aplikasi.
    """
    for exe_key, exe_path in [("FFmpeg", FFmpeg), ("FFplay", FFplay), ("FFprobe", FFprobe)]:
        if not os.path.exists(exe_path) and not shutil.which(os.path.basename(exe_path)):
            exe_name = os.path.basename(exe_path)
            print(f"[PERINGATAN] Berkas '{exe_name}' tidak ditemukan di folder lokal, "
                  f"lokasi baku Termux, maupun PATH sistem.")
            if IS_TERMUX:
                print(f"            -> Install dulu di Termux dengan menjalankan:")
                print(f"               pkg install ffmpeg")
            elif not IS_WINDOWS:
                print("            -> Di Linux/Colab, install dulu dengan menjalankan:")
                print("               sudo apt-get -y install ffmpeg")

    check_gpu_dependencies()
    detect_ffmpeg_hwaccel()

    os.makedirs(TempFolder, exist_ok=True)
    os.makedirs(AssetsFolder, exist_ok=True)
    os.makedirs(ShadersFolder, exist_ok=True)

    if not os.path.exists(QueueFile):
        with open(QueueFile, "w", encoding="utf-8") as f:
            f.write("[]")

    if not os.path.exists(PresetFile):
        with open(PresetFile, "w", encoding="utf-8") as f:
            f.write("[]")