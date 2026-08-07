package com.webtools.optimizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.webtools.optimizer.databinding.ActivityBoostBinding;
import com.webtools.optimizer.util.CacheManager;
import com.webtools.optimizer.util.PrefsManager;
import com.webtools.optimizer.util.RamManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BoostActivity extends AppCompatActivity {

    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    public static final String EXTRA_APP_LABEL = "extra_app_label";
    public static final String EXTRA_MODE = "extra_mode";

    public static final int MODE_PERFORMANCE = 0;
    public static final int MODE_BALANCED = 1;
    public static final int MODE_BATTERY_SAVER = 2;

    private static final int BOOST_DURATION_MS = 2200;

    private ActivityBoostBinding binding;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String targetPackage;
    private int mode;
    private AnimatorSet pulseAnimator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBoostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        targetPackage = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        String label = getIntent().getStringExtra(EXTRA_APP_LABEL);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_BALANCED);

        if (targetPackage == null) {
            finish();
            return;
        }

        binding.gameLabel.setText(label != null ? label : targetPackage);
        binding.modeText.setText(modeLabel());
        loadGameIcon();

        executor = Executors.newSingleThreadExecutor();
        startPulseAnimation();
        startProgressAnimation();
        runBoostTasks();
    }

    private String modeLabel() {
        switch (mode) {
            case MODE_PERFORMANCE:
                return getString(R.string.mode_label_performance);
            case MODE_BATTERY_SAVER:
                return getString(R.string.mode_label_battery_saver);
            default:
                return getString(R.string.mode_label_balanced);
        }
    }

    private void loadGameIcon() {
        try {
            Drawable icon = getPackageManager().getApplicationIcon(targetPackage);
            binding.gameIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException ignored) {
            binding.gameIcon.setVisibility(View.GONE);
        }
    }

    private void startPulseAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(binding.boltIcon, "scaleX", 1f, 1.18f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(binding.boltIcon, "scaleY", 1f, 1.18f, 1f);
        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(scaleX, scaleY);
        pulseAnimator.setDuration(650);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isFinishing() && !isDestroyed()) pulseAnimator.start();
            }
        });
        pulseAnimator.start();
    }

    private void startProgressAnimation() {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(BOOST_DURATION_MS);
        animator.addUpdateListener(a -> {
            int progress = (int) a.getAnimatedValue();
            binding.boostProgress.setProgress(progress);
            binding.percentText.setText(progress + "%");
            updateStatusText(progress);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                launchTargetApp();
            }
        });
        animator.start();
    }

    private void updateStatusText(int progress) {
        String text;
        if (progress < 30) {
            text = getString(R.string.boost_status_cache);
        } else if (progress < 65) {
            text = getString(R.string.boost_status_ram);
        } else if (progress < 95) {
            text = getString(R.string.boost_status_background);
        } else {
            text = getString(R.string.boost_status_ready);
        }
        binding.statusText.setText(text);
    }

    private void runBoostTasks() {
        executor.execute(() -> {
            CacheManager.clearCache(getApplicationContext());
            RamManager.freeMemory(getApplicationContext());
        });
        applyModeAction();
    }

    /**
     * Ini bagian yang beneran ngubah sesuatu antar mode -- bukan kosmetik doang.
     * Performa: nyalain overlay otomatis KALAU izinnya udah ada (gak minta izin baru di sini,
     * biar gak ganggu alur boost). Hemat Daya: matiin overlay kalau lagi nyala, biar gak ada
     * proses Choreographer/network-sampler yang jalan terus pas main. Seimbang: gak diutak-atik.
     * Semua ini cuma start/stop service milik app sendiri -- nol risiko reboot.
     */
    private void applyModeAction() {
        if (mode == MODE_PERFORMANCE) {
            if (Settings.canDrawOverlays(this) && !OverlayService.isRunning) {
                ContextCompat.startForegroundService(this, new Intent(this, OverlayService.class));
            }
        } else if (mode == MODE_BATTERY_SAVER) {
            if (OverlayService.isRunning) {
                stopService(new Intent(this, OverlayService.class));
            }
        }
    }

    private void launchTargetApp() {
        PrefsManager.saveLastBoosted(this, targetPackage);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, R.string.boost_launch_failed, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
        if (pulseAnimator != null) pulseAnimator.cancel();
    }
}
