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

    private static final String TAG   = "DrizzxApi";
    private static final String YTKEY = "AIzaSyA2PIJSWMqWZMmPBaVyV42HZWNE05e1ZIQ";
    private static final String YT    = "https://www.googleapis.com/youtube/v3";

    // Sama persis dengan web
    private static final String[] INVIDIOUS = {
        "https://invidious.fdn.fr",
        "https://yewtu.be",
        "https://inv.tux.pizza",
        "https://invidious.projectsegfau.lt"
    };
    private static int invIdx = 0;

    // Client khusus untuk Invidious - timeout 7s sama persis dengan web (AbortSignal.timeout(7000))
    private static final OkHttpClient invClient = new OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build();

    // Client untuk YouTube API
    private static final OkHttpClient ytClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();

    public interface ApiCallback {
        void onSuccess(List<Song> songs);
        void onError(String message);
    }

    public interface SongCallback {
        void onSuccess(Song song);
        void onError(String message);
    }

    // ── Search (YouTube Data API) ────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = YT + "/search?part=snippet&type=video&videoCategoryId=10"
                    + "&q=" + query.replace(" ", "+")
                    + "&maxResults=25&key=" + YTKEY;
                JsonObject json = ytFetch(url);
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

    // ── Trending (YouTube Data API) ──────────────────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = YT + "/videos?part=snippet,contentDetails"
                    + "&chart=mostPopular&videoCategoryId=10"
                    + "&maxResults=30&key=" + YTKEY;
                JsonObject json = ytFetch(url);
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

    // ── Stream URL — PORT PERSIS DARI WEB ───────────────────
    // Web: fetch(base+/api/v1/videos/id?fields=adaptiveFormats,formatStreams, timeout 7s)
    // filter audio, sort bitrate desc, ambil index 0

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            // Ambil metadata dari YT API dulu (title, thumb, dll)
            Song meta = fetchMeta(videoId);

            // Loop semua Invidious instances — sama persis dengan web
            for (int i = 0; i < INVIDIOUS.length; i++) {
                String base = INVIDIOUS[(invIdx + i) % INVIDIOUS.length];
                try {
                    Log.d(TAG, "Trying stream: " + base);

                    // Request persis sama dengan web
                    String url = base + "/api/v1/videos/" + videoId
                        + "?fields=adaptiveFormats,formatStreams";

                    Request req = new Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
                        .build();

                    String body;
                    try (Response resp = invClient.newCall(req).execute()) {
                        if (!resp.isSuccessful()) {
                            Log.w(TAG, "HTTP " + resp.code() + " from " + base);
                            continue;
                        }
                        body = resp.body() != null ? resp.body().string() : null;
                    }

                    if (body == null || body.isEmpty() || !body.trim().startsWith("{")) {
                        Log.w(TAG, "Bad response from " + base);
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                    // PERSIS WEB:
                    // const af = (d.adaptiveFormats||[]).filter(f=>f.type&&f.type.startsWith('audio'));
                    // af.sort((a,b)=>(b.bitrate||0)-(a.bitrate||0));
                    // if(af.length){ invIdx=(invIdx+i)%INVIDIOUS.length; return af[0].url; }
                    String streamUrl = null;

                    if (json.has("adaptiveFormats")) {
                        JsonArray af = json.getAsJsonArray("adaptiveFormats");

                        // filter audio only
                        List<JsonObject> audioList = new ArrayList<>();
                        for (JsonElement el : af) {
                            JsonObject f = el.getAsJsonObject();
                            String type = str(f, "type", "");
                            if (type.startsWith("audio")) {
                                audioList.add(f);
                            }
                        }

                        // sort by bitrate descending
                        audioList.sort((a, b) -> {
                            int ba = a.has("bitrate") ? a.get("bitrate").getAsInt() : 0;
                            int bb = b.has("bitrate") ? b.get("bitrate").getAsInt() : 0;
                            return Integer.compare(bb, ba);
                        });

                        // ambil index 0 — highest bitrate
                        if (!audioList.isEmpty()) {
                            streamUrl = str(audioList.get(0), "url", "");
                            Log.d(TAG, "Got audio stream, type: " + str(audioList.get(0), "type", "?")
                                + ", bitrate: " + (audioList.get(0).has("bitrate") ? audioList.get(0).get("bitrate").getAsInt() : 0));
                        }
                    }

                    // Fallback: formatStreams (muxed) — sama dengan web
                    // const fs = d.formatStreams||[]; if(fs.length){ return fs[0].url; }
                    if ((streamUrl == null || streamUrl.isEmpty()) && json.has("formatStreams")) {
                        JsonArray fs = json.getAsJsonArray("formatStreams");
                        if (fs.size() > 0) {
                            streamUrl = str(fs.get(0).getAsJsonObject(), "url", "");
                            Log.d(TAG, "Using formatStreams fallback");
                        }
                    }

                    if (streamUrl != null && !streamUrl.isEmpty()) {
                        invIdx = (invIdx + i) % INVIDIOUS.length;
                        meta.streamUrl = streamUrl;
                        Log.d(TAG, "Stream OK from: " + base);
                        callback.onSuccess(meta);
                        return;
                    }

                    Log.w(TAG, "No stream URL from: " + base);

                } catch (Exception e) {
                    Log.w(TAG, "Error from " + base + ": " + e.getMessage());
                }
            }

            callback.onError("Gagal memuat audio. Coba lagu lain.");
        }).start();
    }

    // ── Parse ────────────────────────────────────────────────

    private static List<Song> parseSearch(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;
        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();
                String id       = item.getAsJsonObject("id").get("videoId").getAsString();
                JsonObject s    = item.getAsJsonObject("snippet");
                songs.add(new Song(id, str(s,"title","?"), str(s,"channelTitle","?"), "", "", "", thumb(s)));
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
                String id = str(item, "id", "");
                if (id.isEmpty()) continue;
                JsonObject s = item.getAsJsonObject("snippet");
                String dur = item.has("contentDetails")
                    ? parseIso(str(item.getAsJsonObject("contentDetails"), "duration", "")) : "";
                songs.add(new Song(id, str(s,"title","?"), str(s,"channelTitle","?"), "", dur, "", thumb(s)));
            } catch (Exception ignored) {}
        }
        return songs;
    }

    private static Song fetchMeta(String videoId) {
        try {
            String url = YT + "/videos?part=snippet,contentDetails&id=" + videoId + "&key=" + YTKEY;
            JsonObject json = ytFetch(url);
            JsonArray items = json.getAsJsonArray("items");
            if (items != null && items.size() > 0) {
                JsonObject item = items.get(0).getAsJsonObject();
                JsonObject s    = item.getAsJsonObject("snippet");
                String dur      = item.has("contentDetails")
                    ? parseIso(str(item.getAsJsonObject("contentDetails"), "duration", "")) : "";
                return new Song(videoId, str(s,"title","?"), str(s,"channelTitle","?"), "", dur, "", thumb(s));
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchMeta: " + e.getMessage());
        }
        return new Song(videoId, "Unknown", "Unknown", "", "", "", "");
    }

    // ── Util ─────────────────────────────────────────────────

    private static JsonObject ytFetch(String url) throws IOException {
        Request req = new Request.Builder().url(url)
            .addHeader("User-Agent", "Mozilla/5.0")
            .build();
        try (Response resp = ytClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            String body = resp.body() != null ? resp.body().string() : "";
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static String thumb(JsonObject s) {
        try {
            JsonObject t = s.getAsJsonObject("thumbnails");
            if (t.has("medium"))  return t.getAsJsonObject("medium").get("url").getAsString();
            if (t.has("default")) return t.getAsJsonObject("default").get("url").getAsString();
        } catch (Exception ignored) {}
        return "";
    }

    private static String str(JsonObject o, String k, String d) {
        try { if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsString(); }
        catch (Exception ignored) {}
        return d;
    }

    private static String parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            int h=0, m=0, s=0;
            String t = iso.replace("PT","");
            if (t.contains("H")) { h=Integer.parseInt(t.substring(0,t.indexOf("H"))); t=t.substring(t.indexOf("H")+1); }
            if (t.contains("M")) { m=Integer.parseInt(t.substring(0,t.indexOf("M"))); t=t.substring(t.indexOf("M")+1); }
            if (t.contains("S")) s=Integer.parseInt(t.replace("S",""));
            return h>0 ? String.format("%d:%02d:%02d",h,m,s) : String.format("%d:%02d",m,s);
        } catch (Exception e) { return ""; }
    }
}
