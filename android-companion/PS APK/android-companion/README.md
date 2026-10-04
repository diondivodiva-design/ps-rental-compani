# PS Rental Companion — Android TV

Companion app untuk Android TV / Android Box yang terhubung ke dashboard PS Rental Billing lewat WebSocket. Menerima perintah: `power_on`, `power_off`, `lock`, `unlock`, `show_message`, `shutdown_timer`.

Karena environment ini tidak bisa mengkompilasi APK, project sudah disusun lengkap dan **siap di-build** dengan salah satu dari 2 cara di bawah.

---

## Cara 1 — Build Online (TANPA install Android Studio) via GitHub Actions

1. Buat repo GitHub baru, upload folder `android-companion/` ini (push via git atau drag-drop di web).
2. Buka tab **Actions** → GitHub otomatis mendeteksi workflow `Build APK` di `.github/workflows/build-apk.yml`.
3. Klik **Run workflow** → tunggu ±5 menit.
4. Setelah selesai, di halaman run klik **Artifacts** → unduh `ps-rental-companion-apk`. Isinya file `app-debug.apk`.

APK ini **debug-signed** sehingga langsung bisa di-sideload tanpa Google Play.

## Cara 2 — Build Lokal dengan Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Giraffe atau lebih baru).
2. **File → Open** → pilih folder `android-companion/`.
3. Tunggu Gradle sync selesai (~2 menit, perlu internet).
4. **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
5. APK muncul di `app/build/outputs/apk/debug/app-debug.apk`.

---

## Install ke Android TV

### Metode A — ADB (paling cepat)
Pastikan **Developer Options → USB debugging / Network debugging** aktif di TV.
```bash
adb connect 192.168.x.x:5555     # IP Android TV
adb install -r app-debug.apk
```

### Metode B — Downloader / Send Files to TV
1. Upload `app-debug.apk` ke Google Drive / Dropbox.
2. Dari Android TV buka app **Downloader** (Play Store) → masukkan URL → install.

### Metode C — USB flashdisk
Copy APK ke flashdisk → tancapkan di TV → buka file manager → tap APK.

---

## Setup Pertama Kali

Setelah install, buka aplikasi **PS Rental Companion** di home TV. Isi:

| Field | Contoh | Keterangan |
|---|---|---|
| **Server URL** | `https://ps-rental-billing-11.preview.emergentagent.com` | Domain dashboard (http/https) |
| **Station ID** | `b4a9c1e2-...` | Salin dari dashboard admin → menu **Companion TV** → pilih station |

Klik **Save & Start**. Status akan berubah menjadi **ONLINE** dan muncul juga di dashboard operator.

Setelah ini companion akan:
- Auto-start saat TV booting
- Reconnect otomatis bila jaringan putus
- Berjalan sebagai foreground service (notifikasi persistent di status bar) agar tidak di-kill sistem

---

## Perintah yang Didukung

| Perintah | Efek di TV |
|---|---|
| `power_on` | Dismiss overlay hitam + wake screen |
| `power_off` | Tampilkan overlay hitam fullscreen "SESI BERAKHIR" |
| `lock` | Tampilkan overlay kunci — hanya hilang bila operator kirim `unlock` |
| `unlock` | Hilangkan overlay kunci |
| `show_message` | Toast fullscreen dengan pesan dari dashboard |
| `shutdown_timer` | Hitung mundur, lalu tampilkan overlay off |

> Catatan: Android TV tidak mengizinkan app mematikan panel TV fisik tanpa akses root/HDMI-CEC. Overlay fullscreen hitam adalah pola standar app rental PS komersial — pelanggan tidak bisa menggunakan TV sampai sesi dibuka kembali dari dashboard.

---

## Struktur Project
```
android-companion/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/psrental/companion/
│       │   ├── MainActivity.kt         # Setup screen
│       │   ├── CompanionService.kt     # WebSocket foreground service
│       │   ├── OverlayActivity.kt      # Lock/off overlay fullscreen
│       │   └── BootReceiver.kt         # Auto-start saat boot
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── .github/workflows/build-apk.yml
```

---

## Troubleshooting

| Gejala | Solusi |
|---|---|
| Status terus "Reconnecting" | Cek URL (harus `https://` untuk production), cek firewall, test di browser dulu |
| APK ditolak saat install | Aktifkan **Unknown sources** di Android TV settings |
| Overlay tidak muncul | Beri izin **Display over other apps** di Settings → Apps → PS Rental Companion |
| Service mati setelah 1 jam | Nonaktifkan **Battery optimization** untuk app ini |

---

## Keamanan (Opsional, Produksi)

Endpoint WebSocket saat ini publik — siapapun yang tahu `station_id` bisa koneksi. Untuk produksi tambahkan token di URL:
```
wss://domain.com/api/tv/ws/<station_id>?token=<secret>
```
dan validasi di backend. Beritahu saya bila ingin saya tambahkan lapisan ini.
