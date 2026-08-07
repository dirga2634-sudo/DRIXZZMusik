package com.webtools.optimizer.util;

import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

/**
 * Cek ketersediaan & izin Shizuku -- framework open-source resmi yang ngasih akses level
 * shell/ADB (BUKAN root) ke app yang di-approve user. Butuh app Shizuku terpisah ter-install
 * & jalan (di Android 11+ lewat Wireless Debugging, setup sekali di awal, gak perlu PC tiap
 * pemakaian). Semua method balikin false/aman kalau Shizuku gak ada -- fitur yang pakai ini
 * WAJIB fallback, jangan pernah nganggep Shizuku pasti tersedia.
 */
public class ShizukuHelper {

    private ShizukuHelper() {}

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            if (isAvailable() && !hasPermission()) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable ignored) {
            // Gagal minta izin -- caller cukup cek hasPermission() lagi belakangan.
        }
    }
}
