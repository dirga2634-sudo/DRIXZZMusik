package com.webtools.optimizer.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Baca FPS & CPU% SEBENARNYA lewat ShellServiceManager (Shizuku UserService, level shell/ADB).
 *
 * FPS: dumpsys gfxinfo (package) framestats -- data asli SurfaceFlinger buat app spesifik itu,
 * BUKAN estimasi vsync display kayak fallback di OverlayService kalau Shizuku gak tersedia.
 *
 * CPU: /proc/stat, dihitung dari 2 sample dengan jeda -- ini CPU keseluruhan sistem (bukan
 * per-app; per-app butuh parsing dumpsys cpuinfo yang formatnya kurang stabil antar device).
 *
 * Parsing ini best-effort & defensif -- kalau formatnya beda di device/versi Android tertentu,
 * balikin -1 dan caller WAJIB fallback dengan aman.
 */
public class ShizukuMetrics {

    private ShizukuMetrics() {}

    public static int readRealFps(String packageName) {
        String output = ShellServiceManager.exec("dumpsys gfxinfo " + packageName + " framestats");
        if (output == null || output.isEmpty()) return -1;
        try {
            return parseFramestats(output);
        } catch (Throwable t) {
            android.util.Log.e("ShizukuMetrics", "parse framestats gagal", t);
            return -1;
        }
    }

    private static int parseFramestats(String output) {
        String[] lines = output.split("\n");
        List<Long> vsyncTimestamps = new ArrayList<>();
        boolean inDataBlock = false;
        int markerCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("PROFILEDATA")) {
                markerCount++;
                inDataBlock = markerCount == 1;
                continue;
            }
            if (!inDataBlock || trimmed.isEmpty() || !Character.isDigit(trimmed.charAt(0))) continue;

            String[] cols = trimmed.split(",");
            if (cols.length < 3) continue;
            try {
                long vsync = Long.parseLong(cols[2].trim());
                if (vsync > 0) vsyncTimestamps.add(vsync);
            } catch (NumberFormatException ignored) {
                // Baris format gak dikenal, skip.
            }
        }

        if (vsyncTimestamps.size() < 2) return -1;

        long totalDeltaNanos = vsyncTimestamps.get(vsyncTimestamps.size() - 1) - vsyncTimestamps.get(0);
        int frameCount = vsyncTimestamps.size() - 1;
        if (totalDeltaNanos <= 0 || frameCount <= 0) return -1;

        double avgFrameTimeMs = (totalDeltaNanos / 1_000_000.0) / frameCount;
        if (avgFrameTimeMs <= 0) return -1;

        int fps = (int) Math.round(1000.0 / avgFrameTimeMs);
        if (fps < 1 || fps > 300) return -1;
        return fps;
    }

    public static int readSystemCpuPercent() {
        long[] first = parseProcStat(ShellServiceManager.exec("cat /proc/stat"));
        if (first == null) return -1;
        try {
            Thread.sleep(360);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        long[] second = parseProcStat(ShellServiceManager.exec("cat /proc/stat"));
        if (second == null) return -1;

        long idleDelta = second[3] - first[3];
        long totalDelta = 0;
        for (int i = 0; i < 4; i++) totalDelta += second[i] - first[i];
        if (totalDelta <= 0) return -1;

        int percent = (int) Math.round(100.0 * (totalDelta - idleDelta) / totalDelta);
        if (percent < 0 || percent > 100) return -1;
        return percent;
    }

    private static long[] parseProcStat(String output) {
        if (output == null) return null;
        try {
            for (String line : output.split("\n")) {
                if (line.startsWith("cpu ")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 5) return null;
                    long[] values = new long[4];
                    for (int i = 0; i < 4; i++) {
                        values[i] = Long.parseLong(parts[i + 1]);
                    }
                    return values;
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("ShizukuMetrics", "parse /proc/stat gagal", t);
        }
        return null;
    }
}
