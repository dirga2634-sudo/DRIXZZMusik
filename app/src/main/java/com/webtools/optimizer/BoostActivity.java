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
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.webtools.optimizer.databinding.ActivityBoostBinding;
import com.webtools.optimizer.util.CacheManager;
import com.webtools.optimizer.util.PrefsManager;
import com.webtools.optimizer.util.RamManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BoostActivity extends AppCompatActivity {

    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    public static final String EXTRA_APP_LABEL = "extra_app_label";

    private static final int BOOST_DURATION_MS = 2200;

    private ActivityBoostBinding binding;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String targetPackage;
    private AnimatorSet pulseAnimator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBoostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        targetPackage = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        String label = getIntent().getStringExtra(EXTRA_APP_LABEL);

        if (targetPackage == null) {
            finish();
            return;
        }

        binding.gameLabel.setText(label != null ? label : targetPackage);
        loadGameIcon();

        executor = Executors.newSingleThreadExecutor();
        startPulseAnimation();
        startProgressAnimation();
        runBoostTasks();
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
