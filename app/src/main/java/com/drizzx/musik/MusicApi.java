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

    // Invidious instances - lebih stabil dari Piped, banyak server aktif
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

    // Coba semua instance, pakai yang pertama berhasil
    private static String fetchFromAny(String path) throws IOException {
        // Coba working index dulu
        try {
            String url = INVIDIOUS[workingIdx] + path;
            Log.d(TAG, "Trying: " + url);
            return fetchUrl(url);
        } catch (IOException ignored) {
            Log.w(TAG, "Working server failed, trying others...");
        }

        // Coba semua yang lain
        for (int i = 0; i < INVIDIOUS.length; i++) {
            if (i == workingIdx) continue;
            try {
                String url = INVIDIOUS[i] + path;
                Log.d(TAG, "Trying: " + url);
                String result = fetchUrl(url);
                workingIdx = i; // simpan yang berhasil
                Log.d(TAG, "Using server: " + INVIDIOUS[i]);
                return result;
            } catch (IOException e) {
                Log.w(TAG, "Failed: " + INVIDIOUS[i] + " -> " + e.getMessage());
            }
        }
        throw new IOException("Semua server tidak bisa diakses");
    }

    private static String fetchUrl(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Android)")
            .addHeader("Accept", "application/json")
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            String body = response.body().string();
            if (body == null || body.isEmpty()) {
                throw new IOException("Empty response");
            }
            return body;
        }
    }

    // ── Search ────────────────────────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                // Invidious search API
                String path = "/api/v1/search?q="
                    + query.replace(" ", "+")
                    + "&type=video&page=1";

                String body = fetchFromAny(path);
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                List<Song> songs = parseInvidiousSearch(items);

                if (songs.isEmpty()) {
                    callback.onError("Lagu tidak ditemukan");
                } else {
                    callback.onSuccess(songs);
                }
            } catch (IOException e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                callback.onError("Gagal mencari: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Search parse error: " + e.getMessage());
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    // ── Trending ──────────────────────────────────────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                // Invidious trending API - semua musik global
                String body = fetchFromAny("/api/v1/trending?type=music");
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                List<Song> songs = parseInvidiousSearch(items);

                if (!songs.isEmpty()) {
                    Log.d(TAG, "Trending loaded: " + songs.size() + " songs");
                    callback.onSuccess(songs);
                    return;
                }

                // Fallback: trending umum tanpa filter
                body = fetchFromAny("/api/v1/trending");
                items = JsonParser.parseString(body).getAsJsonArray();
                songs = parseInvidiousSearch(items);

                if (!songs.isEmpty()) {
                    callback.onSuccess(songs);
                    return;
                }

                // Fallback: search lagu Indonesia populer
                searchFallback(callback);

            } catch (IOException e) {
                Log.e(TAG, "Trending error: " + e.getMessage());
                searchFallback(callback);
            } catch (Exception e) {
                Log.e(TAG, "Trending parse error: " + e.getMessage());
                searchFallback(callback);
            }
        }).start();
    }

    private static void searchFallback(ApiCallback callback) {
        String[] queries = {
            "top+hits+2024",
            "music+viral+2024",
            "best+songs+2024"
        };
        for (String q : queries) {
            try {
                String body = fetchFromAny("/api/v1/search?q=" + q + "&type=video&page=1");
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                List<Song> songs = parseInvidiousSearch(items);
                if (!songs.isEmpty()) {
                    callback.onSuccess(songs);
                    return;
                }
            } catch (Exception ignored) {}
        }
        callback.onError("Gagal memuat. Coba lagi.");
    }

    // ── Stream URL ────────────────────────────────────────────

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            try {
                // Invidious video endpoint: dapat info + semua stream URL sekaligus
                String body = fetchFromAny("/api/v1/videos/" + videoId
                    + "?fields=title,author,lengthSeconds,videoThumbnails,adaptiveFormats,formatStreams,description");
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String title     = getStr(json, "title", "Unknown");
                String author    = getStr(json, "author", "Unknown");
                long   duration  = json.has("lengthSeconds") ? json.get("lengthSeconds").getAsLong() : 0;
                String thumb     = getBestThumb(json);
                String desc      = getStr(json, "description", "");

                // Cari audio stream terbaik
                String streamUrl = getBestAudio(json);

                if (streamUrl.isEmpty()) {
                    callback.onError("Tidak ada stream tersedia");
                    return;
                }

                Song song = new Song(videoId, title, author, "", formatDuration(duration), streamUrl, thumb);
                song.lyrics = desc.length() > 20 ? desc : "";

                Log.d(TAG, "Stream loaded for: " + title);
                callback.onSuccess(song);

            } catch (IOException e) {
                Log.e(TAG, "Stream error: " + e.getMessage());
                callback.onError("Gagal memuat audio");
            } catch (Exception e) {
                Log.e(TAG, "Stream parse error: " + e.getMessage());
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────

    private static List<Song> parseInvidiousSearch(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;

        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();

                // Invidious bisa return video/playlist/channel - ambil video saja
                String type = getStr(item, "type", "video");
                if (!type.equals("video")) continue;

                String id    = getStr(item, "videoId", "");
                if (id.isEmpty()) continue;

                String title  = getStr(item, "title", "Unknown");
                String author = getStr(item, "author", "Unknown");
                long   dur    = item.has("lengthSeconds") ? item.get("lengthSeconds").getAsLong() : 0;
                String thumb  = getBestThumb(item);

                // Filter: skip video terlalu pendek (< 60 detik = bukan lagu)
                if (dur > 0 && dur < 60) continue;

                songs.add(new Song(id, title, author, "", formatDuration(dur), "", thumb));
            } catch (Exception e) {
                Log.w(TAG, "Parse item error: " + e.getMessage());
            }
        }
        return songs;
    }

    private static String getBestThumb(JsonObject json) {
        if (!json.has("videoThumbnails")) return "";
        try {
            JsonArray thumbs = json.getAsJsonArray("videoThumbnails");
            // Cari kualitas "medium" atau "high" dulu
            for (JsonElement t : thumbs) {
                JsonObject th = t.getAsJsonObject();
                String quality = getStr(th, "quality", "");
                if (quality.equals("medium") || quality.equals("high") || quality.equals("maxres")) {
                    return getStr(th, "url", "");
                }
            }
            // Kalau tidak ada, pakai yang pertama
            if (thumbs.size() > 0) {
                return getStr(thumbs.get(0).getAsJsonObject(), "url", "");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String getBestAudio(JsonObject json) {
        // Prioritas 1: adaptiveFormats (audio only) - kualitas terbaik
        if (json.has("adaptiveFormats")) {
            try {
                JsonArray af = json.getAsJsonArray("adaptiveFormats");
                String bestUrl  = "";
                int    bestBit  = 0;
                String bestM4a  = ""; // Prioritaskan m4a (mp4 audio)

                for (JsonElement el : af) {
                    JsonObject f  = el.getAsJsonObject();
                    String type   = getStr(f, "type", "");
                    String url    = getStr(f, "url", "");
                    if (url.isEmpty() || !type.startsWith("audio")) continue;

                    int bitrate = 0;
                    if (f.has("bitrate")) {
                        try { bitrate = f.get("bitrate").getAsInt(); } catch (Exception ignored) {}
                    }

                    // m4a/mp4 audio lebih kompatibel di Android ExoPlayer
                    if (type.contains("mp4") || type.contains("m4a")) {
                        if (bitrate > bestBit || bestM4a.isEmpty()) {
                            bestBit = bitrate;
                            bestM4a = url;
                        }
                    } else if (bestM4a.isEmpty() && bitrate > bestBit) {
                        bestBit = bitrate;
                        bestUrl = url;
                    }
                }

                if (!bestM4a.isEmpty()) return bestM4a;
                if (!bestUrl.isEmpty()) return bestUrl;
            } catch (Exception ignored) {}
        }

        // Prioritas 2: formatStreams (video+audio muxed) - fallback
        if (json.has("formatStreams")) {
            try {
                JsonArray fs = json.getAsJsonArray("formatStreams");
                // Ambil stream 360p atau 720p (ada audio-nya)
                for (JsonElement el : fs) {
                    JsonObject f = el.getAsJsonObject();
                    String url  = getStr(f, "url", "");
                    String type = getStr(f, "type", "");
                    if (!url.isEmpty() && type.contains("mp4")) return url;
                }
                // Kalau tidak ada mp4, ambil yang pertama
                if (fs.size() > 0) {
                    return getStr(fs.get(0).getAsJsonObject(), "url", "");
                }
            } catch (Exception ignored) {}
        }

        return "";
    }

    private static String getStr(JsonObject obj, String key, String def) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        } catch (Exception ignored) {}
        return def;
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
