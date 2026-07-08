package com.musicplayer.app.util;

/**
 * Kumpulan konstanta yang dipakai di berbagai class agar tidak ada
 * duplikasi string/angka literal yang rawan typo.
 */
public final class Constants {

    private Constants() {
        // Class utilitas, tidak boleh diinstansiasi
    }

    // Request codes
    public static final int PERMISSION_REQUEST_CODE = 1001;

    // Notification
    public static final int NOTIFICATION_ID = 501;
    public static final String NOTIFICATION_CHANNEL_ID = "music_playback_channel";

    // Intent extras
    public static final String EXTRA_PLAYLIST_ID = "extra_playlist_id";
    public static final String EXTRA_PLAYLIST_NAME = "extra_playlist_name";
    public static final String EXTRA_SONG_ID = "extra_song_id";
    public static final String EXTRA_MODE = "extra_mode";

    // Mode untuk activity_song_list (dipakai FavoriteActivity & PlaylistSongsActivity)
    public static final int MODE_FAVORITES = 1;
    public static final int MODE_PLAYLIST_SONGS = 2;

    // Sort order
    public static final int SORT_NAME = 0;
    public static final int SORT_DATE = 1;

    // Repeat mode
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    // Durasi seek untuk fast-forward / rewind (ms)
    public static final int SEEK_STEP_MS = 10000;

    // Interval update progress playback (ms)
    public static final long PROGRESS_UPDATE_INTERVAL_MS = 500L;

    // Durasi tampil splash screen (ms)
    public static final long SPLASH_DURATION_MS = 1500L;

    // Batas berapa ms dari awal lagu sebelum tombol "previous" mengulang
    // lagu yang sama alih-alih pindah ke lagu sebelumnya (perilaku umum
    // di aplikasi pemutar musik)
    public static final int RESTART_THRESHOLD_MS = 3000;
}
