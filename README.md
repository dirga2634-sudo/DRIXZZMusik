# Game Booster

Aplikasi Android: boost game (bebasin RAM + bersihin cache) dengan loading screen animasi bolt, auto-launch game, plus floating overlay FPS/baterai/jaringan real-time.
100% Android SDK resmi -- tanpa root, tanpa modifikasi sistem, aman dari bootloop.

## Struktur
- `MainActivity` -- BottomNavigationView (tab Games & Optimizer)
- `fragment/GamesFragment` -- daftar aplikasi terinstall (RecyclerView), tap buat boost & launch
- `fragment/OptimizerFragment` -- kartu Clear Cache, RAM Manager, toggle Overlay Performa
- `BoostActivity` -- fullscreen loading screen: animasi bolt + circular progress + status text, jalanin CacheManager+RamManager di background thread, lalu launch game target
- `OverlayService` -- foreground service yang nampilin floating widget draggable (FPS, baterai, jaringan) di atas app/game lain
- `util/CacheManager` -- hitung & hapus cache app
- `util/RamManager` -- info RAM via `ActivityManager`, `killBackgroundProcesses()` resmi
- `util/AppListLoader` -- query semua app yang punya launcher icon (pakai `<queries>`, bukan QUERY_ALL_PACKAGES), app kategori "game" diprioritaskan di atas
- `util/PrefsManager` -- SharedPreferences (simpan game terakhir di-boost, dipin ke atas list)

## Overlay Performa -- cara kerja & keterbatasan jujur
- **Baterai**: akurat 100%, dari `BatteryManager` / sticky broadcast sistem.
- **Jaringan**: akurat, dihitung dari selisih `TrafficStats.getTotalRxBytes()` tiap detik (throughput seluruh device, bukan spesifik 1 app).
- **FPS**: dihitung dari jarak antar tick vsync (`Choreographer`), diupdate tiap ~500ms. Ini ngukur seberapa sering DISPLAY refresh, bukan literal "frame render internal game itu sendiri" -- gak ada API resmi non-root yang bisa baca FPS internal app lain (itu proteksi sandbox Android). Ini persis teknik yang dipakai hampir semua app FPS-overlay non-root di luar sana, jadi wajar kalau angkanya sering nongkrong di angka refresh rate layar (60/90/120) kecuali ada jank berat.

## Izin yang dipakai overlay
- `SYSTEM_ALERT_WINDOW` -- izin khusus, user harus approve manual lewat halaman Settings (app otomatis arahin ke sana pas toggle di-nyalain)
- `POST_NOTIFICATIONS` (Android 13+) -- buat notifikasi wajib foreground service
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` -- servicenya jalan sebagai foreground service (wajib punya notifikasi persisten yang gak bisa disembunyikan -- ini proteksi transparansi bawaan Android, bukan bug)

Widget overlay bisa di-drag ke mana aja di layar, ada tombol X buat matiin langsung, atau dari notifikasi (tombol "Matikan").

## Build via GitHub Actions
Push ke `main` -> trigger `.github/workflows/android-build.yml` -> APK debug muncul di tab Actions -> Artifacts. Project sengaja tidak menyertakan `gradlew`/wrapper jar (binary) -- workflow provision Gradle 8.10.2 langsung lewat `gradle/actions/setup-gradle`.

## Kustomisasi
- Package: `applicationId` & `namespace` di `app/build.gradle` (masih `com.webtools.optimizer`, gampang diganti kalau mau)
- Nama app: `app_name` di `res/values/strings.xml`
- Durasi animasi boost: `BOOST_DURATION_MS` di `BoostActivity.java`
- Posisi awal overlay: `params.x` / `params.y` di `OverlayService.showOverlay()`
