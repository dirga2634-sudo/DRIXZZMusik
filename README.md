# Web Optimizer

Aplikasi utility Android: WebView browser + Clear Cache + RAM Manager.
100% Android SDK resmi -- tanpa root, tanpa modifikasi sistem, aman dari bootloop.

## Struktur
- `MainActivity` -- host BottomNavigationView (tab Browser & Optimizer)
- `fragment/WebFragment` -- WebView browser (address bar, back/forward, pull-to-refresh, ingat URL terakhir)
- `fragment/OptimizerFragment` -- kartu Clear Cache & RAM Manager (proses berat di background thread)
- `util/CacheManager` -- hitung & hapus cache app + WebView (cache dir + WebStorage)
- `util/RamManager` -- info RAM via `ActivityManager`, `killBackgroundProcesses()` resmi
- `util/PrefsManager` -- SharedPreferences (simpan URL terakhir)

## Catatan RAM Manager
Sejak Android 5.0, aplikasi pihak ketiga tidak bisa paksa-tutup proses app lain secara bebas (proteksi sistem). `killBackgroundProcesses()` cuma efektif untuk proses yang memang dianggap "aman dihentikan" oleh sistem, dan `getRunningAppProcesses()` di Android modern umumnya cuma melaporkan proses app sendiri. Ini BUKAN task-killer gaya Android 2.x, tapi 100% sesuai batas resmi API -- makanya aman & tidak bisa bikin bootloop.

## Build via GitHub Actions
Push ke `main` -> trigger `.github/workflows/android-build.yml` -> APK debug muncul di tab Actions -> Artifacts. Project ini sengaja tidak menyertakan `gradlew`/wrapper jar (file binary) -- workflow langsung provision Gradle 8.10.2 lewat `gradle/actions/setup-gradle`.

## Kustomisasi
- Package: `applicationId` & `namespace` di `app/build.gradle` (default `com.webtools.optimizer`)
- Nama app: `app_name` di `res/values/strings.xml`
- URL awal: `DEFAULT_URL` di `WebFragment.java`
