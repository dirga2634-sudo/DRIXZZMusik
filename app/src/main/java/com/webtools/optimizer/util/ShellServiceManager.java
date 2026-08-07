package com.webtools.optimizer.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.webtools.optimizer.IShellService;
import com.webtools.optimizer.ShellUserService;

import rikka.shizuku.Shizuku;

/**
 * Ngatur koneksi ke ShellUserService lewat Shizuku UserService API (BUKAN Shizuku#newProcess
 * yang sudah di-deprecated/gak visible lagi di versi Shizuku terbaru). UserService jalan di
 * proses terpisah dengan identitas shell (ADB), bukan root, dan cuma bisa dipakai kalau
 * Shizuku sendiri sudah terhubung + izin sudah diberikan user.
 */
public class ShellServiceManager {

    private static IShellService shellService;
    private static boolean binding = false;

    private ShellServiceManager() {}

    private static final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            shellService = IShellService.Stub.asInterface(binder);
            binding = false;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            shellService = null;
            binding = false;
        }
    };

    public static void ensureBound(Context context) {
        if (shellService != null || binding || !ShizukuHelper.hasPermission()) return;
        try {
            binding = true;
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(context, ShellUserService.class))
                    .daemon(false)
                    .processNameSuffix("shell")
                    .debuggable(false)
                    .version(1);
            Shizuku.bindUserService(args, connection);
        } catch (Throwable t) {
            binding = false;
            android.util.Log.e("ShellServiceManager", "bind gagal", t);
        }
    }

    @Nullable
    public static String exec(String command) {
        try {
            if (shellService == null) return null;
            return shellService.exec(command);
        } catch (Throwable t) {
            android.util.Log.e("ShellServiceManager", "exec gagal: " + command, t);
            shellService = null;
            return null;
        }
    }

    public static boolean isBound() {
        return shellService != null;
    }
}
