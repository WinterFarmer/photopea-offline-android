# Photopea Offline Wrapper (Android)

Aplikasi wrapper ringan (~22 KB) berbasis Android System WebView untuk menjalankan **Photopea** secara 100% offline.

## Fitur Utama
- **Persistent Engine Lock:** Menyimpan aset inti Photopea (`photopea.com` & `vecpea.com`) ke dalam direktori *User Data* (`getFilesDir()`), sehingga kebal terhadap pembersihan cache sistem (*Clear Cache*).
- **Auto-Immersive Landscape:** Otomatis masuk ke mode *Fullscreen* (menyembunyikan Status Bar, Navigation Bar, dan area Notch) saat orientasi layar Landscape.
- **Native File I/O:** Mendukung pembukaan file lokal (`File > Open`) dan penyimpanan/ekspor langsung (`Save as PSD` / `Export as PNG, JPG, dll`) ke folder `Downloads` perangkat menggunakan `MediaStore`.
- **Adaptive Vector Icon:** Menggunakan ikon vektor XML tajam yang mendukung *Adaptive Icon* dan *Monochrome Themed Icon*.
- **Ultra-Lightweight:** Dikonfigurasi dengan optimasi R8/ProGuard.
