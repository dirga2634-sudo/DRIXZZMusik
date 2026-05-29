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

    private static final String[] INVIDIOUS = {
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.privacyredirect.com",
        "https://iv.melmac.space",
        "https://yt.artemislena.eu",
        "https://invidious.fdn.fr",
        "https://invidious.io.lol",
        "https://invidious.perennialte.ch",
        "https://invidious.slipfox.xyz",
        "https://invidious.darkness.services"
    };

    private static int workingIdx = 0;

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build();

    public interface ApiCallback {
        void onSuccess(List<Song> songs);
        void onError(String message);
    }

    public interface SongCallback {
        void onSuccess(Song song);
        void onError(String message);
    }

    // Validasi response adalah JSON bukan HTML error page
    private static boolean isValidJson(String s) {
        if (s == null || s.length() < 2) return false;
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    // Fetch + validasi JSON dari semua server, fallback otomatis
    private static String fetchFromAny(String path) throws IOException {
        // Coba working server dulu
        try {
            String result = fetchUrl(INVIDIOUS[workingIdx] + path);
            if (isValidJson(result)) return result;
            Log.w(TAG, "Non-JSON from working server, try others...");
        } catch (IOException ignored) {}

        // Coba semua server lain
        for (int i = 0; i < INVIDIOUS.length; i++) {
            if (i == workingIdx) continue;
            try {
                String result = fetchUrl(INVIDIOUS[i] + path);
                if (isValidJson(result)) {
                    workingIdx = i;
                    Log.d(TAG, "Switched to: " + INVIDIOUS[i]);
                    return result;
                }
                Log.w(TAG, "Non-JSON from: " + INVIDIOUS[i]);
            } catch (IOException e) {
                Log.w(TAG, "IO fail: " + INVIDIOUS[i] + " - " + e.getMessage());
            }
        }
        throw new IOException("Semua server tidak bisa diakses");
    }

    private static String fetchUrl(String url) throws IOException {
        Request req = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
            .addHeader("Accept", "application/json")
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            String body = resp.body() != null ? resp.body().string() : null;
            if (body == null || body.isEmpty()) throw new IOException("Empty response");
            return body;
        }
    }

    // ── Search ──────────────────────────────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String path = "/api/v1/search?q="
                    + query.replace(" ", "+")
                    + "&type=video&page=1";
                String body = fetchFromAny(path);
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                List<Song> songs = parseItems(items);
                if (songs.isEmpty()) callback.onError("Lagu tidak ditemukan");
                else callback.onSuccess(songs);
            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                callback.onError("Gagal mencari lagu");
            }
        }).start();
    }

    // ── Trending ────────────────────────────────────────────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                // Trending musik global
                String body = fetchFromAny("/api/v1/trending?type=music");
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                List<Song> songs = parseItems(items);

                if (!songs.isEmpty()) {
                    callback.onSuccess(songs);
                    return;
                }

                // Fallback: trending semua konten
                body = fetchFromAny("/api/v1/trending");
                items = JsonParser.parseString(body).getAsJsonArray();
                songs = parseItems(items);

                if (!songs.isEmpty()) {
                    callback.onSuccess(songs);
                    return;
                }

                // Fallback: search global hits
                fallbackSearch(callback);

            } catch (Exception e) {
                Log.e(TAG, "Trending error: " + e.getMessage());
                fallbackSearch(callback);
            }
        }).start();
    }

    private static void fallbackSearch(ApiCallback callback) {
        String[] queries = {"top+hits+2024", "music+viral+2024", "best+songs+2024"};
        for (String q : queries) {
            try {
                String body = fetchFromAny("/api/v1/search?q=" + q + "&type=video&page=1");
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                List<Song> songs = parseItems(items);
                if (!songs.isEmpty()) {
                    callback.onSuccess(songs);
                    return;
                }
            } catch (Exception ignored) {}
        }
        callback.onError("Gagal memuat. Coba lagi.");
    }

    // ── Stream URL ──────────────────────────────────────────────────

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            // Coba semua server satu per satu sampai dapat stream valid
            String lastError = "Gagal memuat audio";
            for (int attempt = 0; attempt < INVIDIOUS.length; attempt++) {
                int idx = (workingIdx + attempt) % INVIDIOUS.length;
                try {
                    String url = INVIDIOUS[idx]
                        + "/api/v1/videos/" + videoId
                        + "?fields=title,author,lengthSeconds,videoThumbnails,adaptiveFormats,formatStreams,description";

                    String body = fetchUrl(url);

                    // Validasi JSON dulu sebelum parse
                    if (!isValidJson(body)) {
                        Log.w(TAG, "Non-JSON stream response from: " + INVIDIOUS[idx]);
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                    String title    = getStr(json, "title", "Unknown");
                    String author   = getStr(json, "author", "Unknown");
                    long   duration = json.has("lengthSeconds") ? json.get("lengthSeconds").getAsLong() : 0;
                    String thumb    = getBestThumb(json);
                    String desc     = getStr(json, "description", "");
                    String stream   = getBestAudio(json);

                    if (stream.isEmpty()) {
                        Log.w(TAG, "No audio stream from: " + INVIDIOUS[idx]);
                        continue;
                    }

                    // Berhasil — update working index
                    workingIdx = idx;
                    Song song = new Song(videoId, title, author, "", formatDuration(duration), stream, thumb);
                    song.lyrics = desc.length() > 30 ? desc : "";
                    Log.d(TAG, "Stream OK: " + title + " from " + INVIDIOUS[idx]);
                    callback.onSuccess(song);
                    return;

                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.w(TAG, "Stream attempt failed [" + INVIDIOUS[idx] + "]: " + e.getMessage());
                }
            }
            callback.onError("Gagal: " + lastError);
        }).start();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static List<Song> parseItems(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;
        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();
                String type = getStr(item, "type", "video");
                if (!type.equals("video")) continue;

                String id    = getStr(item, "videoId", "");
                if (id.isEmpty()) continue;

                String title  = getStr(item, "title", "Unknown");
                String author = getStr(item, "author", "Unknown");
                long   dur    = item.has("lengthSeconds") ? item.get("lengthSeconds").getAsLong() : 0;
                String thumb  = getBestThumb(item);

                // Skip video terlalu pendek (< 60s = bukan lagu)
                if (dur > 0 && dur < 60) continue;

                songs.add(new Song(id, title, author, "", formatDuration(dur), "", thumb));
            } catch (Exception e) {
                Log.w(TAG, "Parse error: " + e.getMessage());
            }
        }
        return songs;
    }

    private static String getBestThumb(JsonObject json) {
        if (!json.has("videoThumbnails")) return "";
        try {
            JsonArray thumbs = json.getAsJsonArray("videoThumbnails");
            for (JsonElement t : thumbs) {
                JsonObject th = t.getAsJsonObject();
                String q = getStr(th, "quality", "");
                if (q.equals("medium") || q.equals("high") || q.equals("maxres")) {
                    return getStr(th, "url", "");
                }
            }
            if (thumbs.size() > 0) return getStr(thumbs.get(0).getAsJsonObject(), "url", "");
        } catch (Exception ignored) {}
        return "";
    }

    private static String getBestAudio(JsonObject json) {
        // Prioritas 1: adaptiveFormats (audio only)
        if (json.has("adaptiveFormats")) {
            try {
                JsonArray af = json.getAsJsonArray("adaptiveFormats");
                String bestM4a = "";
                String bestOther = "";
                int bestBit = 0;

                for (JsonElement el : af) {
                    JsonObject f   = el.getAsJsonObject();
                    String type    = getStr(f, "type", "");
                    String url     = getStr(f, "url", "");
                    if (url.isEmpty() || !type.startsWith("audio")) continue;

                    int bitrate = 0;
                    try { bitrate = f.get("bitrate").getAsInt(); } catch (Exception ignored) {}

                    if (type.contains("mp4") || type.contains("m4a")) {
                        if (bitrate > bestBit || bestM4a.isEmpty()) {
                            bestBit = bitrate;
                            bestM4a = url;
                        }
                    } else if (bestM4a.isEmpty() && bitrate > bestBit) {
                        bestBit = bitrate;
                        bestOther = url;
                    }
                }
                if (!bestM4a.isEmpty()) return bestM4a;
                if (!bestOther.isEmpty()) return bestOther;
            } catch (Exception ignored) {}
        }

        // Prioritas 2: formatStreams (video+audio muxed)
        if (json.has("formatStreams")) {
            try {
                JsonArray fs = json.getAsJsonArray("formatStreams");
                for (JsonElement el : fs) {
                    JsonObject f = el.getAsJsonObject();
                    String url  = getStr(f, "url", "");
                    String type = getStr(f, "type", "");
                    if (!url.isEmpty() && type.contains("mp4")) return url;
                }
                if (fs.size() > 0) return getStr(fs.get(0).getAsJsonObject(), "url", "");
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static String getStr(JsonObject obj, String key, String def) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull())
                return obj.get(key).getAsString();
        } catch (Exception ignored) {}
        return def;
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
