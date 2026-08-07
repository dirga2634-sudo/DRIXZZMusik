package com.webtools.optimizer.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.webtools.optimizer.OverlayService;
import com.webtools.optimizer.databinding.FragmentOptimizerBinding;
import com.webtools.optimizer.util.CacheManager;
import com.webtools.optimizer.util.RamManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OptimizerFragment extends Fragment {

    private FragmentOptimizerBinding binding;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> overlaySettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (hasOverlayPermission()) {
                    proceedToNotificationCheck();
                } else {
                    bindOverlaySwitch();
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> startOverlayService());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentOptimizerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        refreshRamInfo();
        refreshCacheInfo();
        bindOverlaySwitch();
        binding.btnBoost.setOnClickListener(v -> onBoostClicked());
        binding.btnClearCache.setOnClickListener(v -> onClearCacheClicked());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRamInfo();
        refreshCacheInfo();
        bindOverlaySwitch();
    }

    private void bindOverlaySwitch() {
        if (binding == null) return;
        binding.overlaySwitch.setOnCheckedChangeListener(null);
        binding.overlaySwitch.setChecked(OverlayService.isRunning);
        binding.overlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableOverlay();
            } else {
                stopOverlay();
            }
        });
    }

    private boolean hasOverlayPermission() {
        return Settings.canDrawOverlays(requireContext());
    }

    private void enableOverlay() {
        if (!hasOverlayPermission()) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName()));
            overlaySettingsLauncher.launch(intent);
            return;
        }
        proceedToNotificationCheck();
    }

    private void proceedToNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        startOverlayService();
    }

    private void startOverlayService() {
        if (binding == null || !hasOverlayPermission()) return;
        Intent serviceIntent = new Intent(requireContext(), OverlayService.class);
        ContextCompat.startForegroundService(requireContext(), serviceIntent);
        // Set ON optimis dulu (Service.onCreate() jalan async, isRunning belum tentu ke-update
        // secepat ini). Kalau ternyata gagal, OverlayService bakal toast + isRunning=false,
        // dan onResume berikutnya bakal koreksi switch balik ke OFF.
        binding.overlaySwitch.setOnCheckedChangeListener(null);
        binding.overlaySwitch.setChecked(true);
        binding.overlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) enableOverlay(); else stopOverlay();
        });
    }

    private void stopOverlay() {
        if (getContext() == null) return;
        requireContext().stopService(new Intent(requireContext(), OverlayService.class));
    }

    private void refreshRamInfo() {
        if (binding == null) return;
        RamManager.MemInfo info = RamManager.getMemoryInfo(requireContext());
        binding.ramProgress.setProgress(info.percentUsed);
        String used = CacheManager.formatSize(info.totalBytes - info.availBytes);
        String total = CacheManager.formatSize(info.totalBytes);
        binding.ramText.setText(used + " / " + total + " (" + info.percentUsed + "%)");
    }

    private void refreshCacheInfo() {
        if (binding == null || executor == null) return;
        android.content.Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            long size = CacheManager.getCacheSize(appContext);
            mainHandler.post(() -> {
                if (binding != null) binding.cacheSizeText.setText(CacheManager.formatSize(size));
            });
        });
    }

    private void onBoostClicked() {
        if (binding == null || executor == null) return;
        binding.btnBoost.setEnabled(false);
        android.content.Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            int handled = RamManager.freeMemory(appContext);
            mainHandler.post(() -> {
                refreshRamInfo();
                showStatus("RAM dioptimalkan - " + handled + " proses background diproses");
                if (binding != null) binding.btnBoost.setEnabled(true);
            });
        });
    }

    private void onClearCacheClicked() {
        if (binding == null || executor == null) return;
        binding.btnClearCache.setEnabled(false);
        android.content.Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            long freed = CacheManager.clearCache(appContext);
            mainHandler.post(() -> {
                refreshCacheInfo();
                showStatus("Cache dibersihkan: " + CacheManager.formatSize(freed));
                if (binding != null) binding.btnClearCache.setEnabled(true);
            });
        });
    }

    private void showStatus(String message) {
        if (binding == null) return;
        binding.statusText.setText(message);
        binding.statusText.setVisibility(View.VISIBLE);
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
