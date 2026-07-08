package com.musicplayer.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Menyediakan satu kumpulan thread background (diskIO) dan satu Handler
 * main thread yang dipakai bersama di seluruh aplikasi. Ini mencegah
 * setiap class membuat Thread/ExecutorService sendiri-sendiri, dan
 * memastikan operasi berat (scan MediaStore, query database, decode
 * cover album) tidak pernah memblokir UI Thread.
 */
public final class AppExecutors {

    private static volatile AppExecutors instance;

    private final ExecutorService diskIO;
    private final Handler mainThreadHandler;

    private AppExecutors() {
        diskIO = Executors.newFixedThreadPool(3);
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public static AppExecutors getInstance() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors();
                }
            }
        }
        return instance;
    }

    /**
     * Menjalankan pekerjaan di background thread (disk/database/jaringan).
     */
    public void diskIO(Runnable runnable) {
        diskIO.execute(runnable);
    }

    /**
     * Menjalankan pekerjaan di main thread (untuk update UI setelah
     * pekerjaan background selesai).
     */
    public void mainThread(Runnable runnable) {
        mainThreadHandler.post(runnable);
    }
}
