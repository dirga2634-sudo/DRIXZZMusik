package com.musicplayer.app.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Kumpulan fungsi format tampilan: durasi lagu, ukuran file, dan tanggal.
 * Semua method bersifat static dan tidak menyimpan state apapun.
 */
public final class FormatUtils {

    private FormatUtils() {
        // Class utilitas, tidak boleh diinstansiasi
    }

    /**
     * Mengubah durasi dalam milidetik menjadi format mm:ss, atau h:mm:ss
     * bila lagu berdurasi lebih dari satu jam.
     */
    public static String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /**
     * Mengubah ukuran file dalam byte menjadi format terbaca manusia
     * (KB, MB, GB).
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exponent = (int) (Math.log(bytes) / Math.log(1024));
        exponent = Math.min(exponent, 4);
        char unitChar = "KMGT".charAt(exponent - 1);
        double value = bytes / Math.pow(1024, exponent);
        return String.format(Locale.getDefault(), "%.1f %sB", value, unitChar);
    }

    /**
     * Menghitung persentase progres pemutaran (0-1000, cocok untuk
     * SeekBar dengan max=1000 agar pergerakan lebih presisi/halus).
     */
    public static int calculateProgressPermille(int currentMs, int durationMs) {
        if (durationMs <= 0) return 0;
        long value = (long) currentMs * 1000L / durationMs;
        if (value < 0) return 0;
        if (value > 1000) return 1000;
        return (int) value;
    }

    /**
     * Kebalikan dari calculateProgressPermille: dari nilai SeekBar (0-1000)
     * kembali ke posisi waktu dalam milidetik.
     */
    public static int progressPermilleToMs(int progressPermille, int durationMs) {
        return (int) ((long) progressPermille * durationMs / 1000L);
    }
}
