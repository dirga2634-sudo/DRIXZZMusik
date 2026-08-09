package com.webtools.optimizer.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

import com.webtools.optimizer.CrosshairService;
import com.webtools.optimizer.OverlayService;
import com.webtools.optimizer.R;
import com.webtools.optimizer.databinding.FragmentSettingsBinding;
import com.webtools.optimizer.util.ShellServiceManager;
import com.webtools.optimizer.util.ShizukuHelper;

import rikka.shizuku.Shizuku;

/**
 * Pusat pengaturan: koneksi Shizuku (satu tempat, bukan scattered) + toggle Overlay
 * Performa + toggle Crosshair, keduanya manual/independen dari alur boost.
 */
public class SettingsFragment extends Fragment {

    private static final int SHIZUKU_REQUEST_CODE = 5182;

    private FragmentSettingsBinding binding;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_REQUEST_CODE) refreshShizukuStatus();
            };

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

    private final ActivityResultLauncher<Intent> crosshairSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (hasOverlayPermission()) {
                    proceedToCrosshairNotificationCheck();
                } else {
                    bindCrosshairSwitch();
                }
            });

    private final ActivityResultLauncher<String> crosshairNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> startCrosshairService());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener);
        } catch (Throwable ignored) {
            // Aman diabaikan -- refreshShizukuStatus() tetap fallback dengan benar.
        }
        binding.btnConnectShizuku.setOnClickListener(v -> ShizukuHelper.requestPermission(SHIZUKU_REQUEST_CODE));
        bindOverlaySwitch();
        bindCrosshairSwitch();
        refreshShizukuStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindOverlaySwitch();
        bindCrosshairSwitch();
        refreshShizukuStatus();
    }

    private void refreshShizukuStatus() {
        if (binding == null) return;
        boolean available = ShizukuHelper.isAvailable();
        boolean granted = ShizukuHelper.hasPermission();

        if (!available) {
            binding.shizukuStatusText.setText(R.string.shizuku_not_installed);
            binding.btnConnectShizuku.setVisibility(View.VISIBLE);
        } else if (!granted) {
            binding.shizukuStatusText.setText(R.string.shizuku_not_granted);
            binding.btnConnectShizuku.setVisibility(View.VISIBLE);
        } else {
            binding.shizukuStatusText.setText(R.string.shizuku_connected);
            binding.btnConnectShizuku.setVisibility(View.GONE);
            ShellServiceManager.ensureBound(requireContext());
        }
    }

    // ---------- Overlay Performa ----------

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

    // ---------- Crosshair ----------

    private void bindCrosshairSwitch() {
        if (binding == null) return;
        binding.crosshairSwitch.setOnCheckedChangeListener(null);
        binding.crosshairSwitch.setChecked(CrosshairService.isRunning);
        binding.crosshairSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableCrosshair();
            } else {
                stopCrosshair();
            }
        });
    }

    private void enableCrosshair() {
        if (!hasOverlayPermission()) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName()));
            crosshairSettingsLauncher.launch(intent);
            return;
        }
        proceedToCrosshairNotificationCheck();
    }

    private void proceedToCrosshairNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                crosshairNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        startCrosshairService();
    }

    private void startCrosshairService() {
        if (binding == null || !hasOverlayPermission()) return;
        Intent serviceIntent = new Intent(requireContext(), CrosshairService.class);
        ContextCompat.startForegroundService(requireContext(), serviceIntent);
        binding.crosshairSwitch.setOnCheckedChangeListener(null);
        binding.crosshairSwitch.setChecked(true);
        binding.crosshairSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) enableCrosshair(); else stopCrosshair();
        });
    }

    private void stopCrosshair() {
        if (getContext() == null) return;
        requireContext().stopService(new Intent(requireContext(), CrosshairService.class));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (Throwable ignored) {
            // Aman diabaikan.
        }
        binding = null;
    }
}
