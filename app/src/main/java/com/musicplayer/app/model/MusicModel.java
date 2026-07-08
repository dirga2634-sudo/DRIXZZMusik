package com.musicplayer.app.model;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

/**
 * Model data untuk satu lagu hasil scan MediaStore.
 * Semua field bersifat final (immutable) karena data lagu dari MediaStore
 * tidak berubah selama satu sesi scan.
 */
public class MusicModel {

    private final long id;
    private final String title;
    private final String artist;
    private final String album;
    private final long albumId;
    private final long duration;
    private final long size;
    private final long dateAdded;
    private final String data;

    public MusicModel(long id, String title, String artist, String album, long albumId,
                       long duration, long size, long dateAdded, String data) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.duration = duration;
        this.size = size;
        this.dateAdded = dateAdded;
        this.data = data;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public long getAlbumId() {
        return albumId;
    }

    public long getDuration() {
        return duration;
    }

    public long getSize() {
        return size;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public String getData() {
        return data;
    }

    /**
     * URI content:// resmi untuk lagu ini, dipakai untuk playback lewat
     * MediaPlayer dan untuk MediaMetadataRetriever saat mengambil cover album.
     * Ini adalah cara yang aman terhadap scoped storage (tidak bergantung
     * pada path file mentah yang mungkin null di Android 10+).
     */
    public Uri getContentUri() {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MusicModel)) return false;
        return id == ((MusicModel) obj).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
