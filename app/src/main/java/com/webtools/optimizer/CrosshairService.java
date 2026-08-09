package com.webtools.optimizer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.app.Service;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Foreground service ringan yang cuma nampilin crosshair statis di tengah layar --
 * FLAG_NOT_TOUCHABLE jadi 100% click-through, gak ganggu kontrol game sama sekali.
 * Independen dari OverlayService (FPS/baterai/jaringan) -- bisa dinyalain terpisah.
 */
public class CrosshairService extends Service {

    public static volatile boolean isRunning = false;

    private static final String CHANNEL_ID = "crosshair_service_channel";
    private static final int NOTIF_ID = 1002;

    private WindowManager windowManager;
    private View crosshairView;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        try {
            startForeground(NOTIF_ID, buildNotification());
            showCrosshair();
        } catch (Throwable t) {
            android.util.Log.e("CrosshairService", "Gagal mengaktifkan crosshair", t);
            isRunning = false;
            try {
                Toast.makeText(this,
                        "Crosshair gagal diaktifkan (" + t.getClass().getSimpleName() + ")",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
                // Best-effort saja.
            }
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void showCrosshair() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        crosshairView = LayoutInflater.from(this).inflate(R.layout.crosshair_widget, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        windowManager.addView(crosshairView, params);
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.crosshair_channel_name),
                    NotificationManager.IMPORTANCE_MIN);
            manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_crosshair)
                .setContentTitle(getString(R.string.crosshair_notif_title))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (windowManager != null && crosshairView != null) {
            try {
                windowManager.removeView(crosshairView);
            } catch (IllegalArgumentException ignored) {
                // View mungkin sudah tidak ter-attach.
            }
        }
    }
}
