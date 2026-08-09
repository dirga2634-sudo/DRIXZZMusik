# Game Booster

Aplikasi Android: boost game (bebasin RAM+cache, refresh rate, DND, overlay real-time) dengan
efek geledek/petir (suara sintetis + animasi) pas mau meluncur ke game. 3 mode
(Performa/Seimbang/Hemat Daya) yang beneran ngubah perilaku app, bukan kosmetik doang.

100% Android SDK resmi (+ Shizuku opsional buat data & kontrol lebih dalam) -- tanpa root,
tanpa modifikasi kernel/CPU-GPU governor/partisi sistem, nol risiko bootloop.

## Struktur
- `MainActivity` -- BottomNavigationView (tab **Games** & **Settings**)
- `fragment/GamesFragment` -- banner status Shizuku (biru=nyambung, merah=belum) di paling
  atas, lalu daftar aplikasi terinstall, tap buka ModeSelectActivity
- `fragment/SettingsFragment` -- pusat koneksi Shizuku + toggle Overlay Performa manual
- `ModeSelectActivity` -- gauge RAM/CPU + 3 kartu mode, sebelum boost
- `BoostActivity` -- fullscreen loading: animasi bolt + progress + status, jalanin RAM/cache
  cleanup + aksi mode (overlay/DND/refresh rate) di background, efek petir pas 100%, lalu
  launch game target
- `OverlayService` -- foreground service, floating widget draggable (FPS, baterai, jaringan)
- `GameBoosterApp` + `util/CrashHandler` -- nangkep crash apapun, nulis log ke file
- `util/SoundEffects` -- suara "geledek" sintetis (noise burst + rumble), bukan file eksternal
- `util/CacheManager`, `util/RamManager` -- cache & RAM, API resmi Android
- `util/AppListLoader` -- query app via `<queries>` (bukan QUERY_ALL_PACKAGES)
- `util/PrefsManager` -- SharedPreferences (game terakhir di-boost)
- `util/ShizukuHelper`, `util/ShellServiceManager`, `util/ShizukuMetrics` -- integrasi Shizuku
  (opsional) buat FPS/CPU% asli dan kontrol refresh rate
- `ShellUserService` + `aidl/IShellService.aidl` -- kode yang jalan di proses shell/ADB lewat
  Shizuku UserService

## Soal "FPS masih 60 padahal lag"
Ini kemungkinan besar tanda Shizuku belum kesambung -- cek banner di paling atas tab Games:
**merah = belum, biru = udah**. Kalau merah, FPS di overlay masih pakai estimasi vsync (yang
memang bakal nongkrong deket refresh rate layar hampir selalu, itu bukan bug, itu dokumentasi
apa adanya yang udah dijelasin di bagian atas). Kalau bannernya BIRU tapi FPS tetap gak
berubah/gak match sama lag yang kerasa, itu baru genuinely bug di jalur real-FPS -- kirim
screenshot kondisi itu spesifik (banner biru + FPS aneh) biar bisa didiagnosis, karena dua
skenario itu butuh penanganan yang beda banget.

## Animasi Boost -- versi baru (HUD sci-fi)
Berdasar referensi video yang dikasih, diadaptasi gaya "HUD sci-fi" (cincin ganda + bolt di
tengah + petir prosedural) -- bukan versi "awan badai" karena lebih nyambung ke tema game dan
gak butuh asset gambar/video yang bikin APK gede:
- 2 cincin (progress ring dalam + ring dekoratif luar) + background radial gradient buat depth
- `LightningView` -- custom View yang gambar petir PROSEDURAL (jalur zigzag acak dari kode,
  bukan gambar statis/video) yang nyembur dari tengah pas progress 100%, beda dikit tiap kali
- Suara "geledek" disintesis ulang (lapisan zap + crack + rumble, lebih dramatis dari versi
  sebelumnya) -- tetap generate langsung di kode, bukan file, jadi gak ada masalah lisensi
- Flash layar + bolt membesar bareng petir & suara, ada jeda ~450ms sebelum lompat ke game

## Crosshair Overlay (baru)
Toggle terpisah di tab Settings. Reticle sederhana (lingkaran + 4 garis + titik tengah, vector
asli bukan emoji) di tengah layar, 100% click-through (`FLAG_NOT_TOUCHABLE`) jadi gak
menghalangi kontrol game sama sekali. Independen dari Overlay Performa -- bisa nyala salah
satu atau dua-duanya. Servicenya (`CrosshairService`) sengaja dipisah dari `OverlayService`
biar dua fitur ini bisa dinyalain/dimatiin independen tanpa saling ganggu.

## Kenapa CPU/GPU gak "di-boost" beneran
Nulis ke governor CPU/GPU (buat ubah clock speed) butuh akses setara root. Shizuku (level
shell/ADB) BIASANYA ditolak sistem buat itu di device retail biasa, dan kalaupun kebetulan
tembus di device tertentu, itu justru area paling berisiko bikin instabilitas/reboot --
persis yang dari awal mau dihindari. Jadi ini SENGAJA tidak diimplementasikan.

**Yang beneran diimplementasikan sebagai gantinya (dan ini nyata, bukan kosmetik):**
- **Refresh rate layar** (lewat Shizuku, `settings put system peak_refresh_rate`) -- Performa
  maksimalin ke refresh rate tertinggi yang didukung layar (kerasa langsung kalau HP-nya
  90Hz/120Hz), Hemat Daya turunin ke 60Hz (refresh rate tinggi salah satu penyedot baterai
  terbesar, jadi ini hemat daya yang nyata). Kalau layar device cuma 60Hz dari sononya, gak
  akan kerasa bedanya -- itu batasan hardware, bukan bug.
- RAM+cache cleanup (semua mode, API resmi)
- Do Not Disturb (mode Performa, kalau izinnya udah di-grant manual)
- Overlay performa (auto-nyala di Performa, auto-mati di Hemat Daya)

## Efek Petir pas Boost
Pas progress boost sampai 100%: suara "geledek" (disintesis langsung di kode -- noise burst +
rumble frekuensi rendah, bukan file audio, jadi gak ada masalah lisensi) + flash layar +
bolt-nya membesar sesaat, baru lanjut ke game (ada jeda ~450ms biar efeknya kerasa).

## Shizuku (opsional) -- koneksi terpusat di tab Settings
Shizuku itu framework resmi open-source yang ngasih akses level shell/ADB (BUKAN root, gak
bisa bikin bootloop) -- butuh app Shizuku terpisah ter-install & jalan.

**Setup sekali** (gak perlu PC): install app Shizuku -> HP: Settings > Developer options >
Wireless debugging (nyalain) -> buka app Shizuku -> "Start via Wireless debugging". Biasanya
perlu di-restart manual tiap HP reboot kecuali di-setting auto-start.

Status koneksi ditaruh di 2 tempat: banner di paling atas tab Games (biru/merah, buat kelihatan
dari awal sebelum masuk ke boost manapun) dan detail lengkap + tombol connect di tab Settings.

**Kalau Shizuku gak di-setup**: semua fitur lain (RAM gauge, pilih mode, DND, overlay, boost,
refresh rate manual lewat Android Settings sendiri) tetap jalan penuh. Yang beda: CPU% nampilin
"--", FPS overlay balik ke estimasi vsync, refresh rate gak ke-set otomatis per-mode.

**Catatan teknis**: pakai Shizuku **UserService** (AIDL) -- BUKAN `Shizuku.newProcess()` yang
di-deprecated resmi oleh tim Shizuku sendiri (ada laporan bug "method not visible" di versi
terbaru/Android 14). Ini bagian paling kompleks di project -- kalau ada masalah, kemungkinan
besar di `ShellServiceManager.java`, `ShellUserService.java`, atau file `.aidl`-nya.

- FPS asli: `dumpsys gfxinfo <package> framestats` (data SurfaceFlinger per-app, update ~1.5 detik)
- CPU%: `/proc/stat`, 2 sample dengan jeda ~360ms (CPU keseluruhan sistem, bukan per-app)
- Refresh rate: `settings put system peak_refresh_rate/min_refresh_rate`

## Overlay Performa
- **Baterai**: akurat 100%, `BatteryManager`/sticky broadcast sistem.
- **Jaringan**: akurat, `TrafficStats.getTotalRxBytes()` per detik (throughput device).
- **FPS**: default estimasi vsync (`Choreographer`, update ~500ms). Upgrade ke FPS ASLI (hijau)
  kalau Shizuku terhubung + boost lagi jalan di mode Performa.

## Izin yang dipakai
- `SYSTEM_ALERT_WINDOW` -- overlay, izin khusus lewat Settings (app arahin otomatis)
- `POST_NOTIFICATIONS` (Android 13+) -- notifikasi wajib foreground service
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` -- overlay jalan sebagai FGS
- `ACCESS_NOTIFICATION_POLICY` -- DND mode Performa, grant manual lewat Settings > Apps >
  Special app access > Do Not Disturb access

## Fix crash overlay
Semua callback (frame/network/battery) punya try-catch `Throwable` sendiri-sendiri + crash
logger global (`GameBoosterApp` + `CrashHandler`) nulis ke
`Android/data/com.webtools.optimizer/files/crash_yyyyMMdd_HHmmss.txt` -- bisa dibaca lewat
file manager, gak butuh ADB/logcat.

## Build via GitHub Actions
Push ke `main` -> `.github/workflows/android-build.yml` -> APK debug di tab Actions ->
Artifacts. Tidak ada `gradlew`/wrapper jar (binary) -- workflow provision Gradle 8.10.2 lewat
`gradle/actions/setup-gradle`.

## Kustomisasi
- Package: `applicationId`/`namespace` di `app/build.gradle` (masih `com.webtools.optimizer`)
- Nama app: `app_name` di `res/values/strings.xml`
- Durasi animasi boost & jeda efek petir: `BOOST_DURATION_MS` / `LAUNCH_EFFECT_DELAY_MS` di
  `BoostActivity.java`
- Karakter suara petir: `SoundEffects.buildThunderSound()`
