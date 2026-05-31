package com.drizzx.musik;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.drizzx.musik.model.Playlist;
import com.drizzx.musik.model.Song;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicManager {

    private static final String TAG = "MusicManager";
    private static MusicManager instance;

    private ExoPlayer player;
    private Song currentSong;
    private List<Song> queue     = new ArrayList<>();
    private int currentIndex     = 0;
    private List<Song> favorites = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    private SharedPreferences prefs;
    private final Gson gson = new Gson();

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

        // Header WAJIB — ini yang bikin YouTube CDN mau serve stream
        // Web pakai fetch() browser yang otomatis set Referer dari youtube.com
        // ExoPlayer harus set manual
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer",          "https://www.youtube.com/");
        headers.put("Origin",           "https://www.youtube.com");
        headers.put("Accept",           "*/*");
        headers.put("Accept-Language",  "id-ID,id;q=0.9,en;q=0.8");

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000);

        player = new ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                new DefaultMediaSourceFactory(new DefaultDataSource.Factory(ctx, http)))
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
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage());
                // Auto next saat error
                playNext();
            }
        });
    }

    public void setListener(OnPlayerStateChanged l) { this.listener = l; }

    public void playSong(Song song) {
        if (player == null || song == null
            || song.streamUrl == null || song.streamUrl.isEmpty()) {
            Log.e(TAG, "Cannot play: " + (song == null ? "null" : "empty URL"));
            return;
        }
        currentSong = song;
        Log.d(TAG, "Playing: " + song.title);
        Log.d(TAG, "URL: " + song.streamUrl.substring(0, Math.min(100, song.streamUrl.length())));
        player.setMediaItem(MediaItem.fromUri(song.streamUrl));
        player.prepare();
        player.play();
        if (listener != null) listener.onSongChanged(song);
    }

    public void playQueue(List<Song> songs, int idx) {
        queue = new ArrayList<>(songs);
        currentIndex = Math.max(0, Math.min(idx, songs.size()-1));
        playSong(queue.get(currentIndex));
    }

    public void playPause() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.play();
    }

    public void playNext() {
        if (queue.isEmpty()) return;
        currentIndex = (currentIndex + 1) % queue.size();
        Song next = queue.get(currentIndex);
        if (next.streamUrl == null || next.streamUrl.isEmpty()) return;
        playSong(next);
    }

    public void playPrev() {
        if (queue.isEmpty()) return;
        if (player != null && player.getCurrentPosition() > 3000) { player.seekTo(0); return; }
        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        playSong(queue.get(currentIndex));
    }

    public void seekTo(long ms)   { if (player != null) player.seekTo(ms); }
    public boolean isPlaying()    { return player != null && player.isPlaying(); }
    public long getPosition()     { return player != null ? player.getCurrentPosition() : 0; }
    public long getDuration()     { return player != null ? player.getDuration() : 0; }
    public Song getCurrentSong()  { return currentSong; }
    public ExoPlayer getPlayer()  { return player; }

    // ── Favorites ─────────────────────────────────────────

    public void toggleFavorite(Song s) {
        if (isFavorite(s.id)) favorites.removeIf(f -> f.id.equals(s.id));
        else favorites.add(s);
        saveFavorites();
    }
    public boolean isFavorite(String id) { return favorites.stream().anyMatch(s -> s.id.equals(id)); }
    public List<Song> getFavorites()     { return favorites; }
    private void saveFavorites() { prefs.edit().putString("fav", gson.toJson(favorites)).apply(); }
    private void loadFavorites() {
        Type t = new TypeToken<List<Song>>(){}.getType();
        favorites = gson.fromJson(prefs.getString("fav","[]"), t);
        if (favorites == null) favorites = new ArrayList<>();
    }

    // ── Playlists ─────────────────────────────────────────

    public List<Playlist> getPlaylists() { return playlists; }
    public void createPlaylist(String name) {
        playlists.add(new Playlist("pl_" + System.currentTimeMillis(), name));
        savePlaylists();
    }
    public void addToPlaylist(String plId, Song song) {
        for (Playlist p : playlists) if (p.id.equals(plId)) { p.songs.add(song); break; }
        savePlaylists();
    }
    private void savePlaylists() { prefs.edit().putString("pl", gson.toJson(playlists)).apply(); }
    private void loadPlaylists() {
        Type t = new TypeToken<List<Playlist>>(){}.getType();
        playlists = gson.fromJson(prefs.getString("pl","[]"), t);
        if (playlists == null) playlists = new ArrayList<>();
    }

    public void release() { if (player != null) { player.release(); player = null; } }
}
