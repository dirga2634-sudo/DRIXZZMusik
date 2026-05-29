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

    // Multiple Piped API instances - fallback jika satu down
    private static final String[] PIPED_APIS = {
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.reallyaweso.me",
        "https://piped-api.garudalinux.org",
        "https://api.piped.yt",
        "https://pipedapi.adminforge.de",
        "https://watchapi.whatever.social",
        "https://piped-api.codespace.cz"
    };

    private static String workingApi = null;

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build();

    public interface ApiCallback {
        void onSuccess(List<Song> songs);
        void onError(String message);
    }

    public interface SongCallback {
        void onSuccess(Song song);
        void onError(String message);
    }

    // Coba semua API instances, pakai yang pertama berhasil
    private static String fetchFromAny(String path) throws IOException {
        // Coba working API dulu (cached)
        if (workingApi != null) {
            try {
                String result = fetchUrl(workingApi + path);
                if (result != null) return result;
            } catch (IOException ignored) {}
        }

        // Coba semua API instances
        IOException lastError = null;
        for (String api : PIPED_APIS) {
            if (api.equals(workingApi)) continue; // sudah dicoba
            try {
                String result = fetchUrl(api + path);
                if (result != null) {
                    workingApi = api;
                    Log.d(TAG, "Using API: " + api);
                    return result;
                }
            } catch (IOException e) {
                lastError = e;
                Log.w(TAG, "API failed: " + api + " - " + e.getMessage());
            }
        }
        throw lastError != null ? lastError : new IOException("Semua server tidak bisa diakses");
    }

    private static String fetchUrl(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "DrizzxMusik/2.0")
            .addHeader("Accept", "application/json")
            .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code());
        }
        return response.body().string();
    }

    // ── Search ────────────────────────────────────────────────

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String path = "/search?q=" + query.replace(" ", "+") + "&filter=music_songs";
                String body = fetchFromAny(path);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonArray items = json.getAsJsonArray("items");
                List<Song> songs = parseSongs(items);
                callback.onSuccess(songs);
            } catch (IOException e) {
                callback.onError("Cek koneksi internet kamu");
            } catch (Exception e) {
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    // ── Trending ──────────────────────────────────────────────

    public static void getTrending(ApiCallback callback) {
        new Thread(() -> {
            try {
                // Pakai endpoint trending Piped yang asli untuk Indonesia
                String body = fetchFromAny("/trending?region=ID");
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                List<Song> songs = new ArrayList<>();

                for (JsonElement el : items) {
                    try {
                        JsonObject item = el.getAsJsonObject();
                        String type = item.has("type") ? item.get("type").getAsString() : "";
                        // Ambil semua video, bukan hanya "stream"
                        String id = "";
                        if (item.has("url")) {
                            id = item.get("url").getAsString().replace("/watch?v=", "");
                        } else if (item.has("videoId")) {
                            id = item.get("videoId").getAsString();
                        }
                        if (id.isEmpty()) continue;

                        String title = item.has("title") ? item.get("title").getAsString() : "Unknown";
                        String uploader = item.has("uploaderName") ? item.get("uploaderName").getAsString()
                            : item.has("uploader") ? item.get("uploader").getAsString() : "Unknown";
                        long dur = item.has("duration") ? item.get("duration").getAsLong() : 0;
                        String thumb = item.has("thumbnail") ? item.get("thumbnail").getAsString()
                            : item.has("thumbnailUrl") ? item.get("thumbnailUrl").getAsString() : "";

                        songs.add(new Song(id, title, uploader, "", formatDuration(dur), "", thumb));
                    } catch (Exception ignored) {}
                }

                if (songs.isEmpty()) {
                    // Fallback ke search kalau trending kosong
                    searchTrendingFallback(callback);
                    return;
                }

                callback.onSuccess(songs);

            } catch (IOException e) {
                // Fallback ke search
                searchTrendingFallback(callback);
            } catch (Exception e) {
                searchTrendingFallback(callback);
            }
        }).start();
    }

    private static void searchTrendingFallback(ApiCallback callback) {
        try {
            // Coba beberapa query populer Indonesia
            String[] queries = {"lagu indonesia hits 2024", "top hits indonesia", "musik viral indonesia"};
            for (String q : queries) {
                try {
                    String path = "/search?q=" + q.replace(" ", "+") + "&filter=music_songs";
                    String body = fetchFromAny(path);
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    JsonArray items = json.getAsJsonArray("items");
                    List<Song> songs = parseSongs(items);
                    if (!songs.isEmpty()) {
                        callback.onSuccess(songs);
                        return;
                    }
                } catch (Exception ignored) {}
            }
            callback.onError("Cek koneksi internet kamu");
        } catch (Exception e) {
            callback.onError("Cek koneksi internet kamu");
        }
    }

    // ── Stream URL ────────────────────────────────────────────

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            try {
                String body = fetchFromAny("/streams/" + videoId);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String title = json.has("title") ? json.get("title").getAsString() : "";
                String uploader = json.has("uploader") ? json.get("uploader").getAsString() : "";
                String thumbnail = json.has("thumbnailUrl") ? json.get("thumbnailUrl").getAsString() : "";
                long dur = json.has("duration") ? json.get("duration").getAsLong() : 0;

                JsonArray audioStreams = json.getAsJsonArray("audioStreams");
                String streamUrl = getBestAudioStream(audioStreams);

                Song song = new Song(videoId, title, uploader, "", formatDuration(dur), streamUrl, thumbnail);
                if (json.has("description")) {
                    song.lyrics = json.get("description").getAsString();
                }

                callback.onSuccess(song);

            } catch (IOException e) {
                callback.onError("Tidak dapat memuat stream");
            } catch (Exception e) {
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────

    private static List<Song> parseSongs(JsonArray items) {
        List<Song> songs = new ArrayList<>();
        if (items == null) return songs;

        for (JsonElement el : items) {
            try {
                JsonObject item = el.getAsJsonObject();
                String type = item.has("type") ? item.get("type").getAsString() : "";
                if (!type.isEmpty() && !type.equals("stream")) continue;

                String id = item.has("url") ?
                    item.get("url").getAsString().replace("/watch?v=", "") : "";
                if (id.isEmpty()) continue;

                String title = item.has("title") ? item.get("title").getAsString() : "Unknown";
                String uploader = item.has("uploaderName") ? item.get("uploaderName").getAsString() : "Unknown";
                long dur = item.has("duration") ? item.get("duration").getAsLong() : 0;
                String thumb = item.has("thumbnail") ? item.get("thumbnail").getAsString() : "";

                songs.add(new Song(id, title, uploader, "", formatDuration(dur), "", thumb));
            } catch (Exception ignored) {}
        }
        return songs;
    }

    private static String getBestAudioStream(JsonArray audioStreams) {
        if (audioStreams == null || audioStreams.size() == 0) return "";

        String streamUrl = "";
        int bestBitrate = 0;

        // Preferensi: m4a > mp4 > webm (lebih kompatibel di Android)
        for (JsonElement el : audioStreams) {
            try {
                JsonObject stream = el.getAsJsonObject();
                int bitrate = stream.has("bitrate") ? stream.get("bitrate").getAsInt() : 0;
                String format = stream.has("format") ? stream.get("format").getAsString().toLowerCase() : "";
                String mimeType = stream.has("mimeType") ? stream.get("mimeType").getAsString().toLowerCase() : "";

                // Prioritas format: m4a/mp4 lebih baik dari webm di Android
                boolean isPreferred = mimeType.contains("mp4") || format.contains("m4a") || format.contains("mp4");
                boolean isWebm = mimeType.contains("webm") || format.contains("webm");

                if (isPreferred && bitrate > bestBitrate) {
                    bestBitrate = bitrate;
                    streamUrl = stream.get("url").getAsString();
                } else if (!isWebm && streamUrl.isEmpty() && bitrate > 0) {
                    streamUrl = stream.get("url").getAsString();
                }
            } catch (Exception ignored) {}
        }

        // Kalau tidak ada preferred format, ambil yang pertama
        if (streamUrl.isEmpty()) {
            try {
                streamUrl = audioStreams.get(0).getAsJsonObject().get("url").getAsString();
            } catch (Exception ignored) {}
        }

        return streamUrl;
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
