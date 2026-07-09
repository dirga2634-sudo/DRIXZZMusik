package com.musicplayer.app.helper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Banyak HP dengan skin custom (Infinix/XOS, Xiaomi/MIUI, Oppo/ColorOS,
 * Vivo/FuntouchOS, Huawei/EMUI, dll) mematikan background service jauh
 * lebih agresif daripada batasan Doze/App Standby bawaan Android — bahkan
 * foreground service yang sudah benar sekalipun bisa ikut kena "dibersihkan"
 * kalau izin "Autostart"/"Protected Apps" tidak diaktifkan manual oleh
 * pengguna. Ini keterbatasan level OS/OEM, bukan sesuatu yang bisa
 * dipaksakan penuh lewat kode demi alasan keamanan & privasi pengguna.
 *
 * Class ini membantu semaksimal mungkin secara terprogram:
 * 1. Meminta pengecualian battery optimization standar Android (berlaku di
 *    semua merek, API 23+).
 * 2. Mencoba membuka aplikasi manajemen baterai/autostart bawaan pabrikan
 *    (best-effort, karena activity spesifiknya sering tidak diekspor
 *    sehingga tidak bisa di-deep-link langsung ke toggle-nya).
 */
public final class BatteryOptimizationHelper {

    private BatteryOptimizationHelper() {
        // Class utilitas, tidak boleh diinstansiasi
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /**
     * Menampilkan dialog sistem standar Android untuk meminta pengecualian
     * battery optimization. Bekerja di semua merek karena ini API resmi
     * AOSP (bukan fitur tambahan pabrikan).
     */
    public static void requestIgnoreBatteryOptimizations(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            openAppBatterySettingsFallback(activity);
        }
    }

    /**
     * Mencoba membuka halaman autostart/manajemen baterai bawaan pabrikan
     * satu per satu sampai ada yang berhasil. Mengembalikan true bila salah
     * satu berhasil dibuka, false bila semua percobaan gagal (tidak ada
     * yang cocok dengan HP ini) sehingga pemanggil bisa fallback ke
     * halaman App Info standar.
     */
    public static boolean openAutoStartSettings(Context context) {
        String[][] candidates = new String[][]{
                // Infinix / Tecno / itel (Transsion - XOS/HiOS), termasuk HP Dirga
                {"com.transsion.mobilebutler", "com.transsion.mobilebutler.MainActivity"},
                {"com.transsion.mobilebutler", "com.transsion.mobilebutler.activity.MainActivity"},
                // Xiaomi / Redmi / Poco (MIUI)
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                // Oppo / Realme (ColorOS)
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                // Vivo (FuntouchOS)
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                // Huawei / Honor (EMUI)
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                // Asus (ZenUI)
                {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"},
                // Letv
                {"com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"},
        };

        for (String[] candidate : candidates) {
            if (tryStartActivity(context, candidate[0], candidate[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryStartActivity(Context context, String packageName, String className) {
        try {
            Intent intent = new Intent();
            intent.setClassName(packageName, className);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            // Activity tidak ada / tidak diekspor di HP ini, lanjut coba kandidat berikutnya
            return false;
        }
    }

    /**
     * Membuka halaman detail aplikasi standar Android (App Info). Di HP
     * Infinix/XOS, toggle "Allow Background Running" / "Izinkan Berjalan
     * di Latar Belakang" justru bersarang DI SINI (App Info > Battery /
     * Battery Saver), bukan di aplikasi manajer terpisah — jadi method ini
     * juga dipakai sebagai entry point utama untuk toggle tersebut, bukan
     * cuma fallback terakhir.
     */
    public static void openAppInfoSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Sangat jarang terjadi; jika halaman App Info pun tidak ada, tidak ada lagi yang bisa dilakukan
        }
    }

    /**
     * @deprecated gunakan {@link #openAppInfoSettings(Context)} — nama lama
     * dipertahankan sebagai alias supaya kode lain yang sudah memanggilnya
     * tetap berfungsi tanpa perubahan.
     */
    public static void openAppBatterySettingsFallback(Context context) {
        openAppInfoSettings(context);
    }
}
