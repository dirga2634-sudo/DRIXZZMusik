# Drizzx Cam

Camera app berbasis CameraX + Jetpack Compose. Night mode multi-frame & HDR
exposure-bracket belum masuk, nyusul di iterasi berikutnya - itu bagian paling
kompleks (multi-frame alignment), sengaja dipisah biar digarap dengan hati-hati.

## Cara masukin ke workflow lo

1. Extract zip ini pake ZArchiver
2. Copy semua isinya ke folder repo (baru atau existing), commit + push via MGit
3. GitHub Actions otomatis build APK debug tiap push ke branch `main`, atau
   trigger manual lewat tab Actions > Build APK > Run workflow
4. Hasil APK: tab Actions > run terakhir > Artifacts > `DrizzxCam-debug-apk`

## Fitur yang udah jalan (fully functional, no placeholder)

**Capture inti**
- Foto - auto pilih kualitas terbaik yang device support
- Video - auto quality dengan fallback kalo device gak support kualitas tertinggi
- Ganti kamera depan/belakang, tap-to-focus, pinch-to-zoom
- Flash: off / on / auto - tombolnya otomatis hilang kalo device gak punya flash unit
- Auto-simpen ke galeri sistem (Pictures/DrizzxCam & Movies/DrizzxCam)
- Jalan dari minSdk 26 (Android 8.0) ke atas; scoped storage otomatis di Android 10+

**Pro mode** (tombol "PRO" di kanan atas, cuma nongol kalo Camera2 hardware
level device-nya FULL/LEVEL_3 - HP dengan LIMITED/LEGACY tetep dapet auto mode
yang solid, gak dipaksain)
- ISO manual, shutter speed (stepped, 1/8000 - 4s), exposure compensation
- White balance preset (Auto/Siang/Mendung/Lampu Pijar/Neon/Teduh)
- Fokus manual (diopter, dari infinity ke jarak terdekat device)
- Semua kontrol lewat `Camera2CameraControl`/`CaptureRequestOptions` (CameraX
  Camera2 interop) - bukan simulasi, beneran ngirim capture request manual

**Filter** (strip di atas mode switcher, cuma di mode Foto)
- 5 preset bawaan (Vivid/Mono/Cool/Warm/Fade) + Original
- Diterapkan beneran ke file hasil (bukan cuma preview) lewat ColorMatrix -
  saturasi, kontras, warmth - lalu di-encode ulang jadi JPEG
- Mode Original tetep lewat jalur cepat CameraX -> MediaStore langsung (gak ada
  decode/encode ulang, jadi kualitasnya gak turun kalo emang gak butuh filter)

**Config / "plugin" XML** (tombol gear di kanan atas)
- Semua Pro default + filter preset + kualitas JPEG disimpen di 1 file XML,
  persis konsep config GCam yang bisa di-share
- Export lewat system file picker (Storage Access Framework) - gak perlu izin
  storage tambahan
- Import: pilih file .xml siapapun (hand-edited atau hasil export lo sendiri),
  langsung kepake. Nambah filter baru = nambah 1 baris `<Filter>` di XML terus
  import lagi, gak perlu ubah kode
- Disimpen otomatis ke penyimpanan internal app juga, jadi tetep kepake
  walaupun app di-restart tanpa perlu re-import

## Contoh isi config XML

Ini yang di-export/di-import - hand-editable, gampang nambah filter baru:

```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<DrizzxCamConfig version="1">
    <Pro>
        <Iso auto="true" value="100" />
        <ShutterSpeedNs auto="true" value="16666666" />
        <WhiteBalance preset="auto" />
        <FocusDistanceDiopters auto="true" value="0.0" />
        <ExposureCompensationIndex>0</ExposureCompensationIndex>
    </Pro>
    <ImageProcessing jpegQuality="95" />
    <Filters>
        <Filter name="Vivid" saturation="1.35" contrast="1.15" warmth="0.05" />
        <Filter name="Mono" saturation="0.0" contrast="1.1" warmth="0.0" />
    </Filters>
</DrizzxCamConfig>
```

Mau nambah filter "Sunset"? Tinggal tambahin baris `<Filter name="Sunset"
saturation="1.2" contrast="1.05" warmth="0.3" />` di dalem `<Filters>`, import
lagi file-nya lewat tombol gear > Import XML.

## Yang sengaja belum dimasukin

- Night mode multi-frame & HDR exposure-bracket - paling kompleks (perlu
  capture beberapa frame lalu align + merge), digarap terpisah biar teliti
- Filter cuma jalan di foto, belum di video (butuh pipeline GL real-time
  terpisah, jauh lebih berat)
- Preview filter di viewfinder masih belum live-tinted - milih filter di
  strip langsung berlaku ke hasil capture, tapi preview kamera tetep
  nampilin gambar asli sampe di-capture
- Minifikasi/R8 di release build - sengaja dimatiin dulu (`isMinifyEnabled = false`)
  biar kalo ada crash gampang dilacak, tinggal dinyalain lagi kalo udah stabil
- Deteksi "permission ditolak permanen" masih manual (tombol "Buka Pengaturan"
  selalu muncul setelah penolakan pertama, bukan deteksi otomatis)

## Kenapa gak ada gradlew

Gradle wrapper butuh `gradle-wrapper.jar` (file binary) yang gak bisa dibikin dari
tool text-only. Solusinya: workflow CI provision Gradle 8.14.3 langsung lewat
`gradle/actions/setup-gradle`, jadi gak butuh wrapper sama sekali. Kalo nanti lo
pegang Android Studio, tinggal jalanin `gradle wrapper` sekali buat generate
gradlew seperti biasa.

## Versi yang dipakai

Dicek per Agustus 2026 biar gak mismatch di CI:

| Komponen | Versi | Catatan |
|---|---|---|
| AGP | 8.13.2 | Bukan AGP 9.x - AGP 9 ganti cara handle Kotlin plugin, belum cukup teruji buat dipasangin sekarang |
| Gradle | 8.14.3 | Diprovision di CI, bukan lewat wrapper |
| Kotlin | 2.3.20 | jvmTarget di-set lewat `kotlin { compilerOptions {} }`, bukan `kotlinOptions` lama (udah jadi hard error di Kotlin 2.2+) |
| Compose BOM | 2026.04.01 | BOM setelah ini (1.12+) butuh compileSdk 37 + AGP 9 |
| CameraX | 1.5.1 | Bukan 1.6.0 - Google eksplisit rekomendasiin 1.5.1 buat bug fixes |
| compileSdk | 36 | Compose 1.11 & CameraX 1.5.1 sekarang mensyaratkan minimal compileSdk 35 (ketauan dari error AAR metadata pas build) |
| targetSdk | 34 | Sengaja tetep di 34, sama kayak Android di HP Infinix lo - compileSdk boleh lebih tinggi dari targetSdk, ini normal |
| minSdk | 26 | Android 8.0 ke atas |

Package name: `com.drizzx.camera`, app name "Drizzx Cam" - dua-duanya gampang
diganti kalo mau rename/rebrand.
