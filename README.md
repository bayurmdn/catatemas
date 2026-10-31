# Catat Emas

Aplikasi Android sederhana untuk mencatat kepemilikan emas (merek + gram) dan langsung melihat **total nilai** berdasarkan harga emas terbaru.

## Unduh & pasang

**[⬇ Unduh CatatEmas.apk](https://github.com/bayurmdn/catatemas/raw/ccr-b92d6e00-s42y5z/release/CatatEmas.apk)**

1. Buka tautan di atas dari HP Android (Android 8.0 ke atas).
2. Setelah selesai diunduh, ketuk file `CatatEmas.apk`.
3. Jika muncul peringatan, izinkan **"Instal aplikasi tidak dikenal"** untuk browser/File Manager, lalu ketuk **Instal**.
4. Google Play Protect mungkin menampilkan peringatan karena aplikasi tidak berasal dari Play Store — pilih **Tetap instal**.

## Fitur

- Catat emas cukup dengan **merek** dan **berat (gram)**; merek yang sama digabung otomatis.
- **Total nilai** dihitung otomatis dari harga emas terbaru, diperbarui setiap aplikasi dibuka (dan lewat tombol **Perbarui**).
- **Perubahan nilai** sejak hari sebelumnya (▲/▼ Rupiah dan %).
- Ringkasan **per merek** (total gram, harga/gram, nilai).
- Harga per merek bisa diatur:
  - **Spot dunia** (default),
  - **Spot ± %**: mis. `+8` untuk harga jual logam mulia bermerek atau `-3` untuk harga buyback. Tetap ikut naik-turun otomatis.
  - **Manual (Rp)**: harga tetap per gram.
- **Cadangkan / Pulihkan** data dalam bentuk teks JSON.
- Data tersimpan di HP (tidak dikirim ke server mana pun).

## Sumber harga

| Data | Sumber | Catatan |
|---|---|---|
| Harga emas (XAU/USD per troy ounce) | [gold-api.com](https://gold-api.com) | Gratis, tanpa API key |
| Kurs USD/IDR | [open.er-api.com](https://www.exchangerate-api.com/docs/free) (cadangan: [frankfurter.dev](https://frankfurter.dev)) | Kurs diperbarui harian |

Rumus: `harga per gram (Rp) = XAU/USD × kurs USD/IDR ÷ 31,1034768`.

> Harga spot adalah harga emas murni 24K di pasar dunia. Harga jual/buyback merek seperti Antam, UBS, atau Galeri24 biasanya berbeda dari spot; gunakan pengaturan **Spot ± %** atau **Manual** per merek agar nilainya sesuai.

## Build sendiri

Membutuhkan JDK 17+ dan Android SDK (platform 35).

```bash
./gradlew assembleRelease
# hasil: app/build/outputs/apk/release/app-release.apk
```

Setiap push juga membangun APK otomatis lewat GitHub Actions (tab **Actions** → artefak `CatatEmas-apk`).

Struktur: aplikasi native Android (Java) dengan antarmuka HTML di `app/src/main/assets/index.html`, penyimpanan lewat `SharedPreferences`, dan pengambilan harga lewat jembatan native (`MainActivity.java`).

### Tanda tangan APK

Keystore **tidak** disimpan di repo. Build memakai `keystore/catatemas.jks` (diabaikan git) atau secret GitHub
`CATATEMAS_KEYSTORE_BASE64`, `CATATEMAS_STORE_PASSWORD`, `CATATEMAS_KEY_PASSWORD`. Tanpa keystore, build memakai debug key.
Android hanya menerima pembaruan aplikasi yang ditandatangani dengan kunci yang sama; bila kunci berbeda, cadangkan data, hapus aplikasi lama, pasang yang baru, lalu pulihkan data.
