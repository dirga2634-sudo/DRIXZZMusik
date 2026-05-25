package com.drizzx.musik.model;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    public String id;
    public String name;
    public String description;
    public List<Song> songs;
    public String coverUrl;

    public Playlist(String id, String name) {
        this.id = id;
        this.name = name;
        this.songs = new ArrayList<>();
    }
}
