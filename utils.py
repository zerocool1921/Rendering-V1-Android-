import os
import sys
import re
import math
import time
import wave
import subprocess
import random
import threading
import queue as _queue_module
from datetime import datetime
import numpy as np
import config

try:
    import torch
    HAS_TORCH = True
except BaseException:
    HAS_TORCH = False

try:
    import cupy as cp
    HAS_CUPY = True
except BaseException:
    HAS_CUPY = False


# =========================================================================
# HELPER PEMBACA AUDIO DATA UNTUK FFT LIVE & SPECTRUM (SOLUSI BUG ATTRIBUTEERROR)
# =========================================================================
def safe_read_audio_data(wav_path):
    """
    Pembaca audio robust multi-format PCM/Float WAV untuk mencegah wave.Error.
    Tersedia di utils.py agar dapat diakses oleh Canvas Editor, Spectrum Generator, dan Renderer.
    """
    try:
        wf = wave.open(wav_path, 'rb')
        sample_rate = wf.getframerate()
        channels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        n_frames = wf.getnframes()
        raw_bytes = wf.readframes(n_frames)
        wf.close()

        if sampwidth == 2:
            audio_data = np.frombuffer(raw_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        elif sampwidth == 1:
            audio_data = (np.frombuffer(raw_bytes, dtype=np.uint8).astype(np.float32) - 128.0) / 128.0
        elif sampwidth == 4:
            try:
                audio_data = np.frombuffer(raw_bytes, dtype=np.float32)
            except Exception:
                audio_data = np.frombuffer(raw_bytes, dtype=np.int32).astype(np.float32) / 2147483648.0
        else:
            audio_data = np.frombuffer(raw_bytes, dtype=np.int16).astype(np.float32) / 32768.0

        if channels > 1:
            audio_data = audio_data.reshape(-1, channels).mean(axis=1)

        return audio_data, sample_rate
    except Exception as e:
        write_error_log(f"Gagal membaca audio WAV {wav_path}: {str(e)}")
        return np.zeros(44100 * 5, dtype=np.float32), 44100


# =========================================================================
# HELPER SANITASI ANGKA GENAP & KOORDINAT KESELAMATAN FFMPEG
# =========================================================================
def ensure_even_int(val, min_val=2):
    """
    Memastikan dimensi (Lebar/Tinggi) selalu berupa bilangan bulat genap absolut (divisible by 2).
    Menggunakan floor division untuk memastikan akurasi 1:1 dengan kalkulasi sub-pixel FFmpeg.
    """
    try:
        num = int(math.floor(float(val)))
        num = max(num, min_val)
        if num % 2 != 0:
            num += 1
        return num
    except (ValueError, TypeError):
        return min_val


def sanitize_ffmpeg_coord(val):
    """
    Memastikan koordinat X/Y berupa integer bersih atau ekspresi valid FFmpeg.
    Menghilangkan float berlebih (misal '342.1293') yang merusak parser FFmpeg.
    """
    if val is None or str(val).strip() == "":
        return "0"
    val_str = str(val).strip()
    try:
        f = float(val_str)
        return str(int(round(f)))
    except ValueError:
        return val_str


def escape_process_argument(arg):
    if not arg:
        return '""'
    if not re.search(r'[\s"&|<>#^;]', arg):
        return arg
    escaped = str(arg).replace('\\', '\\\\').replace('"', '\\"')
    return f'"{escaped}"'


def run_ffmpeg_silent(arguments, log_path=None):
    """
    Eksekutor FFmpeg senyap dengan Penyeleksian GPU Hardware Acceleration:
    HANYA menulis log eror jika returncode TIDAK NOL (bukan 0) untuk mencegah log palsu.
    """
    try:
        hw_args = []
        is_audio_only = "-vn" in arguments or any(str(arg).endswith(('.mp3', '.wav', '.flac', '.m4a', '.ogg')) for arg in arguments if "-i" in arguments)
        has_video_input = any(str(arg).endswith(('.mp4', '.mov', '.mkv', '.png', '.jpg', '.jpeg', '.webp', '.gif', '.webm', '.avi')) for arg in arguments)

        if config.STATE.get("gpu_accelerator_active") and not is_audio_only and has_video_input:
            hw_input_args = config.STATE.get("ffmpeg_hw_input_args", [])
            if "-hwaccel" not in arguments and hw_input_args:
                hw_args = hw_input_args.copy()

        full_command = [config.FFmpeg] + hw_args + arguments

        process = subprocess.Popen(
            full_command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0,
            text=True,
            encoding='utf-8',
            errors='ignore'
        )
        stdout_data, stderr_data = process.communicate()
        config.STATE["last_ffmpeg_exit_code"] = process.returncode
        
        if process.returncode != 0:
            if log_path and stderr_data:
                # PERBAIKAN: pada environment Google Drive-mounted (Colab),
                # folder "temp" bisa sempat tidak terbaca/hilang sesaat
                # (desync FUSE mount Drive, terutama setelah proses
                # sebelumnya diinterupsi paksa) walau sudah dibuat sekali
                # di awal lewat config.initialize_app(). Buat ulang foldernya
                # kalau perlu SEBELUM menulis log, supaya penulisan log eror
                # ini sendiri tidak ikut gagal dengan "No such file or directory".
                log_dir = os.path.dirname(os.path.abspath(log_path))
                if log_dir and not os.path.exists(log_dir):
                    os.makedirs(log_dir, exist_ok=True)
                with open(log_path, "w", encoding="utf-8") as f:
                    f.write(stderr_data)
            write_error_log(f"FFmpeg Silent Fail (Exit Code {process.returncode}):\n{stderr_data}")
                
        return process.returncode == 0
    except Exception as e:
        write_error_log(f"Eror saat menjalankan FFmpegSilent: {str(e)}")
        config.STATE["last_ffmpeg_exit_code"] = -1
        return False


def get_process_output(executable, arguments):
    try:
        process = subprocess.Popen(
            [executable] + arguments,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0,
            text=True,
            encoding='utf-8',
            errors='ignore'
        )
        stdout_data, _ = process.communicate()
        return stdout_data
    except Exception as e:
        write_error_log(f"Eror saat mendapatkan output proses {executable}: {str(e)}")
        return ""


def write_error_log(message):
    """
    PERBAIKAN: sebelumnya kalau penulisan ke error.txt gagal (mis. folder
    belum ada, path relatif tidak ketemu karena working directory berubah,
    dsb), kegagalan itu didiamkan total ("except: pass") -- sehingga error
    yang seharusnya tercatat malah TIDAK PERNAH muncul di error.txt DAN
    tidak ada petunjuk apa pun kalau proses pencatatannya sendiri gagal.

    Sekarang: folder tujuan dibuat dulu kalau belum ada, path yang dipakai
    dijadikan absolut (kebal terhadap perubahan working directory), dan
    kalau penulisan tetap gagal, alasannya dicetak ke konsol supaya
    kegagalan pencatatan itu sendiri tidak lagi tak terlihat.
    """
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    gpu_info = f"[{config.STATE.get('gpu_vendor', 'CPU')}] {config.STATE.get('gpu_name', 'Unknown')}"
    try:
        log_path = os.path.abspath(config.ErrorLog)
        log_dir = os.path.dirname(log_path)
        if log_dir and not os.path.exists(log_dir):
            os.makedirs(log_dir, exist_ok=True)
        with open(log_path, "a", encoding="utf-8") as f:
            f.write(f"[{timestamp}] {gpu_info} -> {message}\n")
    except Exception as log_write_err:
        print(f"[PERINGATAN] Gagal menulis ke error.txt ({log_write_err}). Isi eror asli:\n{message}")


def write_detailed_ffmpeg_error(task_name, error_log_path):
    raw_error = ""
    if os.path.exists(error_log_path):
        try:
            with open(error_log_path, "r", encoding="utf-8", errors="ignore") as f:
                raw_error = f.read()
        except:
            raw_error = "Gagal membaca berkas log eror mentah."

    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    gpu_info = f"GPU Active: {config.STATE.get('gpu_name')} | HWAccel: {config.STATE.get('ffmpeg_hwaccel_type')}"
    friendly_explanation = "Sebab Masalah & Analisis Akselerasi Hardware:\n"
    
    if "cannot load nvcuda.dll" in raw_error.lower() or "exit code 4294967295" in str(config.STATE.get("last_ffmpeg_exit_code")):
        friendly_explanation += "-> Driver NVIDIA CUDA tidak ditemukan/tidak cocok di sistem ini.\n   Solusi: Sistem telah dikonfigurasi untuk auto-fallback ke Intel QuickSync (QSV) / CPU."
    elif "out of memory" in raw_error.lower() or "cuda_error_out_of_memory" in raw_error.lower():
        friendly_explanation += "-> Memori VRAM GPU penuh saat merender!\n   Solusi: Turunkan resolusi video output (1080p)."
    elif "openencodersessionex failed" in raw_error.lower():
        friendly_explanation += "-> Batas maksimal sesi Hardware Video Encoder tercapai.\n   Solusi: Tutup aplikasi perekam layar/OBS lalu ulang render."
    elif "impossible to convert between format" in raw_error.lower():
        friendly_explanation += "-> Bentrok Format Memori CUDA dengan filter CPU.\n   Solusi: Menggunakan decoding CPU standar dan enkoding GPU."
    elif "No such file or directory" in raw_error:
        friendly_explanation += "-> Berkas input media tidak ditemukan.\n   Solusi: Pastikan berkas media tidak dipindahkan atau dihapus."
    elif "Invalid data found when processing input" in raw_error:
        friendly_explanation += "-> Format berkas audio/video rusak.\n   Solusi: Gunakan file MP4/WAV standar."
    else:
        friendly_explanation += "-> Kesalahan eksekusi FFmpeg.\n   Solusi: Periksa detail log CLI di bawah ini."

    divider = "=" * 80
    log_message = (
        f"\n{divider}\n"
        f"[{timestamp}] DETAIL EROR TEKNIS FFMPEG - TUGAS: {task_name}\n"
        f"Status Hardware: {gpu_info} (Exit Code: {config.STATE['last_ffmpeg_exit_code']})\n"
        f"{divider}\n"
        f"{friendly_explanation}\n"
        f"{divider}\n"
        f"LOG DETAIL MESIN FFMPEG (TEKNIS CLI):\n{raw_error if raw_error else 'Log mentah kosong.'}\n"
        f"{divider}\n"
    )
    
    # PERBAIKAN: dulu pakai "except: pass" polos (path relatif, folder
    # tujuan tidak dijamin ada, kegagalan tulis didiamkan total) -- pola
    # persis yang sudah didokumentasikan & diperbaiki di write_error_log()
    # di atas, tapi perbaikannya belum ikut diterapkan di sini. Sekarang
    # disamakan: path dibuat absolut, folder tujuan dibuat dulu kalau
    # belum ada, dan kegagalan tulis (kalau tetap terjadi) dicetak ke
    # konsol supaya tidak lagi tak terlihat.
    try:
        log_path = os.path.abspath(config.ErrorLog)
        log_dir = os.path.dirname(log_path)
        if log_dir and not os.path.exists(log_dir):
            os.makedirs(log_dir, exist_ok=True)
        with open(log_path, "a", encoding="utf-8") as f:
            f.write(log_message)
    except Exception as log_write_err:
        print(f"[PERINGATAN] Gagal menulis detail eror FFmpeg ke error.txt ({log_write_err}).")


_PROGRESS_BAR_TIMERS = {}


def _format_eta(seconds):
    """Format detik jadi HH:MM:SS atau MM:SS untuk estimasi waktu tersisa."""
    try:
        seconds = max(0, int(round(seconds)))
    except Exception:
        return "--:--"
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    if h > 0:
        return f"{h:02d}:{m:02d}:{s:02d}"
    return f"{m:02d}:{s:02d}"


def print_progress_bar(current, total, prefix="", suffix="", width=25, unit="frame", fps=None):
    """
    Progress bar teks sederhana & seragam untuk proses non-FFmpeg (loop Python biasa),
    contoh: rendering frame ModernGL, proses gabung/mux, ekstraksi, dsb.
    Dipakai supaya user selalu tahu progres berjalan, bukan mandek/hang.

    Selalu menampilkan KECEPATAN BERJALAN (item/detik, setara FPS proses) dan
    ESTIMASI WAKTU TERSISA (ETA), dihitung dari rata-rata kecepatan sejak
    progress bar ini pertama kali dipanggil untuk key (prefix) yang sama.

    Kalau parameter `fps` diisi (fps target video, BUKAN fps proses render),
    baris ini juga menampilkan "Video: terrender/total" (durasi video, format
    HH:MM:SS) dan "Speed: Nx" (rasio durasi video yang sudah terbentuk dibagi
    waktu asli yang sudah berlalu) -- seragam dengan tampilan FFmpeg biasa
    (run_ffmpeg_with_progress) supaya SEMUA progress bar konsisten formatnya.
    """
    try:
        total = max(1, int(total))
        current = max(0, min(int(current), total))
        percent = (current / total) * 100.0
        filled = int(round((percent / 100.0) * width))
        filled = max(0, min(width, filled))
        unfilled = width - filled
        bar = ("#" * filled) + ("." * unfilled)

        # --- Hitung kecepatan berjalan (item/detik) & ETA ---
        key = prefix or "_default_"
        now = time.time()
        timer = _PROGRESS_BAR_TIMERS.get(key)
        if timer is None or current <= 1:
            timer = {"start": now}
            _PROGRESS_BAR_TIMERS[key] = timer

        elapsed = max(0.001, now - timer["start"])
        speed_ips = current / elapsed
        remaining_items = max(0, total - current)
        eta_sec = (remaining_items / speed_ips) if speed_ips > 0.0001 else 0
        eta_str = _format_eta(eta_sec)

        line = f"\r    [{bar}] {round(percent, 1)}%"
        if prefix:
            line = f"\r    {prefix} [{bar}] {round(percent, 1)}%"
        line += f" | {speed_ips:.1f} {unit}/s | ETA {eta_str}"

        if fps and fps > 0:
            cur_dur_str = _format_eta(current / float(fps))
            total_dur_str = _format_eta(total / float(fps))
            speed_x = (current / float(fps)) / elapsed
            line += f" | Video: {cur_dur_str}/{total_dur_str} | Speed: {speed_x:.2f}x"

        if suffix:
            line += f" | {suffix}"
        sys.stdout.write(line)
        sys.stdout.flush()
        if current >= total:
            print("")
            _PROGRESS_BAR_TIMERS.pop(key, None)
    except Exception:
        pass


def run_ffmpeg_with_progress(executable, arguments, total_duration, log_path=None):
    log_content = []
    total_hours = int(total_duration // 3600)
    total_mins = int((total_duration % 3600) // 60)
    total_secs = int(total_duration % 60)
    total_time_string = f"{total_hours:02d}:{total_mins:02d}:{total_secs:02d}"

    hw_args = []
    is_audio_only = "-vn" in arguments or any(str(arg).endswith(('.mp3', '.wav', '.flac', '.m4a', '.ogg')) for arg in arguments if "-i" in arguments)
    has_video_input = any(str(arg).endswith(('.mp4', '.mov', '.mkv', '.png', '.jpg', '.jpeg', '.webp', '.gif', '.webm', '.avi')) for arg in arguments)

    if config.STATE.get("gpu_accelerator_active") and not is_audio_only and has_video_input:
        hw_input_args = config.STATE.get("ffmpeg_hw_input_args", [])
        if "-hwaccel" not in arguments and hw_input_args:
            hw_args = hw_input_args.copy()

    full_command = [executable] + hw_args + arguments

    try:
        process = subprocess.Popen(
            full_command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0,
            text=True,
            encoding='utf-8',
            errors='ignore'
        )
    except Exception as e:
        write_error_log(f"Gagal memulai FFmpeg Progress Tracker: {str(e)}")
        config.STATE["last_ffmpeg_exit_code"] = -1
        return False

    gpu_label = f"Hardware ({config.STATE.get('gpu_vendor', 'CPU')})"

    # PERBAIKAN: kalau ffmpeg DEADLOCK (proses tetap hidup tapi berhenti total
    # mengeluarkan output -- BUKAN error/exit code), fungsi ini tidak akan
    # pernah sampai ke process.wait() di bawah, sehingga log yang HANYA
    # ditulis "setelah proses selesai" tidak akan pernah tersimpan sama
    # sekali -- baris terakhir sebelum macet pun hilang. Sekarang tiap baris
    # output ffmpeg langsung ditulis & di-flush ke log_path SAAT ITU JUGA,
    # jadi walau prosesnya macet permanen, baris-baris terakhir sebelum
    # macet tetap ada di disk untuk dianalisis.
    live_log_file = None
    if log_path:
        try:
            live_log_dir = os.path.dirname(os.path.abspath(log_path))
            if live_log_dir and not os.path.exists(live_log_dir):
                os.makedirs(live_log_dir, exist_ok=True)
            live_log_file = open(log_path, "w", encoding="utf-8")
        except Exception:
            live_log_file = None

    # =====================================================================
    # WATCHDOG ANTI-MACET TOTAL
    # =====================================================================
    # SEBELUMNYA: `process.stderr.readline()` dipanggil LANGSUNG di loop
    # utama -- kalau ffmpeg-nya sendiri macet total (proses masih hidup,
    # tapi tidak menulis apa pun lagi ke stderr, mis. filter graph saling
    # tunggu / decoder tersandung di sambungan segmen), readline() ini ikut
    # BLOK SELAMANYA karena tidak ada timeout sama sekali -- makanya render
    # ke-2/ke-3 dst di antrian bisa "diam" tanpa henti tanpa ada exit code
    # ataupun error message apapun (persis yang dilaporkan: baru ketahuan
    # kalau proses dipaksa berhenti manual/KeyboardInterrupt).
    #
    # SEKARANG: pembacaan stderr dipindah ke thread terpisah yang menaruh
    # tiap baris ke queue. Loop utama menunggu queue itu dengan TIMEOUT.
    # Kalau tidak ada baris baru sama sekali selama STALL_TIMEOUT_SEC
    # berturut-turut (artinya ffmpeg benar-benar berhenti total, bukan
    # cuma lambat), proses dianggap macet -> dibunuh paksa, dicatat jelas
    # ke error log ("STALL/MACET" -- bukan exit-code error biasa), dan
    # fungsi ini return False supaya pipeline queue bisa lanjut ke tugas
    # berikutnya alih-alih ikut diam selamanya.
    STALL_TIMEOUT_SEC = 45
    line_queue = _queue_module.Queue()
    stall_detected = {"flag": False}

    def _reader_thread_fn():
        try:
            for ln in iter(process.stderr.readline, ''):
                line_queue.put(ln)
                if not ln and process.poll() is not None:
                    break
        except Exception:
            pass
        finally:
            line_queue.put(None)  # sentinel: pembaca selesai (EOF/proses mati)

    reader_thread = threading.Thread(target=_reader_thread_fn, daemon=True)
    reader_thread.start()

    # Poll pakai timeout PENDEK (bukan langsung STALL_TIMEOUT_SEC) supaya bisa
    # cek berkala apakah nilai "time=" di output ffmpeg beneran maju -- karena
    # ffmpeg yang macet TIDAK SELALU berhenti total ngeprint baris (kadang
    # tetap ngeprint statistik berkala tapi nilai time= di dalamnya BERHENTI
    # naik, mis. filter graph saling tunggu). Watchdog versi silang-total-diam
    # saja tidak akan pernah kepicu untuk kasus ini -- makanya bisa "macet
    # tanpa error.txt sama sekali".
    POLL_INTERVAL_SEC = 2
    last_progress_time_value = None
    last_progress_change_ts = time.time()

    while True:
        try:
            line = line_queue.get(timeout=POLL_INTERVAL_SEC)
        except _queue_module.Empty:
            line = ""  # tidak ada baris baru dalam interval poll ini

        if line is None:
            break

        now_ts = time.time()
        if (now_ts - last_progress_change_ts) >= STALL_TIMEOUT_SEC and process.poll() is None:
            stall_detected["flag"] = True
            stalled_at = log_content[-1].strip() if log_content else "(belum ada output progress sama sekali)"
            write_error_log(
                f"FFmpeg TERDETEKSI MACET/STALL (bukan error biasa): nilai waktu render "
                f"({last_progress_time_value}) tidak maju sama sekali selama "
                f"{STALL_TIMEOUT_SEC} detik berturut-turut (walau proses masih hidup & "
                f"mungkin masih ngeprint baris). Proses dihentikan paksa otomatis oleh "
                f"watchdog anti-macet. Baris progres terakhir sebelum macet: {stalled_at}"
            )
            try:
                process.kill()
            except Exception:
                pass
            break

        if not line:
            continue

        if line:
            log_content.append(line)
            if live_log_file:
                try:
                    live_log_file.write(line)
                    live_log_file.flush()
                except Exception:
                    pass
            match = re.search(r'time=(\d{2}):(\d{2}):(\d{2})\.(\d{2})', line)
            if match:
                hours, mins, secs, csecs = map(int, match.groups())
                current_time = (hours * 3600) + (mins * 60) + secs + (csecs / 100.0)

                if current_time != last_progress_time_value:
                    last_progress_time_value = current_time
                    last_progress_change_ts = now_ts

                percent = 0.0
                if total_duration > 0:
                    percent = (current_time / total_duration) * 100.0
                
                fps = "N/A"
                fps_match = re.search(r'fps=\s*([\d.]+)', line)
                if fps_match:
                    fps = fps_match.group(1)
                    
                speed = "N/A"
                speed_val = None
                speed_match = re.search(r'speed=\s*([\d.]+)x', line)
                if speed_match:
                    speed_val = float(speed_match.group(1))
                    speed = f"{speed_val:.2f}x"
                elif re.search(r'speed=\s*N/A', line):
                    speed = "N/A"

                # --- ESTIMASI WAKTU TERSISA (ETA) berdasar sisa durasi video / speed saat ini ---
                remaining_duration = max(0.0, total_duration - current_time)
                if speed_val and speed_val > 0.0001:
                    eta_sec = remaining_duration / speed_val
                    eta_str = _format_eta(eta_sec)
                else:
                    eta_str = "--:--"

                time_string = f"{hours:02d}:{mins:02d}:{secs:02d}"
                if percent > 100.0:
                    percent = 100.0
                    
                width = 25
                filled = int(round((percent / 100.0) * width))
                unfilled = width - filled
                filled = max(0, filled)
                unfilled = max(0, unfilled)
                bar = ("#" * filled) + ("." * unfilled)
                
                sys.stdout.write(f"\r    [{bar}] {round(percent, 1)}% | {gpu_label} | Video: {time_string} / {total_time_string} | FPS: {fps} | Speed: {speed} | ETA: {eta_str}")
                sys.stdout.flush()

    if live_log_file:
        try:
            live_log_file.close()
        except Exception:
            pass

    process.wait()
    reader_thread.join(timeout=2)
    config.STATE["last_ffmpeg_exit_code"] = process.returncode
    print("")
    if stall_detected["flag"]:
        print("    [!] FFmpeg dihentikan otomatis oleh watchdog: TIDAK ADA progres selama "
              f"{STALL_TIMEOUT_SEC} detik (macet total, bukan error biasa). Lihat error.txt.")

    if process.returncode != 0:
        # log_path sudah lengkap tertulis secara live selama proses berjalan
        # (lihat live_log_file di atas) -- tidak perlu ditulis ulang di sini.
        write_error_log(f"FFmpeg Progress Render Fail (Exit Code {process.returncode}):\n" + "".join(log_content[-20:]))

    return process.returncode == 0


# =========================================================================
# KEYFRAME INTERPOLATION ENGINE (VEKTORISASI PYTORCH TENSOR GPU)
# =========================================================================
def apply_easing(t, easing_type="linear"):
    t = max(0.0, min(1.0, float(t)))
    if easing_type == "ease_in":
        return t * t
    elif easing_type == "ease_out":
        return 1.0 - (1.0 - t) * (1.0 - t)
    elif easing_type == "ease_in_out":
        return 3.0 * t * t - 2.0 * t * t * t
    elif easing_type == "bounce":
        if t < (1 / 2.75):
            return 7.5625 * t * t
        elif t < (2 / 2.75):
            t -= (1.5 / 2.75)
            return 7.5625 * t * t + 0.75
        elif t < (2.5 / 2.75):
            t -= (2.25 / 2.75)
            return 7.5625 * t * t + 0.9375
        else:
            t -= (2.625 / 2.75)
            return 7.5625 * t * t + 0.984375
    return t


def interpolate_value(val1, val2, progress, easing="linear"):
    eased_p = apply_easing(progress, easing)
    return val1 + (val2 - val1) * eased_p


def get_keyframe_at_time(keyframes, current_time, default_dict):
    if not keyframes or not isinstance(keyframes, list):
        return default_dict.copy()

    sorted_kf = sorted(keyframes, key=lambda k: k.get("time", 0.0))
    if current_time <= sorted_kf[0].get("time", 0.0):
        res = default_dict.copy()
        res.update(sorted_kf[0])
        return res

    if current_time >= sorted_kf[-1].get("time", 0.0):
        res = default_dict.copy()
        res.update(sorted_kf[-1])
        return res

    prev_kf = sorted_kf[0]
    next_kf = sorted_kf[-1]
    for i in range(len(sorted_kf) - 1):
        if sorted_kf[i].get("time", 0.0) <= current_time <= sorted_kf[i + 1].get("time", 0.0):
            prev_kf = sorted_kf[i]
            next_kf = sorted_kf[i + 1]
            break

    t1 = prev_kf.get("time", 0.0)
    t2 = next_kf.get("time", 0.0)
    progress = 0.0 if t2 == t1 else (current_time - t1) / (t2 - t1)

    easing = next_kf.get("easing", "linear")
    result = default_dict.copy()

    for key in ["x", "y", "scale_x", "scale_y", "rotation", "opacity"]:
        v1 = prev_kf.get(key, default_dict.get(key, 0.0))
        v2 = next_kf.get(key, default_dict.get(key, 0.0))
        result[key] = interpolate_value(float(v1), float(v2), progress, easing)

    return result


def get_keyframes_vectorized_gpu(keyframes, timestamps_list, default_dict):
    if not keyframes or not isinstance(keyframes, list) or len(timestamps_list) == 0:
        return [default_dict.copy() for _ in timestamps_list]

    sorted_kf = sorted(keyframes, key=lambda k: k.get("time", 0.0))
    if len(sorted_kf) == 1:
        res = default_dict.copy()
        res.update(sorted_kf[0])
        return [res.copy() for _ in timestamps_list]

    GPU_KEYFRAME_THRESHOLD = 5000

    if len(timestamps_list) < GPU_KEYFRAME_THRESHOLD:
        try:
            T = np.asarray(timestamps_list, dtype=np.float64)
            kf_times = np.array([k.get("time", 0.0) for k in sorted_kf], dtype=np.float64)

            indices = np.searchsorted(kf_times, T, side="right")
            indices_right = np.clip(indices, 0, len(sorted_kf) - 1)
            indices_left = np.clip(indices - 1, 0, None)

            t_left = kf_times[indices_left]
            t_right = kf_times[indices_right]
            dt = t_right - t_left

            progress = np.where(dt > 1e-6, (T - t_left) / dt, 0.0)
            progress = np.clip(progress, 0.0, 1.0)
            eased_progress = 3.0 * (progress ** 2) - 2.0 * (progress ** 3)

            computed_props = {}
            for prop in ["x", "y", "scale_x", "scale_y", "rotation", "opacity"]:
                v_raw = np.array([float(k.get(prop, default_dict.get(prop, 0.0))) for k in sorted_kf], dtype=np.float64)
                v_left = v_raw[indices_left]
                v_right = v_raw[indices_right]
                computed_props[prop] = (v_left + (v_right - v_left) * eased_progress).tolist()

            results = []
            for i in range(len(timestamps_list)):
                d = default_dict.copy()
                for prop in computed_props:
                    d[prop] = computed_props[prop][i]
                results.append(d)

            return results
        except Exception as e:
            write_error_log(f"Fallback Keyframe NumPy ke metode lambat: {str(e)}")

    if HAS_TORCH and config.STATE.get("torch_cuda_available"):
        try:
            device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
            T = torch.tensor(timestamps_list, dtype=torch.float32, device=device)
            kf_times = torch.tensor([k.get("time", 0.0) for k in sorted_kf], dtype=torch.float32, device=device)
            
            indices = torch.bucketize(T, kf_times, right=True)
            indices_right = torch.clamp(indices, max=len(sorted_kf) - 1)
            indices_left = torch.clamp(indices - 1, min=0)
            
            t_left = kf_times[indices_left]
            t_right = kf_times[indices_right]
            
            dt = t_right - t_left
            progress = torch.where(dt > 1e-6, (T - t_left) / dt, torch.zeros_like(T))
            progress = torch.clamp(progress, 0.0, 1.0)

            eased_progress = 3.0 * (progress ** 2) - 2.0 * (progress ** 3)

            computed_props = {}
            for prop in ["x", "y", "scale_x", "scale_y", "rotation", "opacity"]:
                v_left_raw = [float(k.get(prop, default_dict.get(prop, 0.0))) for k in sorted_kf]
                v_left = torch.tensor(v_left_raw, dtype=torch.float32, device=device)[indices_left]
                v_right = torch.tensor(v_left_raw, dtype=torch.float32, device=device)[indices_right]
                
                computed_props[prop] = (v_left + (v_right - v_left) * eased_progress).cpu().tolist()

            results = []
            for i in range(len(timestamps_list)):
                d = default_dict.copy()
                for prop in computed_props:
                    d[prop] = computed_props[prop][i]
                results.append(d)

            return results
        except Exception as e:
            write_error_log(f"Fallback Keyframe GPU ke CPU: {str(e)}")

    return [get_keyframe_at_time(keyframes, t, default_dict) for t in timestamps_list]


# =========================================================================
# COORDINATE EXPRESSION & COLOR UTILITIES
# =========================================================================
def parse_position_expression(expr_x, expr_y, canvas_w=1920, canvas_h=1080, elem_w=800, elem_h=300):
    """
    Mengevaluasi posisi ekspresi FFmpeg ke koordinat absolut piksel (Top-Left).
    """
    try:
        clean_x = str(expr_x).replace("W", str(canvas_w)).replace("w", str(elem_w))
        clean_y = str(expr_y).replace("H", str(canvas_h)).replace("h", str(elem_h))
        
        pos_x = float(eval(clean_x))
        pos_y = float(eval(clean_y))
        return round(pos_x, 2), round(pos_y, 2)
    except Exception:
        return round((canvas_w - elem_w) / 2.0, 2), round(canvas_h - elem_h - 50.0, 2)


def hex_to_rgb(color_name_or_hex):
    colors = {
        "red": (255, 50, 50), "yellow": (255, 255, 50), "green": (50, 255, 50),
        "blue": (50, 150, 255), "gold": (255, 215, 0), "black": (0, 0, 0),
        "gray": (128, 128, 128), "turquoise": (64, 224, 208), "orange": (255, 165, 0),
        "pink": (238, 75, 180), "white": (255, 255, 255), "none": (0, 0, 0)
    }
    if color_name_or_hex in colors:
        return colors[color_name_or_hex]
    
    if str(color_name_or_hex).startswith("#"):
        hex_str = color_name_or_hex.lstrip("#")
        if len(hex_str) == 6:
            return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))
    return (255, 255, 255)


def get_color_by_choice(prompt_title, default_color="white"):
    print(f"\n--- Pemilihan Warna: {prompt_title} ---")
    colors = {
        "1": "red", "2": "yellow", "3": "green", "4": "blue",
        "5": "gold", "6": "black", "7": "gray", "8": "turquoise",
        "9": "orange", "10": "pink", "11": "white"
    }
    for key, name in colors.items():
        print(f"[{key}] {name.capitalize()} ({name})")
    print("")
    choice = input(f"Pilih nomor warna [Default: {default_color}]: ").strip()
    return colors.get(choice, default_color)


# =========================================================================
# CHROMA KEY / ALPHA KEYING - KHUSUS OVERLAY VIDEO (BUKAN SPECTRUM)
# Dipakai untuk membuang warna latar solid pada file video Overlay Efek
# (Hitam/Hijau/Merah/Biru) supaya area tersebut jadi alpha/transparan saat
# ditumpuk (overlay) di atas visual utama. Spectrum GPU (GLSL/ModernGL)
# sudah menghasilkan alpha asli sendiri, jadi TIDAK memakai chroma key ini.
# =========================================================================
CHROMA_KEY_PRESETS = {
    "black":  {"label": "Black Screen (Default)", "hex": "black",   "similarity": 0.05},
    "green":  {"label": "Green Screen",            "hex": "0x00FF00", "similarity": 0.20},
    "blue":   {"label": "Blue Screen",              "hex": "0x0000FF", "similarity": 0.20},
    "red":    {"label": "Red Screen",               "hex": "0xFF0000", "similarity": 0.20},
}


def get_chroma_key_hex(key_name):
    """Mengambil nilai hex/nama warna FFmpeg untuk filter colorkey berdasarkan nama key."""
    preset = CHROMA_KEY_PRESETS.get(str(key_name).strip().lower())
    return preset["hex"] if preset else CHROMA_KEY_PRESETS["black"]["hex"]


def get_chroma_key_similarity(key_name):
    """Mengambil nilai similarity default filter colorkey berdasarkan nama key."""
    preset = CHROMA_KEY_PRESETS.get(str(key_name).strip().lower())
    return preset["similarity"] if preset else CHROMA_KEY_PRESETS["black"]["similarity"]


def get_chroma_key_filter_chain(key_name, node_suffix=""):
    """
    Menghasilkan potongan filter FFmpeg (tinggal disambung pakai koma) untuk
    membuang warna latar solid overlay (Black/Green/Blue/Red) SAMPAI BENAR-BENAR
    HILANG total -- setara mode "Screen"/Chroma Key di CapCut -- bukan cuma
    tembus pandang sebagian atau menyisakan sisa warna di pinggir subjek.

    PERBAIKAN: sebelumnya SEMUA warna (termasuk green/blue) memakai filter
    'colorkey' (jarak warna murni di ruang RGB) dengan similarity/blend yang
    sama rata (0.20/0.1). Di footage asli (bukan studio yang rata sempurna),
    variasi pencahayaan di layar hijau/biru bikin sebagian area TIDAK ter-key
    (sisa warna solid masih nongol di beberapa titik), sementara similarity
    dinaikkan malah mulai memakan pinggiran subjek. 'colorkey' juga TIDAK
    punya despill, jadi pinggiran subjek (rambut, dsb) tetap kehijauan/
    kebiruan walau background-nya sendiri sudah transparan.

    Sekarang:
      - Green/Blue -> filter 'chromakey' (bekerja di ruang YUV, cuma
        membandingkan komponen chroma, jauh lebih toleran ke variasi
        cahaya dibanding 'colorkey' RGB) dengan similarity/blend yang
        lebih longgar, DITAMBAH filter 'despill' untuk membuang sisa
        warna yang nempel di tepi subjek -> background langsung hilang
        total & bersih, tanpa fringing.
      - Black -> BUKAN colorkey (jarak warna) lagi, tapi LUMA KEY: alpha
        transparansi diambil LANGSUNG dari tingkat kecerahan piksel itu
        sendiri (split -> grayscale -> alphamerge). Piksel hitam pekat
        otomatis alpha≈0 (transparan total), piksel terang (partikel/
        glow/light-leak) alpha tinggi (terlihat penuh), dan transisi di
        antaranya HALUS proporsional ke kecerahan -- bukan potongan keras
        berbasis similarity warna seperti colorkey (yang selalu nyisain
        fringing/tepi kotak di footage nyata). Ini teknik standar utk
        overlay video/gambar tipe partikel & efek cahaya berlatar hitam,
        dan menjamin "transparan semua" tanpa harus mengorbankan tepi
        objek terang.
      - Red -> tetap pakai 'colorkey' (RGB), similarity & blend dinaikkan
        supaya variasi merah gelap vs terang tetap ikut tembus semua.

    node_suffix: string unik (mis. "_0", "_1") WAJIB diisi kalau fungsi ini
    dipanggil BERKALI-KALI di dalam SATU filter_complex yang sama (overlay
    ke-2, ke-3, dst pada render utama) -- supaya label internal (split/
    alphamerge) milik overlay #1 tidak bentrok dengan overlay #2. Untuk
    pemanggilan berdiri sendiri (proses baking 1x per overlay), boleh
    dikosongkan.
    """
    key = str(key_name).strip().lower()
    if key == "green":
        return "chromakey=0x00FF00:0.55:0.30,despill=type=green:mix=0.7:expand=0.3"
    if key == "blue":
        return "chromakey=0x0000FF:0.55:0.30,despill=type=blue:mix=0.7:expand=0.3"
    if key == "red":
        return "colorkey=0xFF0000:0.50:0.30"
    # default: black -> LUMA KEY (lihat penjelasan lengkap di atas)
    s = node_suffix
    return (
        f"split[_lkm{s}][_lka{s}];"
        f"[_lka{s}]format=gray[_lkag{s}];"
        f"[_lkm{s}][_lkag{s}]alphamerge"
    )


# CATATAN: Pemilihan interaktif warna Chroma Key (Black/Green/Blue/Red) DIHAPUS
# dari wizard atas permintaan -- Chroma Key overlay sekarang SELALU di-hardcode
# ke "black" (lihat wizard.py & presets.py, field "chromaKey" selalu diisi
# string "black" langsung, tanpa ditanya ke user). Fungsi get_chroma_key_hex/
# get_chroma_key_similarity/get_chroma_key_filter_chain di atas tetap
# dipertahankan (dipakai renderer.py) dan otomatis selalu balik ke preset
# "black" karena tidak ada lagi caller yang mengirim nilai selain itu.


def get_spectrum_position_settings(default_x="(W-w)/2", default_y="H-h-50"):
    print("\nPosisi Letak Spectrum di Canvas:")
    print("[1] Tengah-Bawah [DEFAULT]")
    print("[2] Tengah-Tengah (Disarankan untuk tipe Circular)")
    print("[3] Tengah-Atas")
    print("[4] Kiri-Bawah")
    print("[5] Kanan-Bawah")
    print("[6] Koordinat Manual (Bebas/Custom)\n")
    pos_choice = input(f"Pilihan posisi [Saat ini: X={default_x}, Y={default_y}]: ").strip()
    if pos_choice == "1":
        return "(W-w)/2", "H-h-50"
    elif pos_choice == "2":
        return "(W-w)/2", "(H-h)/2"
    elif pos_choice == "3":
        return "(W-w)/2", "50"
    elif pos_choice == "4":
        return "50", "H-h-50"
    elif pos_choice == "5":
        return "W-w-50", "H-h-50"
    elif pos_choice == "6":
        custom_x = input("Masukkan ekspresi/koordinat X (misal: (W-w)/2 atau 100): ").strip()
        custom_y = input("Masukkan ekspresi/koordinat Y (misal: H-h-150 atau 200): ").strip()
        if not custom_x: custom_x = default_x
        if not custom_y: custom_y = default_y
        return custom_x, custom_y
    return default_x, default_y


# =========================================================================
# ASSET IMPORTING (DENGAN DUKUNGAN EKSTENSI LENGKAP)
# =========================================================================
def get_asset_overlay_choice():
    if not os.path.exists(config.AssetsFolder):
        os.makedirs(config.AssetsFolder, exist_ok=True)
        
    all_files = os.listdir(config.AssetsFolder)
    supported_media_exts = (".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v", ".gif", ".png", ".jpg", ".jpeg", ".webp")
    
    files = [os.path.join(config.AssetsFolder, f) for f in all_files if os.path.splitext(f)[1].lower() in supported_media_exts]
    
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("       PILIH ASSET OVERLAY / EFEK")
    print("=========================================")
    print("Letakkan file video/gambar efek di folder:")
    print(f"-> {config.AssetsFolder}\n")
    
    if not files:
        print("Tidak ada file media yang ditemukan di folder 'assets'.")
        print("Silakan masukkan file efek terlebih dahulu.\n")
        print("[0] Tidak menggunakan efek\n")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return "none"
        
    for i, file_path in enumerate(files):
        print(f"[{i + 1}] {os.path.basename(file_path)}")
    print("[0] Matikan/Jangan gunakan efek\n")
    
    choice = input("Pilih nomor efek: ").strip()
    if choice == "0" or not choice:
        return "none"
        
    if choice.isdigit():
        idx = int(choice) - 1
        if 0 <= idx < len(files):
            return files[idx]
            
    return "none"


def get_asset_opacity_choice():
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("       SET TINGKAT OPASITAS EFEK")
    print("=========================================")
    print("[1] Sangat Tipis (0.2)")
    print("[2] Sedang / Standar (0.5)")
    print("[3] Jelas (0.8)")
    print("[4] Penuh (1.0)")
    print("[5] Input Nilai Desimal Manual\n")
    
    choice = input("Pilih opsi [Default: 2]: ").strip()
    if choice == "1":
        return 0.2
    elif choice == "2" or not choice:
        return 0.5
    elif choice == "3":
        return 0.8
    elif choice == "4":
        return 1.0
    elif choice == "5":
        print("")
        manual = input("Masukkan nilai desimal manual (0.1 s.d 1.0): ").strip()
        try:
            val = float(manual)
            return max(0.0, min(1.0, val))
        except ValueError:
            return 0.5
    return 0.5


def get_asset_fade_in_choice():
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("       SET DURASI TRANSISI EFEK (FADE IN)")
    print("=========================================")
    ans = input("Masukkan durasi transisi dalam detik [Default: 2.0]: ").strip()
    try:
        return float(ans)
    except ValueError:
        return 2.0


# =========================================================================
# FILE PICKER MODE LIST (PENGGANTI MODE EXPLORER/DIALOG GUI TKINTER)
# Aman dipakai di Google Colab / Server Linux tanpa layar (headless).
#
# CARA PAKAI:
#   - Folder ditandai [<nomor>]  -> ketik ANGKA folder itu untuk MASUK
#                                    (misal ketik "1" untuk masuk folder [1])
#   - Berkas (file) juga ditandai [<nomor>], LANJUTAN dari nomor folder
#     -> ketik SATU angka untuk pilih SATU berkas (misal "3")
#     -> ketik BEBERAPA angka dipisah koma untuk pilih BEBERAPA berkas
#        (misal "3,5,7" atau "1,2,3,5,7")
#   - Ketik "F1" -> pilih SEMUA berkas yang cocok di folder yang sedang dibuka
#   - Ketik "u"  -> naik satu folder ke folder induk (parent)
#   - Ketik "c"  -> pilih FOLDER yang sedang dibuka saat ini (mode folder)
#   - Ketik "p"  -> ketik/tempel path lengkap secara manual (anti salah ketik)
#   - Ketik "0"  -> batal
#
# Folder default saat picker dibuka LANGSUNG menuju root Google Drive
# (menu drive awal / My Drive) kalau lingkungan ini terdeteksi ter-mount
# Google Drive (mis. Google Colab) -- lihat _default_browse_start_dir().
# =========================================================================
def _default_browse_start_dir():
    """
    Folder awal default untuk file picker MODE LIST. Kalau Google Drive
    terdeteksi ter-mount di lingkungan ini (Google Colab), langsung arahkan
    ke root Drive ("menu drive awal") supaya user tidak perlu navigasi
    manual dari direktori kerja script. Kalau tidak terdeteksi, fallback
    ke direktori kerja saat ini seperti biasa.
    """
    gdrive_candidates = [
        "/content/drive/MyDrive",
        "/content/drive/My Drive",
        "/content/drive",
    ]
    for path in gdrive_candidates:
        if os.path.isdir(path):
            return path
    return os.getcwd()


def _list_dir_entries(current_dir, ext_filter=None):
    try:
        entries = os.listdir(current_dir)
    except Exception:
        entries = []
    folders = sorted(
        [e for e in entries if os.path.isdir(os.path.join(current_dir, e))],
        key=str.lower
    )
    if ext_filter:
        files = sorted(
            [e for e in entries if os.path.isfile(os.path.join(current_dir, e))
             and os.path.splitext(e)[1].lower() in ext_filter],
            key=str.lower
        )
    else:
        files = sorted(
            [e for e in entries if os.path.isfile(os.path.join(current_dir, e))],
            key=str.lower
        )
    return folders, files


def _browse_and_select(title, mode="files", ext_filter=None, multi=True, start_dir=None):
    current_dir = os.path.abspath(start_dir or _default_browse_start_dir())

    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        folders, files = _list_dir_entries(current_dir, ext_filter if mode == "files" else None)

        print("=========================================================")
        print(f" {title}  (MODE LIST - Bukan Explorer)")
        print("=========================================================")
        print(f" Lokasi saat ini: {current_dir}\n")

        for i, f in enumerate(folders):
            print(f"  [{i + 1}] \U0001F4C1 {f}")

        if mode == "files":
            if files:
                print("\n Berkas yang cocok di folder ini:")
                for j, f in enumerate(files):
                    print(f"  [{len(folders) + j + 1}] \U0001F3B5 {f}")
            if not folders and not files:
                print("  (Folder ini kosong / tidak ada berkas yang cocok)")

        print("\n---------------------------------------------------------")
        if mode == "folder":
            print(" [c]  Pilih folder INI sebagai folder terpilih")
        if mode == "files":
            print(" [F1] Pilih SEMUA berkas yang cocok di folder ini")
        print(" [u]  Naik satu folder ke atas (parent)")
        print(" [p]  Ketik path lengkap secara manual (hindari salah ketik)")
        print(" [0]  Batal")
        print("---------------------------------------------------------")

        if mode == "files":
            hint = ("Ketik ANGKA untuk pilih (misal 1 atau 1,2,3,5,7),\n"
                    "nomor folder = MASUK folder, nomor berkas = PILIH berkas,\n"
                    "atau F1 untuk pilih SEMUA berkas yang cocok di folder ini")
        else:
            hint = "Ketik ANGKA folder untuk MASUK (misal 1), atau c untuk PILIH folder ini"

        raw = input(f"\n{hint}\n>> ").strip()

        if raw == "0" or not raw:
            return None

        if raw.lower() == "u":
            parent = os.path.dirname(current_dir)
            if parent and parent != current_dir:
                current_dir = parent
            continue

        if raw.lower() == "p":
            manual = input("Masukkan path lengkap: ").strip().strip('"').strip("'")
            if not manual:
                continue
            manual = os.path.abspath(os.path.expanduser(manual))
            if os.path.isdir(manual):
                current_dir = manual
                if mode == "folder":
                    confirm = input(f"Gunakan folder '{manual}' ? (Y/N) [Default: Y]: ").strip().lower()
                    if confirm != "n":
                        return manual
                continue
            elif mode == "files" and os.path.isfile(manual):
                return [manual]
            else:
                print("Path tidak ditemukan.")
                time.sleep(1.5)
                continue

        if mode == "folder" and raw.lower() == "c":
            return current_dir

        if mode == "files" and raw.lower() == "f1":
            if files:
                return [os.path.join(current_dir, f) for f in files]
            else:
                print("Tidak ada berkas yang cocok di folder ini.")
                time.sleep(1.2)
                continue

        if raw.isdigit():
            idx = int(raw) - 1
            if 0 <= idx < len(folders):
                current_dir = os.path.join(current_dir, folders[idx])
                continue
            if mode == "files" and len(folders) <= idx < len(folders) + len(files):
                file_idx = idx - len(folders)
                return [os.path.join(current_dir, files[file_idx])]
            print("Nomor tidak valid.")
            time.sleep(1.2)
            continue

        if mode == "files" and "," in raw:
            parts = [p.strip() for p in raw.split(",") if p.strip()]
            if parts and all(p.isdigit() for p in parts):
                selected = []
                invalid = []
                for p in parts:
                    idx = int(p) - 1
                    if len(folders) <= idx < len(folders) + len(files):
                        selected.append(os.path.join(current_dir, files[idx - len(folders)]))
                    else:
                        invalid.append(p)
                if invalid:
                    print(f"Nomor tidak valid (diabaikan): {', '.join(invalid)}")
                    time.sleep(1.5)
                if selected:
                    # buang duplikat sambil menjaga urutan
                    seen = set()
                    unique_selected = []
                    for s in selected:
                        if s not in seen:
                            seen.add(s)
                            unique_selected.append(s)
                    return unique_selected
                continue

        print("Input tidak dikenali, silakan coba lagi.")
        time.sleep(1.2)


def select_media_files():
    result = _browse_and_select(
        "PILIH MEDIA VISUAL UTAMA (Gambar / Video) - BOLEH LEBIH DARI 1 FILE",
        mode="files",
        ext_filter=[".jpg", ".jpeg", ".png", ".webp", ".gif", ".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v"],
        multi=True
    )
    return result if result else []


def select_audio_files():
    result = _browse_and_select(
        "PILIH MUSIK AUDIO",
        mode="files",
        ext_filter=[".mp3", ".wav", ".m4a", ".flac", ".ogg", ".wma"],
        multi=True
    )
    return result if result else []


def select_output_folder():
    result = _browse_and_select(
        "PILIH FOLDER OUTPUT RENDER",
        mode="folder"
    )
    return result if result else ""


def get_media_duration(file_path):
    if not os.path.exists(file_path):
        return 0
    try:
        args = ["-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", file_path]
        output = get_process_output(config.FFprobe, args).strip()
        match = re.search(r'(\d+(\.\d+)?)', output)
        if match:
            return float(match.group(1))
    except Exception as e:
        write_error_log(f"Gagal membaca durasi media {file_path}: {str(e)}")
    return 0