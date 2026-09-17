# --- START OF FILE audio_fx.py ---
"""
MODUL EFEK AUDIO KHUSUS MURROTAL & BACKSOUND BERLAPIS
=======================================================
Semua diimplementasikan pakai filter native FFmpeg (afftdn, acompressor,
loudnorm, alimiter, aecho, amix) -- TIDAK butuh plugin/model AI tambahan,
jadi ringan dan portable ke semua environment yang sudah punya FFmpeg.

BAGIAN 1 - VOICE FX MURROTAL (dipakai 1x untuk seluruh audio murottal
           yang sudah digabung / concat_audios()):
    - Noise Reduction  (y/n, default N)
    - Mastering/Fixing (y/n, default N)
    - Efek Ruangan/Gema ala Qari (5 preset, default "none" / kering)

BAGIAN 2 - BACKSOUND / BACKGROUND MUSIC BERLAPIS (0..tak terbatas track):
    - Tiap track: Noise Reduction sendiri, Mastering sendiri, preset
      Volume sendiri (5 preset + custom).
    - Semua track di-loop otomatis & gapless sampai PERSIS durasi akhir
      video/audio utama, lalu di-mix bareng (amix, normalize=0 supaya
      volume murottal utama TIDAK ikut turun walau backsound-nya banyak).
    - Default (tanpa backsound ditambahkan) = tidak ada proses tambahan
      sama sekali, murni lewat (copy) -- nol overhead performa.

Semua fungsi di modul ini AMAN dipanggil dengan setting kosong/None --
akan otomatis balik ke perilaku default (audio asli, tanpa proses).
"""

import os
import shutil

import config
import utils

# ============================================================================
# 1. NILAI DEFAULT (SEMUA SETTING DEFAULT = MATI / "N", SESUAI PERMINTAAN)
# ============================================================================
DEFAULT_MURROTAL_FX = {
    "noiseReduction": False,   # y/n
    "mastering": False,        # y/n
    "voiceEffect": "none",     # "none" | "1".."5" -- lihat MURROTAL_VOICE_EFFECTS
}

DEFAULT_BACKSOUND_ITEM = {
    "file": None,
    "noiseReduction": False,
    "mastering": False,
    "volumePreset": "default",  # default|low|med|high|custom
    "volumeCustom": 20.0,       # dipakai kalau volumePreset == "custom" (persen 0-100)
}


def get_default_murottal_fx():
    return dict(DEFAULT_MURROTAL_FX)


def get_default_backsounds():
    return []


# ============================================================================
# 2. PRESET EFEK SUARA/RUANGAN QARI (VOICE EFFECT) -- 5 MODEL
#    aecho=in_gain:out_gain:delays_ms:decays  --> filter gema native FFmpeg,
#    dilapis bertingkat (multi-tap) supaya mendekati karakter reverb ruangan
#    nyata (bukan cuma delay tunggal).
# ============================================================================
MURROTAL_VOICE_EFFECTS = {
    "none": {
        "label": "Tanpa Efek (Suara Asli / Kering, tanpa gema)",
        "filter": None,
    },
    "1": {
        "label": "Ruangan Kecil Menggema (kamar/ruang tahfidz) - gema tipis & dekat",
        "filter": "aecho=0.8:0.7:40|65:0.28|0.18",
    },
    "2": {
        "label": "Masjid/Musholla Menggema - hangat, gema pendek-menengah",
        "filter": "aecho=0.8:0.75:60|110:0.35|0.22",
    },
    "3": {
        "label": "Masjid Besar Menggema (ala Masjidil Haram/Nabawi) - gema panjang & megah",
        "filter": "aecho=0.85:0.85:90|160|260:0.45|0.32|0.20",
    },
    "4": {
        "label": "Studio/Aula Vokal Jernih - gema lembut modern (cocok ceramah/podcast)",
        "filter": "aecho=0.75:0.65:35|55:0.20|0.12",
    },
    "5": {
        "label": "Ruang Sangat Besar/Cave - gema tebal, panjang & dramatis (cinematic)",
        "filter": "aecho=0.9:0.9:120|240|380|500:0.5|0.4|0.3|0.2",
    },
}

VOICE_EFFECT_ORDER = ["none", "1", "2", "3", "4", "5"]


# ============================================================================
# 3. PRESET VOLUME BACKSOUND -- 5 MODEL
#    Nilai "percent" adalah rekomendasi gain relatif (0-100%) terhadap
#    volume asli file backsound, SUDAH dipertimbangkan supaya backsound
#    (mis. suara air/alam) tidak menenggelamkan suara murottal.
# ============================================================================
BACKSOUND_VOLUME_PRESETS = {
    "default": {"label": "Default (rekomendasi ambience alam/air di belakang murottal)", "percent": 12},
    "low":     {"label": "Low (sangat samar, sekadar suasana)", "percent": 6},
    "med":     {"label": "Medium", "percent": 18},
    "high":    {"label": "High / Full (setara audio utama)", "percent": 45},
    "custom":  {"label": "Custom (isi manual persentase sendiri)", "percent": None},
}

BACKSOUND_VOLUME_ORDER = ["default", "low", "med", "high", "custom"]

BACKSOUND_LOOP_FADE_SEC = 1.5   # fade-in/out halus tiap track backsound di awal & akhir durasi


# ============================================================================
# 4. FILTER DASAR: NOISE REDUCTION & MASTERING (dipakai bareng oleh
#    Voice FX Murottal maupun tiap track Backsound)
# ============================================================================
def _noise_reduction_filter():
    """
    FFT denoiser adaptif (afftdn) -- aman untuk suara vokal (murottal)
    maupun audio ambience (backsound), tidak butuh sample noise-profile
    terpisah.
    """
    return "afftdn=nr=12:nf=-25:tn=1"


def _mastering_filter():
    """
    Rantai mastering ringan supaya audio lebih jernih/"keren" & konsisten
    volumenya:
      highpass       -> buang rumble/dengung sangat rendah
      equalizer      -> sedikit angkat "presence" vokal (kejernihan)
      acompressor    -> merapatkan dinamika (biar tidak ada bagian yg tenggelam)
      loudnorm       -> normalisasi loudness standar broadcast
      alimiter       -> jaring pengaman anti-clipping/distorsi
    """
    return (
        "highpass=f=80,"
        "equalizer=f=3000:t=q:w=1.2:g=2,"
        "acompressor=threshold=-18dB:ratio=3:attack=15:release=200:makeup=2,"
        "loudnorm=I=-16:TP=-1.5:LRA=11,"
        "alimiter=limit=0.98"
    )


def build_murottal_fx_chain(fx_settings):
    """
    Menyusun satu chain -af gabungan untuk audio murottal:
    Noise Reduction -> Efek Ruangan/Gema -> Mastering (urutan ini sengaja:
    bersihkan noise dulu sebelum diberi gema, lalu di-mastering terakhir
    supaya loudness akhir tetap konsisten walau ditambah gema).
    Balik None kalau semua setting mati (tidak perlu proses apa pun).
    """
    fx_settings = fx_settings or {}
    parts = []

    if fx_settings.get("noiseReduction"):
        parts.append(_noise_reduction_filter())

    voice_key = str(fx_settings.get("voiceEffect", "none"))
    voice_def = MURROTAL_VOICE_EFFECTS.get(voice_key, MURROTAL_VOICE_EFFECTS["none"])
    if voice_def["filter"]:
        parts.append(voice_def["filter"])

    if fx_settings.get("mastering"):
        parts.append(_mastering_filter())

    return ",".join(parts) if parts else None


def apply_murottal_voice_fx(input_wav, output_wav, fx_settings, run_id="shared", source_duration=None):
    """
    Menerapkan Voice FX ke audio murottal yang SUDAH digabung
    (hasil renderer.concat_audios()). Kalau semua setting default/mati,
    file cuma di-copy apa adanya (tanpa proses tambahan / tanpa re-encode).

    PERBAIKAN: filter gema (aecho) secara alami menambah "ekor" gema di
    akhir audio sehingga output bisa jadi LEBIH PANJANG dari input asli.
    Kalau source_duration diberikan, output dipotong persis ke durasi
    tersebut (-t) supaya panjang audio tetap konsisten dengan audio_duration
    yang sudah dipakai renderer.py untuk menghitung render_duration/timeline.

    Return True kalau output_wav berhasil dibuat (baik lewat proses FX
    maupun fallback copy) -- False HANYA kalau input_wav sendiri tidak ada.
    """
    if not input_wav or not os.path.exists(input_wav):
        return False

    chain = build_murottal_fx_chain(fx_settings)
    if not chain:
        if os.path.abspath(input_wav) != os.path.abspath(output_wav):
            shutil.copyfile(input_wav, output_wav)
        return True

    err_log = os.path.join(config.TempFolder, f"ffmpeg_murottalfx_err_{run_id}.txt")
    args = ["-y", "-i", input_wav, "-af", chain]
    if source_duration and float(source_duration) > 0:
        args += ["-t", str(float(source_duration))]
    args += ["-c:a", "pcm_s16le", "-threads", "0", output_wav]
    ok = utils.run_ffmpeg_silent(args, err_log)
    if not ok:
        utils.write_detailed_ffmpeg_error("Voice FX Murottal", err_log)
        # Kalau proses FX gagal, JANGAN sampai render mati total --
        # fallback pakai audio asli (tanpa efek) supaya render tetap jalan.
        shutil.copyfile(input_wav, output_wav)
    return True


# ============================================================================
# 5. BACKSOUND / BACKGROUND MUSIC BERLAPIS TAK TERBATAS
# ============================================================================
def resolve_backsound_volume_fraction(item):
    """
    Balik nilai gain 0.0 - 1.0 (dipakai filter volume=) dari preset/volume
    custom satu item backsound.
    """
    preset = item.get("volumePreset", "default")
    if preset == "custom":
        try:
            pct = float(item.get("volumeCustom", 20))
        except (TypeError, ValueError):
            pct = 20.0
    else:
        preset_def = BACKSOUND_VOLUME_PRESETS.get(preset, BACKSOUND_VOLUME_PRESETS["default"])
        pct = preset_def["percent"] if preset_def["percent"] is not None else 20.0
    pct = max(0.0, min(100.0, pct))
    return pct / 100.0


def _backsound_prefilter_parts(item):
    parts = []
    if item.get("noiseReduction"):
        parts.append(_noise_reduction_filter())
    if item.get("mastering"):
        parts.append(_mastering_filter())
    return parts


def mix_backsounds(main_audio_path, backsound_list, output_path, total_duration, run_id="shared"):
    """
    Melapisi audio utama (murottal, panjangnya sudah = total_duration)
    dengan SEBANYAK APAPUN track backsound sekaligus (jalan bareng/paralel,
    bukan bergantian).

    Tiap track backsound:
      - Di-loop otomatis (-stream_loop -1) lalu dipotong PERSIS di
        total_duration -- gapless, sesuai titik akhir file aslinya (tanpa
        re-encode ulang tiap putaran, jadi transisi antar-loop tetap mulus
        selama file sumbernya sendiri sudah nyaman didengar berulang).
      - Fade-in & fade-out halus 1.5 detik di awal & akhir durasi total,
        supaya backsound tidak "nyentak" masuk/keluar.
      - Opsional Noise Reduction & Mastering masing-masing.
      - Volume sendiri sesuai preset (atau custom %).

    Kalau backsound_list kosong (default / tidak ada yang ditambahkan),
    main_audio_path langsung di-copy ke output_path -- TIDAK ADA proses
    tambahan / TIDAK ADA overhead performa sama sekali.
    """
    if not main_audio_path or not os.path.exists(main_audio_path):
        return False

    valid_items = [
        b for b in (backsound_list or [])
        if b.get("file") and os.path.exists(str(b.get("file", "")))
    ]

    if not valid_items:
        if os.path.abspath(main_audio_path) != os.path.abspath(output_path):
            shutil.copyfile(main_audio_path, output_path)
        return True

    total_duration = float(total_duration) if total_duration else 0.0
    if total_duration <= 0:
        total_duration = utils.get_media_duration(main_audio_path) or 30.0

    inputs = ["-thread_queue_size", "16384", "-t", str(total_duration), "-i", main_audio_path]
    for bs in valid_items:
        inputs += [
            "-thread_queue_size", "16384",
            "-stream_loop", "-1",
            "-t", str(total_duration),
            "-i", bs["file"]
        ]

    fade_dur = min(BACKSOUND_LOOP_FADE_SEC, max(0.1, total_duration / 4))
    fade_out_start = max(0.0, total_duration - fade_dur)

    filters = []
    mix_labels = ["[0:a]"]
    for i, bs in enumerate(valid_items):
        in_idx = i + 1
        chain_parts = _backsound_prefilter_parts(bs)
        vol = resolve_backsound_volume_fraction(bs)
        chain_parts.append(f"volume={vol:.4f}")
        chain_parts.append(f"afade=t=in:st=0:d={fade_dur:.3f}")
        chain_parts.append(f"afade=t=out:st={fade_out_start:.3f}:d={fade_dur:.3f}")
        label = f"[bs{i}]"
        filters.append(f"[{in_idx}:a]{','.join(chain_parts)}{label}")
        mix_labels.append(label)

    n_total = len(mix_labels)
    # normalize=0 -- WAJIB supaya amix TIDAK otomatis menurunkan volume
    # audio murottal utama hanya karena jumlah track backsound bertambah;
    # volume tiap backsound sudah diatur manual lewat filter volume= di atas.
    filters.append(
        f"{''.join(mix_labels)}amix=inputs={n_total}:duration=first:dropout_transition=0:normalize=0[outa]"
    )
    filter_graph = ";".join(filters)

    err_log = os.path.join(config.TempFolder, f"ffmpeg_backsoundmix_err_{run_id}.txt")
    args = [
        "-y"
    ] + inputs + [
        "-filter_complex", filter_graph,
        "-map", "[outa]",
        "-t", str(total_duration),
        "-c:a", "pcm_s16le", "-threads", "0",
        output_path
    ]
    ok = utils.run_ffmpeg_silent(args, err_log)
    if not ok:
        utils.write_detailed_ffmpeg_error("Mixing Backsound", err_log)
        # Fallback aman: kalau mixing gagal, render tetap lanjut pakai
        # audio utama saja (tanpa backsound) alih-alih gagal total.
        shutil.copyfile(main_audio_path, output_path)
    return True


# ============================================================================
# 6. PIPELINE GABUNGAN (dipanggil dari renderer.py)
# ============================================================================
def process_full_audio_pipeline(concat_audio_path, task, total_duration, run_id="shared"):
    """
    Satu pintu masuk dipanggil dari renderer.py setelah concat_audios():
      1. Terapkan Voice FX Murottal (Noise Reduction/Mastering/Efek Ruangan)
         ke audio murottal yang sudah digabung.
      2. Mix dengan semua track Backsound (kalau ada), auto-loop gapless
         sampai persis `total_duration`.
    Balik path file .wav akhir yang siap dipakai renderer (menggantikan
    file hasil concat_audios() mentah).

    Aman dipanggil walau task tidak punya key "murottalFx"/"backsounds"
    sama sekali (proyek lama) -- otomatis pakai default (tanpa proses).
    """
    fx_settings = task.get("murottalFx") or get_default_murottal_fx()
    backsound_list = task.get("backsounds") or get_default_backsounds()

    fx_path = os.path.join(config.TempFolder, f"temp_audio_murottalfx_{run_id}.wav")
    if os.path.exists(fx_path):
        try:
            os.remove(fx_path)
        except Exception:
            pass
    apply_murottal_voice_fx(concat_audio_path, fx_path, fx_settings, run_id=run_id, source_duration=total_duration)

    if not backsound_list:
        return fx_path

    mixed_path = os.path.join(config.TempFolder, f"temp_audio_final_mixed_{run_id}.wav")
    if os.path.exists(mixed_path):
        try:
            os.remove(mixed_path)
        except Exception:
            pass
    mix_backsounds(fx_path, backsound_list, mixed_path, total_duration, run_id=run_id)

    # fx_path sudah tidak dipakai lagi setelah dimix -- boleh dihapus di
    # sini, tapi dibiarkan untuk dibersihkan renderer.py bareng file temp
    # lain (lihat temp_files_to_clean di render_task_pipeline) supaya
    # konsisten dengan pola cleanup yang sudah ada.
    return mixed_path


def describe_murottal_fx(fx_settings):
    """Ringkasan 1-baris untuk ditampilkan di menu/preview wizard."""
    fx_settings = fx_settings or {}
    if not fx_settings.get("noiseReduction") and not fx_settings.get("mastering") \
            and str(fx_settings.get("voiceEffect", "none")) == "none":
        return "Nonaktif (audio asli)"
    nr = "NR:Y" if fx_settings.get("noiseReduction") else "NR:N"
    mst = "Mastering:Y" if fx_settings.get("mastering") else "Mastering:N"
    voice_key = str(fx_settings.get("voiceEffect", "none"))
    voice_label = MURROTAL_VOICE_EFFECTS.get(voice_key, MURROTAL_VOICE_EFFECTS["none"])["label"]
    return f"{nr}, {mst}, Efek: {voice_label}"


def describe_backsounds(backsound_list):
    """Ringkasan 1-baris untuk ditampilkan di menu/preview wizard."""
    if not backsound_list:
        return "Tidak ada (0 track)"
    return f"{len(backsound_list)} track backsound aktif"

# --- END OF FILE audio_fx.py ---

