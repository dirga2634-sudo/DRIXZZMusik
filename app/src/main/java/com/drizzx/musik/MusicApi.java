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

    private static final String TAG    = "DrizzxApi";
    private static final String YTKEY  = "AIzaSyA2PIJSWMqWZMmPBaVyV42HZWNE05e1ZIQ";
    private static final String YT_BASE = "https://www.googleapis.com/youtube/v3";

    // Invidious hanya untuk stream URL (YouTube API tidak kasih stream)
    private static final String[] INVIDIOUS = {
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.privacyredirect.com",
        "https://iv.melmac.space",
        "https://yt.artemislena.eu",
        "https://invidious.fdn.fr",
        "https://invidious.io.lol",
        "https://invidious.darkness.services"
    };
    private static int invIdx = 0;

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
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

    // ── Search (YouTube Data API) ────────────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = YT_BASE + "/search"
                    + "?part=snippet"
                    + "&type=video"
                    + "&videoCategoryId=10"   // kategori musik
                    + "&q=" + query.replace(" ", "+")
                    + "&maxResults=25"
                    + "&key=" + YTKEY;

                String body = fetchUrl(url);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                // Cek error dari YouTube API
                if (json.has("error")) {
                    String msg = json.getAsJsonObject("error").get("message").getAsString();
                    Log.e(TAG, "YT API Error: " + msg);
                    callback.onError("API Error: " + msg);
                    return;
                }

                JsonArray items = json.getAsJsonArray("items");
                List<Song> songs = parseSearchItems(items);

                if (songs.isEmpty()) callback.onError("Lagu tidak ditemukan");
                else callback.onSuccess(songs);

            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                callback.onError("Gagal mencari lagu");
            }
        }).start();
    }

    // ── Trending (YouTube Data API - mostPopular musik) ──────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                // YouTube chart=mostPopular, kategori 10 = Music
                String url = YT_BASE + "/videos"
                    + "?part=snippet,contentDetails"
                    + "&chart=mostPopular"
                    + "&videoCategoryId=10"
                    + "&maxResults=30"
                    + "&key=" + YTKEY;

                String body = fetchUrl(url);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (json.has("error")) {
                    String msg = json.getAsJsonObject("error").get("message").getAsString();
                    Log.e(TAG, "YT API Error: " + msg);
                    callback.onError("API Error: " + msg);
                    return;
                }

                JsonArray items = json.getAsJsonArray("items");
                List<Song> songs = parseTrendingItems(items);

                if (songs.isEmpty()) callback.onError("Gagal memuat trending");
                else callback.onSuccess(songs);

            } catch (Exception e) {
                Log.e(TAG, "Trending error: " + e.getMessage());
                callback.onError("Gagal memuat. Coba lagi.");
            }
        }).start();
    }

    // ── Stream URL (Invidious - satu-satunya yang bisa kasih audio URL) ──

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            // Coba semua Invidious server sampai dapat stream valid
            for (int attempt = 0; attempt < INVIDIOUS.length; attempt++) {
                int idx = (invIdx + attempt) % INVIDIOUS.length;
                try {
                    String url = INVIDIOUS[idx] + "/api/v1/videos/" + videoId
                        + "?fields=title,author,lengthSeconds,videoThumbnails,adaptiveFormats,formatStreams";

                    String body = fetchUrl(url);
                    if (!isJson(body)) {
                        Log.w(TAG, "Non-JSON from: " + INVIDIOUS[idx]);
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    String streamUrl = getBestAudio(json);

                    if (streamUrl.isEmpty()) {
                        Log.w(TAG, "No stream from: " + INVIDIOUS[idx]);
                        continue;
                    }

                    // Berhasil
                    invIdx = idx;
                    String title    = getStr(json, "title", "Unknown");
                    String author   = getStr(json, "author", "Unknown");
                    long   duration = json.has("lengthSeconds") ? json.get("lengthSeconds").getAsLong() : 0;
                    String thumb    = getBestThumb(json);

                    Song song = new Song(videoId, title, author, "", formatDuration(duration), streamUrl, thumb);
                    Log.d(TAG, "Stream OK: " + title);
                    callback.onSuccess(song);
                    return;

                } catch (Exception e) {
                    Log.w(TAG, "Stream fail [" + INVIDIOUS[idx] + "]: " + e.getMessage());
                }
            }
            callback.onError("Gagal memuat audio. Coba lagu lain.");
        }).start();
    }

    // ── Parse helpers ────────────────────────────────────────────────

    // Parse hasil /search YouTube API
    private static List<Song> parseSearchItems(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;
        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();
                String videoId = item.getAsJsonObject("id").get("videoId").getAsString();
                JsonObject snip = item.getAsJsonObject("snippet");

                String title   = getStr(snip, "title", "Unknown");
                String channel = getStr(snip, "channelTitle", "Unknown");
                String thumb   = getThumbFromSnippet(snip);

                songs.add(new Song(videoId, title, channel, "", "", "", thumb));
            } catch (Exception e) {
                Log.w(TAG, "Parse search item: " + e.getMessage());
            }
        }
        return songs;
    }

    // Parse hasil /videos trending YouTube API
    private static List<Song> parseTrendingItems(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;
        for (JsonElement el : items) {
            try {
                JsonObject item   = el.getAsJsonObject();
                String videoId    = getStr(item, "id", "");
                if (videoId.isEmpty()) continue;

                JsonObject snip   = item.getAsJsonObject("snippet");
                String title      = getStr(snip, "title", "Unknown");
                String channel    = getStr(snip, "channelTitle", "Unknown");
                String thumb      = getThumbFromSnippet(snip);
                String duration   = "";

                // Parse durasi dari contentDetails (format: PT3M45S)
                if (item.has("contentDetails")) {
                    String iso = getStr(item.getAsJsonObject("contentDetails"), "duration", "");
                    duration = parseIsoDuration(iso);
                }

                songs.add(new Song(videoId, title, channel, "", duration, "", thumb));
            } catch (Exception e) {
                Log.w(TAG, "Parse trending item: " + e.getMessage());
            }
        }
        return songs;
    }

    private static String getThumbFromSnippet(JsonObject snip) {
        try {
            JsonObject thumbs = snip.getAsJsonObject("thumbnails");
            // Prioritas: medium > default
            if (thumbs.has("medium"))
                return thumbs.getAsJsonObject("medium").get("url").getAsString();
            if (thumbs.has("default"))
                return thumbs.getAsJsonObject("default").get("url").getAsString();
        } catch (Exception ignored) {}
        return "";
    }

    // Parse ISO 8601 duration: PT3M45S -> "3:45"
    private static String parseIsoDuration(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            int h = 0, m = 0, s = 0;
            String tmp = iso.replace("PT", "");
            if (tmp.contains("H")) {
                h = Integer.parseInt(tmp.substring(0, tmp.indexOf("H")));
                tmp = tmp.substring(tmp.indexOf("H") + 1);
            }
            if (tmp.contains("M")) {
                m = Integer.parseInt(tmp.substring(0, tmp.indexOf("M")));
                tmp = tmp.substring(tmp.indexOf("M") + 1);
            }
            if (tmp.contains("S")) {
                s = Integer.parseInt(tmp.replace("S", ""));
            }
            if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
            return String.format("%d:%02d", m, s);
        } catch (Exception e) {
            return "";
        }
    }

    // ── Invidious stream helpers ─────────────────────────────────────

    private static String getBestAudio(JsonObject json) {
        // Prioritas 1: adaptiveFormats (audio only - kualitas terbaik)
        if (json.has("adaptiveFormats")) {
            try {
                JsonArray af = json.getAsJsonArray("adaptiveFormats");
                String bestM4a = "", bestOther = "";
                int bestBit = 0;
                for (JsonElement el : af) {
                    JsonObject f  = el.getAsJsonObject();
                    String type   = getStr(f, "type", "");
                    String url    = getStr(f, "url", "");
                    if (url.isEmpty() || !type.startsWith("audio")) continue;
                    int bit = 0;
                    try { bit = f.get("bitrate").getAsInt(); } catch (Exception ignored) {}
                    if (type.contains("mp4") || type.contains("m4a")) {
                        if (bit > bestBit || bestM4a.isEmpty()) { bestBit = bit; bestM4a = url; }
                    } else if (bestM4a.isEmpty() && bit > bestBit) {
                        bestBit = bit; bestOther = url;
                    }
                }
                if (!bestM4a.isEmpty()) return bestM4a;
                if (!bestOther.isEmpty()) return bestOther;
            } catch (Exception ignored) {}
        }
        // Prioritas 2: formatStreams (muxed video+audio)
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

    private static String getBestThumb(JsonObject json) {
        if (!json.has("videoThumbnails")) return "";
        try {
            JsonArray thumbs = json.getAsJsonArray("videoThumbnails");
            for (JsonElement t : thumbs) {
                JsonObject th = t.getAsJsonObject();
                String q = getStr(th, "quality", "");
                if (q.equals("medium") || q.equals("high") || q.equals("maxres"))
                    return getStr(th, "url", "");
            }
            if (thumbs.size() > 0) return getStr(thumbs.get(0).getAsJsonObject(), "url", "");
        } catch (Exception ignored) {}
        return "";
    }

    // ── Util ─────────────────────────────────────────────────────────

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

    private static boolean isJson(String s) {
        if (s == null || s.length() < 2) return false;
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private static String getStr(JsonObject obj, String key, String def) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull())
                return obj.get(key).getAsString();
        } catch (Exception ignored) {}
        return def;
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
