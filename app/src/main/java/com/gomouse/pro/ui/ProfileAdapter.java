package com.gomouse.pro.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gomouse.pro.R;
import com.gomouse.pro.model.Profile;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {

    public interface Listener {
        void onProfileClicked(Profile profile);

        void onProfileMenuClicked(Profile profile, View anchor);
    }

    private final List<Profile> profiles = new ArrayList<>();
    private final Listener listener;

    public ProfileAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Profile> newProfiles) {
        profiles.clear();
        profiles.addAll(newProfiles);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Profile profile = profiles.get(position);
        holder.name.setText(profile.getName());
        int count = profile.getMappings().size();
        String meta = holder.itemView.getContext().getResources()
                .getQuantityString(R.plurals.controls_count, count, count)
                + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(profile.getUpdatedAt()));
        holder.meta.setText(meta);
        holder.itemView.setOnClickListener(v -> listener.onProfileClicked(profile));
        holder.menuButton.setOnClickListener(v -> listener.onProfileMenuClicked(profile, v));
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final ImageButton menuButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_profile_name);
            meta = itemView.findViewById(R.id.text_profile_meta);
            menuButton = itemView.findViewById(R.id.btn_profile_menu);
        }
    }
}
