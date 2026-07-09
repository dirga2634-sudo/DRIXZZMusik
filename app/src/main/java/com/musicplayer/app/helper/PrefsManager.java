package com.musicplayer.app.helper;

import android.content.Context;
import android.content.SharedPreferences;

import com.musicplayer.app.util.Constants;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Membungkus seluruh akses SharedPreferences aplikasi:
 * - Daftar lagu favorit (disimpan sebagai Set<String> berisi ID lagu)
 * - Lagu & posisi terakhir diputar
 * - Preferensi sort, shuffle, repeat, dan tampilan ukuran file
 */
public class PrefsManager {

    private static final String PREFS_NAME = "music_player_prefs";

    private static final String KEY_FAVORITES = "favorite_song_ids";
    private static final String KEY_LAST_SONG_ID = "last_song_id";
    private static final String KEY_LAST_POSITION_MS = "last_position_ms";
    private static final String KEY_SORT_ORDER = "sort_order";
    private static final String KEY_SHUFFLE_ENABLED = "shuffle_enabled";
    private static final String KEY_REPEAT_MODE = "repeat_mode";
    private static final String KEY_SHOW_FILE_SIZE = "show_file_size";
    private static final String KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ----- Favorit -----

    public boolean isFavorite(long songId) {
        return getFavoriteIds().contains(String.valueOf(songId));
    }

    public void setFavorite(long songId, boolean favorite) {
        Set<String> updated = new HashSet<>(getFavoriteIds());
        if (favorite) {
            updated.add(String.valueOf(songId));
        } else {
            updated.remove(String.valueOf(songId));
        }
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply();
    }

    public Set<String> getFavoriteIds() {
        Set<String> stored = prefs.getStringSet(KEY_FAVORITES, Collections.emptySet());
        return stored != null ? stored : Collections.emptySet();
    }

    public void clearFavorites() {
        prefs.edit().putStringSet(KEY_FAVORITES, new HashSet<>()).apply();
    }

    // ----- Lagu terakhir -----

    public void saveLastSong(long songId, int positionMs) {
        prefs.edit()
                .putLong(KEY_LAST_SONG_ID, songId)
                .putInt(KEY_LAST_POSITION_MS, positionMs)
                .apply();
    }

    public long getLastSongId() {
        return prefs.getLong(KEY_LAST_SONG_ID, -1L);
    }

    public int getLastPositionMs() {
        return prefs.getInt(KEY_LAST_POSITION_MS, 0);
    }

    // ----- Preferensi tampilan & pemutaran -----

    public int getSortOrder() {
        return prefs.getInt(KEY_SORT_ORDER, Constants.SORT_NAME);
    }

    public void setSortOrder(int sortOrder) {
        prefs.edit().putInt(KEY_SORT_ORDER, sortOrder).apply();
    }

    public boolean isShuffleEnabled() {
        return prefs.getBoolean(KEY_SHUFFLE_ENABLED, false);
    }

    public void setShuffleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHUFFLE_ENABLED, enabled).apply();
    }

    public int getRepeatMode() {
        return prefs.getInt(KEY_REPEAT_MODE, Constants.REPEAT_OFF);
    }

    public void setRepeatMode(int mode) {
        prefs.edit().putInt(KEY_REPEAT_MODE, mode).apply();
    }

    public boolean isShowFileSizeEnabled() {
        return prefs.getBoolean(KEY_SHOW_FILE_SIZE, true);
    }

    public void setShowFileSizeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOW_FILE_SIZE, enabled).apply();
    }

    public boolean hasShownBatteryPrompt() {
        return prefs.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false);
    }

    public void setBatteryPromptShown() {
        prefs.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, true).apply();
    }
}
