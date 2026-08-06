package com.webtools.optimizer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.TrafficStats;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.webtools.optimizer.databinding.OverlayWidgetBinding;

import java.util.Locale;

/**
 * Foreground service yang nampilin floating widget draggable berisi FPS, baterai, dan
 * kecepatan jaringan real-time di atas app/game lain. 100% API resmi Android:
 * TYPE_APPLICATION_OVERLAY (izin SYSTEM_ALERT_WINDOW harus di-approve manual lewat Settings),
 * BatteryManager sticky broadcast, TrafficStats, dan Choreographer vsync callback.
 *
 * Catatan soal FPS: angka dihitung dari jarak antar tick vsync display (Choreographer),
 * BUKAN dari introspeksi render loop internal app lain -- itu memang tidak tersedia lewat
 * API publik non-root. Ini teknik standar yang dipakai FPS-overlay non-root pada umumnya.
 */
public class OverlayService extends Service {

    public static volatile boolean isRunning = false;
    public static final String ACTION_STOP = "com.webtools.optimizer.ACTION_STOP_OVERLAY";

    private static final String CHANNEL_ID = "overlay_service_channel";
    private static final int NOTIF_ID = 1001;

    private WindowManager windowManager;
    private OverlayWidgetBinding overlayBinding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int frameCountSinceLastUpdate = 0;
    private long lastFpsUpdateMs = 0;
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameCountSinceLastUpdate++;
            long nowMs = frameTimeNanos / 1_000_000L;
            if (lastFpsUpdateMs == 0) lastFpsUpdateMs = nowMs;
            long elapsed = nowMs - lastFpsUpdateMs;
            if (elapsed >= 500 && overlayBinding != null) {
                int fps = (int) Math.round(Math.min(frameCountSinceLastUpdate * 1000.0 / elapsed, 240));
                overlayBinding.overlayFps.setText(fps + " FPS");
                frameCountSinceLastUpdate = 0;
                lastFpsUpdateMs = nowMs;
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private long lastRxBytes = 0;
    private long lastSampleTime = 0;
    private final Runnable networkSampler = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            long rxBytes = TrafficStats.getTotalRxBytes();
            if (lastSampleTime != 0 && rxBytes >= lastRxBytes && overlayBinding != null) {
                double seconds = (now - lastSampleTime) / 1000.0;
                double kbps = seconds > 0 ? (rxBytes - lastRxBytes) / 1024.0 / seconds : 0;
                String type = connectionTypeLabel();
                overlayBinding.overlayNetwork.setText(
                        formatNetworkSpeed(kbps) + (type.isEmpty() ? "" : " " + type));
            }
            lastRxBytes = rxBytes;
            lastSampleTime = now;
            mainHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0 && overlayBinding != null) {
                int percent = Math.round(level * 100f / scale);
                overlayBinding.overlayBattery.setText(percent + "%");
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        startForeground(NOTIF_ID, buildNotification());
        showOverlay();

        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(batteryReceiver, batteryFilter);
        }

        Choreographer.getInstance().postFrameCallback(frameCallback);
        mainHandler.post(networkSampler);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayBinding = OverlayWidgetBinding.inflate(LayoutInflater.from(this));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 150;

        setupDragging(overlayBinding.getRoot(), params);
        overlayBinding.overlayClose.setOnClickListener(v -> stopSelf());

        windowManager.addView(overlayBinding.getRoot(), params);
    }

    private void setupDragging(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        if (windowManager != null) windowManager.updateViewLayout(view, params);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private String formatNetworkSpeed(double kbps) {
        if (kbps >= 1024) {
            return String.format(Locale.getDefault(), "%.1f MB/s", kbps / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.0f KB/s", kbps);
    }

    private String connectionTypeLabel() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return "";
        Network network = cm.getActiveNetwork();
        if (network == null) return "";
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return "";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WiFi";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Data";
        return "";
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.overlay_channel_name),
                    NotificationManager.IMPORTANCE_MIN);
            manager.createNotificationChannel(channel);
        }

        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle(getString(R.string.overlay_notif_title))
                .setContentText(getString(R.string.overlay_notif_text))
                .setContentIntent(contentPendingIntent)
                .addAction(0, getString(R.string.overlay_notif_stop), stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        mainHandler.removeCallbacks(networkSampler);
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver mungkin belum/sudah ter-unregister.
        }
        if (windowManager != null && overlayBinding != null) {
            try {
                windowManager.removeView(overlayBinding.getRoot());
            } catch (IllegalArgumentException ignored) {
                // View mungkin sudah tidak ter-attach.
            }
            overlayBinding = null;
        }
    }
}
