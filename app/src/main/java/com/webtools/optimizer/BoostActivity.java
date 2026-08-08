package com.webtools.optimizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
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
import com.webtools.optimizer.util.ShellServiceManager;
import com.webtools.optimizer.util.ShizukuHelper;
import com.webtools.optimizer.util.SoundEffects;

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
    private static final int LAUNCH_EFFECT_DELAY_MS = 450;

    private ActivityBoostBinding binding;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String targetPackage;
    private int mode;
    private AnimatorSet pulseAnimator;
    private AudioTrack thunderSound;

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
        executor.execute(() -> {
            try {
                thunderSound = SoundEffects.buildThunderSound();
            } catch (Throwable t) {
                android.util.Log.e("BoostActivity", "Gagal bikin suara", t);
            }
        });

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
                playLaunchEffect();
                mainHandler.postDelayed(BoostActivity.this::launchTargetApp, LAUNCH_EFFECT_DELAY_MS);
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

    /** Efek "petir" pas boost kelar -- suara sintetis (bukan file eksternal) + flash + burst bolt. */
    private void playLaunchEffect() {
        try {
            if (thunderSound != null) {
                thunderSound.play();
            }
        } catch (Throwable ignored) {
            // Suara gagal, animasi tetap lanjut.
        }

        try {
            binding.flashOverlay.animate().cancel();
            binding.flashOverlay.setAlpha(0f);
            binding.flashOverlay.animate()
                    .alpha(0.55f)
                    .setDuration(70)
                    .withEndAction(() -> {
                        if (binding == null) return;
                        binding.flashOverlay.animate().alpha(0f).setDuration(320).start();
                    })
                    .start();

            ObjectAnimator burstX = ObjectAnimator.ofFloat(binding.boltIcon, "scaleX", 1f, 1.6f, 1f);
            ObjectAnimator burstY = ObjectAnimator.ofFloat(binding.boltIcon, "scaleY", 1f, 1.6f, 1f);
            AnimatorSet burst = new AnimatorSet();
            burst.playTogether(burstX, burstY);
            burst.setDuration(400);
            burst.start();
        } catch (Throwable ignored) {
            // Efek visual gagal, gak masalah -- boost tetap lanjut ke game.
        }
    }

    private void runBoostTasks() {
        executor.execute(() -> {
            CacheManager.clearCache(getApplicationContext());
            RamManager.freeMemory(getApplicationContext());
        });
        applyModeAction();
    }

    /**
     * Ini yang beneran ngubah sesuatu antar mode. RAM+cache selalu jalan (semua mode).
     * Performa: overlay auto-nyala (kalau izinnya ada) + DND aktif (kalau izinnya ada) +
     * refresh rate layar dimaksimalkan lewat Shizuku (kalau tersedia) -- ini yang bikin layar
     * kerasa lebih "licin", bukan klaim kosong. Hemat Daya: overlay mati, DND mati, refresh
     * rate diturunin ke 60Hz (baterai kerasa nyata -- refresh rate tinggi salah satu penyedot
     * baterai terbesar di layar). CPU/GPU governor TIDAK disentuh sama sekali -- itu butuh
     * akses setara root buat ditulis, Shizuku (level shell) biasanya ditolak sistem, dan
     * kalaupun ke-bypass di device tertentu risikonya persis yang mau dihindari dari awal.
     */
    private void applyModeAction() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean dndAllowed = nm != null && nm.isNotificationPolicyAccessGranted();
        boolean shizukuReady = ShizukuHelper.hasPermission();

        if (mode == MODE_PERFORMANCE) {
            if (Settings.canDrawOverlays(this) && !OverlayService.isRunning) {
                Intent overlayIntent = new Intent(this, OverlayService.class);
                overlayIntent.putExtra(OverlayService.EXTRA_TARGET_PACKAGE, targetPackage);
                ContextCompat.startForegroundService(this, overlayIntent);
            }
            if (dndAllowed) {
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                } catch (SecurityException ignored) {
                }
            }
            if (shizukuReady) {
                ShellServiceManager.ensureBound(this);
                executor.execute(this::applyMaxRefreshRate);
            }
        } else {
            if (mode == MODE_BATTERY_SAVER && OverlayService.isRunning) {
                stopService(new Intent(this, OverlayService.class));
            }
            if (dndAllowed) {
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                } catch (SecurityException ignored) {
                }
            }
            if (shizukuReady) {
                ShellServiceManager.ensureBound(this);
                executor.execute(this::resetRefreshRate);
            }
        }
    }

    private void applyMaxRefreshRate() {
        try {
            float max = getMaxSupportedRefreshRate();
            ShellServiceManager.exec("settings put system peak_refresh_rate " + max);
            ShellServiceManager.exec("settings put system min_refresh_rate " + max);
        } catch (Throwable t) {
            android.util.Log.e("BoostActivity", "Gagal set refresh rate", t);
        }
    }

    private void resetRefreshRate() {
        try {
            ShellServiceManager.exec("settings put system peak_refresh_rate 60.0");
            ShellServiceManager.exec("settings put system min_refresh_rate 60.0");
        } catch (Throwable t) {
            android.util.Log.e("BoostActivity", "Gagal reset refresh rate", t);
        }
    }

    private float getMaxSupportedRefreshRate() {
        float max = 60f;
        try {
            Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? getDisplay()
                    : getWindowManager().getDefaultDisplay();
            if (display != null) {
                for (Display.Mode m : display.getSupportedModes()) {
                    if (m.getRefreshRate() > max) max = m.getRefreshRate();
                }
            }
        } catch (Throwable ignored) {
        }
        return max;
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
        if (thunderSound != null) {
            try {
                thunderSound.stop();
                thunderSound.release();
            } catch (Throwable ignored) {
            }
        }
    }
}
