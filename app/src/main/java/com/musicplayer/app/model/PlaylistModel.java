package com.musicplayer.app.model;

/**
 * Model data untuk satu playlist buatan pengguna, disimpan di database
 * SQLite lewat DatabaseHelper. songCount dihitung lewat JOIN saat query
 * sehingga selalu mencerminkan jumlah lagu terkini di playlist tersebut.
 */
public class PlaylistModel {

    private final long id;
    private final String name;
    private final long createdAt;
    private final int songCount;

    public PlaylistModel(long id, String name, long createdAt, int songCount) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.songCount = songCount;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getSongCount() {
        return songCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PlaylistModel)) return false;
        return id == ((PlaylistModel) obj).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
