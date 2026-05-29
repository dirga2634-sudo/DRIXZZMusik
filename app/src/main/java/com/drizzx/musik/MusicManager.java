package com.drizzx.musik;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.drizzx.musik.model.Song;
import com.drizzx.musik.model.Playlist;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

public class MusicManager {

    private static final String TAG = "DrizzxMusik";
    private static MusicManager instance;

    private ExoPlayer player;
    private Song currentSong;
    private List<Song> queue = new ArrayList<>();
    private int currentIndex = 0;

    private List<Song> favorites = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    private SharedPreferences prefs;
    private Gson gson = new Gson();

    public interface OnPlayerStateChanged {
        void onSongChanged(Song song);
        void onPlayPause(boolean isPlaying);
        void onProgress(long position, long duration);
    }

    private OnPlayerStateChanged listener;

    private MusicManager() {}

    public static MusicManager getInstance() {
        if (instance == null) instance = new MusicManager();
        return instance;
    }

    public void init(Context ctx) {
        prefs = ctx.getSharedPreferences("drizzx_musik", Context.MODE_PRIVATE);
        loadFavorites();
        loadPlaylists();

        // OkHttp client dengan header yang sama persis kayak web version
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

        // OkHttpDataSource - ini yang bikin web work, sekarang APK juga pakai ini
        OkHttpDataSource.Factory okhttpFactory = new OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .setDefaultRequestProperties(new java.util.HashMap<String, String>() {{
                put("Accept", "*/*");
                put("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8");
                put("Origin", "https://inv.nadeko.net");
                put("Referer", "https://inv.nadeko.net/");
            }});

        player = new ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(okhttpFactory))
            .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) playNext();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (listener != null) listener.onPlayPause(isPlaying);
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage());
            }
        });
    }

    public void setListener(OnPlayerStateChanged l) { this.listener = l; }

    public void playSong(Song song) {
        if (player == null || song == null || song.streamUrl == null) return;
        currentSong = song;
        try {
            Log.d(TAG, "Playing: " + song.title);
            Log.d(TAG, "Stream URL: " + song.streamUrl);
            player.setMediaItem(MediaItem.fromUri(song.streamUrl));
            player.prepare();
            player.play();
            if (listener != null) listener.onSongChanged(song);
        } catch (Exception e) {
            Log.e(TAG, "Play error: " + e.getMessage());
        }
    }

    public void playQueue(List<Song> songs, int startIndex) {
        queue = new ArrayList<>(songs);
        currentIndex = startIndex;
        playSong(queue.get(currentIndex));
    }

    public void playPause() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    public void playNext() {
        if (queue.isEmpty()) return;
        currentIndex = (currentIndex + 1) % queue.size();
        playSong(queue.get(currentIndex));
    }

    public void playPrev() {
        if (queue.isEmpty()) return;
        if (player != null && player.getCurrentPosition() > 3000) { player.seekTo(0); return; }
        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        playSong(queue.get(currentIndex));
    }

    public void seekTo(long pos) { if (player != null) player.seekTo(pos); }
    public boolean isPlaying()   { return player != null && player.isPlaying(); }
    public long getPosition()    { return player != null ? player.getCurrentPosition() : 0; }
    public long getDuration()    { return player != null ? player.getDuration() : 0; }
    public Song getCurrentSong() { return currentSong; }
    public ExoPlayer getPlayer() { return player; }

    // ── Favorites ─────────────────────────────────────────────

    public void toggleFavorite(Song song) {
        if (isFavorite(song.id)) favorites.removeIf(s -> s.id.equals(song.id));
        else favorites.add(song);
        saveFavorites();
    }
    public boolean isFavorite(String id) { return favorites.stream().anyMatch(s -> s.id.equals(id)); }
    public List<Song> getFavorites()     { return favorites; }

    private void saveFavorites() { prefs.edit().putString("favorites", gson.toJson(favorites)).apply(); }
    private void loadFavorites() {
        Type t = new TypeToken<List<Song>>(){}.getType();
        favorites = gson.fromJson(prefs.getString("favorites", "[]"), t);
        if (favorites == null) favorites = new ArrayList<>();
    }

    // ── Playlists ─────────────────────────────────────────────

    public List<Playlist> getPlaylists() { return playlists; }
    public void createPlaylist(String name) { playlists.add(new Playlist("pl_" + System.currentTimeMillis(), name)); savePlaylists(); }
    public void addToPlaylist(String plId, Song song) {
        for (Playlist pl : playlists) if (pl.id.equals(plId)) { pl.songs.add(song); break; }
        savePlaylists();
    }

    private void savePlaylists() { prefs.edit().putString("playlists", gson.toJson(playlists)).apply(); }
    private void loadPlaylists() {
        Type t = new TypeToken<List<Playlist>>(){}.getType();
        playlists = gson.fromJson(prefs.getString("playlists", "[]"), t);
        if (playlists == null) playlists = new ArrayList<>();
    }

    public void release() { if (player != null) { player.release(); player = null; } }
}
