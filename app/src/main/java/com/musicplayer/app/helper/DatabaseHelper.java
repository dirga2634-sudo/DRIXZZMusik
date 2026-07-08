package com.musicplayer.app.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.musicplayer.app.model.PlaylistModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Menyimpan playlist sederhana buatan pengguna lewat SQLite.
 * Dua tabel: "playlists" (daftar playlist) dan "playlist_songs"
 * (relasi many-to-many antara playlist dan ID lagu MediaStore).
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "playlists.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_PLAYLISTS = "playlists";
    private static final String TABLE_PLAYLIST_SONGS = "playlist_songs";

    private static final String COL_ID = "_id";
    private static final String COL_NAME = "name";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_PLAYLIST_ID = "playlist_id";
    private static final String COL_SONG_ID = "song_id";
    private static final String COL_POSITION = "position";

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PLAYLISTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_PLAYLIST_SONGS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PLAYLIST_ID + " INTEGER NOT NULL, "
                + COL_SONG_ID + " INTEGER NOT NULL, "
                + COL_POSITION + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + COL_PLAYLIST_ID + ") REFERENCES " + TABLE_PLAYLISTS + "(" + COL_ID + ") ON DELETE CASCADE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLIST_SONGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLISTS);
        onCreate(db);
    }

    /**
     * Membuat playlist baru, mengembalikan ID playlist yang baru dibuat
     * atau -1 bila gagal.
     */
    public long createPlaylist(String name) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_CREATED_AT, System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        return db.insert(TABLE_PLAYLISTS, null, values);
    }

    public void renamePlaylist(long playlistId, String newName) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, newName);
        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_PLAYLISTS, values, COL_ID + "=?", new String[]{String.valueOf(playlistId)});
    }

    public void deletePlaylist(long playlistId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PLAYLIST_SONGS, COL_PLAYLIST_ID + "=?", new String[]{String.valueOf(playlistId)});
        db.delete(TABLE_PLAYLISTS, COL_ID + "=?", new String[]{String.valueOf(playlistId)});
    }

    /**
     * Mengambil seluruh playlist beserta jumlah lagu di masing-masing,
     * diurutkan dari yang terbaru dibuat.
     */
    public List<PlaylistModel> getAllPlaylists() {
        List<PlaylistModel> playlists = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT p." + COL_ID + ", p." + COL_NAME + ", p." + COL_CREATED_AT
                + ", (SELECT COUNT(*) FROM " + TABLE_PLAYLIST_SONGS + " ps WHERE ps." + COL_PLAYLIST_ID + " = p." + COL_ID + ") AS song_count"
                + " FROM " + TABLE_PLAYLISTS + " p ORDER BY p." + COL_CREATED_AT + " DESC";

        try (Cursor cursor = db.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                long createdAt = cursor.getLong(2);
                int songCount = cursor.getInt(3);
                playlists.add(new PlaylistModel(id, name, createdAt, songCount));
            }
        }
        return playlists;
    }

    /**
     * True bila lagu dengan songId sudah ada di playlist tersebut,
     * dipakai untuk mencegah duplikat.
     */
    public boolean isSongInPlaylist(long playlistId, long songId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_PLAYLIST_SONGS, new String[]{COL_ID},
                COL_PLAYLIST_ID + "=? AND " + COL_SONG_ID + "=?",
                new String[]{String.valueOf(playlistId), String.valueOf(songId)},
                null, null, null)) {
            return cursor.getCount() > 0;
        }
    }

    /**
     * Menambahkan lagu ke akhir playlist. Mengembalikan false bila lagu
     * sudah ada di playlist tersebut (tidak ditambahkan dua kali).
     */
    public boolean addSongToPlaylist(long playlistId, long songId) {
        if (isSongInPlaylist(playlistId, songId)) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        int nextPosition = getSongCount(playlistId);
        ContentValues values = new ContentValues();
        values.put(COL_PLAYLIST_ID, playlistId);
        values.put(COL_SONG_ID, songId);
        values.put(COL_POSITION, nextPosition);
        db.insert(TABLE_PLAYLIST_SONGS, null, values);
        return true;
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PLAYLIST_SONGS,
                COL_PLAYLIST_ID + "=? AND " + COL_SONG_ID + "=?",
                new String[]{String.valueOf(playlistId), String.valueOf(songId)});
    }

    private int getSongCount(long playlistId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_PLAYLIST_SONGS, new String[]{"COUNT(*)"},
                COL_PLAYLIST_ID + "=?", new String[]{String.valueOf(playlistId)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    /**
     * Mengambil daftar ID lagu dalam sebuah playlist, terurut sesuai
     * posisi saat ditambahkan.
     */
    public List<Long> getSongIdsForPlaylist(long playlistId) {
        List<Long> ids = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_PLAYLIST_SONGS, new String[]{COL_SONG_ID},
                COL_PLAYLIST_ID + "=?", new String[]{String.valueOf(playlistId)},
                null, null, COL_POSITION + " ASC")) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
        }
        return ids;
    }
}
