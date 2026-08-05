package com.webtools.optimizer.util;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;

public class RamManager {

    private RamManager() {}

    public static class MemInfo {
        public long totalBytes;
        public long availBytes;
        public boolean lowMemory;
        public int percentUsed;
    }

    /** Mengambil info RAM perangkat lewat ActivityManager resmi (tanpa izin khusus). */
    public static MemInfo getMemoryInfo(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);

        MemInfo info = new MemInfo();
        info.totalBytes = mi.totalMem;
        info.availBytes = mi.availMem;
        info.lowMemory = mi.lowMemory;
        info.percentUsed = info.totalBytes > 0
                ? (int) (100 - (info.availBytes * 100L / info.totalBytes))
                : 0;
        return info;
    }

    /**
     * Membebaskan memori pakai API resmi Android:
     * killBackgroundProcesses() (izin normal KILL_BACKGROUND_PROCESSES, otomatis disetujui sistem)
     * dipanggil untuk proses background yang memang dianggap "aman dihentikan" oleh sistem.
     *
     * Catatan: sejak Android 5.0, aplikasi pihak ketiga TIDAK bisa paksa-tutup proses app lain
     * secara bebas (demi keamanan & mencegah hal seperti bootloop) -- beda dengan task-killer
     * jadul Android 2.x. getRunningAppProcesses() di Android modern juga umumnya cuma melaporkan
     * proses milik aplikasi sendiri. Jadi fungsi ini bekerja penuh dalam batas resmi yang
     * diizinkan API Android, tanpa root & tanpa modifikasi sistem.
     */
    public static int freeMemory(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;

        int handled = 0;
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) {
            String selfPackage = context.getPackageName();
            for (ActivityManager.RunningAppProcessInfo proc : processes) {
                if (proc.processName.equals(selfPackage)) continue;
                if (proc.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) continue;
                try {
                    am.killBackgroundProcesses(proc.processName);
                    handled++;
                } catch (SecurityException ignored) {
                    // Sistem menolak sesuai desain keamanannya; tidak perlu ditangani khusus.
                }
            }
        }
        System.gc();
        return handled;
    }
}
