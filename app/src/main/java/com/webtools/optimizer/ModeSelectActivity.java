package com.webtools.optimizer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.webtools.optimizer.databinding.ActivityModeSelectBinding;
import com.webtools.optimizer.util.RamManager;
import com.webtools.optimizer.util.ShellServiceManager;
import com.webtools.optimizer.util.ShizukuHelper;
import com.webtools.optimizer.util.ShizukuMetrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Layar pilih mode sebelum boost: gauge RAM (selalu real) + gauge CPU (real kalau Shizuku
 * terhubung -- koneksi Shizuku sendiri diurus dari tab Settings, di sini cuma baca status)
 * + 3 kartu mode.
 */
public class ModeSelectActivity extends AppCompatActivity {

    private ActivityModeSelectBinding binding;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String targetPackage;
    private String targetLabel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityModeSelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        targetPackage = getIntent().getStringExtra(BoostActivity.EXTRA_PACKAGE_NAME);
        targetLabel = getIntent().getStringExtra(BoostActivity.EXTRA_APP_LABEL);

        if (targetPackage == null) {
            finish();
            return;
        }

        binding.gameName.setText(targetLabel != null ? targetLabel : targetPackage);
        loadGameIcon();

        executor = Executors.newSingleThreadExecutor();

        binding.cardPerformance.setOnClickListener(v -> launchBoost(BoostActivity.MODE_PERFORMANCE));
        binding.cardBalanced.setOnClickListener(v -> launchBoost(BoostActivity.MODE_BALANCED));
        binding.cardBatterySaver.setOnClickListener(v -> launchBoost(BoostActivity.MODE_BATTERY_SAVER));

        refreshRamGauge();
        refreshShizukuStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshShizukuStatus();
    }

    private void loadGameIcon() {
        try {
            Drawable icon = getPackageManager().getApplicationIcon(targetPackage);
            binding.gameIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException ignored) {
            binding.gameIcon.setVisibility(View.GONE);
        }
    }

    private void refreshRamGauge() {
        RamManager.MemInfo info = RamManager.getMemoryInfo(this);
        binding.ramGauge.setProgress(info.percentUsed);
        binding.ramGaugeText.setText(info.percentUsed + "%");
    }

    private void refreshShizukuStatus() {
        if (binding == null) return;
        if (!ShizukuHelper.hasPermission()) {
            binding.shizukuStatusText.setText(R.string.shizuku_not_granted);
            binding.cpuGaugeText.setText("--%");
            return;
        }
        binding.shizukuStatusText.setText(R.string.shizuku_connected);
        ShellServiceManager.ensureBound(this);
        loadCpuUsage();
    }

    private void loadCpuUsage() {
        executor.execute(() -> {
            int cpu = ShizukuMetrics.readSystemCpuPercent();
            mainHandler.post(() -> {
                if (binding == null) return;
                if (cpu >= 0) {
                    binding.cpuGauge.setProgress(cpu);
                    binding.cpuGaugeText.setText(cpu + "%");
                } else {
                    binding.cpuGaugeText.setText("--%");
                }
            });
        });
    }

    private void launchBoost(int mode) {
        Intent intent = new Intent(this, BoostActivity.class);
        intent.putExtra(BoostActivity.EXTRA_PACKAGE_NAME, targetPackage);
        intent.putExtra(BoostActivity.EXTRA_APP_LABEL, targetLabel);
        intent.putExtra(BoostActivity.EXTRA_MODE, mode);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
