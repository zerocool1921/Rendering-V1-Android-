import os
import sys
import time
import traceback


def select_hardware_mode_menu():
    """
    Submenu Pengunci Mode Akselerasi Hardware Manual Pilihan Pengguna.
    """
    import config
    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        curr_mode = config.STATE.get("user_hardware_mode", "AUTO")
        curr_vendor = config.STATE.get("gpu_vendor", "CPU")
        curr_vcodec = config.STATE.get("ffmpeg_vcodec", "libx264")

        print("=========================================================")
        print("     PENGATURAN MODE AKSELERASI HARDWARE (GPU / CPU)")
        print("=========================================================")
        print(f" Mode Aktif Saat Ini : [{curr_mode}] -> Driver: {curr_vendor} ({curr_vcodec})")
        print("=========================================================\n")
        print(" [1] Mode AUTO DETECT (Deteksi Otomatis Sistem) [DIANJURKAN]")
        print(" [2] Paksa Mode NVIDIA CUDA (NVENC)    -> PC dengan VGA NVIDIA")
        print(" [3] Paksa Mode INTEL QuickSync (QSV)  -> Laptop/PC Intel HD Graphics")
        print(" [4] Paksa Mode Android MediaCodec     -> HP Android via Termux (hemat baterai)")
        print(" [5] Paksa Mode CPU (libx264)          -> Mode Darurat (100% Anti-Crash)")
        print(" [0] Kembali ke Menu Utama\n")

        opt = input("Pilih Nomor Mode Hardware: ").strip()
        if opt == "1":
            config.set_manual_hardware_mode("AUTO")
            print("\nMode diubah ke AUTO DETECT.")
            time.sleep(1)
            break
        elif opt == "2":
            config.set_manual_hardware_mode("NVENC")
            print("\nMode dikunci ke NVIDIA CUDA (NVENC).")
            time.sleep(1)
            break
        elif opt == "3":
            config.set_manual_hardware_mode("QSV")
            print("\nMode dikunci ke INTEL QuickSync (QSV).")
            time.sleep(1)
            break
        elif opt == "4":
            config.set_manual_hardware_mode("MEDIACODEC")
            print("\nMode dikunci ke Android MediaCodec.")
            time.sleep(1)
            break
        elif opt == "5":
            config.set_manual_hardware_mode("CPU")
            print("\nMode dikunci ke CPU (libx264).")
            time.sleep(1)
            break
        elif opt == "0":
            break


def main():
    import config
    import utils
    import queue_manager
    import presets
    import renderer
    import wizard

    # Inisialisasi Deteksi Hardware GPU & Lingkungan Kerja
    config.initialize_app()

    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        queue = queue_manager.get_queue()

        gpu_name = config.STATE.get("gpu_name", "Software Render")
        gpu_vendor = config.STATE.get("gpu_vendor", "CPU")
        vram_mb = config.STATE.get("gpu_memory_total_mb", 0)
        hwaccel = config.STATE.get("ffmpeg_hwaccel_type", "None")
        hw_mode = config.STATE.get("user_hardware_mode", "AUTO")

        vram_info = f" ({vram_mb} MB VRAM)" if vram_mb > 0 else ""
        status_gpu = f"[{gpu_vendor}] {gpu_name}{vram_info} | Mode: {hw_mode} | Codec: {config.STATE.get('ffmpeg_vcodec')}"

        print("=========================================================================")
        print("         SISTEM MANAJEMEN RENDER VISUALIZER GPU by BASTOMI")
        print("=========================================================================")
        print(f" Accelerator Engine : {status_gpu}")
        print(f" Antrian Tugas Aktif: {len(queue)} Tugas\n")
        print(" [1] Tambah Tugas Baru (Wizard / Batch Subfolder)")
        print(" [2] Lihat Antrian Aktif")
        print(" [3] Edit Rincian Tugas")
        print(" [4] Hapus Tugas")
        print(" [5] Manajemen Preset Visual")
        print(" [6] Mulai Render Antrian (Akselerasi Hardware)")
        print(" [7] Ganti Mode Hardware (NVIDIA / Intel QSV / Android MediaCodec / CPU)")
        print(" [0] Keluar\n")

        choice = input("Pilih Menu: ").strip()
        if choice == "1":
            wizard.add_task_wizard()
        elif choice == "2":
            queue_manager.show_queue()
        elif choice == "3":
            wizard.edit_task()
        elif choice == "4":
            queue_manager.remove_task()
        elif choice == "5":
            presets.manage_presets_menu()
        elif choice == "6":
            renderer.start_queue_render()
        elif choice == "7":
            select_hardware_mode_menu()
        elif choice == "0":
            if os.path.exists(config.TempFolder):
                for f in os.listdir(config.TempFolder):
                    fp = os.path.join(config.TempFolder, f)
                    try:
                        if os.path.isfile(fp):
                            os.remove(fp)
                    except Exception:
                        pass
            sys.exit(0)
        else:
            print("\nPilihan tidak valid.")
            time.sleep(1)


def _log_fatal_crash_to_file(exc):
    """
    PERBAIKAN: sebelumnya penangkap paling luar ini HANYA mencetak traceback
    ke layar (traceback.print_exc()) dan TIDAK PERNAH menulisnya ke
    error.txt -- itu sebabnya error.txt bisa kosong walau layar sudah
    menampilkan "TERJADI KESALAHAN/CRASH". Fungsi ini berdiri sendiri
    (tidak bergantung pada berhasilnya import config/utils) supaya tetap
    bisa mencatat traceback lengkap ke error.txt bahkan kalau crash terjadi
    sebelum modul lain sempat ter-import.
    """
    try:
        script_root = os.path.dirname(os.path.abspath(__file__))
        error_log_path = os.path.join(script_root, "error.txt")
        timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
        tb_text = traceback.format_exc()
        with open(error_log_path, "a", encoding="utf-8") as f:
            f.write(f"\n{'=' * 80}\n[{timestamp}] CRASH FATAL DI LUAR PROSES RENDER TUGAS\n{'=' * 80}\n{tb_text}\n")
        return error_log_path
    except Exception as log_err:
        print(f"[PERINGATAN] Gagal menulis crash fatal ke error.txt: {log_err}")
        return None


if __name__ == "__main__":
    try:
        main()
    except BaseException as e:
        os.system('cls' if os.name == 'nt' else 'clear')
        print("\n=========================================================")
        print("       TERJADI KESALAHAN/CRASH SAAT MENJALANKAN SCRIPT")
        print("=========================================================\n")
        traceback.print_exc()
        saved_path = _log_fatal_crash_to_file(e)
        print("\n=========================================================")
        if saved_path:
            print(f"Traceback lengkap di atas SUDAH disimpan ke: {saved_path}")
        print("Pesan Eror Tertangkap! Silakan baca baris eror di atas.")
        print("=========================================================\n")
        time.sleep(1.2)  # Auto-lanjut tanpa perlu tekan ENTER
