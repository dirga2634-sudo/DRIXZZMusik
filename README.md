# Gomouse Pro

Keymapper/controller mapper untuk Android — konsep seperti GGMouse, tapi branding, UI, logo, dan seluruh implementasinya orisinal. Memetakan keyboard, mouse, dan controller ke touch di layar lewat editor visual, memakai **hanya API resmi Android** (AccessibilityService gesture dispatch, overlay window, pointer capture) — tanpa root, tanpa exploit, tanpa API tersembunyi.

Dibuat untuk dibangun 100% lewat **GitHub Actions** — tidak perlu Android Studio atau ADB sama sekali.

---

## 1. Struktur Repository

```
Gomouse-Pro/
├── .github/workflows/
│   ├── build.yml                  # build debug otomatis tiap push + manual trigger
│   └── release.yml                # build release + GitHub Release otomatis
├── app/
│   ├── build.gradle                # dependencies, SDK versions, signing conditional
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/gomouse/pro/
│       │   ├── GomouseApplication.java
│       │   ├── model/              # InputMapping, Profile, ActionType, dll — data class
│       │   ├── storage/            # ProfileRepository — simpan/baca profile JSON lokal
│       │   ├── service/            # GomouseAccessibilityService (engine keymapping),
│       │   │                       # OverlayService (window overlay + foreground service)
│       │   ├── overlay/            # OverlayRootView, OverlayButtonView — render saat main
│       │   ├── editor/             # EditorActivity, EditorCanvasView, EditMappingDialog
│       │   ├── ui/                 # MainActivity, SettingsActivity, adapter RecyclerView
│       │   └── util/               # GestureBuilder, PermissionUtils, InputCodeUtils, dll
│       └── res/                    # layout, drawable (vector, tanpa aset GGMouse), values, xml
├── build.gradle                    # root: deklarasi versi Android Gradle Plugin
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── .gitignore
└── README.md                       # file ini
```

### Kenapa `gradle-wrapper.jar` tidak ikut di-commit

`gradle-wrapper.jar` adalah file **binary**, bukan teks — jadi bukan sesuatu yang aman untuk dibuat manual lewat proses seperti ini. Daripada menyertakan file binary yang tidak bisa diverifikasi, kedua workflow (`build.yml` dan `release.yml`) punya langkah **"Regenerate Gradle wrapper"** yang menjalankan `gradle wrapper --gradle-version 9.5.0` di awal build — jadi `gradlew` selalu lengkap dan konsisten dengan versi Gradle yang dipakai, setiap kali CI jalan, tanpa kamu perlu melakukan apa pun. `gradlew` (script shell) dan `gradlew.bat` sendiri sudah ikut di-commit seperti biasa karena keduanya file teks biasa.

Kalau suatu saat kamu punya akses ke komputer dengan Gradle terpasang, kamu juga bisa menjalankan `gradle wrapper --gradle-version 9.5.0` sendiri di root project untuk menghasilkan jar yang sama persis.

---

## 2. Cara Upload ke GitHub

Dari HP kamu (MGit atau Code on the Go), alurnya sama seperti project Android lain yang biasa kamu push:

1. Extract folder `Gomouse-Pro` hasil zip ini.
2. Inisialisasi repo git di folder tersebut (`git init` kalau pakai terminal/Code on the Go), atau buat repo baru langsung di GitHub lalu import foldernya lewat MGit.
3. Commit semua file:
   ```
   git add .
   git commit -m "Initial commit: Gomouse Pro"
   ```
4. Tambahkan remote dan push:
   ```
   git remote add origin https://github.com/<username>/Gomouse-Pro.git
   git branch -M main
   git push -u origin main
   ```
5. Push pertama ke branch `main` ini otomatis memicu `build.yml`.

---

## 3. Cara Menjalankan GitHub Actions

- **Otomatis**: setiap `git push` ke branch `main` (atau pull request ke `main`) langsung menjalankan `build.yml`.
- **Manual**: buka tab **Actions** di repo GitHub → pilih workflow **"Build Gomouse Pro"** di sidebar kiri → klik **"Run workflow"** → pilih `assembleDebug` atau `assembleRelease` → **Run workflow**.
- **Release**: push tag versi (`git tag v1.0.0 && git push origin v1.0.0`), atau jalankan workflow **"Release Gomouse Pro"** secara manual dari tab Actions dan isi version name-nya.

---

## 4. Lokasi APK Hasil Build

Setiap run `build.yml` yang sukses akan menghasilkan **Artifact** bernama `gomouse-pro-assembleDebug-<nomor_run>` (atau `assembleRelease`) yang bisa diunduh di:

**Actions → (pilih run yang sukses, tanda centang hijau) → bagian "Artifacts" di bagian bawah halaman run tersebut.**

Artifact ini berisi file `.apk` langsung dari `app/build/outputs/apk/debug/` (atau `/release/`). Unduh, lalu install seperti APK biasa (aktifkan "Install from unknown sources" kalau diminta).

Untuk `release.yml`, APK juga otomatis dilampirkan ke halaman **Releases** repo (Releases → versi terkait → assets di bagian bawah).

---

## 5. Cara Membuat Release APK Bertanda Tangan (Signed)

Tanpa 4 secret di bawah, `release.yml` **tetap jalan** tapi menghasilkan APK **debug** (bukan fake-signed release — sesuai instruksi, kami tidak pernah membuat signing config palsu). Untuk release APK yang benar-benar ditandatangani:

### a. Buat keystore (sekali saja, lewat mesin mana pun yang punya JDK — atau minta tolong siapa pun yang punya laptop sebentar)
```
keytool -genkeypair -v -storetype PKCS12 \
  -keystore release.keystore \
  -alias gomouse-pro \
  -keyalg RSA -keysize 2048 -validity 10000
```
Simpan `release.keystore` dan ingat password + alias yang kamu masukkan.

### b. Encode keystore ke base64
```
base64 -w0 release.keystore > release.keystore.b64
```
(di Windows/PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File release.keystore.b64`)

### c. Tambahkan 4 GitHub Secrets
Buka **Settings → Secrets and variables → Actions → New repository secret** di repo GitHub, tambahkan:

| Nama secret | Isi |
|---|---|
| `KEYSTORE_BASE64` | isi file `release.keystore.b64` |
| `KEYSTORE_PASSWORD` | password keystore kamu |
| `KEY_ALIAS` | `gomouse-pro` (atau alias yang kamu pakai) |
| `KEY_PASSWORD` | password key kamu |

### d. Jalankan release
Push tag baru (`git tag v1.0.1 && git push origin v1.0.1`) atau jalankan workflow **Release Gomouse Pro** manual. Begitu ke-4 secret terisi, `release.yml` otomatis mendeteksinya dan mem-build `assembleRelease` yang sudah ditandatangani.

---

## 6. Troubleshooting Kalau Gradle Gagal

Log lengkap selalu ada di **Actions → run yang gagal (tanda silang merah) → klik step "Build with Gradle"** — ini menampilkan stack trace Gradle apa adanya (workflow sengaja pakai `--stacktrace --info` dan tidak menyembunyikan error).

Masalah paling umum:

- **"SDK location not found" / lisensi SDK** — seharusnya tidak terjadi karena `android-actions/setup-android@v3` sudah otomatis menyetujui semua lisensi SDK yang diperlukan. Kalau tetap muncul, cek apakah step "Set up Android SDK" ada di log dan sukses.
- **Versi dependency tidak ketemu ("Could not find ...")** — biasanya karena versi library tertentu sudah ditarik dari Maven Central. Naikkan versi di `app/build.gradle` untuk dependency yang disebut di error.
- **Build sukses tapi tidak ada APK** — cek step "Check APK was produced"; kalau ini yang gagal, kemungkinan `assembleDebug`/`assembleRelease` sebenarnya error di step sebelumnya tapi tidak terdeteksi — lihat log lengkap step Gradle.
- **Release APK unsigned** — berarti salah satu dari 4 secret (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) belum diisi atau namanya typo. Cek ulang di Settings → Secrets.
- **Error aneh yang gak nyambung sama kode (misalnya tiba-tiba banyak `attribute res-auto:... not found` padahal sebelumnya build sempat lolos linking tahap itu)** — kemungkinan besar cache Gradle lintas-run yang basi, apalagi kalau run sebelumnya gagal (Gradle tetap menyimpan cache walau build gagal). Workflow ini sudah set `cache-disabled: true` di step "Set up Gradle" untuk sementara supaya setiap run mulai bersih total. Begitu build sudah hijau beberapa kali berturut-turut, boleh dihapus baris itu supaya build lebih cepat lagi.
- **`onKeyEvent` / gesture tidak jalan di HP** — ini bukan gagal build, tapi runtime: pastikan izin **"Display over other apps"** dan **Accessibility Service Gomouse Pro** sama-sama aktif (Settings di dalam aplikasi menampilkan status keduanya secara real-time).
- **Ingin build ulang dari nol** — hapus cache dengan menambahkan step `./gradlew clean` sebelum `assembleDebug` di `build.yml`, atau re-run job dari Actions dengan opsi "Re-run all jobs".

---

## Fitur

- Visual key mapping editor fullscreen: tambah, drag, resize, undo/redo, grid/snap, lock, hide/show, reset layout.
- Tipe kontrol: Tap, Hold, Double Tap, Swipe, Virtual Joystick (WASD atau mouse-look), D-Pad (4/8 arah).
- Binding fisik: keyboard, tombol gamepad/controller, tombol mouse — semua ditangkap dengan cara "tekan tombolnya langsung" di editor.
- Profile per game: sensitivity X/Y, opacity overlay, grid snap, import/export JSON, disimpan lokal otomatis.
- Dark UI, aksen biru, Material Design 3, logo & ikon orisinal (vector, tanpa aset GGMouse).
- Device detection real-time (keyboard/mouse/controller yang terhubung).

## Keterbatasan Android (dibaca sebelum lapor "bug")

Sesuai instruksi awal, tidak ada exploit/bypass/root di project ini — jadi ada beberapa batas resmi dari Android sendiri yang perlu diketahui:

- **Kenapa overlay-nya banyak window kecil, bukan satu window besar** — tiap kontrol (tombol, joystick, dpad) punya window overlay-nya sendiri, pas ukurannya. Alternatifnya (satu window fullscreen dengan "lubang" touchable di posisi tombol) butuh `ViewTreeObserver.InternalInsetsInfo`, yang ternyata API tersembunyi/internal, bukan bagian dari Android SDK publik — jadi sengaja tidak dipakai walau lebih umum dipakai contoh-contoh chat-heads di internet. Dengan window kecil per kontrol, area kosong otomatis tembus ke game di baliknya karena memang tidak ada window apa pun di situ.
- **Mouse global (klik di mana saja, tanpa kursor di atas tombol)** memerlukan mode **"Mouse Mode"** (toggle bulat kecil di pojok overlay) yang mengaktifkan Pointer Capture resmi Android lewat satu window kecil tambahan yang focusable. Di luar mode ini, klik mouse tetap berfungsi persis seperti sentuhan biasa — hanya aktif kalau kursor memang berada di atas kontrol yang bersangkutan.
- **AccessibilityService tidak menerima event mouse generik** dari Android (hanya keyboard + tombol gamepad lewat `onKeyEvent`) — ini keterbatasan platform, bukan bug, makanya penanganan mouse ditaruh di level window overlay (`OverlayService`), bukan di accessibility service.
- **Multi-touch bersamaan** (misalnya menahan WASD sambil menekan tombol Fire) memakai mekanisme resmi `StrokeDescription.continueStroke`. Ini didukung resmi oleh Android, tapi perilaku detailnya bisa sedikit berbeda antar versi Android/skin OEM.
- **OEM battery optimization** (Xiaomi/MIUI, Oppo/ColorOS, Vivo, dll) sering membunuh accessibility service atau overlay di background. Satu-satunya solusi resmi adalah meminta user mengizinkan lewat Settings (tombol "Ignore battery optimization" sudah disediakan di Settings aplikasi) — tidak ada cara lain yang tidak root untuk memaksanya.
- Aplikasi ini **tidak pernah** mengklaim bisa menembus proteksi anti-cheat game atau memodifikasi memori/file game — ia murni mensimulasikan sentuhan layar di posisi yang kamu tentukan sendiri di editor.
