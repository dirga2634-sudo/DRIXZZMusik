package com.drizzx.musik.model;

public class Song {
    public String id;
    public String title;
    public String artist;
    public String album;
    public String duration;
    public String streamUrl;
    public String thumbnailUrl;
    public String lyrics;
    public boolean isFavorite;

    public Song() {}

    public Song(String id, String title, String artist, String album,
                String duration, String streamUrl, String thumbnailUrl) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.streamUrl = streamUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.isFavorite = false;
    }
}
