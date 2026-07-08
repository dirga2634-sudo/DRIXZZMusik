package com.musicplayer.app.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Mengurus permission yang dibutuhkan aplikasi:
 * - Android 8-12 (API 26-32): READ_EXTERNAL_STORAGE
 * - Android 13+ (API 33+): READ_MEDIA_AUDIO + POST_NOTIFICATIONS
 */
public final class PermissionHelper {

    private PermissionHelper() {
        // Class utilitas, tidak boleh diinstansiasi
    }

    /**
     * Daftar permission yang wajib diminta sesuai versi Android perangkat.
     */
    public static String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return permissions.toArray(new String[0]);
    }

    /**
     * Permission utama yang menentukan apakah aplikasi bisa membaca
     * library musik pengguna (dipakai untuk cek status & rationale).
     */
    public static String getStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_AUDIO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    /**
     * True jika permission akses musik sudah diberikan pengguna.
     */
    public static boolean hasStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, getStoragePermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Meminta seluruh permission yang dibutuhkan sekaligus.
     */
    public static void requestPermissions(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, getRequiredPermissions(), requestCode);
    }

    /**
     * True jika sistem menyarankan menampilkan penjelasan (rationale)
     * sebelum meminta ulang permission storage.
     */
    public static boolean shouldShowStorageRationale(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, getStoragePermission());
    }
}
