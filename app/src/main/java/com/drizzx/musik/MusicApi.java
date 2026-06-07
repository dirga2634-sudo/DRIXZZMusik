package com.drizzx.musik;

import android.util.Log;
import com.drizzx.musik.model.Song;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MusicApi {

    private static final String TAG = "DrizzxApi";

    // Ganti dengan URL Vercel kamu setelah deploy!
    // Contoh: https://drizzx-api.vercel.app
    private static final String BASE_URL = "https://drixzz-musik.vercel.app/";

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // 60s untuk yt-dlp proses
        .followRedirects(true)
        .followSslRedirects(true)
        .build();

    public interface ApiCallback {
        void onSuccess(List<Song> songs);
        void onError(String message);
    }

    public interface SongCallback {
        void onSuccess(Song song);
        void onError(String message);
    }

    // ── Trending ──────────────────────────────────────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                JsonObject json = get(BASE_URL + "/api/trending");
                List<Song> songs = parseSongs(json.getAsJsonArray("songs"));
                if (songs.isEmpty()) callback.onError("Gagal memuat");
                else callback.onSuccess(songs);
            } catch (Exception e) {
                Log.e(TAG, "trending: " + e.getMessage());
                callback.onError("Gagal memuat. Coba lagi.");
            }
        }).start();
    }

    // ── Search ────────────────────────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/api/search?q=" + query.replace(" ", "+");
                JsonObject json = get(url);
                List<Song> songs = parseSongs(json.getAsJsonArray("songs"));
                if (songs.isEmpty()) callback.onError("Lagu tidak ditemukan");
                else callback.onSuccess(songs);
            } catch (Exception e) {
                Log.e(TAG, "search: " + e.getMessage());
                callback.onError("Gagal mencari");
            }
        }).start();
    }

    // ── Stream URL ────────────────────────────────────────────
    // Backend kita (Vercel + yt-dlp) yang handle ekstraksi URL
    // Tidak ada masalah IP-lock karena URL diproses di server

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Getting stream for: " + videoId);
                String url = BASE_URL + "/api/stream?id=" + videoId;
                JsonObject json = get(url);

                if (json.has("error")) {
                    callback.onError(json.get("error").getAsString());
                    return;
                }

                String streamUrl = str(json, "url", "");
                if (streamUrl.isEmpty()) {
                    callback.onError("Tidak ada stream URL");
                    return;
                }

                String title     = str(json, "title", "Unknown");
                String artist    = str(json, "artist", "Unknown");
                String thumbnail = str(json, "thumbnail", "");
                long   duration  = json.has("duration") ? json.get("duration").getAsLong() : 0;

                Log.d(TAG, "Stream OK: " + title);

                Song song = new Song(
                    videoId, title, artist, "",
                    fmt(duration), streamUrl, thumbnail
                );
                callback.onSuccess(song);

            } catch (Exception e) {
                Log.e(TAG, "stream: " + e.getMessage());
                callback.onError("Gagal memuat audio. Coba lagi.");
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────

    private static List<Song> parseSongs(JsonArray arr) {
        List<Song> songs = new ArrayList<>();
        if (arr == null) return songs;
        for (JsonElement el : arr) {
            try {
                JsonObject o = el.getAsJsonObject();
                songs.add(new Song(
                    str(o,"id",""),
                    str(o,"title","?"),
                    str(o,"artist","?"),
                    "",
                    str(o,"duration",""),
                    "",
                    str(o,"thumbnail","")
                ));
            } catch (Exception ignored) {}
        }
        return songs;
    }

    private static JsonObject get(String url) throws IOException {
        Request req = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "DrizzxMusik/3.0")
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            String body = resp.body() != null ? resp.body().string() : "";
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static String str(JsonObject o, String k, String d) {
        try { if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsString(); }
        catch (Exception ignored) {}
        return d;
    }

    private static String fmt(long sec) {
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
