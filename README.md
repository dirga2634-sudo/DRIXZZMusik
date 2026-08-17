# Drizzx Cam

Camera app berbasis CameraX + Jetpack Compose. Ini versi pertama (core capture) -
Pro mode, Night mode, dan Filter belum masuk, nyusul di iterasi berikutnya.

## Cara masukin ke workflow lo

1. Extract zip ini pake ZArchiver
2. Copy semua isinya ke folder repo (baru atau existing), commit + push via MGit
3. GitHub Actions otomatis build APK debug tiap push ke branch `main`, atau
   trigger manual lewat tab Actions > Build APK > Run workflow
4. Hasil APK: tab Actions > run terakhir > Artifacts > `DrizzxCam-debug-apk`

## Fitur yang udah jalan (fully functional, no placeholder)

- Foto - auto pilih kualitas terbaik yang device support
- Video - auto quality dengan fallback kalo device gak support kualitas tertinggi
- Ganti kamera depan/belakang
- Flash: off / on / auto - tombolnya otomatis hilang kalo device gak punya flash unit
- Tap-to-focus + pinch-to-zoom di viewfinder
- Auto-simpen ke galeri sistem (Pictures/DrizzxCam & Movies/DrizzxCam), langsung
  kebaca di app galeri manapun
- Jalan dari minSdk 26 (Android 8.0) ke atas; scoped storage otomatis dipakai di
  Android 10+, permission storage lama cuma diminta di Android 9 ke bawah

## Yang sengaja belum dimasukin

- Pro mode manual (ISO/shutter/fokus/WB) - butuh deteksi Camera2 hardware level
  per device dulu supaya UI-nya adaptif, ini next step paling logis
- Night mode multi-frame & HDR exposure-bracket
- Filter/preset warna
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
