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

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MusicApi {

    private static final String TAG    = "DrizzxApi";
    private static final String YTKEY  = "AIzaSyA2PIJSWMqWZMmPBaVyV42HZWNE05e1ZIQ";
    private static final String YT     = "https://www.googleapis.com/youtube/v3";

    private static final String[] INVIDIOUS = {
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.privacyredirect.com",
        "https://iv.melmac.space",
        "https://invidious.io.lol",
        "https://yt.artemislena.eu",
        "https://invidious.fdn.fr",
        "https://invidious.darkness.services"
    };

    private static int invIdx = 0;

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

    // ── Search ────────────────────────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = YT + "/search?part=snippet&type=video&videoCategoryId=10"
                    + "&q=" + query.replace(" ", "+")
                    + "&maxResults=25&key=" + YTKEY;
                JsonObject json = fetchJson(url);
                if (json.has("error")) { callback.onError("API Error"); return; }
                List<Song> songs = parseSearch(json.getAsJsonArray("items"));
                if (songs.isEmpty()) callback.onError("Lagu tidak ditemukan");
                else callback.onSuccess(songs);
            } catch (Exception e) {
                Log.e(TAG, "search: " + e.getMessage());
                callback.onError("Gagal mencari");
            }
        }).start();
    }

    // ── Trending ──────────────────────────────────────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = YT + "/videos?part=snippet,contentDetails"
                    + "&chart=mostPopular&videoCategoryId=10"
                    + "&maxResults=30&key=" + YTKEY;
                JsonObject json = fetchJson(url);
                if (json.has("error")) { callback.onError("API Error"); return; }
                List<Song> songs = parseTrending(json.getAsJsonArray("items"));
                if (songs.isEmpty()) callback.onError("Gagal memuat");
                else callback.onSuccess(songs);
            } catch (Exception e) {
                Log.e(TAG, "trending: " + e.getMessage());
                callback.onError("Gagal memuat. Coba lagi.");
            }
        }).start();
    }

    // ── Stream URL ────────────────────────────────────────────
    // 3 metode: cobalt → invidious latest_version → invidious api

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            Log.d(TAG, "Getting stream for: " + videoId);

            // Metadata dari YouTube API dulu
            Song meta = fetchYTMeta(videoId);

            // Metode 1: cobalt.tools (paling reliable, return direct URL)
            String url = getCobaltStream(videoId);
            if (url != null) {
                Log.d(TAG, "Stream via cobalt OK");
                meta.streamUrl = url;
                callback.onSuccess(meta);
                return;
            }

            // Metode 2: Invidious /latest_version?itag=140 (m4a audio langsung)
            url = getLatestVersion(videoId);
            if (url != null) {
                Log.d(TAG, "Stream via latest_version OK");
                meta.streamUrl = url;
                callback.onSuccess(meta);
                return;
            }

            // Metode 3: Invidious /api/v1/videos (parse adaptiveFormats)
            url = getInvidiousApi(videoId);
            if (url != null) {
                Log.d(TAG, "Stream via invidious api OK");
                meta.streamUrl = url;
                callback.onSuccess(meta);
                return;
            }

            callback.onError("Gagal memuat audio. Coba lagu lain.");
        }).start();
    }

    // Metode 1: cobalt.tools
    private static String getCobaltStream(String videoId) {
        try {
            String body = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId
                + "\",\"downloadMode\":\"audio\",\"audioFormat\":\"best\"}";

            RequestBody rb = RequestBody.create(body, MediaType.get("application/json"));
            Request req = new Request.Builder()
                .url("https://api.cobalt.tools/")
                .post(rb)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) return null;
                String s = resp.body() != null ? resp.body().string() : null;
                if (!isJson(s)) return null;
                JsonObject json = JsonParser.parseString(s).getAsJsonObject();
                // cobalt: {"status":"tunnel/stream", "url":"..."}
                if (json.has("url")) {
                    String u = json.get("url").getAsString();
                    if (!u.isEmpty()) return u;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "cobalt: " + e.getMessage());
        }
        return null;
    }

    // Metode 2: Invidious /latest_version (direct audio CDN)
    private static String getLatestVersion(String videoId) {
        // itag 140 = m4a 128kbps (ExoPlayer kompatibel)
        // itag 251 = opus webm (fallback)
        int[] itags = {140, 251};

        for (int i = 0; i < INVIDIOUS.length; i++) {
            String base = INVIDIOUS[(invIdx + i) % INVIDIOUS.length];
            for (int itag : itags) {
                try {
                    // Tidak pakai local=true supaya URL-nya langsung ke CDN
                    String url = base + "/latest_version?id=" + videoId
                        + "&itag=" + itag;

                    // HEAD request buat cek apakah URL valid
                    Request req = new Request.Builder()
                        .url(url)
                        .head()
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .build();

                    try (Response resp = client.newCall(req).execute()) {
                        // 200 atau 206 = OK, bisa diplay
                        if (resp.code() == 200 || resp.code() == 206) {
                            // Ambil URL final setelah redirect
                            String finalUrl = resp.request().url().toString();
                            invIdx = (invIdx + i) % INVIDIOUS.length;
                            Log.d(TAG, "latest_version OK: itag=" + itag);
                            return finalUrl;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "latest_version [" + base + "]: " + e.getMessage());
                }
            }
        }
        return null;
    }

    // Metode 3: Invidious /api/v1/videos - parse stream URLs
    private static String getInvidiousApi(String videoId) {
        for (int i = 0; i < INVIDIOUS.length; i++) {
            String base = INVIDIOUS[(invIdx + i) % INVIDIOUS.length];
            try {
                String url = base + "/api/v1/videos/" + videoId
                    + "?fields=adaptiveFormats,formatStreams&local=true";

                String body = fetchRaw(url);
                if (!isJson(body)) continue;

                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String stream = pickBestAudio(json);
                if (stream != null && !stream.isEmpty()) {
                    invIdx = (invIdx + i) % INVIDIOUS.length;
                    return stream;
                }
            } catch (Exception e) {
                Log.w(TAG, "invapi [" + base + "]: " + e.getMessage());
            }
        }
        return null;
    }

    private static String pickBestAudio(JsonObject json) {
        // adaptiveFormats: audio only, bitrate tertinggi
        if (json.has("adaptiveFormats")) {
            try {
                JsonArray af = json.getAsJsonArray("adaptiveFormats");
                List<JsonObject> audio = new ArrayList<>();
                for (JsonElement el : af) {
                    JsonObject f = el.getAsJsonObject();
                    if (getStr(f, "type", "").startsWith("audio"))
                        audio.add(f);
                }
                // sort by bitrate descending
                audio.sort((a, b) -> {
                    int ba = a.has("bitrate") ? a.get("bitrate").getAsInt() : 0;
                    int bb = b.has("bitrate") ? b.get("bitrate").getAsInt() : 0;
                    return Integer.compare(bb, ba);
                });
                if (!audio.isEmpty()) {
                    String u = getStr(audio.get(0), "url", "");
                    if (!u.isEmpty()) return u;
                }
            } catch (Exception ignored) {}
        }
        // formatStreams: muxed, pakai yang pertama
        if (json.has("formatStreams")) {
            try {
                JsonArray fs = json.getAsJsonArray("formatStreams");
                if (fs.size() > 0) return getStr(fs.get(0).getAsJsonObject(), "url", "");
            } catch (Exception ignored) {}
        }
        return null;
    }

    // Ambil metadata dari YouTube API
    private static Song fetchYTMeta(String videoId) {
        try {
            String url = YT + "/videos?part=snippet,contentDetails&id=" + videoId + "&key=" + YTKEY;
            JsonObject json = fetchJson(url);
            JsonArray items = json.getAsJsonArray("items");
            if (items != null && items.size() > 0) {
                JsonObject item = items.get(0).getAsJsonObject();
                JsonObject snip = item.getAsJsonObject("snippet");
                String title = getStr(snip, "title", "Unknown");
                String channel = getStr(snip, "channelTitle", "Unknown");
                String thumb = getThumb(snip);
                String dur = "";
                if (item.has("contentDetails"))
                    dur = parseIso(getStr(item.getAsJsonObject("contentDetails"), "duration", ""));
                return new Song(videoId, title, channel, "", dur, "", thumb);
            }
        } catch (Exception e) {
            Log.w(TAG, "meta: " + e.getMessage());
        }
        return new Song(videoId, "Unknown", "Unknown", "", "", "", "");
    }

    // ── Parse ─────────────────────────────────────────────────

    private static List<Song> parseSearch(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;
        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();
                String id = item.getAsJsonObject("id").get("videoId").getAsString();
                JsonObject s = item.getAsJsonObject("snippet");
                songs.add(new Song(id, getStr(s,"title","?"), getStr(s,"channelTitle","?"), "", "", "", getThumb(s)));
            } catch (Exception ignored) {}
        }
        return songs;
    }

    private static List<Song> parseTrending(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;
        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();
                String id = getStr(item, "id", "");
                if (id.isEmpty()) continue;
                JsonObject s = item.getAsJsonObject("snippet");
                String dur = item.has("contentDetails")
                    ? parseIso(getStr(item.getAsJsonObject("contentDetails"), "duration", "")) : "";
                songs.add(new Song(id, getStr(s,"title","?"), getStr(s,"channelTitle","?"), "", dur, "", getThumb(s)));
            } catch (Exception ignored) {}
        }
        return songs;
    }

    private static String getThumb(JsonObject snip) {
        try {
            JsonObject t = snip.getAsJsonObject("thumbnails");
            if (t.has("medium"))  return t.getAsJsonObject("medium").get("url").getAsString();
            if (t.has("default")) return t.getAsJsonObject("default").get("url").getAsString();
        } catch (Exception ignored) {}
        return "";
    }

    // ── Utils ─────────────────────────────────────────────────

    private static JsonObject fetchJson(String url) throws IOException {
        return JsonParser.parseString(fetchRaw(url)).getAsJsonObject();
    }

    private static String fetchRaw(String url) throws IOException {
        Request req = new Request.Builder().url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
            .addHeader("Accept", "application/json, */*")
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            String b = resp.body() != null ? resp.body().string() : null;
            if (b == null || b.isEmpty()) throw new IOException("Empty");
            return b;
        }
    }

    private static boolean isJson(String s) {
        if (s == null || s.length() < 2) return false;
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private static String getStr(JsonObject o, String k, String d) {
        try { if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsString(); }
        catch (Exception ignored) {}
        return d;
    }

    private static String parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            int h=0, m=0, s=0;
            String t = iso.replace("PT", "");
            if (t.contains("H")) { h = Integer.parseInt(t.substring(0, t.indexOf("H"))); t = t.substring(t.indexOf("H")+1); }
            if (t.contains("M")) { m = Integer.parseInt(t.substring(0, t.indexOf("M"))); t = t.substring(t.indexOf("M")+1); }
            if (t.contains("S")) s = Integer.parseInt(t.replace("S",""));
            return h > 0 ? String.format("%d:%02d:%02d",h,m,s) : String.format("%d:%02d",m,s);
        } catch (Exception e) { return ""; }
    }
}
