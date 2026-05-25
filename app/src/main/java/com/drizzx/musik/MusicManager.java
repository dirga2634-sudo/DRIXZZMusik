package com.drizzx.musik;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.drizzx.musik.model.Song;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MusicManager {

    private static final String TAG = "DrizzxMusik";
    private static MusicManager instance;

    private ExoPlayer player;
    private Song currentSong;
    private List<Song> queue = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShuffled = false;
    private int repeatMode = Player.REPEAT_MODE_OFF;

    private List<Song> favorites = new ArrayList<>();
    private List<com.drizzx.musik.model.Playlist> playlists = new ArrayList<>();
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

        player = new ExoPlayer.Builder(ctx).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    playNext();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (listener != null) listener.onPlayPause(isPlaying);
            }
        });
    }

    public void setListener(OnPlayerStateChanged l) {
        this.listener = l;
    }

    // ── Playback ──────────────────────────────────────────────

    public void playSong(Song song) {
        if (player == null) return;
        currentSong = song;
        try {
            MediaItem item = MediaItem.fromUri(song.streamUrl);
            player.setMediaItem(item);
            player.prepare();
            player.play();
            if (listener != null) listener.onSongChanged(song);
            Log.d(TAG, "Playing: " + song.title + " | URL: " + song.streamUrl);
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
        if (player != null && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            return;
        }
        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        playSong(queue.get(currentIndex));
    }

    public void seekTo(long position) {
        if (player != null) player.seekTo(position);
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long getPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public Song getCurrentSong() { return currentSong; }

    public ExoPlayer getPlayer() { return player; }

    // ── Equalizer ─────────────────────────────────────────────

    public void setBassBoost(boolean enable, int strength) {
        // ExoPlayer handles audio via AudioTrack - bass boost via AudioEffect
        Log.d(TAG, "Bass boost: " + enable + " strength: " + strength);
    }

    // ── Favorites ─────────────────────────────────────────────

    public void toggleFavorite(Song song) {
        song.isFavorite = !song.isFavorite;
        if (song.isFavorite) {
            if (!containsFavorite(song.id)) favorites.add(song);
        } else {
            favorites.removeIf(s -> s.id.equals(song.id));
        }
        saveFavorites();
    }

    public boolean isFavorite(String songId) {
        return favorites.stream().anyMatch(s -> s.id.equals(songId));
    }

    public List<Song> getFavorites() { return favorites; }

    private boolean containsFavorite(String id) {
        return favorites.stream().anyMatch(s -> s.id.equals(id));
    }

    private void saveFavorites() {
        prefs.edit().putString("favorites", gson.toJson(favorites)).apply();
    }

    private void loadFavorites() {
        String json = prefs.getString("favorites", "[]");
        Type type = new TypeToken<List<Song>>(){}.getType();
        favorites = gson.fromJson(json, type);
        if (favorites == null) favorites = new ArrayList<>();
    }

    // ── Playlists ─────────────────────────────────────────────

    public List<com.drizzx.musik.model.Playlist> getPlaylists() { return playlists; }

    public void createPlaylist(String name) {
        String id = "pl_" + System.currentTimeMillis();
        playlists.add(new com.drizzx.musik.model.Playlist(id, name));
        savePlaylists();
    }

    public void addToPlaylist(String playlistId, Song song) {
        for (com.drizzx.musik.model.Playlist pl : playlists) {
            if (pl.id.equals(playlistId)) {
                pl.songs.add(song);
                break;
            }
        }
        savePlaylists();
    }

    private void savePlaylists() {
        prefs.edit().putString("playlists", gson.toJson(playlists)).apply();
    }

    private void loadPlaylists() {
        String json = prefs.getString("playlists", "[]");
        Type type = new TypeToken<List<com.drizzx.musik.model.Playlist>>(){}.getType();
        playlists = gson.fromJson(json, type);
        if (playlists == null) playlists = new ArrayList<>();
    }

    public void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
