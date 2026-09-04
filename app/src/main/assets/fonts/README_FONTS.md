# Font Pengganti Legal — BELUM LENGKAP (jujur ditandai, lihat Appendix C)

Sesi ini **tidak punya akses jaringan** (network egress dimatikan di container), jadi file
`.ttf` fisik pengganti bebas lisensi **belum bisa didownload/dibundel** di sesi ini. Yang
sudah disiapkan hanya mapping nama family (wajib dipakai sesi manapun yang menyelesaikan ini):

| Nama family di UI (sama seperti sumber asli) | Pengganti bebas lisensi yang WAJIB dipakai | Sumber |
|---|---|---|
| Arial | Liberation Sans | metric-compatible, lisensi OFL/GPL+exception |
| Courier New | Liberation Mono | metric-compatible |
| Georgia | Gelasio | Google Fonts, OFL |
| Impact | Anton | Google Fonts, OFL |
| Times New Roman | Liberation Serif | metric-compatible |
| Verdana | Verdana-pengganti: DejaVu Sans | metric-compatible |
| Comic Sans MS | Comic Neue | Google Fonts, OFL |
| Segoe UI | Noto Sans | Google Fonts, OFL |
| Century Gothic | Poppins | Google Fonts, OFL |
| Garamond | EB Garamond | Google Fonts, OFL |

**Langkah yang WAJIB dilakukan sesi berikutnya (yang punya akses jaringan) sebelum rilis:**
1. Download 10 file `.ttf` di atas dari Google Fonts / Liberation Fonts Project.
2. Taruh persis di `app/src/main/assets/fonts/<nama_family_pengganti>.ttf`.
3. Pastikan `FontAssetResolverImpl.kt` (dimiliki Fase 2) memetakan nama family ASLI (kolom
   kiri tabel) ke file pengganti (kolom tengah) — TIDAK mengubah nama yang tampil di UI wizard.
4. Centang ulang checklist Appendix A.6 setelah file fisik ada.

Status: ❌ file `.ttf` fisik belum ada. ✅ mapping nama & sumber pengganti sudah lengkap.
