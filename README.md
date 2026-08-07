# Game Booster

Aplikasi Android: boost game (bebasin RAM + bersihin cache) dengan loading screen animasi bolt,
overlay FPS/baterai/jaringan real-time, dan 3 mode (Performa/Seimbang/Hemat Daya) yang beneran
ngubah perilaku app, bukan kosmetik doang.

100% Android SDK resmi (+ Shizuku opsional untuk data lebih akurat) -- tanpa root, tanpa
modifikasi sistem/kernel/partisi, nol risiko bootloop.

## Struktur
- `MainActivity` -- BottomNavigationView (tab Games & Optimizer)
- `fragment/GamesFragment` -- daftar aplikasi terinstall (RecyclerView), tap buka ModeSelectActivity
- `fragment/OptimizerFragment` -- kartu Clear Cache, RAM Manager, toggle Overlay Performa manual
- `ModeSelectActivity` -- gauge RAM/CPU + 3 kartu mode, sebelum boost
- `BoostActivity` -- fullscreen loading: animasi bolt + progress + status, jalanin RAM/cache
  cleanup + aksi khusus mode (overlay, DND) di background, lalu launch game target
- `OverlayService` -- foreground service, floating widget draggable (FPS, baterai, jaringan)
- `GameBoosterApp` + `util/CrashHandler` -- nangkep crash apapun, nulis log ke file yang bisa
  dibaca lewat file manager (gak butuh ADB/logcat)
- `util/CacheManager`, `util/RamManager` -- cache & RAM, API resmi Android
- `util/AppListLoader` -- query app via `<queries>` (bukan QUERY_ALL_PACKAGES)
- `util/PrefsManager` -- SharedPreferences (game terakhir di-boost)
- `util/ShizukuHelper`, `util/ShellServiceManager`, `util/ShizukuMetrics` -- integrasi Shizuku
  (opsional) buat FPS & CPU% asli
- `ShellUserService` + `aidl/IShellService.aidl` -- kode yang jalan di proses shell/ADB lewat
  Shizuku UserService

## Overlay Performa
- **Baterai**: akurat 100%, dari `BatteryManager`/sticky broadcast sistem.
- **Jaringan**: akurat, dari selisih `TrafficStats.getTotalRxBytes()` tiap detik (throughput
  seluruh device, bukan spesifik 1 app).
- **FPS**: default estimasi dari jarak antar tick vsync (`Choreographer`), update tiap ~500ms --
  ini ngukur refresh activity display, BUKAN render loop internal app lain (gak ada API resmi
  non-root buat itu). KALAU Shizuku terhubung + game target lagi di-boost lewat mode Performa,
  angka ini di-upgrade jadi FPS ASLI dari `dumpsys gfxinfo` tiap ~2 detik, teks jadi hijau
  sebagai penanda "data asli". Tanpa Shizuku, ya tetap estimasi -- gak pernah error/crash
  gara-gara itu.

Widget bisa di-drag ke mana aja, ada tombol X, atau matiin dari notifikasi (foreground service
wajib punya notifikasi persisten -- proteksi transparansi bawaan Android, bukan bug).

## Izin yang dipakai
- `SYSTEM_ALERT_WINDOW` -- overlay, izin khusus lewat Settings (app otomatis arahin ke sana)
- `POST_NOTIFICATIONS` (Android 13+) -- notifikasi wajib foreground service
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` -- overlay jalan sebagai FGS
- `ACCESS_NOTIFICATION_POLICY` -- buat toggle Do Not Disturb di mode Performa. Grant-nya manual
  sekali lewat Settings > Apps > Special app access > Do Not Disturb access -- app gak bisa
  nyalain otomatis, itu sengaja dibikin Android supaya user yang kontrol penuh.

## FPS & CPU% asli lewat Shizuku (opsional)
Shizuku itu framework resmi open-source yang ngasih akses level shell/ADB (BUKAN root, gak bisa
bikin bootloop) ke app yang di-approve user -- butuh app Shizuku terpisah ter-install & jalan.

**Setup sekali di awal** (gak perlu PC): install app Shizuku (Play Store/GitHub) -> HP: Settings
> Developer options > Wireless debugging (nyalain) -> buka app Shizuku -> "Start via Wireless
debugging". Biasanya perlu di-restart manual tiap HP reboot kecuali di-setting auto-start.

**Kalau Shizuku gak di-setup**: semua fitur lain (RAM gauge, pilih mode, DND, overlay, boost)
tetap jalan penuh. Yang beda cuma CPU% nampilin "--" dan FPS overlay balik ke estimasi vsync.

**Catatan teknis penting**: implementasi ini pakai Shizuku **UserService** (AIDL,
`ShellUserService` + `IShellService.aidl`) -- BUKAN `Shizuku.newProcess()`, karena method itu
lagi di-deprecated resmi oleh tim Shizuku sendiri (bahkan ada laporan bug "method not visible"
di versi terbaru/Android 14). UserService lebih kompleks tapi ini jalur yang didukung ke depan.
Ini bagian paling baru & paling kompleks di seluruh project -- kalau ada masalah spesifik di
sini, kemungkinan besar penyebabnya ada di `ShellServiceManager.java`, `ShellUserService.java`,
atau file `.aidl`-nya.

- FPS asli: `dumpsys gfxinfo <package> framestats` (data SurfaceFlinger spesifik per-app)
- CPU%: `/proc/stat`, dihitung dari 2 sample dengan jeda ~360ms (CPU keseluruhan sistem, bukan
  per-app -- per-app butuh parsing `dumpsys cpuinfo` yang formatnya kurang stabil antar device)

## Mode Boost
- **Performa** -- RAM+cache dibersihkan (semua mode selalu), overlay auto-nyala kalau izinnya
  udah ada (FPS asli via Shizuku kalau tersedia), Do Not Disturb aktif kalau izinnya udah di-grant.
- **Seimbang** -- RAM+cache dibersihkan, overlay gak diutak-atik, DND dimatikan.
- **Hemat Daya** -- RAM+cache dibersihkan, overlay dimatikan kalau lagi nyala, DND dimatikan.

Semua mode 100% real -- gak ada yang kosmetik doang, dan gak satupun (termasuk lewat Shizuku)
nyentuh CPU governor/kernel/partisi sistem, jadi nol risiko reboot.

## Fix crash overlay
Overlay sempat force-close (dua kali). Sekarang tiap callback (frame/network/battery) punya
try-catch sendiri, nangkep `Throwable` (bukan cuma `Exception`, biar `Error` juga ketangkep),
dan seluruh app punya crash logger global (`GameBoosterApp` + `CrashHandler`) yang nulis detail
crash apapun ke `Android/data/com.webtools.optimizer/files/crash_yyyyMMdd_HHmmss.txt` -- bisa
dibaca lewat file manager (ZArchiver dll), gak butuh ADB/logcat.

## Build via GitHub Actions
Push ke `main` -> trigger `.github/workflows/android-build.yml` -> APK debug muncul di tab
Actions -> Artifacts. Project sengaja tidak menyertakan `gradlew`/wrapper jar (binary) --
workflow provision Gradle 8.10.2 langsung lewat `gradle/actions/setup-gradle`.

## Kustomisasi
- Package: `applicationId` & `namespace` di `app/build.gradle` (masih `com.webtools.optimizer`)
- Nama app: `app_name` di `res/values/strings.xml`
- Durasi animasi boost: `BOOST_DURATION_MS` di `BoostActivity.java`
- Interval sampling FPS asli / CPU: di `OverlayService.scheduleRealFpsSample()` (2000ms) dan
  `ShizukuMetrics.readSystemCpuPercent()` (jeda 360ms antar sample)
