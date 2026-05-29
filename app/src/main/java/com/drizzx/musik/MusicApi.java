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

    // Sama persis kayak web version
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
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

    // ── Search (YouTube Data API) ──────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = YT_BASE + "/search?part=snippet&type=video&videoCategoryId=10"
                    + "&q=" + query.replace(" ", "+")
                    + "&maxResults=25&key=" + YTKEY;
                JsonObject json = fetchJson(url);
                if (json.has("error")) { callback.onError("API Error"); return; }
                List<Song> songs = parseSearch(json.getAsJsonArray("items"));
                if (songs.isEmpty()) callback.onError("Lagu tidak ditemukan");
                else callback.onSuccess(songs);
            } catch (Exception e) {
                Log.e(TAG, "Search: " + e.getMessage());
                callback.onError("Gagal mencari");
            }
        }).start();
    }

    // ── Trending (YouTube Data API) ───────────────────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = YT_BASE + "/videos?part=snippet,contentDetails"
                    + "&chart=mostPopular&videoCategoryId=10"
                    + "&maxResults=30&key=" + YTKEY;
                JsonObject json = fetchJson(url);
                if (json.has("error")) { callback.onError("API Error"); return; }
                List<Song> songs = parseTrending(json.getAsJsonArray("items"));
                if (songs.isEmpty()) callback.onError("Gagal memuat");
                else callback.onSuccess(songs);
            } catch (Exception e) {
                Log.e(TAG, "Trending: " + e.getMessage());
                callback.onError("Gagal memuat. Coba lagi.");
            }
        }).start();
    }

    // ── Stream URL - SALIN PERSIS DARI WEB VERSION ────────────
    // Web: fetch `/api/v1/videos/{id}?fields=adaptiveFormats,formatStreams`
    // Sort by bitrate, ambil audio tertinggi → set ke audio.src

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            for (int i = 0; i < INVIDIOUS.length; i++) {
                String base = INVIDIOUS[(invIdx + i) % INVIDIOUS.length];
                try {
                    // SAMA PERSIS dengan web version
                    String url = base + "/api/v1/videos/" + videoId
                        + "?fields=adaptiveFormats,formatStreams,title,author,lengthSeconds,videoThumbnails";

                    String body = fetchRaw(url);

                    // Validasi JSON (bukan HTML error page)
                    if (body == null || (!body.trim().startsWith("{") && !body.trim().startsWith("["))) {
                        Log.w(TAG, "Non-JSON dari: " + base);
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                    // SALIN LOGIKA WEB: filter audio, sort by bitrate, ambil tertinggi
                    String streamUrl = getAudioStream(json);
                    if (streamUrl == null || streamUrl.isEmpty()) {
                        Log.w(TAG, "No audio stream: " + base);
                        continue;
                    }

                    // Berhasil!
                    invIdx = (invIdx + i) % INVIDIOUS.length;
                    String title    = getStr(json, "title", "Unknown");
                    String author   = getStr(json, "author", "Unknown");
                    long   duration = json.has("lengthSeconds") ? json.get("lengthSeconds").getAsLong() : 0;
                    String thumb    = getBestThumb(json);

                    Log.d(TAG, "Stream OK [" + base + "]: " + title);
                    Log.d(TAG, "URL: " + streamUrl.substring(0, Math.min(80, streamUrl.length())) + "...");

                    Song song = new Song(videoId, title, author, "", fmt(duration), streamUrl, thumb);
                    callback.onSuccess(song);
                    return;

                } catch (Exception e) {
                    Log.w(TAG, "Fail [" + base + "]: " + e.getMessage());
                }
            }
            callback.onError("Semua server gagal. Coba lagu lain.");
        }).start();
    }

    // Salin dari web: filter audio, sort by bitrate, ambil tertinggi
    private static String getAudioStream(JsonObject json) {
        // adaptiveFormats (audio only) - sama kayak web
        if (json.has("adaptiveFormats")) {
            try {
                JsonArray af = json.getAsJsonArray("adaptiveFormats");
                // Filter: hanya audio
                List<JsonObject> audioStreams = new ArrayList<>();
                for (JsonElement el : af) {
                    JsonObject f = el.getAsJsonObject();
                    String type = getStr(f, "type", "");
                    if (type.startsWith("audio")) {
                        audioStreams.add(f);
                    }
                }
                // Sort by bitrate (descending) - sama kayak web: af.sort((a,b)=>(b.bitrate||0)-(a.bitrate||0))
                audioStreams.sort((a, b) -> {
                    int ba = a.has("bitrate") ? a.get("bitrate").getAsInt() : 0;
                    int bb = b.has("bitrate") ? b.get("bitrate").getAsInt() : 0;
                    return Integer.compare(bb, ba);
                });
                // Ambil yang pertama (bitrate tertinggi)
                if (!audioStreams.isEmpty()) {
                    String url = getStr(audioStreams.get(0), "url", "");
                    if (!url.isEmpty()) return url;
                }
            } catch (Exception e) {
                Log.w(TAG, "adaptiveFormats parse: " + e.getMessage());
            }
        }
        // Fallback: formatStreams (video+audio muxed) - sama kayak web
        if (json.has("formatStreams")) {
            try {
                JsonArray fs = json.getAsJsonArray("formatStreams");
                if (fs.size() > 0) {
                    return getStr(fs.get(0).getAsJsonObject(), "url", "");
                }
            } catch (Exception e) {
                Log.w(TAG, "formatStreams parse: " + e.getMessage());
            }
        }
        return "";
    }

    // ── Parse helpers ─────────────────────────────────────────

    private static List<Song> parseSearch(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;
        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();
                String id       = item.getAsJsonObject("id").get("videoId").getAsString();
                JsonObject snip = item.getAsJsonObject("snippet");
                songs.add(new Song(id, getStr(snip,"title","?"), getStr(snip,"channelTitle","?"), "", "", "", getThumb(snip)));
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
                JsonObject snip = item.getAsJsonObject("snippet");
                String dur = item.has("contentDetails")
                    ? parseIso(getStr(item.getAsJsonObject("contentDetails"), "duration", "")) : "";
                songs.add(new Song(id, getStr(snip,"title","?"), getStr(snip,"channelTitle","?"), "", dur, "", getThumb(snip)));
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

    // ── Utils ─────────────────────────────────────────────────

    private static JsonObject fetchJson(String url) throws IOException {
        return JsonParser.parseString(fetchRaw(url)).getAsJsonObject();
    }

    private static String fetchRaw(String url) throws IOException {
        Request req = new Request.Builder().url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
            .addHeader("Accept", "application/json")
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            String b = resp.body() != null ? resp.body().string() : null;
            if (b == null || b.isEmpty()) throw new IOException("Empty response");
            return b;
        }
    }

    private static String getStr(JsonObject o, String k, String d) {
        try { if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsString(); } catch (Exception ig) {}
        return d;
    }

    private static String parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            int h=0,m=0,s=0; String t=iso.replace("PT","");
            if(t.contains("H")){h=Integer.parseInt(t.substring(0,t.indexOf("H")));t=t.substring(t.indexOf("H")+1);}
            if(t.contains("M")){m=Integer.parseInt(t.substring(0,t.indexOf("M")));t=t.substring(t.indexOf("M")+1);}
            if(t.contains("S")) s=Integer.parseInt(t.replace("S",""));
            return h>0 ? String.format("%d:%02d:%02d",h,m,s) : String.format("%d:%02d",m,s);
        } catch (Exception e) { return ""; }
    }

    private static String fmt(long sec) {
        return String.format("%d:%02d", sec/60, sec%60);
    }
}
