# --- START OF FILE renderer.py ---

import os
import sys
import time
import re
import gc
import copy
import shutil
import subprocess
import traceback
import config
import utils
import queue_manager
import audio_fx


def safe_input(prompt=""):
    try:
        return input(prompt)
    except (EOFError, OSError):
        time.sleep(1)
        return ""


def get_optimal_filter_threads():
    """
    PERBAIKAN BUG UTAMA FPS/SPEED DROP DI CPU LEMAH (mis. Xeon single-core Colab):
    Sebelumnya filter_threads/filter_complex_threads di-HARDCODE ke 8/16 dengan niat
    "mendorong speed maksimal". Ini keliru total di mesin dengan core sedikit (1-2
    vCPU seperti Colab free): memaksa 16 thread berebut 1 core fisik menimbulkan
    overhead context-switching & thread scheduling yang JAUH lebih besar daripada
    core yang benar-benar tersedia -> filter graph (scale/crop/zoompan/overlay/
    boxblur/drawtext, yang semuanya jalan di CPU) jadi lebih LAMBAT, bukan lebih
    cepat, walau encoder-nya sendiri (NVENC di GPU) sudah cepat.

    Solusi: deteksi jumlah core CPU ASLI mesin (os.cpu_count()) dan pakai itu,
    dibatasi wajar 1-16, alih-alih angka tetap yang bisa jauh melebihi core nyata.
    """
    try:
        cores = os.cpu_count() or 2
    except Exception:
        cores = 2
    return str(max(1, min(16, cores)))


def get_youtube_live_bitrate_kbps(res_w, res_h, fps):
    """
    Rekomendasi bitrate video (kbps) mengikuti panduan resmi YouTube Live
    (H.264, standard frame rate 30 vs high frame rate >30).
    """
    high_fps = fps > 30
    long_edge = max(res_w, res_h)

    if long_edge >= 3840:
        return 35000 if high_fps else 25000
    elif long_edge >= 2560:
        return 16000 if high_fps else 12000
    elif long_edge >= 1920:
        return 8500 if high_fps else 6000
    elif long_edge >= 1280:
        return 5500 if high_fps else 4000
    else:
        return 2500


def estimate_video_kbps(res_w, res_h, fps, quality_mode="CBR", quality_level="med"):
    """
    Perkiraan bitrate video (kbps) untuk kombinasi mode+level kualitas tertentu,
    SEBELUM render dijalankan. Dipakai wizard untuk menampilkan estimasi ukuran
    file ke user sebelum dia menekan konfirmasi.

    - CBR: akurat, karena bitrate video memang dipatok konstan (-b:v/-minrate/
      -maxrate sama persis dengan angka ini saat render).
    - CRF: hanya PERKIRAAN. CRF menjaga kualitas visual konstan, bukan bitrate,
      jadi bitrate asli nanti bisa naik/turun tergantung kompleksitas konten
      (adegan ramai/detail = bitrate lebih tinggi, adegan statis = lebih rendah).
      Perkiraan di sini pakai aturan umum encoding H.264: tiap CRF naik/turun
      6 poin, bitrate kira-kira dibagi/dikali 2, dihitung dari titik acuan
      CRF 23 (setara CBR "Sedang").
    """
    preset_group = config.QUALITY_PRESETS.get(quality_mode, config.QUALITY_PRESETS["CBR"])
    preset_info = preset_group.get(quality_level, preset_group.get("med"))
    base_bitrate = get_youtube_live_bitrate_kbps(res_w, res_h, fps)

    if quality_mode == "CRF":
        crf_val = preset_info.get("crf", 23)
        video_kbps = base_bitrate * (2 ** ((23 - crf_val) / 6.0))
    else:
        factor = preset_info.get("bitrate_factor", 1.0)
        video_kbps = base_bitrate * factor

    return max(200.0, float(video_kbps))


def estimate_output_size_mb(duration_sec, res_w, res_h, fps, quality_mode="CBR", quality_level="med"):
    """
    Estimasi total ukuran file output (MB) = (bitrate video + bitrate audio) x durasi.
    Audio selalu AAC 128kbps tetap (lihat get_youtube_live_audio_args).
    Return: (size_mb, video_kbps)
    """
    duration_sec = max(0.1, float(duration_sec))
    video_kbps = estimate_video_kbps(res_w, res_h, fps, quality_mode, quality_level)
    audio_kbps = 128.0
    total_kbps = video_kbps + audio_kbps
    size_mb = (total_kbps * duration_sec) / 8.0 / 1024.0
    return size_mb, video_kbps


def get_optimal_encoder(fps=30, res_w=1280, res_h=720, quality_mode="CBR", quality_level="med"):
    """
    Mengembalikan argumen OUTPUT ENCODER GPU dari config.STATE.
    SANGAT KETAT & DIOPTIMALKAN UNTUK KECEPATAN (FPS MAKSIMAL).

    quality_mode: "CBR" (bitrate tetap, ukuran file pasti) atau
                  "CRF" (kualitas tetap, ukuran file menyesuaikan konten).
    quality_level: "very_low" / "low" / "med" / "high" (lihat config.QUALITY_PRESETS).
    """
    gop = max(2, int(round(fps))) * 2
    vcodec = config.STATE.get("ffmpeg_vcodec", "libx264")
    vendor = config.STATE.get("gpu_vendor", "").upper()

    preset_group = config.QUALITY_PRESETS.get(quality_mode, config.QUALITY_PRESETS["CBR"])
    preset_info = preset_group.get(quality_level, preset_group.get("med"))

    # ================= MODE CRF (Kualitas Konstan) =================
    if quality_mode == "CRF":
        crf_val = preset_info.get("crf", 23)
        cq_val = preset_info.get("cq", 23)

        # 1. NVIDIA NVENC -> mode setara CRF adalah VBR + Constant Quality (-cq)
        if vcodec == "h264_nvenc" and vendor == "NVIDIA":
            return [
                "-c:v", "h264_nvenc",
                "-preset", "p1",
                "-tune", "ll",
                "-rc", "vbr",
                "-cq", str(cq_val),
                "-b:v", "0",
                "-g", str(gop),
                "-keyint_min", str(gop),
                "-no-scenecut", "1",
                "-forced-idr", "1",
                "-spatial-aq", "1",
                "-temporal-aq", "1",
                "-rc-lookahead", "10",
                "-surfaces", "16",
                "-pix_fmt", "yuv420p"
            ]

        # 2. INTEL QUICKSYNC (QSV) -> mode setara CRF adalah ICQ (-global_quality)
        if vcodec == "h264_qsv" or vendor == "INTEL":
            return [
                "-c:v", "h264_qsv",
                "-preset", "veryfast",
                "-global_quality", str(cq_val),
                "-look_ahead", "0",
                "-g", str(gop),
                "-keyint_min", str(gop),
                "-pix_fmt", "nv12"
            ]

        # 3. ANDROID MEDIACODEC (Termux/HP) -> hardware encoder Android tidak
        #    punya mode "kualitas konstan" asli seperti CRF/CQ desktop, jadi
        #    dipakai bitrate konstan setara (dihitung dari estimate_video_kbps
        #    mode CRF -- fungsi itu sudah mengonversi nilai CRF ke perkiraan
        #    kbps yang setara). Jauh lebih baik daripada diam-diam fallback
        #    ke CPU seperti sebelumnya (lihat PERBAIKAN BUG di bawah).
        if vcodec == "h264_mediacodec" or vendor == "ANDROID":
            mc_bitrate = int(round(estimate_video_kbps(res_w, res_h, fps, "CRF", quality_level)))
            return [
                "-c:v", "h264_mediacodec",
                "-b:v", f"{mc_bitrate}k",
                "-g", str(gop),
                "-pix_fmt", "nv12"
            ]

        # 4. FALLBACK CPU (libx264) -> CRF asli
        return [
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-tune", "zerolatency",
            "-crf", str(crf_val),
            "-g", str(gop),
            "-keyint_min", str(gop),
            "-sc_threshold", "0",
            "-threads", "0",
            "-pix_fmt", "yuv420p"
        ]

    # ================= MODE CBR (Bitrate Konstan, default lama) =================
    bitrate = int(round(estimate_video_kbps(res_w, res_h, fps, "CBR", quality_level)))
    bufsize = bitrate * 2

    # 1. NVIDIA NVENC (Hanya jika benar-benar didukung hardware NVIDIA)
    if vcodec == "h264_nvenc" and vendor == "NVIDIA":
        return [
            "-c:v", "h264_nvenc",
            "-preset", "p1",     # MAX SPEED P1
            "-tune", "ll",       # Low Latency
            "-rc", "cbr",
            "-b:v", f"{bitrate}k",
            "-minrate", f"{bitrate}k",
            "-maxrate", f"{bitrate}k",
            "-bufsize", f"{bufsize}k",
            "-g", str(gop),
            "-keyint_min", str(gop),
            "-no-scenecut", "1",
            "-forced-idr", "1",
            "-spatial-aq", "1",
            "-temporal-aq", "1",
            "-rc-lookahead", "10",
            "-surfaces", "16",
            "-pix_fmt", "yuv420p"
        ]

    # 2. INTEL QUICKSYNC (QSV) - Cocok untuk Intel HD Graphics
    if vcodec == "h264_qsv" or vendor == "INTEL":
        return [
            "-c:v", "h264_qsv",
            "-preset", "veryfast",
            "-b:v", f"{bitrate}k",
            "-minrate", f"{bitrate}k",
            "-maxrate", f"{bitrate}k",
            "-bufsize", f"{bufsize}k",
            "-g", str(gop),
            "-keyint_min", str(gop),
            "-pix_fmt", "nv12"
        ]

    # 3. ANDROID MEDIACODEC (Termux/HP)
    # PERBAIKAN BUG: sebelumnya fungsi ini HANYA mengenali vendor "NVIDIA"
    # dan "INTEL" -- vendor "ANDROID" (diset config.py saat MediaCodec
    # terdeteksi) tidak pernah cocok di kondisi manapun, sehingga kode
    # selalu jatuh diam-diam ke fallback CPU (libx264) di bawah, walau
    # config.STATE sudah benar menandai MediaCodec aktif. Akibatnya fitur
    # "hemat baterai & lebih cepat" MediaCodec yang diklaim README TIDAK
    # PERNAH benar-benar terpakai saat render sungguhan. Cabang ini
    # menambahkan penanganan eksplisit supaya h264_mediacodec benar-benar
    # dipakai saat vendor == "ANDROID".
    if vcodec == "h264_mediacodec" or vendor == "ANDROID":
        return [
            "-c:v", "h264_mediacodec",
            "-b:v", f"{bitrate}k",
            "-g", str(gop),
            "-pix_fmt", "nv12"
        ]

    # 4. FALLBACK CPU (libx264) - 100% Bebas Crash nvcuda.dll
    return [
        "-c:v", "libx264",
        "-preset", "ultrafast",
        "-tune", "zerolatency",
        "-b:v", f"{bitrate}k",
        "-minrate", f"{bitrate}k",
        "-maxrate", f"{bitrate}k",
        "-bufsize", f"{bufsize}k",
        "-g", str(gop),
        "-keyint_min", str(gop),
        "-sc_threshold", "0",
        "-threads", "0",
        "-pix_fmt", "yuv420p"
    ]


def get_youtube_live_audio_args():
    return ["-c:a", "aac", "-profile:a", "aac_low", "-b:a", "128k", "-ar", "44100", "-ac", "2"]


def get_gpu_input_hwaccel_flags():
    if config.STATE.get("gpu_accelerator_active") and config.STATE.get("ffmpeg_hw_input_args"):
        return config.STATE["ffmpeg_hw_input_args"]
    return []


# =====================================================================
# PERBAIKAN: fitur distribusi multi-visual (mediaMode: equalSplit / perTrack)
# DIHAPUS TOTAL. Fitur itu menyusun banyak file visual jadi satu timeline
# lewat concat, dan concat itulah sumber bug "macet permanen di titik
# sambung" (1 visual selalu aman, 2+ visual selalu rawan). Sekarang hanya
# task["media"][0] yang pernah dipakai -- input sudah dibatasi maksimal
# satu file dari sisi wizard (utils.select_media_files).
# =====================================================================

def _is_picture_file(path):
    return os.path.splitext(str(path))[1].lower() in [".jpg", ".jpeg", ".png", ".webp"]


def _run_ffmpeg_silent(args):
    try:
        result = subprocess.run([config.FFmpeg] + args, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        return result.returncode == 0
    except Exception:
        return False


# PERBAIKAN: registry temp file per-task, dibersihkan TERJAMIN lewat finally
# di render_task_pipeline() -- termasuk saat task CRASH di tengah jalan
# (exception apa pun), bukan cuma saat proses sampai selesai dengan normal
# seperti sebelumnya. Ini yang bikin temp file numpuk kalau ada error/crash
# di tengah render (sebelumnya cleanup manual di akhir fungsi tidak pernah
# tercapai kalau ada exception duluan).
_ACTIVE_TASK_TEMP_FILES = []


def _register_temp_file(path):
    """Daftarkan 1 path temp supaya otomatis dihapus di akhir task (lihat render_task_pipeline)."""
    _ACTIVE_TASK_TEMP_FILES.append(path)
    return path


def _prerender_overlay_alpha_once(f_path, ov_opacity, ov_shear_x, ov_shear_y, res_w, res_h, gpu_hw_flags,
                                   ov_chroma_key="black", is_picture=False):
    """
    PERBAIKAN FPS/KECEPATAN RENDER TURUN pada Overlay Video/Gambar:
    Sebelumnya, proses "chroma/alpha key" (masking transparansi) overlay
    dijalankan ULANG setiap frame sepanjang SELURUH durasi render tiap kali
    overlay non-loop (mode "standar") dipakai -> filter colorkey/scale/crop
    dihitung ulang untuk SETIAP frame video overlay tsb, berbarengan dengan
    decode hardware video utama -> beban CPU/GPU menumpuk & FPS ambruk
    (sebelumnya optimasi "bakar sekali" ini HANYA berlaku utk overlay yg
    di-set Loop, overlay mode standar tetap dihitung per-frame -> ini akar
    masalah FPS turun/crash saat overlay video diaktifkan).

    Sekarang SEMUA overlay (video loop, video standar, MAUPUN gambar) selalu
    dibakar (baked) SEKALI di sini: crop/scale/shear/chroma-key dihitung
    sekali saja, disimpan sebagai file RGBA lossless di TempFolder. Pipeline
    utama nanti tinggal memutar/mengulang file hasil ini sebagai video biasa
    (tanpa colorkey/scale/crop lagi) -> FPS & kecepatan render normal
    kembali baik untuk overlay video standar maupun loop.

    - is_picture=False (video): source didekode utuh SEKALI (tanpa trim/loop),
      disimpan sebagai .mkv (utvideo, lossless, multi-thread, cepat).
    - is_picture=True (gambar): dibakar jadi SATU frame PNG (lossless,
      mendukung alpha), lalu di pipeline utama tinggal di-"panjangkan"
      (-loop 1 -framerate 1) sepanjang durasi render, hanya 1x proses render
      gambar seperti diminta (tidak perlu decode/filter berulang).

    Chroma Key (ov_chroma_key: black/green/blue/red) membuang warna latar
    solid overlay (studio hitam/hijau/merah/biru) supaya jadi alpha/transparan
    saat ditumpuk di atas visual utama.

    Return: path file hasil jika berhasil, None jika gagal (fallback otomatis
    ke metode lama / masking inline per-frame).
    """
    try:
        chroma_filter = utils.get_chroma_key_filter_chain(ov_chroma_key)

        mtime = int(os.path.getmtime(f_path)) if os.path.exists(f_path) else 0
        raw_key = f"{os.path.basename(f_path)}_{mtime}_{res_w}x{res_h}_{round(float(ov_opacity), 2)}_{round(float(ov_shear_x), 2)}_{round(float(ov_shear_y), 2)}_{ov_chroma_key}_{'img' if is_picture else 'vid'}"
        cache_key = re.sub(r'[^A-Za-z0-9_.-]', '_', raw_key)
        ext_out = "png" if is_picture else "mkv"
        out_path = os.path.join(config.TempFolder, f"prealpha_{cache_key}.{ext_out}")

        if os.path.exists(out_path) and os.path.getsize(out_path) > 0:
            return out_path

        shear_filter = ""
        if ov_shear_x != 0.0 or ov_shear_y != 0.0:
            shear_filter = f",shear=shx={ov_shear_x}:shy={ov_shear_y}:fillcolor=0x00000000"

        vf_chain = (
            f"scale={res_w}:{res_h}:force_original_aspect_ratio=increase,crop={res_w}:{res_h},"
            f"format=rgba{shear_filter},"
            f"{chroma_filter},colorchannelmixer=aa={ov_opacity}"
        )

        if is_picture:
            # Gambar: cukup 1x decode + 1x proses filter -> 1 frame PNG lossless.
            args = ["-y", "-loop", "1", "-framerate", "1", "-t", "1", "-i", f_path,
                    "-filter_complex", vf_chain,
                    "-frames:v", "1", "-c:v", "png", out_path]
            ok = _run_ffmpeg_silent(args)
        else:
            # PERBAIKAN: qtrle (lambat, single-threaded) -> utvideo (lossless native
            # FFmpeg, jauh lebih cepat & mendukung multithreading), lihat penjelasan
            # yang sama di spectrum_generator.generate_custom_spectrum_gpu.
            #
            # PERCEPATAN CPU SINGLE-CORE: decode file sumber overlay sekarang ikut
            # pakai gpu_hw_flags (NVDEC/-hwaccel cuda) kalau GPU aktif -- sebelumnya
            # parameter ini diterima fungsi tapi TIDAK PERNAH dipakai (selalu decode
            # software walau GPU nganggur). File sumber overlay itu video biasa
            # (H.264/H.265/dll, BUKAN UTVideo), jadi aman didekode NVDEC -- beda
            # kasus dengan pembacaan file HASIL bake (UTVideo) yang memang tidak
            # didukung NVDEC (lihat catatan di custom_spec_input_indices di bawah).
            # Filter (split/alphamerge/scale/crop) & encode (utvideo) tetap CPU
            # (tidak ada versi GPU-nya di ffmpeg), tapi beban decode -- biasanya
            # porsi terbesar utk video terkompresi -- pindah ke GPU, jadi CPU
            # single-core kamu gak morat-marit walau overlay-nya loop / lebih dari
            # satu.
            args = (
                ["-y"] + gpu_hw_flags + ["-thread_queue_size", "16384", "-i", f_path,
                "-filter_complex", vf_chain,
                "-c:v", "utvideo", "-pix_fmt", "bgra", "-threads", "0", "-an", out_path]
            )
            src_dur = utils.get_media_duration(f_path)
            if not src_dur or src_dur <= 0: src_dur = 5.0
            ok = utils.run_ffmpeg_with_progress(config.FFmpeg, args, src_dur)
            if not ok and gpu_hw_flags:
                # Fallback: kalau decode GPU gagal (mis. codec sumber tidak
                # didukung NVDEC di GPU Colab yang sedang aktif), ulangi TANPA
                # hwaccel supaya baking tetap jalan (CPU, seperti sebelumnya)
                # daripada overlay gagal total/hilang.
                args_cpu = [
                    "-y", "-thread_queue_size", "16384", "-i", f_path,
                    "-filter_complex", vf_chain,
                    "-c:v", "utvideo", "-pix_fmt", "bgra", "-threads", "0", "-an", out_path
                ]
                ok = utils.run_ffmpeg_with_progress(config.FFmpeg, args_cpu, src_dur)

        if ok and os.path.exists(out_path) and os.path.getsize(out_path) > 0:
            return out_path
    except Exception as e:
        utils.write_error_log(f"Gagal pra-render alpha overlay '{f_path}': {str(e)}")
    return None


def get_ffmpeg_font_file(font_family, bold, italic):
    font_map = {
        "Arial": {"bi": "arialbi.ttf", "b": "arialbd.ttf", "i": "ariali.ttf", "r": "arial.ttf"},
        "Courier New": {"bi": "courbi.ttf", "b": "courbd.ttf", "i": "couri.ttf", "r": "cour.ttf"},
        "Georgia": {"bi": "georgiaz.ttf", "b": "georgiab.ttf", "i": "georgiai.ttf", "r": "georgia.ttf"},
        "Impact": {"bi": "impact.ttf", "b": "impact.ttf", "i": "impact.ttf", "r": "impact.ttf"},
        "Times New Roman": {"bi": "timesbi.ttf", "b": "timesbd.ttf", "i": "timesi.ttf", "r": "times.ttf"},
        "Verdana": {"bi": "verdanaz.ttf", "b": "verdanab.ttf", "i": "verdanai.ttf", "r": "verdana.ttf"},
        "Comic Sans MS": {"bi": "comicz.ttf", "b": "comicbd.ttf", "i": "comici.ttf", "r": "comic.ttf"},
        "Segoe UI": {"bi": "segoeuiz.ttf", "b": "segoeuib.ttf", "i": "segoeuii.ttf", "r": "segoeui.ttf"},
        "Century Gothic": {"bi": "gothicbi.ttf", "b": "gothicb.ttf", "i": "gothici.ttf", "r": "gothic.ttf"},
        "Garamond": {"bi": "garabd.ttf", "b": "garabd.ttf", "i": "garait.ttf", "r": "gara.ttf"}
    }
    
    style = "r"
    if bold and italic:
        style = "bi"
    elif bold:
        style = "b"
    elif italic:
        style = "i"
        
    font_name = font_map.get(font_family, {}).get(style, "arial.ttf")
    return "C\\:/Windows/Fonts/" + font_name


def get_ffmpeg_drawtext_filter(font_config, escaped_text, enable_expr, delay=0.0):
    if font_config == "none" or not font_config or not isinstance(font_config, dict):
        return ""
        
    f_file = get_ffmpeg_font_file(font_config.get("fontFamily", "Arial"), font_config.get("bold", False), font_config.get("italic", False))
    f_size = font_config.get("fontSize", 32)
    f_color = font_config.get("fontColor", "white")
    f_op = font_config.get("opacity", 1.0)
    base_x = font_config.get("x", "(w-tw)/2")
    base_y = font_config.get("y", "(h-th)/2")
    a_dur = font_config.get("animDur", 1.0)
    d_dur = font_config.get("displayDur", 5.0)
    a_type = int(font_config.get("animType", 1))

    t_var = f"(t-{delay})" if delay > 0 else "t"
    
    if delay > 0:
        alpha_expr = f"if(lt(t,{delay}),0,if(lt({t_var},{a_dur}),({t_var}/{a_dur})*{f_op},if(lt({t_var},{d_dur}),{f_op},max(0,{f_op}*(1-({t_var}-{d_dur})/{a_dur})))))"
    else:
        alpha_expr = f"if(lt({t_var},{a_dur}),({t_var}/{a_dur})*{f_op},if(lt({t_var},{d_dur}),{f_op},max(0,{f_op}*(1-({t_var}-{d_dur})/{a_dur}))))"

    x_expr = base_x
    y_expr = base_y
    size_expr = str(f_size)

    if a_type == 2:
        y_expr = f"if(lt({t_var},{a_dur}),{base_y}+(1-{t_var}/{a_dur})*50,{base_y})"
        if delay > 0: y_expr = f"if(lt(t,{delay}),{base_y}+50,{y_expr})"
    elif a_type == 3:
        x_expr = f"if(lt({t_var},{a_dur}),{base_x}+(1-{t_var}/{a_dur})*150,{base_x})"
        if delay > 0: x_expr = f"if(lt(t,{delay}),{base_x}+150,{x_expr})"
    elif a_type == 4:
        size_expr = f"if(lt({t_var},{a_dur}),{f_size}*(0.5+0.5*({t_var}/{a_dur})),{f_size})"
        if delay > 0: size_expr = f"if(lt(t,{delay}),{f_size}*0.5,{size_expr})"

    stroke_expr = ""
    if font_config.get("useStroke", False):
        s_col = font_config.get("strokeColor", "black")
        s_width = font_config.get("strokeWidth", 2)
        stroke_expr = f":borderw={s_width}:bordercolor={s_col}"

    return f"drawtext=fontfile='{f_file}':text='{escaped_text}':fontsize='{size_expr}':fontcolor={f_color}:alpha='{alpha_expr}':x='{x_expr}':y='{y_expr}':enable='{enable_expr}'{stroke_expr}"


def concat_audios(audio_list, output_path, run_id=None):
    print("Menggabungkan berkas audio...")
    if not audio_list:
        return
        
    N = len(audio_list)
    suffix = f"_{run_id}" if run_id else ""
    ffmpeg_audio_err = _register_temp_file(os.path.join(config.TempFolder, f"ffmpeg_audio_err{suffix}.txt"))
    
    if N == 1:
        args = ["-y", "-thread_queue_size", "16384", "-vn", "-i", audio_list[0], "-c:a", "pcm_s16le", "-threads", "0", output_path]
        success = utils.run_ffmpeg_silent(args, ffmpeg_audio_err)
        if not success:
            utils.write_detailed_ffmpeg_error("Penggabungan Audio", ffmpeg_audio_err)
    else:
        inputs = []
        for file in audio_list:
            inputs += ["-thread_queue_size", "16384", "-vn", "-i", file]
            
        filters = []
        last_a = "[0:a]"
        transition_duration = 2.0
        
        for i in range(N - 1):
            next_a = f"[{i + 1}:a]"
            out_a = f"[a{i + 1}]"
            if i == (N - 2):
                out_a = "[outa]"
            filters.append(f"{last_a}{next_a}acrossfade=d={transition_duration}:c1=qsin:c2=qsin{out_a}")
            last_a = out_a
            
        filter_graph = ";".join(filters)
        args = ["-y"] + inputs + ["-filter_complex", filter_graph, "-map", "[outa]", "-vn", "-c:a", "pcm_s16le", "-threads", "0", output_path]
        
        success = utils.run_ffmpeg_silent(args, ffmpeg_audio_err)
        if not success:
            utils.write_detailed_ffmpeg_error("Penggabungan Audio", ffmpeg_audio_err)


def build_multi_visual_source(media_list, audio_duration, fps, res_w, res_h, gpu_hw_flags, run_id=None):
    """
    Menggabungkan LEBIH DARI 1 file visual (gambar dan/atau video) yang dipilih
    user menjadi SATU file video utuh berdurasi 1 siklus audio (audio_duration),
    lewat proses ffmpeg TERPISAH & HANYA SEKALI -- persis pola yang sama dengan
    concat_audios() untuk audio. File hasil gabungan ini nanti dipakai pipeline
    utama sebagai "sample_media" biasa (di-loop -stream_loop -1 apa adanya),
    JADI TIDAK ADA lagi timeline/concat yang dibangun di dalam filter_complex
    pipeline render utama.

    CATATAN PENTING soal bug lama "macet permanen di titik sambung" (lihat
    komentar historis di _render_task_pipeline_inner / _concat_clips_copy):
    bug tsb berasal dari concat STREAM-COPY (tanpa re-encode, rawan mismatch
    keyframe/timestamp antar segmen). Fungsi ini TIDAK memakai stream-copy --
    setiap sumber didekode penuh lalu dinormalisasi (scale/crop/fps/pix_fmt)
    sebelum digabung lewat filter "concat" (re-encode, sama seperti pendekatan
    acrossfade di concat_audios) -- sehingga aman dipakai untuk 2+ visual.

    Aturan durasi (sesuai permintaan):
    - Video  : diputar apa adanya (durasi asli masing-masing), berurutan sesuai
               urutan file dipilih user.
    - Gambar : sisa durasi audio (audio_duration dikurangi total durasi semua
               video dalam daftar) dibagi rata ke seluruh gambar. Kalau hanya
               gambar (tanpa video sama sekali) ini otomatis sama dengan
               audio_duration / jumlah_gambar.
    Hasil gabungan (durasi ~= audio_duration) ini yang nanti di-LOOP oleh
    pipeline utama (-stream_loop -1) supaya tetap mengikuti "loops" (opsi
    pengulangan) yang sudah ada -- sama seperti 1 file video biasa.

    Return: path file hasil gabungan, atau file pertama di media_list sebagai
    fallback kalau proses gagal / cuma ada 1 file valid.
    """
    valid_media = [m for m in media_list if m and os.path.exists(m)]
    if len(valid_media) <= 1:
        return valid_media[0] if valid_media else None

    try:
        cache_parts = []
        for m in valid_media:
            try:
                mtime = int(os.path.getmtime(m))
            except Exception:
                mtime = 0
            cache_parts.append(f"{os.path.basename(m)}_{mtime}")
        raw_key = "_".join(cache_parts) + f"_{round(float(audio_duration), 2)}_{fps}_{res_w}x{res_h}"
        cache_key = re.sub(r'[^A-Za-z0-9_.-]', '_', raw_key)
        out_path = os.path.join(config.TempFolder, f"multivisual_{cache_key}.mkv")

        if os.path.exists(out_path) and os.path.getsize(out_path) > 0:
            return out_path

        images = [m for m in valid_media if _is_picture_file(m)]
        videos = [m for m in valid_media if not _is_picture_file(m)]

        video_total_dur = 0.0
        for v in videos:
            d = utils.get_media_duration(v)
            if d <= 0:
                d = 5.0
            video_total_dur += d

        if images:
            remaining = float(audio_duration) - video_total_dur
            if remaining > 0:
                per_image_dur = remaining / len(images)
            else:
                per_image_dur = max(1.0, float(audio_duration) / len(valid_media))
        else:
            per_image_dur = 0.0

        print(f"Menggabungkan {len(valid_media)} file visual ({len(images)} gambar, {len(videos)} video) menjadi satu sumber utuh...")

        inputs = []
        filters = []
        labels = []
        for idx, m in enumerate(valid_media):
            is_pic = _is_picture_file(m)
            if is_pic:
                inputs += ["-thread_queue_size", "16384", "-loop", "1", "-framerate", str(fps),
                           "-t", str(round(max(0.1, per_image_dur), 3)), "-i", m]
            else:
                # File video didekode software di sini (bukan hwaccel) supaya
                # negosiasi hardware frame antar banyak input sekaligus tidak
                # rawan macet tanpa error (gejala sama seperti kasus UTVideo
                # custom-spectrum di atas) -- proses ini toh cuma sekali jalan.
                inputs += ["-thread_queue_size", "16384", "-i", m]
            lbl = f"[mv{idx}]"
            filters.append(
                f"[{idx}:v]scale={res_w}:{res_h}:force_original_aspect_ratio=increase:eval=init,"
                f"crop={res_w}:{res_h},setsar=1,fps={fps},format=yuv420p,setpts=PTS-STARTPTS{lbl}"
            )
            labels.append(lbl)

        concat_inputs = "".join(labels)
        filters.append(f"{concat_inputs}concat=n={len(valid_media)}:v=1:a=0[outv]")
        filter_graph = ";".join(filters)

        # "-hwaccel none" dipasang eksplisit di sini supaya utils.run_ffmpeg_with_progress
        # TIDAK auto-inject hwaccel-nya sendiri (lihat isinya: hwaccel otomatis
        # ditambahkan kalau "-hwaccel" belum ada di argumen & ada input video).
        # Proses gabung banyak visual sekaligus ini cuma jalan SEKALI per task
        # (hasilnya di-cache), jadi decode software di sini tidak berdampak ke
        # performa render utama, dan menghindari risiko banyak sesi hwaccel
        # dekode berbarengan yang jadi ciri khas bug lama.
        args = ["-y", "-hwaccel", "none"] + inputs + [
            "-filter_complex", filter_graph,
            "-map", "[outv]",
            "-c:v", "utvideo", "-pix_fmt", "yuv420p", "-threads", "0", "-an",
            out_path
        ]

        total_est_dur = video_total_dur + (per_image_dur * len(images))
        if total_est_dur <= 0:
            total_est_dur = float(audio_duration) if audio_duration > 0 else 30.0

        suffix = f"_{run_id}" if run_id else ""
        ffmpeg_err_path = _register_temp_file(os.path.join(config.TempFolder, f"ffmpeg_multivisual_err{suffix}.txt"))
        ok = utils.run_ffmpeg_with_progress(config.FFmpeg, args, total_est_dur, ffmpeg_err_path)

        if ok and os.path.exists(out_path) and os.path.getsize(out_path) > 0:
            return out_path

        utils.write_detailed_ffmpeg_error("Penggabungan Multi-Visual", ffmpeg_err_path)
        print("Gagal menggabungkan multi-visual, memakai file visual pertama saja sebagai fallback.")
    except Exception as e:
        utils.write_error_log(f"Error saat menggabungkan multi-visual: {str(e)}")
        print(f"Gagal menggabungkan multi-visual ({e}), memakai file visual pertama saja sebagai fallback.")

    return valid_media[0]


def render_task_pipeline(task):
    """
    PEMBUNGKUS AMAN: menangkap SEMUA jenis error (ffmpeg maupun murni Python)
    yang terjadi di mana pun sepanjang proses render 1 tugas.

    SEBELUMNYA: error yang bukan dari "ffmpeg exit code != 0" (mis. bug di
    loop pembaca progress, error parsing, dsb) lolos tak tertangani sampai
    ke penangkap paling luar di main.py -- yang cuma mencetak traceback ke
    LAYAR (hilang begitu layar di-clear/scroll, TIDAK PERNAH ditulis ke
    error.txt) dan lebih parah lagi: MENGHENTIKAN SELURUH antrian render,
    padahal cuma 1 tugas yang bermasalah.

    SEKARANG: apa pun eror-nya, traceback LENGKAP-nya selalu ditulis ke
    error.txt (lihat config.ErrorLog) via utils.write_error_log, tugas ini
    ditandai gagal (return False, sesuai kontrak start_queue_render yang
    memang sudah mengharapkan True/False), dan antrian tetap lanjut ke
    tugas berikutnya alih-alih macet total.
    """
    global _ACTIVE_TASK_TEMP_FILES
    _ACTIVE_TASK_TEMP_FILES = []
    try:
        return _render_task_pipeline_inner(task)
    except BaseException as e:
        tb_text = traceback.format_exc()
        try:
            utils.write_error_log(
                f"CRASH SAAT MERENDER TUGAS '{task.get('name', '?')}' "
                f"-> {type(e).__name__}: {e}\n{tb_text}"
            )
        except Exception:
            pass
        print("\n=========================================================")
        print(f"   TUGAS '{task.get('name', '?')}' GAGAL / CRASH SAAT RENDER")
        print("=========================================================")
        try:
            print(f"Detail traceback LENGKAP tersimpan di: {config.ErrorLog}")
        except Exception:
            pass
        print(f"Ringkasan eror: {type(e).__name__}: {e}")
        print("=========================================================\n")
        time.sleep(2)
        return False
    finally:
        # PERBAIKAN: pembersihan temp file dipindah ke sini (bukan cuma di
        # akhir _render_task_pipeline_inner) supaya TERJAMIN jalan apa pun
        # yang terjadi -- sukses, gagal biasa, ATAUPUN crash di tengah jalan.
        # Urutannya juga otomatis benar: baris ini baru jalan SETELAH inner
        # function selesai menulis detail eror (kalau ada) ke error.txt, jadi
        # file log mentahnya tidak keburu terhapus sebelum sempat dibaca.
        for _p in _ACTIVE_TASK_TEMP_FILES:
            if _p and os.path.exists(_p):
                try:
                    os.remove(_p)
                except Exception:
                    pass
        _ACTIVE_TASK_TEMP_FILES = []


def _render_task_pipeline_inner(task):
    os.system('cls' if os.name == 'nt' else 'clear')
    gpu_status = f"GPU: {config.STATE.get('gpu_name')} ({config.STATE.get('gpu_vendor')})"
    print("=========================================")
    print(f"MENJALANKAN RENDERING TUGAS: {task.get('name')}")
    print(f"AKSELERASI HARDWARE: {gpu_status}")
    print("=========================================")

    # PERBAIKAN: run_id UNIK dibuat sekali di sini lalu dipakai konsisten ke
    # SEMUA nama file temp task ini (audio gabungan, error log, file spectrum,
    # dst) -- sebelumnya banyak nama file tetap (mis. "temp_audio_concat.wav")
    # dipakai bersama oleh beberapa modul (renderer.py & spectrum_generator.py
    # sama-sama baca/tulis nama yang SAMA), jadi berisiko tabrakan/ke-timpa
    # kalau ada sisa file dari task sebelumnya yang gagal dibersihkan.
    run_id = f"{int(time.time() * 1000)}_{os.getpid()}"

    temp_task_audio = _register_temp_file(os.path.join(config.TempFolder, f"temp_audio_concat_{run_id}.wav"))
    if os.path.exists(temp_task_audio):
        try: os.remove(temp_task_audio)
        except: pass

    concat_audios(task.get("audio", []), temp_task_audio, run_id=run_id)
    if not os.path.exists(temp_task_audio):
        print("Gagal memproses berkas audio pendukung.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return False

    audio_duration = utils.get_media_duration(temp_task_audio)
    if audio_duration <= 0:
        audio_duration = 30

    # SAMBUNGAN AUDIO FX: terapkan Noise Reduction/Mastering/Efek Ruangan Qari
    # ke audio murottal yang sudah digabung, lalu mix dengan Backsound berlapis
    # (kalau task punya key "murottalFx"/"backsounds"). Aman dipanggil walau
    # task tidak punya setting FX sama sekali -- otomatis fallback ke audio
    # asli tanpa proses tambahan (nol overhead). Hasil akhirnya menimpa
    # temp_task_audio supaya baik ffmpeg utama (di bawah) maupun generator
    # spectrum GPU sama-sama memakai audio final yang sudah di-FX.
    # (dulu pakai run_id KEDUA yang terpisah di sini -- sekarang dikonsolidasi
    # jadi 1 run_id yang sama dengan seluruh temp file task ini)
    audio_fx_temp_files = [
        _register_temp_file(os.path.join(config.TempFolder, f"temp_audio_murottalfx_{run_id}.wav")),
        _register_temp_file(os.path.join(config.TempFolder, f"temp_audio_final_mixed_{run_id}.wav")),
    ]
    try:
        fx_audio_path = audio_fx.process_full_audio_pipeline(
            temp_task_audio, task, audio_duration, run_id=run_id
        )
        if fx_audio_path and os.path.exists(fx_audio_path) and \
                os.path.abspath(fx_audio_path) != os.path.abspath(temp_task_audio):
            shutil.copyfile(fx_audio_path, temp_task_audio)
    except Exception as e:
        print(f"Peringatan: Audio FX gagal diterapkan, memakai audio asli. ({e})")

    # PERBAIKAN: Terapkan jumlah pengulangan (loops) video hasil akhir.
    # Sebelumnya task["loops"] tersimpan di queue tapi tidak pernah dibaca
    # di sini, sehingga input pengulangan dari wizard tidak berpengaruh.
    loop_count = task.get("loops", 1)
    try:
        loop_count = int(loop_count)
    except (TypeError, ValueError):
        loop_count = 1
    if loop_count < 1:
        loop_count = 1

    render_duration = audio_duration * loop_count

    out_dir = task.get("output", config.SCRIPT_ROOT)
    os.makedirs(out_dir, exist_ok=True)

    final_output_name = f"{task.get('name')}.mp4"
    final_output_path = os.path.join(out_dir, final_output_name)

    if os.path.exists(final_output_path):
        try: os.remove(final_output_path)
        except: pass

    # 1. NORMALISASI DAFTAR SPECTRUM (MULTI-SPECTRUM SUPPORT)
    spectrums_list = task.get("spectrums", [])
    if not spectrums_list:
        spec_type_old = task.get("specType", "custom_oscilloscope")
        if spec_type_old != "none":
            spectrums_list = [{
                "specType": spec_type_old,
                "specWidth": utils.ensure_even_int(task.get("specWidth") or 800, min_val=40),
                "specHeight": utils.ensure_even_int(task.get("specHeight") or 300, min_val=20),
                "specX": utils.sanitize_ffmpeg_coord(task.get("specX", "(W-w)/2")),
                "specY": utils.sanitize_ffmpeg_coord(task.get("specY", "H-h-50")),
                "specColor": task.get("specColor", "white"),
                "spectrumOpacity": float(task.get("spectrumOpacity") or 0.8),
                "specBgColor": str(task.get("specBgColor") or "none"),
                "specBgOpacity": float(task.get("specBgOpacity") or 0.5),
                "useBeatZoom": bool(task.get("useBeatZoom", False)),
                "shearX": 0.0,
                "shearY": 0.0
            }]

    # CATATAN: fitur Spectrum Visualizer GPU (GLSL/ModernGL) sudah dihapus dari
    # versi ini. Entri "spectrums" dari queue.json/presets.json versi lama
    # (kalau ada) dilewati saja secara aman di bawah ini -- tidak dirender,
    # tapi juga tidak membuat proses render tugas gagal.
    custom_spec_videos = {}
    temp_custom_files_created = []

    for idx, spec in enumerate(spectrums_list):
        stype = spec.get("specType", "none")
        if stype != "none":
            print(f"[INFO] Spectrum '{stype}' (#{idx+1}) dilewati -- fitur Spectrum Generator "
                  f"tidak tersedia di versi ini.")

    # 2. PERSIAPAN INPUTS FFMPEG
    inputs = []
    gpu_hw_flags = get_gpu_input_hwaccel_flags()

    res_w, res_h = 1280, 720
    resolution = task.get("resolution", "720p")
    if resolution == "1080p":
        res_w, res_h = 1920, 1080
    elif resolution == "2K":
        res_w, res_h = 2560, 1440
    elif resolution == "4K":
        res_w, res_h = 3840, 2160
    fps = int(task.get("fps", 30) or 30)

    # PERBAIKAN: mendukung LEBIH DARI 1 file visual (gambar dan/atau video).
    # Kalau media > 1 file, semuanya digabung TERLEBIH DAHULU lewat proses
    # ffmpeg TERPISAH (build_multi_visual_source, re-encode -- BUKAN
    # stream-copy) menjadi satu file utuh sepanjang 1 siklus audio, baru
    # HASIL GABUNGAN itu yang dipakai sebagai "sample_media" di sini --
    # jadi filter_complex pipeline utama di bawah ini tetap hanya pernah
    # melihat SATU sumber video utuh tanpa sambungan (persis seperti dulu),
    # sehingga kelas bug "macet permanen di titik sambung" pada timeline
    # concat inline tidak berlaku lagi. Kalau media cuma 1 file, tidak ada
    # proses gabung sama sekali (perilaku lama, paling efisien, tetap sama).
    media_list_all = [m for m in task.get("media", []) if m and os.path.exists(m)]
    if not media_list_all:
        print("Media visual utama tidak ditemukan!")
        return False

    is_multi_visual = len(media_list_all) > 1
    if is_multi_visual:
        sample_media = build_multi_visual_source(media_list_all, audio_duration, fps, res_w, res_h, gpu_hw_flags, run_id=run_id)
        # Kalau proses gabung gagal, build_multi_visual_source() sudah fallback
        # ke media_list_all[0] -- tandai is_multi_visual False supaya pipeline
        # di bawah memperlakukannya persis seperti 1 file visual biasa lagi.
        is_multi_visual = bool(sample_media) and sample_media != media_list_all[0]
    else:
        sample_media = media_list_all[0]

    if not sample_media or not os.path.exists(sample_media):
        print("Media visual utama tidak ditemukan / gagal digabungkan!")
        return False

    ext = os.path.splitext(sample_media)[1].lower()
    is_picture = (not is_multi_visual) and (ext in [".jpg", ".jpeg", ".png", ".webp"])

    # sample_media selalu satu sumber video/gambar UTUH tanpa sambungan (baik
    # itu 1 file asli, atau hasil gabungan multi-visual) -- hwaccel decode
    # selalu aman dipakai, tidak perlu genpts tambahan. Perkecualian: hasil
    # gabungan multi-visual dienkode UTVideo (lossless, software-only, lihat
    # build_multi_visual_source) -- NVDEC/hwaccel TIDAK mendukung UTVideo dan
    # bisa macet tanpa error kalau tetap dipaksa, jadi hwaccel dimatikan
    # khusus utk sumber hasil gabungan ini (persis pola yang sama dipakai utk
    # custom_spec_videos di bawah).
    main_input_hw_flags = [] if is_multi_visual else gpu_hw_flags
    main_input_genpts_flags = []

    if is_picture:
        inputs += ["-thread_queue_size", "16384", "-loop", "1", "-framerate", "1", "-t", str(render_duration), "-i", sample_media]
    else:
        inputs += main_input_hw_flags + main_input_genpts_flags + ["-thread_queue_size", "16384", "-stream_loop", "-1", "-an", "-sn", "-i", sample_media]

    if loop_count > 1:
        inputs += ["-thread_queue_size", "16384", "-stream_loop", str(loop_count - 1), "-t", str(render_duration), "-i", temp_task_audio]
    else:
        inputs += ["-thread_queue_size", "16384", "-t", str(render_duration), "-i", temp_task_audio]

    bg_video_overlays = task.get("bgVideoOverlays", [])
    valid_overlays = []
    for ov in bg_video_overlays:
        f_path = str(ov.get("file", "")).strip()
        if f_path and f_path != "none" and os.path.exists(f_path):
            valid_overlays.append(ov)

    overlay_input_start_idx = 2
    prerendered_overlay_indices = {}
    prerendered_overlay_is_picture = {}
    for ov_idx, ov in enumerate(valid_overlays):
        f_path = ov.get("file")
        loop_st = ov.get("loop", False)
        ext_ov = os.path.splitext(f_path)[1].lower()
        ov_is_picture = ext_ov in [".jpg", ".jpeg", ".png", ".webp"]

        # PERBAIKAN UTAMA FPS: dulu hanya overlay VIDEO mode Loop yang di-"bakar"
        # (chroma-key dihitung sekali). Overlay video mode STANDAR (non-loop)
        # menghitung ulang colorkey/scale/crop di SETIAP frame sepanjang video
        # overlay tsb diputar -> penyebab FPS anjlok/crash saat overlay video
        # diaktifkan. Sekarang overlay VIDEO (loop MAUPUN standar) dan GAMBAR
        # semuanya di-bake sekali lewat _prerender_overlay_alpha_once().
        prealpha_path = _prerender_overlay_alpha_once(
            f_path,
            float(ov.get("opacity", 1.0)),
            float(ov.get("shearX", 0.0)),
            float(ov.get("shearY", 0.0)),
            res_w, res_h, gpu_hw_flags,
            str(ov.get("chromaKey", "black")),
            is_picture=ov_is_picture
        )

        if prealpha_path:
            prerendered_overlay_indices[ov_idx] = True
            prerendered_overlay_is_picture[ov_idx] = ov_is_picture
            if ov_is_picture:
                # Gambar: hanya 1x render (dilakukan di atas), lalu diperpanjang
                # (-loop 1 -framerate 1) sepanjang durasi render -- berlaku sama
                # baik loop maupun standar diceklis (gambar statis tidak beda).
                inputs += ["-thread_queue_size", "16384", "-loop", "1", "-framerate", "1", "-t", str(render_duration), "-i", prealpha_path]
            elif loop_st:
                # Video Loop: hasil bake (1 siklus) diputar berulang sampai durasi habis.
                inputs += ["-thread_queue_size", "16384", "-stream_loop", "-1", "-t", str(render_duration), "-i", prealpha_path]
            else:
                # Video Standar: hasil bake diputar SEKALI sampai durasinya habis
                # (tidak di-loop) -- eof_action=pass di overlay filter membuat
                # background utama tetap tampil normal setelah overlay ini selesai.
                inputs += ["-thread_queue_size", "16384", "-t", str(render_duration), "-i", prealpha_path]
        else:
            # Fallback kalau proses bake gagal -> metode lama (masking inline per-frame).
            if ov_is_picture:
                inputs += ["-thread_queue_size", "16384", "-loop", "1", "-framerate", "1", "-t", str(render_duration), "-i", f_path]
            elif loop_st:
                inputs += ["-thread_queue_size", "16384", "-stream_loop", "-1", "-t", str(render_duration), "-i", f_path]
            else:
                inputs += ["-thread_queue_size", "16384", "-t", str(render_duration), "-i", f_path]

    custom_spec_input_indices = {}
    next_input_idx = overlay_input_start_idx + len(valid_overlays)
    for idx in sorted(custom_spec_videos.keys()):
        vid_path = custom_spec_videos[idx]
        # PERBAIKAN: file ini SELALU UTVideo (lossless, dari spectrum_generator.py),
        # dan NVDEC (hwaccel cuda) TIDAK mendukung codec UTVideo sama sekali --
        # sebelumnya baris ini tetap memaksa gpu_hw_flags (-hwaccel cuda) ke sini,
        # padahal utvideo cuma bisa didekode software. Di beberapa kombinasi
        # driver, ini bikin filter graph macet total nunggu negosiasi hardware
        # frame yang tidak pernah selesai untuk codec yang tidak didukung --
        # TANPA error/exit code (persis gejala "macet tanpa error.txt"). File
        # overlay prealpha (juga UTVideo) sudah benar TIDAK pakai hwaccel di
        # sini; sekarang custom spectrum diperlakukan sama (CPU decode -- toh
        # UTVideo ringan didekode, jadi tidak ada penalti performa berarti).
        inputs += ["-thread_queue_size", "16384", "-i", vid_path]
        custom_spec_input_indices[idx] = next_input_idx
        next_input_idx += 1

    # 3. PENYUSUNAN FILTERGRAPH MULTI-LAYER
    use_beat_zoom = task.get("useBeatZoom", False)
    sp_config = task.get("overlaySpeedConfig", {})

    filters = []
    zoom_expr = ""
    if use_beat_zoom:
        zoom_expr = f",zoompan=z='min(max(zoom,1.0)+0.0015*sin(time*3.1415*2),1.08)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={res_w}x{res_h}:fps={fps}"

    # CROP DISISIPKAN SEBELUM SCALING DENGAN BUKTI SINKRONISASI CANVAS
    crop_l = int(task.get("cropLeft", 0))
    crop_r = int(task.get("cropRight", 0))
    crop_t = int(task.get("cropTop", 0))
    crop_b = int(task.get("cropBottom", 0))
    
    crop_expr = f"crop=iw-{crop_l}-{crop_r}:ih-{crop_t}-{crop_b}:{crop_l}:{crop_t}," if (crop_l > 0 or crop_r > 0 or crop_t > 0 or crop_b > 0) else ""

    if is_picture:
        filters.append(f"[0:v]{crop_expr}scale={res_w}:{res_h}:force_original_aspect_ratio=increase:eval=init,crop={res_w}:{res_h},setpts=PTS-STARTPTS,settb=AVTB,fps={fps}{zoom_expr}[bg_base]")
    else:
        filters.append(f"[0:v]{crop_expr}scale={res_w}:{res_h}:force_original_aspect_ratio=increase:eval=init,crop={res_w}:{res_h},setpts=N/FRAME_RATE/TB,settb=AVTB,fps={fps}{zoom_expr}[bg_base]")
        
    current_video_node = "[bg_base]"

    bg_effect = task.get("bgEffect", "none")
    if bg_effect and bg_effect != "none":
        effect_node = "[bg_video_effect]"
        if bg_effect == "vignette":
            filters.append(f"{current_video_node}vignette{effect_node}")
        elif bg_effect == "blur":
            filters.append(f"{current_video_node}boxblur=10:5{effect_node}")
        elif bg_effect == "grayscale":
            filters.append(f"{current_video_node}hue=s=0{effect_node}")
        elif bg_effect == "sepia":
            filters.append(f"{current_video_node}colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131{effect_node}")
        current_video_node = effect_node

    # PENGATURAN REDUP/KECERAHAN VISUAL UTAMA (bukan overlay/spectrum).
    # Sengaja ditaruh di sini -- SETELAH bg_base & bgEffect, SEBELUM loop
    # overlay & spectrum di bawah -- supaya brightness ini HANYA memengaruhi
    # video/gambar latar utama, tidak ikut menggelapkan/menerangi overlay
    # efek maupun spectrum yang ditumpuk belakangan.
    # Range: -1.0 (gelap total/hitam) s/d 1.0 (paling terang), 0.0 = normal.
    bg_brightness = float(task.get("bgBrightness", 0.0))
    if bg_brightness != 0.0:
        bright_node = "[bg_video_bright]"
        filters.append(f"{current_video_node}eq=brightness={bg_brightness}{bright_node}")
        current_video_node = bright_node

    if valid_overlays:
        for idx, ov in enumerate(valid_overlays):
            input_idx = overlay_input_start_idx + idx
            ov_opacity = float(ov.get("opacity", 1.0))
            ov_fade_in = float(ov.get("fadeIn", 2.0))
            ov_delay = float(ov.get("delay", 0.0))
            is_loop = ov.get("loop", False)
            ov_shear_x = float(ov.get("shearX", 0.0))
            ov_shear_y = float(ov.get("shearY", 0.0))

            pts_speed = "PTS-STARTPTS"
            if sp_config.get("active", False) and idx in sp_config.get("target_overlays", []):
                pts_speed = "(PTS-STARTPTS)*(0.7+0.6*abs(sin(N/20)))"

            ov_shear_filter = ""
            ov_overlay_x = "0"
            ov_overlay_y = "0"
            if ov_shear_x != 0.0 or ov_shear_y != 0.0:
                ov_shear_filter = f",shear=shx={ov_shear_x}:shy={ov_shear_y}:fillcolor=0x00000000"
                ov_overlay_x = f"0 - (w-{res_w})/2"
                ov_overlay_y = f"0 - (h-{res_h})/2"

            ov_raw = f"[ov_raw_{idx}]"
            ov_clean = f"[ov_clean_{idx}]"
            next_bg = f"[bg_with_ov_{idx}]"
            ov_chroma_filter = utils.get_chroma_key_filter_chain(ov.get("chromaKey", "black"), node_suffix=f"_{idx}")

            ov_is_picture_flag = prerendered_overlay_is_picture.get(idx, _is_picture_file(ov.get("file", "")))
            if ov_is_picture_flag:
                # Gambar selalu "dipanjangkan" mengisi seluruh durasi render, jadi
                # patokan durasi utk fade-out adalah durasi render itu sendiri
                # (sebelumnya jatuh ke default 10 detik yg salah/terlalu cepat).
                effective_ov_duration = render_duration
            else:
                ov_duration = utils.get_media_duration(ov.get("file", ""))
                if ov_duration <= 0: ov_duration = 10
                effective_ov_duration = ov_duration
            fade_out_dur = 2.0
            fade_out_start = max(0, min(effective_ov_duration, render_duration) - fade_out_dur)

            fade_filter = f"fade=t=in:st={ov_delay}:d={ov_fade_in}:alpha=1"
            if not is_loop:
                fade_filter += f",fade=t=out:st={fade_out_start}:d={fade_out_dur}:alpha=1"

            if idx in prerendered_overlay_indices:
                # Alpha sudah "dipanggang" sekali di prealpha_path (scale/crop/shear/mask
                # sudah dilakukan) -> tinggal setpts (kecepatan) + fade + overlay, TANPA
                # menghitung ulang masking setiap frame.
                filters.append(f"[{input_idx}:v]setpts='{pts_speed}',format=rgba,{fade_filter}{ov_clean}")
            else:
                filters.append(f"[{input_idx}:v]setpts='{pts_speed}',scale={res_w}:{res_h}:force_original_aspect_ratio=increase,crop={res_w}:{res_h},format=rgba{ov_shear_filter}{ov_raw}")
                filters.append(f"{ov_raw}{ov_chroma_filter},colorchannelmixer=aa={ov_opacity},{fade_filter}{ov_clean}")
            filters.append(f"{current_video_node}{ov_clean}overlay=x='{ov_overlay_x}':y='{ov_overlay_y}':eof_action=pass{next_bg}")
            current_video_node = next_bg

    for idx, spec in enumerate(spectrums_list):
        stype = spec.get("specType", "none")
        if stype == "none": continue

        sw = utils.ensure_even_int(spec.get("specWidth") or 800, min_val=40)
        sh = utils.ensure_even_int(spec.get("specHeight") or 300, min_val=20)
        sx = utils.sanitize_ffmpeg_coord(spec.get("specX", "(W-w)/2"))
        sy = utils.sanitize_ffmpeg_coord(spec.get("specY", "H-h-50"))
        # Warna garis & background box spectrum dihapus: selalu putih netral, tanpa box.
        scolor = "white"
        sop = float(spec.get("spectrumOpacity") or 0.8)

        spec_shear_x = float(spec.get("shearX", 0.0))
        spec_shear_y = float(spec.get("shearY", 0.0))
        
        spec_shear_filter = ""
        offset_x_expr = sx
        offset_y_expr = sy
        if spec_shear_x != 0.0 or spec_shear_y != 0.0:
            spec_shear_filter = f",shear=shx={spec_shear_x}:shy={spec_shear_y}:fillcolor=0x00000000"
            offset_x_expr = f"({sx}) - (w-{sw})/2"
            offset_y_expr = f"({sy}) - (h-{sh})/2"

        next_spec_node = f"[v_spec_out_{idx}]"

        if idx in custom_spec_input_indices:
            inp_i = custom_spec_input_indices[idx]
            spec_clean_node = f"[spec_custom_clean_{idx}]"
            filters.append(f"[{inp_i}:v]format=rgba,scale={sw}:{sh}{spec_shear_filter},colorchannelmixer=aa={sop}{spec_clean_node}")
            filters.append(f"{current_video_node}{spec_clean_node}overlay=x='{offset_x_expr}':y='{offset_y_expr}'{next_spec_node}")
            current_video_node = next_spec_node
        else:
            # Model spectrum bawaan FFmpeg (showfreqs/showwaves/dst) sudah dihapus.
            # Spectrum GLSL GPU seharusnya selalu ada di custom_spec_input_indices;
            # kalau gagal digenerate, spectrum ini dilewati saja (aman, tidak crash).
            continue

    text_filters = []

    if task.get("useIntro") and task.get("introConfig") != "none":
        lines = []
        if task.get("introText1") and str(task.get("introText1")).strip():
            lines.append({"text": task.get("introText1"), "size": task.get("introSize1", 48)})
        if int(task.get("introLineCount", 1)) >= 2 and task.get("introText2") and str(task.get("introText2")).strip():
            lines.append({"text": task.get("introText2"), "size": task.get("introSize2", 32)})
        if int(task.get("introLineCount", 1)) >= 3 and task.get("introText3") and str(task.get("introText3")).strip():
            lines.append({"text": task.get("introText3"), "size": task.get("introSize3", 24)})
            
        N = len(lines)
        for i, line in enumerate(lines):
            escaped_intro_text = str(line["text"]).replace("'", "\\'").replace(":", "\\:").replace(",", "\\,")
            line_config = task.get("introConfig").copy()
            line_config["fontSize"] = line["size"]
            
            offset = (i - (N - 1) / 2.0) * (line["size"] * 1.4)
            y_val = line_config.get("y", "(h-th)/2")
            if y_val == "(h-th)/2":
                line_config["y"] = f"(h-th)/2 + ({offset})"
            elif "h-th-" in str(y_val):
                match = re.search(r'h-th-(\d+)', str(y_val))
                if match:
                    base_val = int(match.group(1))
                    line_config["y"] = f"h-th-{base_val - offset}"
            elif re.match(r'^\d+$', str(y_val)):
                base_val = int(y_val)
                line_config["y"] = f"{base_val + offset}"
                
            intro_f = get_ffmpeg_drawtext_filter(line_config, escaped_intro_text, f"lt(t,{task.get('introConfig').get('displayDur', 5.0)})")
            if intro_f:
                text_filters.append(intro_f)

    if task.get("useTitle") and task.get("titleConfig") != "none":
        title_lines = []
        audio_files = task.get("audio", [])
        first_audio_name = os.path.splitext(os.path.basename(audio_files[0]))[0] if audio_files else "Song"
        
        if int(task.get("titleMode", 1)) == 1:
            parts = first_audio_name.split('_')
            if len(parts) >= 1 and parts[0].strip():
                title_lines.append({"text": parts[0], "size": task.get("titleSize1", 48)})
            if len(parts) >= 2 and parts[1].strip():
                title_lines.append({"text": parts[1], "size": task.get("titleSize2", 32)})
            if len(parts) >= 3:
                line3_text = "_".join(parts[2:])
                if line3_text.strip():
                    title_lines.append({"text": line3_text, "size": task.get("titleSize3", 24)})
        else:
            if task.get("titleText1") and str(task.get("titleText1")).strip():
                title_lines.append({"text": task.get("titleText1"), "size": task.get("titleSize1", 48)})
            if int(task.get("titleLineCount", 1)) >= 2 and task.get("titleText2") and str(task.get("titleText2")).strip():
                title_lines.append({"text": task.get("titleText2"), "size": task.get("titleSize2", 32)})
            if int(task.get("titleLineCount", 1)) >= 3 and task.get("titleText3") and str(task.get("titleText3")).strip():
                title_lines.append({"text": task.get("titleText3"), "size": task.get("titleSize3", 24)})

        N_title = len(title_lines)
        if N_title > 0:
            delay_time = float(task.get("introConfig").get("displayDur", 5.0)) if task.get("useIntro") else 0.0
            max_t = delay_time + float(task.get("titleConfig").get("displayDur", 5.0))
            
            for i, line in enumerate(title_lines):
                escaped_title_text = str(line["text"]).replace("'", "\\'").replace(":", "\\:").replace(",", "\\,")
                line_config = task.get("titleConfig").copy()
                line_config["fontSize"] = line["size"]
                
                offset = (i - (N_title - 1) / 2.0) * (line["size"] * 1.4)
                y_val = line_config.get("y", "(h-th)/2")
                if y_val == "(h-th)/2":
                    line_config["y"] = f"(h-th)/2 + ({offset})"
                elif "h-th-" in str(y_val):
                    match = re.search(r'h-th-(\d+)', str(y_val))
                    if match:
                        base_val = int(match.group(1))
                        line_config["y"] = f"h-th-{base_val - offset}"
                elif re.match(r'^\d+$', str(y_val)):
                    base_val = int(y_val)
                    line_config["y"] = f"{base_val + offset}"
                    
                title_f = get_ffmpeg_drawtext_filter(line_config, escaped_title_text, f"between(t,{delay_time},{max_t})", delay=delay_time)
                if title_f:
                    text_filters.append(title_f)

    if text_filters:
        t_chain = ",".join(text_filters)
        filters.append(f"{current_video_node}{t_chain}[vout]")
    else:
        filters.append(f"{current_video_node}null[vout]")

    filter_graph = "; ".join(filters)
    
    # Kualitas encode (CRF/CBR + level) mengikuti pilihan user di wizard sebelum render
    encoder_params = get_optimal_encoder(
        fps=fps, res_w=res_w, res_h=res_h,
        quality_mode=task.get("qualityMode", "CBR"),
        quality_level=task.get("qualityLevel", "med")
    )
    audio_params = get_youtube_live_audio_args()
    
    # PERBAIKAN: Jumlah thread filter mengikuti core CPU asli mesin (bukan angka tetap)
    filter_threads = get_optimal_filter_threads()
    args = [
        "-y",
        "-sws_flags", "fast_bilinear",
        "-filter_threads", filter_threads,
        "-filter_complex_threads", filter_threads,
        "-threads", "0"
    ] + inputs + [
        "-filter_complex", filter_graph,
        "-map", "[vout]",
        "-map", "1:a"
    ] + encoder_params + audio_params + [
        "-max_muxing_queue_size", "2048",
        "-movflags", "+faststart",
        "-t", str(render_duration),
        final_output_path
    ]

    ffmpeg_err_path = _register_temp_file(os.path.join(config.TempFolder, f"ffmpeg_run_err_{run_id}.txt"))

    # LOGGING DIAGNOSTIK: simpan perintah ffmpeg LENGKAP (semua argumen +
    # filter_complex) yang akan dijalankan, SETIAP KALI render dimulai --
    # bukan cuma saat gagal. Ini murni tambahan (tidak mengubah proses render
    # sama sekali) supaya kalau render macet/deadlock di tengah jalan (proses
    # masih hidup, jadi TIDAK memicu penanganan error/exit-code apa pun),
    # kita tetap punya rekaman persis apa yang sedang dijalankan untuk
    # dianalisis, tanpa harus menebak-nebak lagi.
    try:
        debug_cmd_path = os.path.join(config.TempFolder, "last_render_command_debug.txt")
        with open(debug_cmd_path, "w", encoding="utf-8") as f:
            f.write(f"Tugas: {task.get('name', '?')}\n")
            f.write(f"render_duration: {render_duration}\n")
            f.write(f"media list: {task.get('media', [])}\n")
            f.write(f"bgVideoOverlays: {task.get('bgVideoOverlays', [])}\n")
            f.write("\n--- FULL FFMPEG ARGS ---\n")
            f.write(" ".join(str(a) for a in args))
            f.write("\n\n--- FILTER_COMPLEX (rapi per baris) ---\n")
            f.write(filter_graph.replace(";", ";\n"))
    except Exception:
        pass

    print("Rendering Hardware GPU/CPU dimulai...")
    
    render_success = utils.run_ffmpeg_with_progress(config.FFmpeg, args, render_duration, ffmpeg_err_path)

    # (Pembersihan temp file sekarang ditangani terpusat lewat registry
    # _ACTIVE_TASK_TEMP_FILES + blok finally di render_task_pipeline() --
    # lihat komentar di sana. Ini memastikan cleanup tetap jalan walau
    # terjadi crash di titik mana pun sebelum baris ini tercapai, dan
    # urutannya juga otomatis benar karena finally baru jalan SETELAH
    # write_detailed_ffmpeg_error di bawah selesai membaca ffmpeg_err_path.)

    if render_success and os.path.exists(final_output_path):
        print("Proses render berhasil!")
        time.sleep(1)
        return True
    else:
        utils.write_detailed_ffmpeg_error("Render Video", ffmpeg_err_path)
        print("Gagal merender video. Detail ditulis ke error.txt")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return False


def start_queue_render():
    queue = queue_manager.get_queue()
    if not queue:
        print("\nAntrian kosong.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return

    success_count = 0
    failed_count = 0
    total = len(queue)

    for i in range(total):
        os.system('cls' if os.name == 'nt' else 'clear')
        print("=========================================")
        print("         PROSES ANTRIAN RENDER GPU")
        print("=========================================\n")

        for j in range(total):
            task = queue[j]
            if j < i:
                print(f"[{j+1}/{total}] {task.get('name')} -> Selesai")
            elif j == i:
                print(f"[{j+1}/{total}] {task.get('name')} -> Rendering GPU Hardware...")
            else:
                print(f"[{j+1}/{total}] {task.get('name')} -> Menunggu...")

        print("\n-----------------------------------------\n")

        current_task = queue[i]
        status = render_task_pipeline(current_task)
        if status:
            success_count += 1
        else:
            failed_count += 1

        gc.collect()
        time.sleep(1)

    queue_manager.clear_queue(silent=True)
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("RENDER ANTRIAN SELESAI")
    print("=========================================\n")
    print(f"Berhasil : {success_count} tugas")
    print(f"Gagal    : {failed_count} tugas\n")
    time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
