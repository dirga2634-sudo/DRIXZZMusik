package com.musicplayer.app.helper;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Melakukan scan seluruh lagu di perangkat lewat MediaStore. Semua query
 * dijalankan di background thread lewat AppExecutors agar UI Thread tidak
 * pernah terblokir oleh operasi disk MediaStore.
 */
public final class MediaStoreHelper {

    private static final long MIN_DURATION_MS = 15000L; // saring notifikasi/ringtone pendek

    private MediaStoreHelper() {
        // Class utilitas, tidak boleh diinstansiasi
    }

    public interface OnSongsLoadedListener {
        void onSongsLoaded(List<MusicModel> songs);
    }

    public interface OnSongLoadedListener {
        void onSongLoaded(MusicModel song);
    }

    /**
     * Scan seluruh lagu di perangkat secara asynchronous, hasil dikirim
     * kembali ke main thread lewat listener.
     */
    public static void loadAllSongsAsync(Context context, OnSongsLoadedListener listener) {
        Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO(() -> {
            List<MusicModel> songs = querySongs(appContext, null);
            AppExecutors.getInstance().mainThread(() -> listener.onSongsLoaded(songs));
        });
    }

    /**
     * Mengambil satu lagu berdasarkan ID secara asynchronous. Dipakai untuk
     * memulihkan "lagu terakhir" saat MusicService pertama kali dibuat.
     */
    public static void loadSongByIdAsync(Context context, long songId, OnSongLoadedListener listener) {
        Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO(() -> {
            String selection = MediaStore.Audio.Media._ID + "=?";
            List<MusicModel> result = querySongs(appContext, new QueryFilter(selection, new String[]{String.valueOf(songId)}));
            MusicModel song = result.isEmpty() ? null : result.get(0);
            AppExecutors.getInstance().mainThread(() -> listener.onSongLoaded(song));
        });
    }

    /**
     * Mengambil beberapa lagu sekaligus berdasarkan daftar ID (satu query,
     * bukan query berulang per ID) lalu mengembalikan hasilnya dengan
     * urutan sesuai daftar ID yang diberikan. Dipakai untuk menampilkan isi
     * playlist.
     */
    public static void loadSongsByIdsAsync(Context context, List<Long> ids, OnSongsLoadedListener listener) {
        Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO(() -> {
            if (ids.isEmpty()) {
                AppExecutors.getInstance().mainThread(() -> listener.onSongsLoaded(new ArrayList<>()));
                return;
            }
            StringBuilder placeholders = new StringBuilder();
            String[] args = new String[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                placeholders.append(i == 0 ? "?" : ",?");
                args[i] = String.valueOf(ids.get(i));
            }
            String selection = MediaStore.Audio.Media._ID + " IN (" + placeholders + ")";
            List<MusicModel> unordered = querySongs(appContext, new QueryFilter(selection, args));

            Map<Long, MusicModel> byId = new HashMap<>();
            for (MusicModel song : unordered) {
                byId.put(song.getId(), song);
            }
            List<MusicModel> ordered = new ArrayList<>();
            for (Long id : ids) {
                MusicModel song = byId.get(id);
                if (song != null) {
                    ordered.add(song);
                }
            }
            AppExecutors.getInstance().mainThread(() -> listener.onSongsLoaded(ordered));
        });
    }

    public static Uri getContentUriForSong(long songId) {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId);
    }

    /**
     * Filter opsional (selection + selectionArgs) untuk query MediaStore.
     */
    private static class QueryFilter {
        final String selection;
        final String[] args;

        QueryFilter(String selection, String[] args) {
            this.selection = selection;
            this.args = args;
        }
    }

    private static List<MusicModel> querySongs(Context context, QueryFilter extraFilter) {
        List<MusicModel> songs = new ArrayList<>();

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
        };

        String selection;
        String[] selectionArgs;
        if (extraFilter != null) {
            selection = extraFilter.selection;
            selectionArgs = extraFilter.args;
        } else {
            selection = MediaStore.Audio.Media.IS_MUSIC + "=1 AND "
                    + MediaStore.Audio.Media.DURATION + ">=?";
            selectionArgs = new String[]{String.valueOf(MIN_DURATION_MS)};
        }

        String sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder)) {

            if (cursor == null) {
                return songs;
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String title = cursor.getString(titleCol);
                String artist = cursor.getString(artistCol);
                String album = cursor.getString(albumCol);
                long albumId = cursor.getLong(albumIdCol);
                long duration = cursor.getLong(durationCol);
                long size = cursor.getLong(sizeCol);
                long dateAdded = cursor.getLong(dateAddedCol);
                String data = cursor.getString(dataCol);

                if (TextUtils.isEmpty(title)) {
                    title = "Unknown Title";
                }
                if (TextUtils.isEmpty(artist) || "<unknown>".equals(artist)) {
                    artist = "Unknown Artist";
                }
                if (TextUtils.isEmpty(album)) {
                    album = "Unknown Album";
                }

                songs.add(new MusicModel(id, title, artist, album, albumId, duration, size, dateAdded, data));
            }
        }

        return songs;
    }
}
