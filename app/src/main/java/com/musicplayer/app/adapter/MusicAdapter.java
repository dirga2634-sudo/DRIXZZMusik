package com.musicplayer.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.musicplayer.app.R;
import com.musicplayer.app.helper.AlbumArtLoader;
import com.musicplayer.app.helper.PrefsManager;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter RecyclerView untuk daftar lagu. Dipakai ulang oleh MainActivity,
 * FavoriteActivity, dan PlaylistSongsActivity supaya tampilan & perilaku
 * baris lagu selalu konsisten di seluruh aplikasi.
 */
public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.SongViewHolder> {

    public interface OnSongActionListener {
        void onSongClick(MusicModel song, int position);

        void onSongMenuClick(MusicModel song, View anchorView);

        void onFavoriteIconClick(MusicModel song);
    }

    private final Context context;
    private final OnSongActionListener listener;
    private final PrefsManager prefsManager;
    private final boolean showFileSize;
    private List<MusicModel> songs = new ArrayList<>();
    private long playingSongId = -1L;

    public MusicAdapter(Context context, OnSongActionListener listener, boolean showFileSize) {
        this.context = context;
        this.listener = listener;
        this.prefsManager = new PrefsManager(context);
        this.showFileSize = showFileSize;
    }

    public void submitList(List<MusicModel> newSongs) {
        this.songs = new ArrayList<>(newSongs);
        notifyDataSetChanged();
    }

    public void setPlayingSongId(long songId) {
        this.playingSongId = songId;
        notifyDataSetChanged();
    }

    /**
     * Memberi tahu adapter bahwa status favorit satu lagu berubah, supaya
     * ikon hati di baris tersebut ikut diperbarui tanpa refresh seluruh list.
     */
    public void notifyFavoriteChanged(long songId) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId() == songId) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        MusicModel song = songs.get(position);

        holder.title.setText(song.getTitle());
        String artistAlbum = song.getArtist() + " • " + song.getAlbum();
        holder.artistAlbum.setText(artistAlbum);
        holder.duration.setText(FormatUtils.formatDuration(song.getDuration()));

        if (showFileSize) {
            holder.size.setVisibility(View.VISIBLE);
            holder.size.setText(" • " + FormatUtils.formatFileSize(song.getSize()));
        } else {
            holder.size.setVisibility(View.GONE);
        }

        boolean isFavorite = prefsManager.isFavorite(song.getId());
        holder.favoriteIcon.setImageResource(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);

        boolean isCurrentlyPlaying = song.getId() == playingSongId;
        holder.title.setTextColor(androidx.core.content.ContextCompat.getColor(context,
                isCurrentlyPlaying ? R.color.blue_accent_light : R.color.text_primary));

        AlbumArtLoader.getInstance(context).loadInto(holder.albumArt, song.getId(), song.getAlbumId());

        holder.itemView.setOnClickListener(v -> listener.onSongClick(song, holder.getBindingAdapterPosition()));
        holder.menuButton.setOnClickListener(v -> listener.onSongMenuClick(song, v));
        holder.favoriteIcon.setOnClickListener(v -> listener.onFavoriteIconClick(song));
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        final ImageView albumArt;
        final ImageView favoriteIcon;
        final TextView title;
        final TextView artistAlbum;
        final TextView duration;
        final TextView size;
        final ImageButton menuButton;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.songItemArt);
            favoriteIcon = itemView.findViewById(R.id.songItemFavoriteIcon);
            title = itemView.findViewById(R.id.songItemTitle);
            artistAlbum = itemView.findViewById(R.id.songItemArtistAlbum);
            duration = itemView.findViewById(R.id.songItemDuration);
            size = itemView.findViewById(R.id.songItemSize);
            menuButton = itemView.findViewById(R.id.songItemMenuButton);
        }
    }
}
