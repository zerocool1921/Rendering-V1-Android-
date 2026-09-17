import os
import re
import time
import copy
import config
import utils
import presets
import queue_manager
import audio_fx
import renderer


# Daftar format media yang didukung 100% tersinkronisasi di seluruh engine
SUPPORTED_VIDEO_EXTS = [".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v", ".gif"]
SUPPORTED_IMAGE_EXTS = [".jpg", ".jpeg", ".png", ".webp"]
SUPPORTED_MEDIA_EXTS = SUPPORTED_VIDEO_EXTS + SUPPORTED_IMAGE_EXTS
SUPPORTED_AUDIO_EXTS = [".mp3", ".wav", ".m4a", ".flac", ".ogg", ".wma"]


def get_wizard_input(step_title, prompt_msg, current_value=None):
    """Fungsi pembantu pembacaan input pada wizard interaktif"""
    print("-----------------------------------------")
    print(f"Langkah: {step_title}")
    if current_value is not None:
        print(f"Nilai saat ini: {current_value}")
    print("Ketik '0' untuk KEMBALI ke langkah sebelumnya")
    print("Ketik 'L' untuk LANJUT (Gunakan nilai saat ini / Default)")
    print("Ketik 'S' untuk SELESAI (Simpan tugas sekarang)")
    print("-----------------------------------------")
    val = input(prompt_msg).strip()
    return val


def configure_multi_video_overlays():
    """Mengatur multi overlay video/image effect secara independen"""
    overlays_list = []
    print("\n=========================================")
    print("    KONFIGURASI MULTI OVERLAY EFEK ASSET")
    print("=========================================")
    
    first_asset = utils.get_asset_overlay_choice()
    if first_asset == "none":
        return []

    # Overlay Efek Pertama (Main Overlay)
    print("\n--- Pengaturan Overlay Efek #1 (Pertama / Utama) ---")
    op1 = utils.get_asset_opacity_choice()
    fade1 = utils.get_asset_fade_in_choice()
    chroma1 = "black"  # Chroma Key overlay di-hardcode hitam, opsi pemilihan warna dihapus dari wizard
    print("\nLoop Animasi Efek #1?")
    print("[1] Ya, Ulangi terus menerus (Loop) [DEFAULT]")
    print("[2] Tidak, Putar sekali saja")
    loop1 = input("Pilihan [Default: 1]: ").strip() != "2"

    overlays_list.append({
        "file": first_asset,
        "opacity": op1,
        "fadeIn": fade1,
        "loop": loop1,
        "delay": 0.0,
        "smoothTransition": False,
        "shearX": 0.0,
        "shearY": 0.0,
        "chromaKey": chroma1
    })

    # Overlay Efek Selanjutnya (#2, #3, dst.)
    overlay_idx = 2
    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        print(f"Overlay Efek Tersimpan: {len(overlays_list)} file")
        add_more = input(f"Apakah Anda ingin menambahkan Overlay Efek ke-{overlay_idx}? (Y/N) [Default: N]: ").strip().lower()
        if add_more != 'y':
            break

        next_asset = utils.get_asset_overlay_choice()
        if next_asset == "none":
            break

        print(f"\n--- Pengaturan Overlay Efek #{overlay_idx} ---")
        op = utils.get_asset_opacity_choice()
        fade = utils.get_asset_fade_in_choice()
        chroma_n = "black"  # Chroma Key overlay di-hardcode hitam, opsi pemilihan warna dihapus dari wizard
        print(f"\nLoop Animasi Efek #{overlay_idx}?")
        print("[1] Ya, Ulangi terus menerus (Loop) [DEFAULT]")
        print("[2] Tidak, Putar sekali saja")
        loop = input("Pilihan [Default: 1]: ").strip() != "2"

        print(f"\nEfek #{overlay_idx} akan diputar setelah Overlay #1 dengan transisi halus secara bersamaan.")
        trans_time = input("Masukkan durasi transisi masuk halus (detik) [Default: 2.0]: ").strip()
        try:
            delay_sec = float(trans_time)
        except ValueError:
            delay_sec = 2.0

        overlays_list.append({
            "file": next_asset,
            "opacity": op,
            "fadeIn": fade,
            "loop": loop,
            "delay": delay_sec,
            "smoothTransition": True,
            "shearX": 0.0,
            "shearY": 0.0,
            "chromaKey": chroma_n
        })
        overlay_idx += 1

    return overlays_list


def _detect_video_files(paths):
    """
    Menyaring daftar path, mengembalikan HANYA format Animasi / Video.
    Sekarang mencakup GIF, WEBM, MKV sesuai update Canvas.
    """
    if not paths:
        return []
    if isinstance(paths, str):
        paths = [paths]
    return [p for p in paths if os.path.splitext(str(p))[1].lower() in SUPPORTED_VIDEO_EXTS]


def configure_murottal_fx(current=None):
    """Menu konfigurasi Audio FX Murottal (Noise Reduction, Mastering, Efek Ruangan/Gema Qari)."""
    fx = dict(current) if current else audio_fx.get_default_murottal_fx()

    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        print("=========================================================")
        print("     AUDIO FX MURROTAL (Noise Reduction/Mastering/Gema)")
        print("=========================================================")
        print(f" Status Saat Ini: {audio_fx.describe_murottal_fx(fx)}\n")
        print(f" [1] Noise Reduction   : {'AKTIF' if fx.get('noiseReduction') else 'Nonaktif'}")
        print(f" [2] Mastering/Fixing  : {'AKTIF' if fx.get('mastering') else 'Nonaktif'}")
        voice_label = audio_fx.MURROTAL_VOICE_EFFECTS.get(
            str(fx.get("voiceEffect", "none")), audio_fx.MURROTAL_VOICE_EFFECTS["none"]
        )["label"]
        print(f" [3] Efek Ruangan/Gema : {voice_label}")
        print(" [0] Selesai / Kembali\n")

        opt = input("Pilih nomor untuk diubah: ").strip()
        if opt == "0" or not opt:
            break
        elif opt == "1":
            ans = input("Aktifkan Noise Reduction? (Y/N) [Default: N]: ").strip().lower()
            fx["noiseReduction"] = (ans == "y")
        elif opt == "2":
            ans = input("Aktifkan Mastering/Fixing? (Y/N) [Default: N]: ").strip().lower()
            fx["mastering"] = (ans == "y")
        elif opt == "3":
            print("\nPilih Efek Ruangan/Gema:")
            for i, key in enumerate(audio_fx.VOICE_EFFECT_ORDER):
                print(f"[{i}] {audio_fx.MURROTAL_VOICE_EFFECTS[key]['label']}")
            v_ans = input("Pilihan: ").strip()
            if v_ans.isdigit() and 0 <= int(v_ans) < len(audio_fx.VOICE_EFFECT_ORDER):
                fx["voiceEffect"] = audio_fx.VOICE_EFFECT_ORDER[int(v_ans)]
            else:
                print("Pilihan tidak valid.")
                time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        else:
            print("Pilihan tidak valid.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER

    return fx


def configure_backsound_item(item=None):
    """Konfigurasi 1 track Backsound/Musik Latar (file, Noise Reduction, Mastering, Volume)."""
    item = dict(item) if item else dict(audio_fx.DEFAULT_BACKSOUND_ITEM)

    print("\nPilih file Backsound/Musik Latar (mis. suara alam/air/instrumental):")
    picked = utils.select_audio_files()
    if not picked:
        return None
    item["file"] = picked[0]

    ans = input("Aktifkan Noise Reduction untuk track ini? (Y/N) [Default: N]: ").strip().lower()
    item["noiseReduction"] = (ans == "y")

    ans = input("Aktifkan Mastering/Fixing untuk track ini? (Y/N) [Default: N]: ").strip().lower()
    item["mastering"] = (ans == "y")

    print("\nPilih Preset Volume Backsound (relatif terhadap murottal utama):")
    for i, key in enumerate(audio_fx.BACKSOUND_VOLUME_ORDER):
        preset_def = audio_fx.BACKSOUND_VOLUME_PRESETS[key]
        pct_txt = f"{preset_def['percent']}%" if preset_def['percent'] is not None else "isi manual"
        print(f"[{i}] {preset_def['label']} ({pct_txt})")
    v_ans = input(f"Pilihan [Default: 0 - {audio_fx.BACKSOUND_VOLUME_PRESETS['default']['label']}]: ").strip()
    if v_ans.isdigit() and 0 <= int(v_ans) < len(audio_fx.BACKSOUND_VOLUME_ORDER):
        chosen_key = audio_fx.BACKSOUND_VOLUME_ORDER[int(v_ans)]
    else:
        chosen_key = "default"
    item["volumePreset"] = chosen_key
    if chosen_key == "custom":
        pct_ans = input("Masukkan persentase volume custom (0-100) [Default: 20]: ").strip()
        try:
            item["volumeCustom"] = float(pct_ans)
        except ValueError:
            item["volumeCustom"] = 20.0

    return item


def configure_backsounds(current_list=None):
    """Menu manajemen Backsound/Musik Latar berlapis (0..tak terbatas track)."""
    backsound_list = [dict(b) for b in (current_list or [])]

    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        print("=========================================================")
        print("      BACKSOUND / MUSIK LATAR BERLAPIS (TAK TERBATAS)")
        print("=========================================================")
        if not backsound_list:
            print(" Belum ada track backsound.\n")
        else:
            for i, b in enumerate(backsound_list):
                fname = os.path.basename(str(b.get("file", "")))
                vol_pct = audio_fx.resolve_backsound_volume_fraction(b) * 100
                print(f" [{i + 1}] {fname}  (NR:{'Y' if b.get('noiseReduction') else 'N'}, "
                      f"Mastering:{'Y' if b.get('mastering') else 'N'}, Volume:{vol_pct:.0f}%)")
            print("")
        print(" [T] Tambah Track Backsound Baru")
        if backsound_list:
            print(" [H] Hapus Salah Satu Track (masukkan nomor setelah ini)")
        print(" [0] Selesai / Kembali\n")

        opt = input("Pilihan: ").strip().upper()
        if opt == "0" or not opt:
            break
        elif opt == "T":
            new_item = configure_backsound_item()
            if new_item:
                backsound_list.append(new_item)
        elif opt == "H" and backsound_list:
            idx_ans = input("Masukkan nomor track yang ingin dihapus: ").strip()
            if idx_ans.isdigit():
                idx = int(idx_ans) - 1
                if 0 <= idx < len(backsound_list):
                    backsound_list.pop(idx)
        else:
            print("Pilihan tidak valid.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER

    return backsound_list


def add_task_wizard():
    """Wizard Utama Pemilihan Mode Tambah Tugas"""
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("          MODE PEMILIHAN LOKASI")
    print("=========================================\n")
    print("[1] Mode 1: Pemilihan Folder Induk (Otomatis Scan Subfolder)")
    print("[2] Mode 2: Pemilihan Satu Folder (Gunakan File Di Dalam Folder Terpilih)")
    print("[3] Mode 3: Pembuatan Tugas Manual Satu-per-Satu")
    print("[0] Kembali\n")
    
    mode_loc = input("Pilih Mode: ").strip()
    if mode_loc == "0" or not mode_loc:
        return

    saved_presets = presets.get_presets()
    active_preset = None
    if saved_presets:
        os.system('cls' if os.name == 'nt' else 'clear')
        print("Pilih Konfigurasi Preset Visual untuk tugas ini:")
        print("[1] Gunakan Preset Tersimpan")
        print("[2] Konfigurasi Manual Baru")
        pre_choice = input("Pilihan: ").strip()
        if pre_choice == "1":
            os.system('cls' if os.name == 'nt' else 'clear')
            print("Daftar Preset Tersimpan:")
            for i, p in enumerate(saved_presets):
                print(f"[{i + 1}] {p.get('name')}")
            p_sel = input("Pilih nomor preset: ").strip()
            if p_sel.isdigit():
                p_idx = int(p_sel) - 1
                if 0 <= p_idx < len(saved_presets):
                    active_preset = saved_presets[p_idx]

    if active_preset is None and mode_loc == "3":
        add_task()
        return
    elif active_preset is None and mode_loc in ["1", "2"]:
        print("Mode pencarian folder otomatis wajib menggunakan konfigurasi preset tersimpan!")
        print("Silakan buat preset terlebih dahulu di menu utama.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return

    # JALUR MODE LOKASI 1: SCAN FOLDER INDUK (SUBFOLDER)
    if mode_loc == "1":
        os.system('cls' if os.name == 'nt' else 'clear')
        print("======================================================")
        print("PILIH FOLDER INDUK UNTUK SCAN OTOMATIS")
        print("======================================================")
        print("Silakan navigasi & pilih folder (Mode List)...")
        
        parent_folder = utils.select_output_folder()
        if not parent_folder or not os.path.exists(parent_folder):
            print("Folder tidak valid.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
            return

        sub_dirs = [d for d in os.listdir(parent_folder) if os.path.isdir(os.path.join(parent_folder, d))]
        valid_subs = []
        
        for sub in sub_dirs:
            sub_full_path = os.path.join(parent_folder, sub)
            files = [f for f in os.listdir(sub_full_path) if os.path.isfile(os.path.join(sub_full_path, f))]
            
            # PERBAIKAN: Deteksi Ekstensi Lengkap (Bug #3)
            # Semua file visual (gambar/video) yang ada di subfolder dipakai
            # (bukan cuma yang pertama) -- diurutkan supaya urutan gabungan
            # visualnya konsisten & bisa diprediksi. Mekanisme penggabungan
            # banyak visual sekaligus ditangani di renderer.py
            # (build_multi_visual_source), bukan di sini.
            media_f = sorted(
                [os.path.join(sub_full_path, f) for f in files if os.path.splitext(f)[1].lower() in SUPPORTED_MEDIA_EXTS],
                key=str.lower
            )
            # PERBAIKAN: audio_f dulu TIDAK disortir (beda perlakuan dari
            # media_f di atas), padahal urutan os.listdir() itu acak/tidak
            # terjamin tergantung filesystem. Untuk audio yang digabung
            # berurutan (mis. beberapa track murottal), urutan yang salah
            # bisa terputar tidak sesuai urutan aslinya. Disamakan dengan
            # media_f supaya urutan gabungan konsisten & bisa diprediksi.
            audio_f = sorted(
                [os.path.join(sub_full_path, f) for f in files if os.path.splitext(f)[1].lower() in SUPPORTED_AUDIO_EXTS],
                key=str.lower
            )
            
            if media_f and audio_f:
                valid_subs.append({
                    "Name": sub,
                    "FullName": sub_full_path,
                    "Media": media_f,
                    "Audio": audio_f
                })

        if not valid_subs:
            print("Tidak ditemukan subfolder yang memenuhi syarat (wajib memiliki file gambar/video DAN musik).")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
            return

        valid_subs.sort(key=lambda x: x["Name"])

        os.system('cls' if os.name == 'nt' else 'clear')
        print("======================================================")
        print(f"DAFTAR SUBFOLDER YANG MEMENUHI SYARAT (TERDETEKSI: {len(valid_subs)})")
        print("======================================================")
        for i, sub in enumerate(valid_subs):
            print(f"[{i + 1}] {sub['Name']}")
        print("")
        print("Pilih Mode Filter Impor Subfolder:")
        print("[1] OTOMATIS (Impor seluruh subfolder tanpa kecuali)")
        print("[2] RENTANG  (Impor rentang indeks tertentu, misal: 1-5)")
        print("[3] ACAK     (Impor indeks tertentu dipisahkan koma, misal: 1,3,9)\n")
        filter_mode = input("Pilihan [Default: 1]: ").strip()

        selected_subs = []
        if filter_mode == "2":
            range_input = input("Masukkan rentang indeks (contoh: 1-5): ").strip()
            match = re.match(r'^(\d+)-(\d+)$', range_input)
            if match:
                start_idx, end_idx = map(int, match.groups())
                if start_idx < 1: start_idx = 1
                if end_idx > len(valid_subs): end_idx = len(valid_subs)
                if start_idx <= end_idx:
                    for idx in range(start_idx, end_idx + 1):
                        selected_subs.append(valid_subs[idx - 1])
        elif filter_mode == "3":
            rand_input = input("Masukkan nomor indeks yang diinginkan dipisahkan koma (contoh: 1,3,9): ").strip()
            parts = rand_input.split(',')
            for p in parts:
                p_clean = p.strip()
                if p_clean.isdigit():
                    val = int(p_clean)
                    if 1 <= val <= len(valid_subs):
                        selected_subs.append(valid_subs[val - 1])
        else:
            selected_subs = valid_subs

        if not selected_subs:
            print("Tidak ada subfolder yang terpilih berdasarkan filter yang Anda masukkan.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
            return

        os.system('cls' if os.name == 'nt' else 'clear')
        print("======================================================")
        print("KONFIGURASI LOKASI FOLDER OUTPUT HASIL RENDER")
        print("======================================================")
        print("Pilih ke mana hasil render (.mp4) akan disimpan:")
        print("[1] OTOMATIS (Disimpan langsung di dalam subfolder tugas masing-masing) [DEFAULT]")
        print("[2] MANUAL   (Pilih satu folder penampung manual untuk seluruh tugas)\n")
        output_choice = input("Pilihan: ").strip()

        session_output_folder = ""
        if output_choice == "2":
            print("\nSilakan pilih folder penampung ekspor hasil render...")
            session_output_folder = utils.select_output_folder()
            if not session_output_folder or not os.path.exists(session_output_folder):
                print("Pemilihan folder dibatalkan. Menggunakan opsi penyimpanan otomatis di subfolder tugas.")
                time.sleep(2)
                session_output_folder = ""

        queue = queue_manager.get_queue()
        batch_count = 0

        # Sinkronisasi multi-spectrum dari preset
        preset_spectrums = copy.deepcopy(active_preset.get("spectrums", []))
        if not preset_spectrums and active_preset.get("specType") and active_preset.get("specType") != "none":
            preset_spectrums = [{
                "specType": active_preset.get("specType"),
                "specWidth": active_preset.get("specWidth", 800),
                "specHeight": active_preset.get("specHeight", 300),
                "specX": active_preset.get("specX", "(W-w)/2"),
                "specY": active_preset.get("specY", "H-h-50"),
                "specColor": active_preset.get("specColor", "white"),
                "specKeyColor": active_preset.get("specKeyColor", "black"),
                "spectrumOpacity": active_preset.get("spectrumOpacity", 0.8),
                "specBgColor": active_preset.get("specBgColor", "none"),
                "specBgOpacity": active_preset.get("specBgOpacity", 0.5),
                "useBeatZoom": active_preset.get("useBeatZoom", False),
                "shearX": 0.0,
                "shearY": 0.0
            }]

        for sub in selected_subs:
            final_output_dest = session_output_folder if session_output_folder else sub["FullName"]

            bg_overlays = active_preset.get("bgVideoOverlays", [])
            if not bg_overlays and active_preset.get("bgVideoOverlay") and active_preset.get("bgVideoOverlay") != "none":
                bg_overlays = [{
                    "file": active_preset.get("bgVideoOverlay"),
                    "opacity": active_preset.get("bgVideoOverlayOpacity", 0.5),
                    "fadeIn": active_preset.get("bgVideoOverlayFadeIn", 2.0),
                    "loop": active_preset.get("bgVideoOverlayLoop", True),
                    "delay": 0.0,
                    "smoothTransition": False,
                    "shearX": 0.0,
                    "shearY": 0.0
                }]

            task = {
                "name": sub["Name"],
                "media": sub["Media"],
                "audio": sub["Audio"],
                "output": final_output_dest,
                "resolution": active_preset.get("resolution"),
                "fps": active_preset.get("fps"),
                "qualityMode": active_preset.get("qualityMode", "CBR"),
                "qualityLevel": active_preset.get("qualityLevel", "med"),
                "spectrums": preset_spectrums,
                "spectrumOpacity": active_preset.get("spectrumOpacity"),
                "specType": active_preset.get("specType"),
                "specWidth": active_preset.get("specWidth"),
                "specHeight": active_preset.get("specHeight"),
                "specX": active_preset.get("specX", "(W-w)/2"),
                "specY": active_preset.get("specY", "H-h-50"),
                "specColor": active_preset.get("specColor"),
                "specBgColor": active_preset.get("specBgColor"),
                "specBgOpacity": active_preset.get("specBgOpacity"),
                "bgEffect": active_preset.get("bgEffect"),
                "bgVideoOverlays": bg_overlays,
                "useBeatZoom": active_preset.get("useBeatZoom", False),
                "overlaySpeedConfig": active_preset.get("overlaySpeedConfig", {"active": False, "target_overlays": [], "speed_mode": "auto_beat"}),
                "loops": active_preset.get("loops"),
                "useIntro": active_preset.get("useIntro"),
                "introLineCount": active_preset.get("introLineCount"),
                "introText1": active_preset.get("introText1"),
                "introText2": active_preset.get("introText2"),
                "introText3": active_preset.get("introText3"),
                "introSize1": active_preset.get("introSize1"),
                "introSize2": active_preset.get("introSize2"),
                "introSize3": active_preset.get("introSize3"),
                "introConfig": active_preset.get("introConfig"),
                "useTitle": active_preset.get("useTitle"),
                "titleMode": active_preset.get("titleMode"),
                "titleLineCount": active_preset.get("titleLineCount"),
                "titleText1": active_preset.get("titleText1"),
                "titleText2": "",
                "titleText3": "",
                "titleSize1": active_preset.get("titleSize1"),
                "titleSize2": active_preset.get("titleSize2"),
                "titleSize3": active_preset.get("titleSize3"),
                "titleConfig": active_preset.get("titleConfig"),
                "keyframes": active_preset.get("keyframes", presets.get_default_keyframes_structure())
            }
            queue.append(task)
            batch_count += 1
            print(f"Subfolder Terpilih: {sub['Name']} -> Ditambahkan!")

        if batch_count > 0:
            queue_manager.save_queue(queue)
            print(f"\nSelesai! Berhasil mengimpor {batch_count} tugas subfolder ke antrian GPU.")
        else:
            print("Tidak ada subfolder yang berhasil diimpor.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER

    # JALUR MODE LOKASI 2: PEMILIHAN SATU FOLDER SPESIFIK
    elif mode_loc == "2":
        os.system('cls' if os.name == 'nt' else 'clear')
        print("======================================================")
        print("PEMILIHAN SATU FOLDER SPESIFIK")
        print("======================================================")
        print("Silakan navigasi & pilih folder (Mode List)...")
        target_folder = utils.select_output_folder()
        if not target_folder or not os.path.exists(target_folder):
            print("Folder tidak valid atau dibatalkan.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
            return

        folder_name = os.path.basename(target_folder)
        all_files = os.listdir(target_folder)
        
        # PERBAIKAN: Deteksi Ekstensi Lengkap (Bug #3)
        # Semua file visual (gambar/video) di folder dipakai (bukan cuma yang
        # pertama), diurutkan agar urutan gabungan visual konsisten.
        media_f = sorted(
            [os.path.join(target_folder, f) for f in all_files if os.path.splitext(f)[1].lower() in SUPPORTED_MEDIA_EXTS],
            key=str.lower
        )
        # PERBAIKAN: sama seperti di scan subfolder di atas -- audio_f
        # disortir juga supaya urutan gabungan track audio konsisten &
        # tidak tergantung urutan acak os.listdir().
        audio_f = sorted(
            [os.path.join(target_folder, f) for f in all_files if os.path.splitext(f)[1].lower() in SUPPORTED_AUDIO_EXTS],
            key=str.lower
        )

        if not media_f or not audio_f:
            print(f"Folder '{folder_name}' kekurangan aset. Wajib terdapat file Visual (gambar/video) DAN Audio (musik).")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
            return

        bg_overlays = active_preset.get("bgVideoOverlays", [])
        if not bg_overlays and active_preset.get("bgVideoOverlay") and active_preset.get("bgVideoOverlay") != "none":
            bg_overlays = [{
                "file": active_preset.get("bgVideoOverlay"),
                "opacity": active_preset.get("bgVideoOverlayOpacity", 0.5),
                "fadeIn": active_preset.get("bgVideoOverlayFadeIn", 2.0),
                "loop": active_preset.get("bgVideoOverlayLoop", True),
                "delay": 0.0,
                "smoothTransition": False,
                "shearX": 0.0,
                "shearY": 0.0
            }]

        preset_spectrums = copy.deepcopy(active_preset.get("spectrums", []))
        if not preset_spectrums and active_preset.get("specType") and active_preset.get("specType") != "none":
            preset_spectrums = [{
                "specType": active_preset.get("specType"),
                "specWidth": active_preset.get("specWidth", 800),
                "specHeight": active_preset.get("specHeight", 300),
                "specX": active_preset.get("specX", "(W-w)/2"),
                "specY": active_preset.get("specY", "H-h-50"),
                "specColor": active_preset.get("specColor", "white"),
                "specKeyColor": active_preset.get("specKeyColor", "black"),
                "spectrumOpacity": active_preset.get("spectrumOpacity", 0.8),
                "specBgColor": active_preset.get("specBgColor", "none"),
                "specBgOpacity": active_preset.get("specBgOpacity", 0.5),
                "useBeatZoom": active_preset.get("useBeatZoom", False),
                "shearX": 0.0,
                "shearY": 0.0
            }]

        queue = queue_manager.get_queue()
        task = {
            "name": folder_name,
            "media": media_f,
            "audio": audio_f,
            "output": target_folder,
            "resolution": active_preset.get("resolution"),
            "fps": active_preset.get("fps"),
            "qualityMode": active_preset.get("qualityMode", "CBR"),
            "qualityLevel": active_preset.get("qualityLevel", "med"),
            "spectrums": preset_spectrums,
            "spectrumOpacity": active_preset.get("spectrumOpacity"),
            "specType": active_preset.get("specType"),
            "specWidth": active_preset.get("specWidth"),
            "specHeight": active_preset.get("specHeight"),
            "specX": active_preset.get("specX", "(W-w)/2"),
            "specY": active_preset.get("specY", "H-h-50"),
            "specColor": active_preset.get("specColor"),
            "specBgColor": active_preset.get("specBgColor"),
            "specBgOpacity": active_preset.get("specBgOpacity"),
            "bgEffect": active_preset.get("bgEffect"),
            "bgVideoOverlays": bg_overlays,
            "useBeatZoom": active_preset.get("useBeatZoom", False),
            "overlaySpeedConfig": active_preset.get("overlaySpeedConfig", {"active": False, "target_overlays": [], "speed_mode": "auto_beat"}),
            "loops": active_preset.get("loops"),
            "useIntro": active_preset.get("useIntro"),
            "introLineCount": active_preset.get("introLineCount"),
            "introText1": active_preset.get("introText1"),
            "introText2": active_preset.get("introText2"),
            "introText3": active_preset.get("introText3"),
            "introSize1": active_preset.get("introSize1"),
            "introSize2": active_preset.get("introSize2"),
            "introSize3": active_preset.get("introSize3"),
            "introConfig": active_preset.get("introConfig"),
            "useTitle": active_preset.get("useTitle"),
            "titleMode": active_preset.get("titleMode"),
            "titleLineCount": active_preset.get("titleLineCount"),
            "titleText1": active_preset.get("titleText1"),
            "titleText2": "",
            "titleText3": "",
            "titleSize1": active_preset.get("titleSize1"),
            "titleSize2": active_preset.get("titleSize2"),
            "titleSize3": active_preset.get("titleSize3"),
            "titleConfig": active_preset.get("titleConfig"),
            "keyframes": active_preset.get("keyframes", presets.get_default_keyframes_structure())
        }
        queue.append(task)
        queue_manager.save_queue(queue)
        print(f"Tugas folder '{folder_name}' berhasil ditambahkan ke antrian GPU.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER

    # JALUR MODE LOKASI 3: MANUAL TUNGGAL
    elif mode_loc == "3":
        os.system('cls' if os.name == 'nt' else 'clear')
        print("=========================================")
        print("PEMBUATAN TUGAS MANUAL (DENGAN PRESET)")
        print("=========================================\n")
        task_name = input("Nama tugas: ").strip()
        if not task_name:
            return

        media_files = utils.select_media_files()
        if not media_files:
            return

        audio_files = utils.select_audio_files()
        if not audio_files:
            return

        output_folder = utils.select_output_folder()
        if not output_folder:
            return

        bg_overlays = active_preset.get("bgVideoOverlays", [])
        if not bg_overlays and active_preset.get("bgVideoOverlay") and active_preset.get("bgVideoOverlay") != "none":
            bg_overlays = [{
                "file": active_preset.get("bgVideoOverlay"),
                "opacity": active_preset.get("bgVideoOverlayOpacity", 0.5),
                "fadeIn": active_preset.get("bgVideoOverlayFadeIn", 2.0),
                "loop": active_preset.get("bgVideoOverlayLoop", True),
                "delay": 0.0,
                "smoothTransition": False,
                "shearX": 0.0,
                "shearY": 0.0
            }]

        preset_spectrums = copy.deepcopy(active_preset.get("spectrums", []))
        if not preset_spectrums and active_preset.get("specType") and active_preset.get("specType") != "none":
            preset_spectrums = [{
                "specType": active_preset.get("specType"),
                "specWidth": active_preset.get("specWidth", 800),
                "specHeight": active_preset.get("specHeight", 300),
                "specX": active_preset.get("specX", "(W-w)/2"),
                "specY": active_preset.get("specY", "H-h-50"),
                "specColor": active_preset.get("specColor", "white"),
                "specKeyColor": active_preset.get("specKeyColor", "black"),
                "spectrumOpacity": active_preset.get("spectrumOpacity", 0.8),
                "specBgColor": active_preset.get("specBgColor", "none"),
                "specBgOpacity": active_preset.get("specBgOpacity", 0.5),
                "useBeatZoom": active_preset.get("useBeatZoom", False),
                "shearX": 0.0,
                "shearY": 0.0
            }]

        queue = queue_manager.get_queue()
        task = {
            "name": task_name,
            "media": media_files,
            "audio": audio_files,
            "output": output_folder,
            "resolution": active_preset.get("resolution"),
            "fps": active_preset.get("fps"),
            "qualityMode": active_preset.get("qualityMode", "CBR"),
            "qualityLevel": active_preset.get("qualityLevel", "med"),
            "spectrums": preset_spectrums,
            "spectrumOpacity": active_preset.get("spectrumOpacity"),
            "specType": active_preset.get("specType"),
            "specWidth": active_preset.get("specWidth"),
            "specHeight": active_preset.get("specHeight"),
            "specX": active_preset.get("specX", "(W-w)/2"),
            "specY": active_preset.get("specY", "H-h-50"),
            "specColor": active_preset.get("specColor"),
            "specBgColor": active_preset.get("specBgColor"),
            "specBgOpacity": active_preset.get("specBgOpacity"),
            "bgEffect": active_preset.get("bgEffect"),
            "bgVideoOverlays": bg_overlays,
            "useBeatZoom": active_preset.get("useBeatZoom", False),
            "overlaySpeedConfig": active_preset.get("overlaySpeedConfig", {"active": False, "target_overlays": [], "speed_mode": "auto_beat"}),
            "loops": active_preset.get("loops"),
            "useIntro": active_preset.get("useIntro"),
            "introLineCount": active_preset.get("introLineCount"),
            "introText1": active_preset.get("introText1"),
            "introText2": active_preset.get("introText2"),
            "introText3": active_preset.get("introText3") if active_preset.get("introText3") else "",
            "introSize1": active_preset.get("introSize1"),
            "introSize2": active_preset.get("introSize2"),
            "introSize3": active_preset.get("introSize3") if active_preset.get("introSize3") else 24,
            "introConfig": active_preset.get("introConfig"),
            "useTitle": active_preset.get("useTitle"),
            "titleMode": active_preset.get("titleMode"),
            "titleLineCount": active_preset.get("titleLineCount"),
            "titleText1": active_preset.get("titleText1"),
            "titleText2": active_preset.get("titleText2"),
            "titleText3": active_preset.get("titleText3"),
            "titleSize1": active_preset.get("titleSize1"),
            "titleSize2": active_preset.get("titleSize2"),
            "titleSize3": active_preset.get("titleSize3"),
            "titleConfig": active_preset.get("titleConfig"),
            "keyframes": active_preset.get("keyframes", presets.get_default_keyframes_structure())
        }
        queue.append(task)
        queue_manager.save_queue(queue)
        print("Tugas manual berbasis preset berhasil ditambahkan.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER

def _menu_pause():
    time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER


def _clear_screen():
    os.system('cls' if os.name == 'nt' else 'clear')


def _new_default_task(name, media_files, audio_files, output_folder):
    """Struktur default 1 tugas baru (dipakai add_task() sebelum masuk menu terkelompok)."""
    return {
        "name": name,
        "media": media_files,
        "audio": audio_files,
        "output": output_folder,
        "resolution": "1080p",
        "fps": 60,
        "qualityMode": "CBR",
        "qualityLevel": "med",
        "spectrums": [],
        "spectrumOpacity": 0.8,
        "specType": "custom_oscilloscope",
        "specWidth": 800,
        "specHeight": 300,
        "specX": "(W-w)/2",
        "specY": "H-h-50",
        "specColor": "white",
        "specKeyColor": "black",
        "specBgColor": "none",
        "specBgOpacity": 0.5,
        "bgEffect": "none",
        "bgVideoOverlays": [],
        "useBeatZoom": False,
        "overlaySpeedConfig": {"active": False, "target_overlays": [], "speed_mode": "auto_beat"},
        "loops": 1,
        "useIntro": False,
        "introLineCount": 1,
        "introText1": "",
        "introText2": "",
        "introText3": "",
        "introSize1": 48,
        "introSize2": 32,
        "introSize3": 24,
        "introConfig": "none",
        "useTitle": False,
        "titleMode": 1,
        "titleLineCount": 1,
        "titleText1": "",
        "titleText2": "",
        "titleText3": "",
        "titleSize1": 48,
        "titleSize2": 32,
        "titleSize3": 24,
        "titleConfig": "none",
        "murottalFx": audio_fx.get_default_murottal_fx(),
        "backsounds": audio_fx.get_default_backsounds(),
        "keyframes": presets.get_default_keyframes_structure()
    }


def _normalize_task_spectrums(task):
    """Migrasi field lama (specType tunggal) -> task['spectrums'] kalau belum ada."""
    spectrums_list = task.get("spectrums", [])
    if not spectrums_list:
        spec_type_old = task.get("specType", "custom_oscilloscope")
        if spec_type_old and spec_type_old != "none":
            spectrums_list = [{
                "specType": spec_type_old,
                "specWidth": int(task.get("specWidth") or 800),
                "specHeight": int(task.get("specHeight") or 300),
                "specX": task.get("specX", "(W-w)/2"),
                "specY": task.get("specY", "H-h-50"),
                "specColor": "white",
                "spectrumOpacity": float(task.get("spectrumOpacity") or 0.8),
                "specBgColor": "none",
                "specBgOpacity": float(task.get("specBgOpacity") or 0.5),
                "useBeatZoom": bool(task.get("useBeatZoom", False)),
                "shearX": 0.0,
                "shearY": 0.0
            }]
            task["spectrums"] = spectrums_list
    return spectrums_list


def _normalize_task_overlays(task):
    """Migrasi field lama (bgVideoOverlay tunggal) -> task['bgVideoOverlays'] kalau belum ada."""
    bg_video_overlays = task.get("bgVideoOverlays", [])
    if not bg_video_overlays and task.get("bgVideoOverlay") and task.get("bgVideoOverlay") != "none":
        bg_video_overlays = [{
            "file": task.get("bgVideoOverlay"),
            "opacity": task.get("bgVideoOverlayOpacity", 0.5),
            "fadeIn": task.get("bgVideoOverlayFadeIn", 2.0),
            "loop": task.get("bgVideoOverlayLoop", True),
            "delay": 0.0,
            "smoothTransition": False,
            "shearX": 0.0,
            "shearY": 0.0,
            "chromaKey": "black"
        }]
        task["bgVideoOverlays"] = bg_video_overlays
    return bg_video_overlays


# ============================================================================
# MENU TERKELOMPOK PENGATURAN TUGAS (dipakai saat Setting Pertama / Tugas Baru
# MAUPUN saat Edit Tugas) -- setiap grup punya sub-menu bernomor: tekan nomor
# untuk mengatur bagian tersebut, kalau tidak disentuh otomatis pakai nilai
# default/nonaktif. Menggantikan wizard 20-langkah lama & dashboard 28-opsi
# lama supaya jumlah langkah lebih ringkas & rapi.
# ============================================================================

def _group_info_file(task):
    """[1] INFO TUGAS & FILE -- Nama, Media, Audio, Folder Output."""
    while True:
        _clear_screen()
        print("=========================================================")
        print("   [1] INFO TUGAS & FILE")
        print("=========================================================")
        print(f" [1] Nama Tugas           : {task.get('name')}")
        print(f" [2] File Media (Visual)  : {len(task.get('media', []))} file terpilih")
        print(f" [3] File Audio           : {len(task.get('audio', []))} file terpilih")
        print(f" [4] Folder Output        : {task.get('output')}")
        print(" [0] Kembali\n")

        opt = input("Pilih nomor untuk diubah: ").strip()
        if opt == "0" or not opt:
            break
        elif opt == "1":
            new_name = input(f"Masukkan nama tugas baru [Saat ini: {task.get('name')}]: ").strip()
            if new_name:
                task["name"] = new_name
        elif opt == "2":
            new_media = utils.select_media_files()
            if new_media:
                task["media"] = new_media
        elif opt == "3":
            new_audio = utils.select_audio_files()
            if new_audio:
                task["audio"] = new_audio
        elif opt == "4":
            new_out = utils.select_output_folder()
            if new_out:
                task["output"] = new_out
        else:
            print("Pilihan tidak valid.")
            _menu_pause()


def _edit_quality_level(task):
    """Sub-langkah pemilihan Mode & Kualitas Encode (CBR/CRF) dengan estimasi ukuran file."""
    res_w_est, res_h_est = 1280, 720
    _res = task.get("resolution", "1080p")
    if _res == "1080p":
        res_w_est, res_h_est = 1920, 1080
    elif _res == "2K":
        res_w_est, res_h_est = 2560, 1440
    elif _res == "4K":
        res_w_est, res_h_est = 3840, 2160
    _fps_est = int(task.get("fps", 30) or 30)

    _dur = 0.0
    for a in task.get("audio", []):
        try:
            d = utils.get_media_duration(a)
            if d and d > 0:
                _dur += d
        except Exception:
            pass
    if _dur <= 0:
        _dur = 180.0

    print("Pilih MODE & KUALITAS ENCODE VIDEO:")
    print("  CBR = bitrate video tetap (ukuran file PASTI)")
    print("  CRF = kualitas tetap, bitrate menyesuaikan konten (ukuran file HANYA ESTIMASI)\n")
    options = []
    idx = 1
    for mode in ["CBR", "CRF"]:
        print(f"--- MODE {mode} ---")
        for lvl in config.QUALITY_LEVEL_ORDER:
            info = config.QUALITY_PRESETS[mode][lvl]
            size_mb, v_kbps = renderer.estimate_output_size_mb(_dur, res_w_est, res_h_est, _fps_est, mode, lvl)
            t = "~" if mode == "CRF" else ""
            print(f"[{idx:2d}] {info['label']:<24} -> {t}{size_mb:,.0f} MB  ({t}{v_kbps:,.0f} kbps video)")
            options.append((mode, lvl))
            idx += 1
        print("")
    ans = input("Pilih nomor: ").strip()
    if ans.isdigit():
        sel = int(ans) - 1
        if 0 <= sel < len(options):
            task["qualityMode"], task["qualityLevel"] = options[sel]


def _group_video_quality(task):
    """[2] VIDEO & KUALITAS -- Resolusi, FPS, Kualitas Encode, Loop, Efek Latar."""
    while True:
        _clear_screen()
        ql_label = config.QUALITY_PRESETS.get(task.get("qualityMode", "CBR"), {}) \
            .get(task.get("qualityLevel", "med"), {}).get("label", task.get("qualityLevel"))
        print("=========================================================")
        print("   [2] VIDEO & KUALITAS")
        print("=========================================================")
        print(f" [1] Resolusi Video            : {task.get('resolution')}")
        print(f" [2] Frame Rate (FPS)          : {task.get('fps')} FPS")
        print(f" [3] Kualitas Video (CRF/CBR)  : {task.get('qualityMode')} - {ql_label}")
        print(f" [4] Pengulangan Loop Akhir    : {task.get('loops')}x")
        print(f" [5] Efek Visual Latar Belakang: {task.get('bgEffect')}")
        print(f" [6] Kecerahan/Redup Visual Utama: {task.get('bgBrightness', 0.0)}")
        print(" [0] Kembali\n")

        opt = input("Pilih nomor untuk diubah: ").strip()
        if opt == "0" or not opt:
            break
        elif opt == "1":
            print("Resolusi Video:")
            print("[1] 720p")
            print("[2] 1080p")
            print("[3] 2K")
            print("[4] 4K\n")
            ans = input("Pilihan: ").strip()
            mapping = {"1": "720p", "2": "1080p", "3": "2K", "4": "4K"}
            if ans in mapping:
                task["resolution"] = mapping[ans]
        elif opt == "2":
            print("Frame Rate (FPS):")
            print("[1] 30 FPS")
            print("[2] 60 FPS")
            print("[3] 100 FPS")
            print("[4] 120 FPS\n")
            ans = input("Pilihan: ").strip()
            mapping = {"1": 30, "2": 60, "3": 100, "4": 120}
            if ans in mapping:
                task["fps"] = mapping[ans]
        elif opt == "3":
            _edit_quality_level(task)
        elif opt == "4":
            ans = input(f"Masukkan pengulangan loop baru [Saat ini: {task.get('loops')}]: ").strip()
            if ans.isdigit():
                task["loops"] = int(ans)
        elif opt == "5":
            print("Pilih Efek Visual Latar Belakang Video:")
            print("[1] Tanpa Efek (Normal)")
            print("[2] Vignette (Gelap melingkar di pinggir bingkai)")
            print("[3] Blur (Latar belakang buram/soft)")
            print("[4] Grayscale (Hitam putih pudar)")
            print("[5] Sepia (Efek retro jadul)\n")
            ans = input("Pilihan: ").strip()
            mapping = {"1": "none", "2": "vignette", "3": "blur", "4": "grayscale", "5": "sepia"}
            task["bgEffect"] = mapping.get(ans, task.get("bgEffect", "none"))
        elif opt == "6":
            print("Atur Kecerahan/Redup Visual Utama (bukan overlay/spectrum):")
            print("Nilai -1.0 = gelap total (hitam)")
            print("Nilai  0.0 = normal (tidak berubah)")
            print("Nilai  1.0 = paling terang\n")
            cur = task.get("bgBrightness", 0.0)
            val = input(f"Masukkan nilai -1.0 s/d 1.0 [Saat ini: {cur}] [Default: -0.2]: ").strip()
            if not val:
                task["bgBrightness"] = -0.2
            else:
                try:
                    v = float(val)
                    v = max(-1.0, min(1.0, v))
                    task["bgBrightness"] = v
                except ValueError:
                    print("Input tidak valid, nilai tidak diubah.")
                    time.sleep(1.2)
        else:
            print("Pilihan tidak valid.")
            _menu_pause()


def _edit_intro(task):
    """Sub-langkah Overlay Identitas Channel (Intro): aktif/nonaktif + teks & ukuran font."""
    print("[1] Aktifkan overlay identitas")
    print("[2] Matikan\n")
    ans = input(f"Pilihan [Saat ini: {'Aktif' if task.get('useIntro') else 'Nonaktif'}]: ").strip()
    if ans == "1":
        task["useIntro"] = True
        lc = input("Berapa baris teks identitas? (1 - 3) [Default: 1]: ").strip()
        task["introLineCount"] = int(lc) if lc in ["1", "2", "3"] else 1

        new_text1 = input("Masukkan Teks Baris 1: ").strip()
        task["introText1"] = new_text1 if new_text1 else (task.get("introText1") or "MY CHANNEL")
        sz1 = input("Ukuran Font Baris 1 (px) [Default: 48]: ").strip()
        task["introSize1"] = int(sz1) if sz1.isdigit() else 48

        if task["introLineCount"] >= 2:
            task["introText2"] = input("Masukkan Teks Baris 2: ").strip()
            sz2 = input("Ukuran Font Baris 2 (px) [Default: 32]: ").strip()
            task["introSize2"] = int(sz2) if sz2.isdigit() else 32
        else:
            task["introText2"] = ""
            task["introSize2"] = 32

        if task["introLineCount"] >= 3:
            task["introText3"] = input("Masukkan Teks Baris 3: ").strip()
            sz3 = input("Ukuran Font Baris 3 (px) [Default: 24]: ").strip()
            task["introSize3"] = int(sz3) if sz3.isdigit() else 24
        else:
            task["introText3"] = ""
            task["introSize3"] = 24

        task["introConfig"] = presets.get_font_settings("Identitas Channel (Intro)", skip_size_input=True)
    elif ans == "2":
        task["useIntro"] = False


def _edit_title(task):
    """Sub-langkah Overlay Judul Lagu (Title): aktif/nonaktif, mode, teks & ukuran font."""
    print("[1] Aktifkan overlay judul")
    print("[2] Matikan\n")
    ans = input(f"Pilihan [Saat ini: {'Aktif' if task.get('useTitle') else 'Nonaktif'}]: ").strip()
    if ans == "1":
        task["useTitle"] = True
        print("Pilih Skema Judul:")
        print("[1] Mode 1: Dinamis (Dari nama file audio otomatis pecah '_')")
        print("[2] Mode 2: Custom Teks Manual\n")
        tm = input("Pilih Mode [Default: 1]: ").strip()
        if tm == "2":
            task["titleMode"] = 2
            lc = input("Berapa baris teks judul? (1 - 3) [Default: 1]: ").strip()
            task["titleLineCount"] = int(lc) if lc in ["1", "2", "3"] else 1

            new_text1 = input("Masukkan Judul Teks Baris 1: ").strip()
            task["titleText1"] = new_text1 if new_text1 else (task.get("titleText1") or "MY SONG")
            sz1 = input("Ukuran Font Baris 1 (px) [Default: 48]: ").strip()
            task["titleSize1"] = int(sz1) if sz1.isdigit() else 48

            if task["titleLineCount"] >= 2:
                task["titleText2"] = input("Masukkan Teks Baris 2: ").strip()
                sz2 = input("Ukuran Font Baris 2 (px) [Default: 32]: ").strip()
                task["titleSize2"] = int(sz2) if sz2.isdigit() else 32
            else:
                task["titleText2"] = ""
                task["titleSize2"] = 32

            if task["titleLineCount"] >= 3:
                task["titleText3"] = input("Masukkan Teks Baris 3: ").strip()
                sz3 = input("Ukuran Font Baris 3 (px) [Default: 24]: ").strip()
                task["titleSize3"] = int(sz3) if sz3.isdigit() else 24
            else:
                task["titleText3"] = ""
                task["titleSize3"] = 24
        else:
            task["titleMode"] = 1
            sz1 = input("Ukuran Font Judul Baris 1 (px) [Default: 48]: ").strip()
            task["titleSize1"] = int(sz1) if sz1.isdigit() else 48
            sz2 = input("Ukuran Font Judul Baris 2 (px) [Default: 32]: ").strip()
            task["titleSize2"] = int(sz2) if sz2.isdigit() else 32
            sz3 = input("Ukuran Font Judul Baris 3 (px) [Default: 24]: ").strip()
            task["titleSize3"] = int(sz3) if sz3.isdigit() else 24
        task["titleConfig"] = presets.get_font_settings("Judul Lagu", skip_size_input=True)
    elif ans == "2":
        task["useTitle"] = False


def _group_overlay_text(task):
    """Sub-grup OVERLAY FONT JUDUL & IDENTITAS."""
    while True:
        _clear_screen()
        print("---------------------------------------------------------")
        print("   OVERLAY FONT JUDUL & IDENTITAS")
        print("---------------------------------------------------------")
        intro_status = f"Aktif ({task.get('introLineCount')} Baris)" if task.get("useIntro") else "Nonaktif"
        title_status = f"Aktif (Mode {task.get('titleMode')}, {task.get('titleLineCount')} Baris)" if task.get("useTitle") else "Nonaktif"
        print(f" [1] Overlay Identitas Channel (Intro): {intro_status}")
        print(f" [2] Overlay Judul Lagu (Title)        : {title_status}")
        print(" [0] Kembali\n")

        opt = input("Pilih nomor untuk diubah: ").strip()
        if opt == "0" or not opt:
            break
        elif opt == "1":
            _edit_intro(task)
        elif opt == "2":
            _edit_title(task)
        else:
            print("Pilihan tidak valid.")
            _menu_pause()


def _group_overlay(task):
    """[3] OVERLAY -- Video Asset, Font Judul & Identitas."""
    while True:
        overlays = _normalize_task_overlays(task)
        _clear_screen()
        print("=========================================================")
        print("   [3] OVERLAY")
        print("=========================================================")
        print(f" [1] Overlay Video Asset            : {len(overlays)} Efek Aktif")
        intro_txt = "Aktif" if task.get("useIntro") else "Nonaktif"
        title_txt = "Aktif" if task.get("useTitle") else "Nonaktif"
        print(f" [2] Overlay Font Judul & Identitas  : Identitas={intro_txt}, Judul={title_txt}")
        print(" [0] Kembali\n")

        opt = input("Pilih nomor untuk diubah: ").strip()
        if opt == "0" or not opt:
            break
        elif opt == "1":
            task["bgVideoOverlays"] = configure_multi_video_overlays()
        elif opt == "2":
            _group_overlay_text(task)
        else:
            print("Pilihan tidak valid.")
            _menu_pause()


def _group_audio_fx(task):
    """[4] AUDIO FX -- Murottal FX (NR/Mastering/Gema) & Backsound berlapis."""
    while True:
        murottal_fx = task.get("murottalFx") or audio_fx.get_default_murottal_fx()
        backsound_list = task.get("backsounds") or audio_fx.get_default_backsounds()
        _clear_screen()
        print("=========================================================")
        print("   [4] AUDIO FX")
        print("=========================================================")
        print(f" [1] Audio FX Murottal (NR/Mastering/Gema): {audio_fx.describe_murottal_fx(murottal_fx)}")
        print(f" [2] Backsound / Musik Latar Berlapis     : {audio_fx.describe_backsounds(backsound_list)}")
        print(" [0] Kembali\n")

        opt = input("Pilih nomor untuk diubah: ").strip()
        if opt == "0" or not opt:
            break
        elif opt == "1":
            task["murottalFx"] = configure_murottal_fx(murottal_fx)
        elif opt == "2":
            task["backsounds"] = configure_backsounds(backsound_list)
        else:
            print("Pilihan tidak valid.")
            _menu_pause()


def _save_task_as_preset(task):
    """Simpan konfigurasi visual tugas saat ini sebagai preset baru."""
    saved_presets = presets.get_presets()
    p_name = input("Masukkan Nama Preset Baru: ").strip()
    if not p_name:
        return
    new_preset = {
        "name": p_name,
        "resolution": task.get("resolution"),
        "fps": task.get("fps"),
        "qualityMode": task.get("qualityMode"),
        "qualityLevel": task.get("qualityLevel"),
        "spectrums": task.get("spectrums", []),
        "spectrumOpacity": task.get("spectrumOpacity"),
        "specType": task.get("specType"),
        "specWidth": task.get("specWidth"),
        "specHeight": task.get("specHeight"),
        "specX": task.get("specX", "(W-w)/2"),
        "specY": task.get("specY", "H-h-50"),
        "specColor": task.get("specColor"),
        "specBgColor": task.get("specBgColor"),
        "specBgOpacity": task.get("specBgOpacity"),
        "bgEffect": task.get("bgEffect"),
        "bgVideoOverlays": task.get("bgVideoOverlays", []),
        "useBeatZoom": task.get("useBeatZoom", False),
        "overlaySpeedConfig": task.get("overlaySpeedConfig", {"active": False, "target_overlays": [], "speed_mode": "auto_beat"}),
        "loops": task.get("loops"),
        "useIntro": task.get("useIntro"),
        "introLineCount": task.get("introLineCount"),
        "introText1": task.get("introText1"),
        "introText2": task.get("introText2"),
        "introText3": task.get("introText3"),
        "introSize1": task.get("introSize1"),
        "introSize2": task.get("introSize2"),
        "introSize3": task.get("introSize3"),
        "introConfig": task.get("introConfig"),
        "useTitle": task.get("useTitle"),
        "titleMode": task.get("titleMode"),
        "titleLineCount": task.get("titleLineCount"),
        "titleText1": task.get("titleText1"),
        "titleText2": task.get("titleText2"),
        "titleText3": task.get("titleText3"),
        "titleSize1": task.get("titleSize1"),
        "titleSize2": task.get("titleSize2"),
        "titleSize3": task.get("titleSize3"),
        "titleConfig": task.get("titleConfig"),
        "keyframes": task.get("keyframes", presets.get_default_keyframes_structure())
    }
    saved_presets.append(new_preset)
    presets.save_presets(saved_presets)
    print("Konfigurasi visual berhasil disimpan sebagai preset baru!")
    _menu_pause()


def run_grouped_task_menu(task, is_new=False, index=None, queue=None):
    """
    Menu utama pengaturan tugas TERKELOMPOK -- dipakai baik saat Setting
    Pertama (Tugas Baru) MAUPUN saat Edit Tugas. Tiap grup [1]-[4] punya
    sub-menu bernomor sendiri; kalau sub-item tidak disentuh, otomatis
    tetap pakai nilai default / nonaktif (tidak ada langkah wajib dilewati
    satu-satu seperti wizard lama).
    """
    while True:
        _normalize_task_spectrums(task)
        _normalize_task_overlays(task)
        _clear_screen()
        ql_label = config.QUALITY_PRESETS.get(task.get("qualityMode", "CBR"), {}) \
            .get(task.get("qualityLevel", "med"), {}).get("label", task.get("qualityLevel"))
        title = "WIZARD TUGAS BARU" if is_new else f"DASHBOARD EDITOR TUGAS: {task.get('name')}"
        print("=========================================================================")
        print(f"            {title}")
        print("=========================================================================")
        print("Pilih grup pengaturan [1-4]. Tiap grup punya sub-menu bernomor -- kalau")
        print("tidak diubah, otomatis pakai nilai default/nonaktif.\n")
        print(f" [1] Info Tugas & File   : {task.get('name')} | {len(task.get('media', []))} media | {len(task.get('audio', []))} audio")
        print(f" [2] Video & Kualitas    : {task.get('resolution')} @ {task.get('fps')}fps, {task.get('qualityMode')}-{ql_label}, Loop {task.get('loops')}x")
        print(f" [3] Overlay             : {len(task.get('bgVideoOverlays', []))} Efek Asset, {len(task.get('spectrums', []))} Spectrum, "
              f"Identitas={'Y' if task.get('useIntro') else 'N'}, Judul={'Y' if task.get('useTitle') else 'N'}")
        print(f" [4] Audio FX            : {audio_fx.describe_murottal_fx(task.get('murottalFx'))}")
        if not is_new:
            print(" [5] Simpan Konfigurasi Sebagai Preset Baru")
            print(" [6] Buka & Edit Visual di Canvas Editor GUI OpenGL (Interactive Drag & Keyframe)")
        print(" [0] Selesai & Simpan\n")

        opt = input("Pilih nomor: ").strip()
        if opt == "0" or not opt:
            break
        elif opt == "1":
            _group_info_file(task)
        elif opt == "2":
            _group_video_quality(task)
        elif opt == "3":
            _group_overlay(task)
        elif opt == "4":
            _group_audio_fx(task)
        elif opt == "5" and not is_new:
            _save_task_as_preset(task)
        elif opt == "6" and not is_new:
            try:
                import canvas_editor
                queue[index] = task
                queue_manager.save_queue(queue)
                canvas_editor.launch_canvas_editor(index)
                refreshed_queue = queue_manager.get_queue()
                task.clear()
                task.update(refreshed_queue[index])
                queue[:] = refreshed_queue
            except Exception as e:
                print(f"Gagal membuka Canvas Editor GUI GPU: {str(e)}")
                _menu_pause()
        else:
            print("Pilihan tidak valid.")
            _menu_pause()

    return task


def add_task():
    """Pembuatan Tugas Baru: Nama/Media/Audio/Output dulu, lalu Menu Pengaturan Terkelompok."""
    queue = queue_manager.get_queue()

    _clear_screen()
    print("=========================================")
    print("       PEMBUATAN TUGAS BARU (MANUAL)")
    print("=========================================\n")
    task_name = input("Masukkan Nama Tugas [Default: Tugas Baru]: ").strip() or "Tugas Baru"

    print("\nSilakan navigasi & pilih file media (Mode List)...")
    media_files = utils.select_media_files()
    if not media_files:
        return

    print("Silakan navigasi & pilih file audio (Mode List)...")
    audio_files = utils.select_audio_files()
    if not audio_files:
        return

    print("Silakan navigasi & pilih folder output (Mode List)...")
    output_folder = utils.select_output_folder()
    if not output_folder:
        return

    task = _new_default_task(task_name, media_files, audio_files, output_folder)
    task = run_grouped_task_menu(task, is_new=True)

    print("")
    save_pre = input("Apakah Anda ingin menyimpan settingan visual ini sebagai preset? (Y/N) [Default: N]: ").strip().lower()
    if save_pre == 'y':
        _save_task_as_preset(task)

    queue.append(task)
    queue_manager.save_queue(queue)
    print("Tugas berhasil ditambahkan ke antrian GPU.")
    _menu_pause()


def edit_task():
    """Dashboard Editor Tugas -- Menu Terkelompok (Info/File, Video, Overlay, Audio FX, Canvas Editor)."""
    queue = queue_manager.get_queue()
    if not queue:
        print("\nTidak ada tugas untuk diedit.")
        _menu_pause()
        return

    _clear_screen()
    print("=========================================")
    print("            EDIT TUGAS GPU")
    print("=========================================\n")

    for i, task in enumerate(queue):
        print(f"[{i + 1}] {task.get('name')}")
    print("\n[0] Batal\n")

    choice = input("Pilih nomor tugas yang ingin diedit: ").strip()
    if choice == "0" or not choice:
        return

    if not choice.isdigit():
        print("Harap masukkan angka.")
        _menu_pause()
        return

    index = int(choice) - 1
    if not (0 <= index < len(queue)):
        print("Pilihan tidak valid.")
        _menu_pause()
        return

    task = queue[index]
    _normalize_task_spectrums(task)
    _normalize_task_overlays(task)
    task = run_grouped_task_menu(task, is_new=False, index=index, queue=queue)

    queue[index] = task
    queue_manager.save_queue(queue)
    print("\nTugas berhasil diperbarui.")
    _menu_pause()
