package com.gomouse.pro.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;

/**
 * Gomouse Pro is only ever tested on a real device with no ADB/Android
 * Studio available, so there is no normal way to pull a logcat stack trace
 * after a crash. This installs a global uncaught-exception handler that
 * writes the full trace to SharedPreferences (survives the process death
 * that follows) before letting the crash proceed as normal, so MainActivity
 * can show it — as selectable/shareable text — the next time the app opens.
 */
public final class CrashReporter {

    private static final String PREFS_NAME = "gomouse_prefs";
    private static final String KEY_LAST_CRASH = "last_crash_trace";

    private CrashReporter() {
    }

    public static void install(Context appContext) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                saveCrash(appContext, throwable);
            } catch (Throwable ignored) {
                // Never let the crash reporter itself block the real crash from proceeding.
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                System.exit(1);
            }
        });
    }

    private static void saveCrash(Context appContext, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String timestamp = DateFormat.getDateTimeInstance().format(new Date());
        String text = "Gomouse Pro crash — " + timestamp + "\n\n" + sw;
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH, text)
                .commit(); // synchronous: the process may die right after this returns
    }

    /** Returns the last saved crash text, or null if there isn't one. */
    public static String getLastCrash(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_CRASH, null);
    }

    public static void clearLastCrash(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_CRASH)
                .apply();
    }
}
