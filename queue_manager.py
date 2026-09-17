import os
import json
import time
import config
import utils


def get_queue():
    """Membaca daftar antrian tugas dari berkas queue.json"""
    if not os.path.exists(config.QueueFile):
        save_queue([])
    try:
        with open(config.QueueFile, "r", encoding="utf-8") as f:
            content = f.read().strip()
            if not content:
                return []
            data = json.loads(content)
            return data if isinstance(data, list) else []
    except Exception as e:
        utils.write_error_log(f"Eror saat membaca queue.json: {str(e)}")
        return []


def save_queue(queue):
    """Menyimpan daftar antrian tugas ke berkas queue.json"""
    try:
        cleaned_queue = []
        for item in queue:
            if hasattr(item, '__dict__'):
                cleaned_queue.append(item.__dict__)
            else:
                cleaned_queue.append(item)
        with open(config.QueueFile, "w", encoding="utf-8") as f:
            json.dump(cleaned_queue, f, indent=4, ensure_ascii=False)
    except Exception as e:
        utils.write_error_log(f"Eror saat menyimpan queue.json: {str(e)}")


def clear_queue(silent=False):
    """Membersihkan seluruh tugas di dalam antrian"""
    save_queue([])
    if not silent:
        print("Antrian berhasil dibersihkan.")


def show_queue():
    """Menampilkan rincian seluruh tugas yang sedang aktif di dalam antrian"""
    queue = get_queue()
    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("        ANTRIAN TUGAS RENDER GPU")
    print("=========================================\n")

    if not queue:
        print("Belum ada tugas di dalam antrian.\n")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return

    for i, task in enumerate(queue):
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
                "chromaKey": task.get("bgVideoOverlayChromaKey", "black")
            }]

        if not bg_video_overlays:
            overlay_str = "Nonaktif"
        else:
            overlay_names = []
            for idx, ov in enumerate(bg_video_overlays):
                f_name = os.path.basename(ov.get("file", ""))
                loop_st = "Loop: Ya" if ov.get("loop", True) else "Loop: Tidak"
                delay_st = f"Delay: {ov.get('delay', 0.0)}s" if ov.get('delay', 0.0) > 0 else "Main Overlay"
                overlay_names.append(f"FX#{idx+1}: {f_name} ({loop_st}, {delay_st})")
            overlay_str = " | ".join(overlay_names)

        spectrums_list = task.get("spectrums", [])
        if not spectrums_list and task.get("specType") and task.get("specType") != "none":
            spec_info = f"1 Layer ({task.get('specType')})"
        else:
            spec_info = f"{len(spectrums_list)} Layer Aktif"

        use_zoom = "Aktif (GPU)" if task.get("useBeatZoom", False) else "Nonaktif"
        sp_conf = task.get("overlaySpeedConfig", {})
        use_sp = "Aktif (GPU)" if sp_conf.get("active", False) else "Nonaktif"

        kf_data = task.get("keyframes", {})
        has_kf = "Aktif (CapCut GPU Engine)" if kf_data and any(len(v) > 1 for v in kf_data.values() if isinstance(v, list)) else "Standard (Static)"

        print(f"{i + 1}. {task.get('name')}")
        print(f"   Resolusi  : {task.get('resolution')} @ {task.get('fps')} FPS")
        print(f"   Spectrum  : {spec_info}")
        print(f"   Beat Sync : Zoom Beat={use_zoom} | Speed Modulation={use_sp}")
        print(f"   Keyframe  : {has_kf}")
        print(f"   Overlay FX: {overlay_str}")
        print(f"   Loop Video: {task.get('loops')} kali")
        print(f"   Identitas : {'Aktif (' + str(task.get('introLineCount')) + ' Baris)' if task.get('useIntro') else 'Nonaktif'}")
        print(f"   Judul Lagu: {'Aktif (Mode ' + str(task.get('titleMode')) + ')' if task.get('useTitle') else 'Nonaktif'}")
        print(f"   Output    : {task.get('output')}\n")
        
    time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER


def remove_task():
    """Menu interaktif untuk menghapus tugas spesifik atau mengosongkan antrian"""
    queue = get_queue()
    if not queue:
        print("\nTidak ada tugas di antrian.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return

    os.system('cls' if os.name == 'nt' else 'clear')
    print("=========================================")
    print("            HAPUS TUGAS")
    print("=========================================\n")
    print("[1] Hapus Tugas Spesifik (Tunggal)")
    print("[2] Hapus Semua Tugas Sekaligus")
    print("[0] Batal\n")
    opt = input("Pilihan: ").strip()
    
    if opt == "2":
        clear_queue(silent=True)
        print("Semua tugas di dalam antrian berhasil dihapus.")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        return
    elif opt == "1":
        os.system('cls' if os.name == 'nt' else 'clear')
        for i, task in enumerate(queue):
            print(f"[{i + 1}] {task.get('name')}")
        print("\n[0] Batal\n")
        
        choice = input("Pilih nomor tugas yang ingin dihapus: ").strip()
        if choice == "0" or not choice:
            return

        if choice.isdigit():
            index = int(choice) - 1
            if 0 <= index < len(queue):
                queue.pop(index)
                save_queue(queue)
                print("\nTugas berhasil dihapus.")
                time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
            else:
                print("Pilihan tidak valid.")
                time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
        else:
            print("Harap masukkan angka.")
            time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
