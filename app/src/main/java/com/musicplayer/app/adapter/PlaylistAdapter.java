package com.musicplayer.app.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.musicplayer.app.R;
import com.musicplayer.app.model.PlaylistModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter RecyclerView untuk daftar playlist. Dipakai di dua tempat:
 * - PlaylistActivity (mode manajemen: long-press untuk rename/delete)
 * - Dialog "Add to Playlist" (mode pilih: tap langsung menambahkan lagu)
 */
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistActionListener {
        void onPlaylistClick(PlaylistModel playlist);

        void onPlaylistLongClick(PlaylistModel playlist);
    }

    private final Context context;
    private final OnPlaylistActionListener listener;
    private final boolean enableLongPress;
    private List<PlaylistModel> playlists = new ArrayList<>();

    public PlaylistAdapter(Context context, boolean enableLongPress, OnPlaylistActionListener listener) {
        this.context = context;
        this.enableLongPress = enableLongPress;
        this.listener = listener;
    }

    public void submitList(List<PlaylistModel> newPlaylists) {
        this.playlists = new ArrayList<>(newPlaylists);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return playlists.isEmpty();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        PlaylistModel playlist = playlists.get(position);
        holder.name.setText(playlist.getName());

        Resources res = context.getResources();
        holder.count.setText(res.getString(R.string.playlist_songs_count_format, playlist.getSongCount()));

        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        if (enableLongPress) {
            holder.itemView.setOnLongClickListener(v -> {
                listener.onPlaylistLongClick(playlist);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.playlistItemName);
            count = itemView.findViewById(R.id.playlistItemCount);
        }
    }
}
