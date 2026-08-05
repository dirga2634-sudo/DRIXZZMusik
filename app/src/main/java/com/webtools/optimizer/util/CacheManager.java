package com.webtools.optimizer.util;

import android.content.Context;
import android.webkit.WebStorage;

import java.io.File;
import java.util.Locale;

public class CacheManager {

    private CacheManager() {}

    /** Menghitung total ukuran cache aplikasi (internal + eksternal) dalam byte. */
    public static long getCacheSize(Context context) {
        long size = getFolderSize(context.getCacheDir());
        File externalCache = context.getExternalCacheDir();
        if (externalCache != null) {
            size += getFolderSize(externalCache);
        }
        return size;
    }

    /**
     * Membersihkan cache aplikasi (termasuk cache WebView) memakai API resmi Android:
     * hapus isi folder cache milik app sendiri + WebStorage (localStorage/IndexedDB WebView).
     * Tidak menyentuh file sistem lain, tidak butuh root.
     *
     * @return jumlah byte yang berhasil dibebaskan.
     */
    public static long clearCache(Context context) {
        long before = getCacheSize(context);

        WebStorage.getInstance().deleteAllData();
        deleteFolderContents(context.getCacheDir());
        File externalCache = context.getExternalCacheDir();
        if (externalCache != null) {
            deleteFolderContents(externalCache);
        }

        long after = getCacheSize(context);
        return Math.max(0, before - after);
    }

    private static long getFolderSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        long size = 0;
        for (File file : files) {
            size += file.isDirectory() ? getFolderSize(file) : file.length();
        }
        return size;
    }

    private static void deleteFolderContents(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) deleteFolderContents(file);
            file.delete();
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.min(exp, 4);
        char unit = "KMGT".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %cB", bytes / Math.pow(1024, exp), unit);
    }
}
