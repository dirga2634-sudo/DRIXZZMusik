package com.webtools.optimizer.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.webtools.optimizer.databinding.ItemAppBinding;
import com.webtools.optimizer.model.AppInfo;

import java.util.ArrayList;
import java.util.List;

public class InstalledAppsAdapter extends RecyclerView.Adapter<InstalledAppsAdapter.ViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    private final List<AppInfo> apps = new ArrayList<>();
    private final OnAppClickListener listener;

    public InstalledAppsAdapter(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AppInfo> newApps) {
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAppBinding binding = ItemAppBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.binding.appIcon.setImageDrawable(app.icon);
        holder.binding.appLabel.setText(app.label);
        holder.binding.getRoot().setOnClickListener(v -> listener.onAppClick(app));
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemAppBinding binding;

        ViewHolder(@NonNull ItemAppBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
