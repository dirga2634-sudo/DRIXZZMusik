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
    private static final String PIPED_API = "https://pipedapi.kavin.rocks";

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

    public static void search(String query, ApiCallback callback) {
        new Thread(() -> {
            try {
                String url = PIPED_API + "/search?q=" + query.replace(" ", "+") + "&filter=music_songs";
                Request request = new Request.Builder().url(url)
                    .addHeader("User-Agent", "DrizzxMusik/1.0").build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    callback.onError("HTTP " + response.code());
                    return;
                }
                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonArray items = json.getAsJsonArray("items");
                List<Song> songs = new ArrayList<>();
                for (JsonElement el : items) {
                    try {
                        JsonObject item = el.getAsJsonObject();
                        String type = item.has("type") ? item.get("type").getAsString() : "";
                        if (!type.equals("stream")) continue;
                        String id = item.get("url").getAsString().replace("/watch?v=", "");
                        String title = item.get("title").getAsString();
                        String uploader = item.has("uploaderName") ? item.get("uploaderName").getAsString() : "Unknown";
                        long dur = item.has("duration") ? item.get("duration").getAsLong() : 0;
                        String thumb = item.has("thumbnail") ? item.get("thumbnail").getAsString() : "";
                        songs.add(new Song(id, title, uploader, "", formatDuration(dur), "", thumb));
                    } catch (Exception ignored) {}
                }
                callback.onSuccess(songs);
            } catch (IOException e) {
                callback.onError("Cek koneksi internet kamu");
            }
        }).start();
    }

    public static void getStreamUrl(String videoId, SongCallback callback) {
        new Thread(() -> {
            try {
                String url = PIPED_API + "/streams/" + videoId;
                Request request = new Request.Builder().url(url)
                    .addHeader("User-Agent", "DrizzxMusik/1.0").build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    callback.onError("HTTP " + response.code());
                    return;
                }
                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String title = json.has("title") ? json.get("title").getAsString() : "";
                String uploader = json.has("uploader") ? json.get("uploader").getAsString() : "";
                String thumbnail = json.has("thumbnailUrl") ? json.get("thumbnailUrl").getAsString() : "";
                long dur = json.has("duration") ? json.get("duration").getAsLong() : 0;
                JsonArray audioStreams = json.getAsJsonArray("audioStreams");
                String streamUrl = "";
                int bestBitrate = 0;
                for (JsonElement el : audioStreams) {
                    JsonObject stream = el.getAsJsonObject();
                    int bitrate = stream.has("bitrate") ? stream.get("bitrate").getAsInt() : 0;
                    String format = stream.has("format") ? stream.get("format").getAsString() : "";
                    if (bitrate > bestBitrate && !format.contains("webm")) {
                        bestBitrate = bitrate;
                        streamUrl = stream.get("url").getAsString();
                    }
                }
                if (streamUrl.isEmpty() && audioStreams.size() > 0) {
                    streamUrl = audioStreams.get(0).getAsJsonObject().get("url").getAsString();
                }
                Song song = new Song(videoId, title, uploader, "", formatDuration(dur), streamUrl, thumbnail);
                if (json.has("description")) song.lyrics = json.get("description").getAsString();
                callback.onSuccess(song);
            } catch (IOException e) {
                callback.onError("Tidak dapat memuat stream");
            }
        }).start();
    }

    public static void getTrending(ApiCallback callback) {
        search("top hits indonesia 2024", callback);
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
