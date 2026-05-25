package com.drizzx.musik.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.drizzx.musik.R;
import com.drizzx.musik.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {

    private List<Song> data = new ArrayList<>();
    private OnClick listener;

    public interface OnClick {
        void onClick(Song song, int index);
        void onMore(Song song);
    }

    public SongAdapter(OnClick listener) {
        this.listener = listener;
    }

    public void setData(List<Song> songs) {
        this.data = songs;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_song, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Song song = data.get(pos);
        h.title.setText(song.title);
        h.artist.setText(song.artist);
        h.duration.setText(song.duration);

        if (song.thumbnailUrl != null && !song.thumbnailUrl.isEmpty()) {
            Glide.with(h.itemView.getContext())
                .load(song.thumbnailUrl)
                .placeholder(R.drawable.ic_music_placeholder)
                .centerCrop()
                .into(h.thumbnail);
        } else {
            h.thumbnail.setImageResource(R.drawable.ic_music_placeholder);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(song, pos);
        });

        h.btnMore.setOnClickListener(v -> {
            if (listener != null) listener.onMore(song);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title, artist, duration;
        View btnMore;

        VH(View v) {
            super(v);
            thumbnail = v.findViewById(R.id.iv_thumbnail);
            title = v.findViewById(R.id.tv_title);
            artist = v.findViewById(R.id.tv_artist);
            duration = v.findViewById(R.id.tv_duration);
            btnMore = v.findViewById(R.id.btn_more);
        }
    }
}
