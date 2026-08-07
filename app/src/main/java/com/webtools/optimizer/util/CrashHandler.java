package com.webtools.optimizer.util;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Nangkep crash apapun di seluruh app dan nulis detailnya ke file teks di folder
 * eksternal aplikasi sendiri (gak butuh izin apapun) -- supaya bisa dibaca lewat
 * file manager (ZArchiver dll) tanpa perlu logcat/ADB.
 *
 * Lokasi file: Android/data/com.webtools.optimizer/files/crash_yyyyMMdd_HHmmss.txt
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    private CrashHandler(Context context) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            writeCrashLog(thread, throwable);
        } catch (Throwable ignored) {
            // Jangan sampai crash handler-nya sendiri ikut jatuh.
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private void writeCrashLog(Thread thread, Throwable throwable) {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) dir = appContext.getFilesDir();
        String filename = "crash_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
        File file = new File(dir, filename);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Waktu: " + new Date());
            writer.println("Thread: " + thread.getName());
            writer.println("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            writer.println("Perangkat: " + Build.MANUFACTURER + " " + Build.MODEL);
            writer.println();
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            writer.println(sw);
        } catch (Exception ignored) {
            // Best-effort logging saja.
        }
    }
}
