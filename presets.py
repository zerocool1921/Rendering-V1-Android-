import os
import json
import time
import utils
import config


def get_presets():
    """Membaca daftar preset tersimpan dari file presets.json"""
    if not os.path.exists(config.PresetFile):
        save_presets([])
    try:
        with open(config.PresetFile, "r", encoding="utf-8") as f:
            content = f.read().strip()
            if not content:
                return []
            data = json.loads(content)
            return data if isinstance(data, list) else []
    except Exception as e:
        utils.write_error_log(f"Eror saat membaca presets.json: {str(e)}")
        return []


def save_presets(presets):
    """Menyimpan daftar preset ke berkas presets.json"""
    try:
        with open(config.PresetFile, "w", encoding="utf-8") as f:
            json.dump(presets, f, indent=4, ensure_ascii=False)
    except Exception as e:
        utils.write_error_log(f"Eror saat menyimpan presets.json: {str(e)}")


def get_default_keyframes_structure():
    """
    Membuat struktur default Keyframes GPU untuk sinkronisasi Canvas Editor.
    """
    return {
        "bg": [
            {"time": 0.0, "x": 0, "y": 0, "scale_x": 1.0, "scale_y": 1.0, "rotation": 0, "opacity": 1.0, "easing": "linear"}
        ],
        "spectrum": [
            {"time": 0.0, "x": 0, "y": 0, "scale_x": 1.0, "scale_y": 1.0, "rotation": 0, "opacity": 1.0, "easing": "linear"}
        ],
        "intro": [
            {"time": 0.0, "x": 0, "y": 0, "scale_x": 1.0, "scale_y": 1.0, "rotation": 0, "opacity": 1.0, "easing": "linear"}
        ],
        "title": [
            {"time": 0.0, "x": 0, "y": 0, "scale_x": 1.0, "scale_y": 1.0, "rotation": 0, "opacity": 1.0, "easing": "linear"}
        ],
        "overlays": {}
    }


def get_font_settings(context_name, skip_size_input=False):
    """Wizard interaktif konfigurasi gaya & animasi font overlay"""
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print(f"   KONFIGURASI FONT: {context_name}")
    print("=========================================\n")
    
    font_family = "Arial"
    bold = False
    italic = False
    font_size = 32
    font_color = "white"
    opacity = 1.0
    x = "(w-tw)/2"
    y = "(h-th)/2"
    anim_dur = 1.0
    display_dur = 5.0
    anim_type = 1
    use_stroke = False
    stroke_color = "black"
    stroke_width = 2

    print("Pilih Keluarga Font:")
    fonts_list = [
        "Arial", "Courier New", "Georgia", "Impact", "Times New Roman",
        "Verdana", "Comic Sans MS", "Segoe UI", "Century Gothic", "Garamond"
    ]
    for i, font in enumerate(fonts_list):
        print(f"[{i + 1}] {font}")
    print("")
    f_choice = input("Pilihan [Default: 1]: ").strip()
    if f_choice.isdigit():
        idx = int(f_choice) - 1
        if 0 <= idx < len(fonts_list):
            font_family = fonts_list[idx]

    print("")
    b_opt = input("Gunakan Tebal/Bold? (Y/N): ").strip().lower()
    if b_opt == 'y': bold = True

    print("")
    i_opt = input("Gunakan Miring/Italic? (Y/N): ").strip().lower()
    if i_opt == 'y': italic = True

    if not skip_size_input:
        print("")
        size_input = input("Ukuran Font (px) [Default: 32]: ").strip()
        if size_input.isdigit(): font_size = int(size_input)

    font_color = utils.get_color_by_choice("Warna Font", "white")

    print("")
    op_input = input("Opasitas Font (0.1 - 1.0) [Default: 1.0]: ").strip()
    try: opacity = float(op_input)
    except ValueError: opacity = 1.0

    print("")
    print("Posisi Koordinat Teks:")
    print("[1] Tengah Layar Pas (Tengah-Tengah) [DEFAULT]")
    print("[2] Tengah-Bawah")
    print("[3] Tengah-Atas")
    print("[4] Kiri-Atas")
    print("[5] Kanan-Atas")
    print("[6] Kiri-Tengah")
    print("[7] Kanan-Tengah")
    print("[8] Bawah-Kiri")
    print("[9] Bawah-Kanan\n")
    
    pos_input = input("Pilihan posisi [Default: 1]: ").strip()
    if pos_input == "2": x, y = "(w-tw)/2", "h-th-100"
    elif pos_input == "3": x, y = "(w-tw)/2", "100"
    elif pos_input == "4": x, y = "50", "50"
    elif pos_input == "5": x, y = "w-tw-50", "50"
    elif pos_input == "6": x, y = "50", "(h-th)/2"
    elif pos_input == "7": x, y = "w-tw-50", "(h-th)/2"
    elif pos_input == "8": x, y = "50", "h-th-50"
    elif pos_input == "9": x, y = "w-tw-50", "h-th-50"

    print("")
    print("Efek Animasi Teks:")
    print("[1] Fade In-Out")
    print("[2] Slide Up")
    print("[3] Slide Left")
    print("[4] Zoom In\n")
    a_choice = input("Pilih jenis animasi [Default: 1]: ").strip()
    if a_choice in ["1", "2", "3", "4"]:
        anim_type = int(a_choice)

    print("")
    anim_d_input = input("Durasi Transisi Animasi (detik) [Default: 1.0]: ").strip()
    try: anim_dur = float(anim_d_input)
    except ValueError: anim_dur = 1.0

    print("")
    disp_d_input = input("Total Durasi Tampil (detik) [Default: 5.0]: ").strip()
    try: display_dur = float(disp_d_input)
    except ValueError: display_dur = 5.0

    print("")
    s_opt = input("Gunakan Garis Tepi (Stroke/Outline)? (Y/N): ").strip().lower()
    if s_opt == 'y':
        use_stroke = True
        stroke_color = utils.get_color_by_choice("Warna Garis Tepi", "black")
        print("")
        s_w_input = input("Ketebalan Garis Tepi (pixel) [Default: 2]: ").strip()
        if s_w_input.isdigit(): stroke_width = int(s_w_input)

    return {
        "fontFamily": font_family,
        "bold": bold,
        "italic": italic,
        "fontSize": font_size,
        "fontColor": font_color,
        "opacity": opacity,
        "x": x,
        "y": y,
        "animDur": anim_dur,
        "displayDur": display_dur,
        "animType": anim_type,
        "useStroke": use_stroke,
        "strokeColor": stroke_color,
        "strokeWidth": stroke_width
    }


def configure_multi_video_overlays_preset():
    """Mengatur multi overlay video/image effect khusus Preset"""
    overlays_list = []
    first_asset = utils.get_asset_overlay_choice()
    if first_asset == "none":
        return []

    print("\n--- Pengaturan Overlay Efek #1 ---")
    op1 = utils.get_asset_opacity_choice()
    fade1 = utils.get_asset_fade_in_choice()
    chroma1 = "black"  # Chroma Key overlay di-hardcode hitam, opsi pemilihan warna dihapus dari wizard
    print("\nLoop Animasi Efek #1?")
    print("[1] Ya (Loop) [DEFAULT]")
    print("[2] Tidak")
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

    overlay_idx = 2
    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        print(f"Overlay Efek Tersimpan di Preset: {len(overlays_list)} file")
        add_more = input(f"Tambah Overlay Efek ke-{overlay_idx}? (Y/N) [Default: N]: ").strip().lower()
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
        print("[1] Ya (Loop) [DEFAULT]")
        print("[2] Tidak")
        loop = input("Pilihan [Default: 1]: ").strip() != "2"

        trans_time = input("Masukkan durasi transisi masuk halus (detik) [Default: 2.0]: ").strip()
        try: delay_sec = float(trans_time)
        except ValueError: delay_sec = 2.0

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


def create_preset_manually():
    presets = get_presets()
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("      BUAT PRESET VISUAL BARU")
    print("=========================================\n")
    preset_name = input("Masukkan Nama Preset Baru: ").strip()
    if not preset_name:
        return

    resolution = "1080p"
    fps = 60
    spec_type = "none"
    use_beat_zoom = False
    overlay_speed_config = {"active": False, "target_overlays": [], "speed_mode": "auto_beat"}
    
    spec_width = 800
    spec_height = 300
    spec_x = "(W-w)/2"
    spec_y = "H-h-50"
    spec_color = "white"
    spec_key_color = "black"
    spec_bg_color = "none"
    spec_bg_opacity = 0.5
    bg_effect = "none"
    spectrum_opacity = 0.8
    bg_video_overlays = []
    loops = 1
    
    use_intro = False
    intro_line_count = 1
    intro_text1 = ""
    intro_text2 = ""
    intro_text3 = ""
    intro_size1 = 48
    intro_size2 = 32
    intro_size3 = 24
    intro_font_config = None
    
    use_title = False
    title_mode = 1
    title_line_count = 1
    title_text1 = ""
    title_text2 = ""
    title_text3 = ""
    title_size1 = 48
    title_size2 = 32
    title_size3 = 24
    title_font_config = None

    # Catatan: fitur Spectrum Visualizer GPU (GLSL/ModernGL) sudah dihapus
    # dari versi ini. spec_type tetap "none" (nilai default di atas), preset
    # lama yang masih menyimpan jenis spectrum akan otomatis diabaikan saat
    # dirender (lihat renderer.py).

    print("\nEfek Visual Latar Belakang:")
    print("[1] Tanpa Efek (Normal) [DEFAULT]")
    print("[2] Vignette")
    print("[3] Blur")
    print("[4] Grayscale")
    print("[5] Sepia\n")
    eff_input = input("Pilihan [Default: 1]: ").strip()
    if eff_input == "2": bg_effect = "vignette"
    elif eff_input == "3": bg_effect = "blur"
    elif eff_input == "4": bg_effect = "grayscale"
    elif eff_input == "5": bg_effect = "sepia"

    ov_opt = input("\nAktifkan Efek Video/Gambar Overlay dalam Preset? (Y/N) [Default: N]: ").strip().lower()
    if ov_opt == 'y':
        bg_video_overlays = configure_multi_video_overlays_preset()

    in_opt = input("\nAktifkan Overlay Identitas Channel dalam Preset? (Y/N): ").strip().lower()
    if in_opt == 'y':
        use_intro = True
        lc = input("Berapa baris teks identitas? (1 - 3) [Default: 1]: ").strip()
        intro_line_count = int(lc) if lc in ["1", "2", "3"] else 1
        
        intro_text1 = input("Masukkan Teks Baris 1: ").strip()
        if not intro_text1: intro_text1 = "MY CHANNEL"
        sz1 = input("Ukuran Font Baris 1 (px) [Default: 48]: ").strip()
        intro_size1 = int(sz1) if sz1.isdigit() else 48
        
        if intro_line_count >= 2:
            intro_text2 = input("Masukkan Teks Baris 2: ").strip()
            sz2 = input("Ukuran Font Baris 2 (px) [Default: 32]: ").strip()
            intro_size2 = int(sz2) if sz2.isdigit() else 32
        if intro_line_count >= 3:
            intro_text3 = input("Masukkan Teks Baris 3: ").strip()
            sz3 = input("Ukuran Font Baris 3 (px) [Default: 24]: ").strip()
            intro_size3 = int(sz3) if sz3.isdigit() else 24
            
        intro_font_config = get_font_settings("Identitas Channel (Intro)", skip_size_input=True)

    ti_opt = input("\nAktifkan Overlay Judul Lagu dalam Preset? (Y/N): ").strip().lower()
    if ti_opt == 'y':
        use_title = True
        print("Pilih Skema Judul:")
        print("[1] Mode 1: Dinamis (Dari nama file audio)")
        print("[2] Mode 2: Custom Teks Manual\n")
        tm = input("Pilihan [Default: 1]: ").strip()
        if tm == "2":
            title_mode = 2
            lc = input("Berapa baris teks judul? (1 - 3) [Default: 1]: ").strip()
            title_line_count = int(lc) if lc in ["1", "2", "3"] else 1
            
            title_text1 = input("Masukkan Judul Teks Baris 1: ").strip()
            if not title_text1: title_text1 = "MY SONG"
            sz1 = input("Ukuran Font Baris 1 (px) [Default: 48]: ").strip()
            title_size1 = int(sz1) if sz1.isdigit() else 48
            
            if title_line_count >= 2:
                title_text2 = input("Masukkan Judul Teks Baris 2: ").strip()
                sz2 = input("Ukuran Font Baris 2 (px) [Default: 32]: ").strip()
                title_size2 = int(sz2) if sz2.isdigit() else 32
            if title_line_count >= 3:
                title_text3 = input("Masukkan Judul Teks Baris 3: ").strip()
                sz3 = input("Ukuran Font Baris 3 (px) [Default: 24]: ").strip()
                title_size3 = int(sz3) if sz3.isdigit() else 24
        else:
            title_mode = 1
            sz1 = input("Ukuran Font Baris 1 (px) [Default: 48]: ").strip()
            title_size1 = int(sz1) if sz1.isdigit() else 48
            sz2 = input("Ukuran Font Baris 2 (px) [Default: 32]: ").strip()
            title_size2 = int(sz2) if sz2.isdigit() else 32
            sz3 = input("Ukuran Font Baris 3 (px) [Default: 24]: ").strip()
            title_size3 = int(sz3) if sz3.isdigit() else 24
            
        title_font_config = get_font_settings("Judul Lagu", skip_size_input=True)

    spectrums_structure = []
    if spec_type != "none":
        spectrums_structure.append({
            "specType": spec_type,
            "specWidth": spec_width,
            "specHeight": spec_height,
            "specX": spec_x,
            "specY": spec_y,
            "specColor": spec_color,
            "specKeyColor": spec_key_color,
            "spectrumOpacity": spectrum_opacity,
            "specBgColor": spec_bg_color,
            "specBgOpacity": spec_bg_opacity,
            "useBeatZoom": use_beat_zoom,
            "shearX": 0.0,
            "shearY": 0.0
        })

    preset = {
        "name": preset_name,
        "resolution": resolution,
        "fps": fps,
        "spectrums": spectrums_structure,
        "spectrumOpacity": spectrum_opacity,
        "specType": spec_type,
        "useBeatZoom": use_beat_zoom,
        "overlaySpeedConfig": overlay_speed_config,
        "specWidth": spec_width,
        "specHeight": spec_height,
        "specX": spec_x,
        "specY": spec_y,
        "specColor": spec_color,
        "specKeyColor": spec_key_color,
        "specBgColor": spec_bg_color,
        "specBgOpacity": spec_bg_opacity,
        "bgEffect": bg_effect,
        "bgVideoOverlays": bg_video_overlays,
        "loops": loops,
        "useIntro": use_intro,
        "introLineCount": intro_line_count,
        "introText1": intro_text1,
        "introText2": intro_text2,
        "introText3": intro_text3,
        "introSize1": intro_size1,
        "introSize2": intro_size2,
        "introSize3": intro_size3,
        "introConfig": intro_font_config,
        "useTitle": use_title,
        "titleMode": title_mode,
        "titleLineCount": title_line_count,
        "titleText1": title_text1,
        "titleText2": title_text2,
        "titleText3": title_text3,
        "titleSize1": title_size1,
        "titleSize2": title_size2,
        "titleSize3": title_size3,
        "titleConfig": title_font_config,
        "keyframes": get_default_keyframes_structure()
    }

    presets.append(preset)
    save_presets(presets)
    print(f"\nPreset '{preset_name}' berhasil disimpan.")
    time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER


def manage_presets_menu():
    """Menu Manajemen Preset Visual"""
    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        presets = get_presets()
        print("=========================================")
        print("            MANAJEMEN PRESET")
        print("=========================================")
        print(f"Preset Tersimpan: {len(presets)}\n")
        print("[1] Lihat Preset Tersimpan")
        print("[2] Buat Preset Visual Manual")
        print("[3] Hapus Preset")
        print("[4] Hapus Semua Preset")
        print("[0] Kembali\n")
        
        choice = input("Pilih Menu: ").strip()
        if choice == "0":
            return
        elif choice == "1":
            os.system('cls' if os.name == 'nt' else 'clear')
            if not presets:
                print("Belum ada preset tersimpan.")
            else:
                for i, p in enumerate(presets):
                    spec_count = len(p.get("spectrums", []))
                    if spec_count == 0 and p.get("specType") and p.get("specType") != "none":
                        spec_count = 1
                    ov_count = len(p.get("bgVideoOverlays", []))
                    has_kf = "Ya" if p.get("keyframes") else "Tidak"
                    print(f"[{i + 1}] {p.get('name')} -> (Spectrums: {spec_count} Layer, Overlays: {ov_count} Asset, Keyframes: {has_kf})")
            print("")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        elif choice == "2":
            create_preset_manually()
        elif choice == "3":
            os.system('cls' if os.name == 'nt' else 'clear')
            if not presets:
                print("Tidak ada preset.")
                time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
                continue
            for i, p in enumerate(presets):
                print(f"[{i + 1}] {p.get('name')}")
            del_choice = input("\nPilih nomor preset untuk dihapus: ").strip()
            if del_choice.isdigit():
                idx = int(del_choice) - 1
                if 0 <= idx < len(presets):
                    presets.pop(idx)
                    save_presets(presets)
                    print("Preset berhasil dihapus.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        elif choice == "4":
            save_presets([])
            print("Semua preset berhasil dikosongkan.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
