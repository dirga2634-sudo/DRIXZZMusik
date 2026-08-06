package com.webtools.optimizer.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.webtools.optimizer.BoostActivity;
import com.webtools.optimizer.adapter.InstalledAppsAdapter;
import com.webtools.optimizer.databinding.FragmentGamesBinding;
import com.webtools.optimizer.model.AppInfo;
import com.webtools.optimizer.util.AppListLoader;
import com.webtools.optimizer.util.PrefsManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GamesFragment extends Fragment {

    private FragmentGamesBinding binding;
    private InstalledAppsAdapter adapter;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentGamesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new InstalledAppsAdapter(this::onAppSelected);
        binding.appList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.appList.setAdapter(adapter);

        executor = Executors.newSingleThreadExecutor();
        loadApps();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null && adapter.getItemCount() > 0) {
            loadApps();
        }
    }

    private void loadApps() {
        if (binding == null || executor == null) return;
        binding.loadingIndicator.setVisibility(View.VISIBLE);
        android.content.Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            List<AppInfo> apps = AppListLoader.loadLaunchableApps(appContext);
            String lastBoosted = PrefsManager.getLastBoosted(appContext);
            if (lastBoosted != null) {
                for (int i = 0; i < apps.size(); i++) {
                    if (apps.get(i).packageName.equals(lastBoosted)) {
                        AppInfo app = apps.remove(i);
                        apps.add(0, app);
                        break;
                    }
                }
            }
            mainHandler.post(() -> {
                if (binding == null) return;
                adapter.submitList(apps);
                binding.loadingIndicator.setVisibility(View.GONE);
            });
        });
    }

    private void onAppSelected(AppInfo app) {
        Intent intent = new Intent(requireContext(), BoostActivity.class);
        intent.putExtra(BoostActivity.EXTRA_PACKAGE_NAME, app.packageName);
        intent.putExtra(BoostActivity.EXTRA_APP_LABEL, app.label);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
