package com.drizzx.musik.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;

import com.drizzx.musik.MainActivity;
import com.drizzx.musik.MusicManager;
import com.drizzx.musik.R;
import com.drizzx.musik.model.Song;

public class MusicService extends Service {

    private static final String CHANNEL_ID  = "drizzx_musik_channel";
    private static final int    NOTIF_ID    = 101;

    private static final String ACTION_PLAY_PAUSE = "PLAY_PAUSE";
    private static final String ACTION_NEXT       = "NEXT";
    private static final String ACTION_PREV       = "PREV";
    private static final String ACTION_STOP       = "STOP";

    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // WakeLock: jaga CPU tetap aktif saat layar mati
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DrizzxMusik::MusicWakeLock"
            );
            wakeLock.setReferenceCounted(false);
        }

        // MediaSession buat kontrol notifikasi & headset
        mediaSession = new MediaSessionCompat(this, "DrizzxMusik");
        mediaSession.setActive(true);

        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
            .build();
        mediaSession.setPlaybackState(state);

        startForeground(NOTIF_ID, buildNotification(null));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE:
                    MusicManager.getInstance().playPause();
                    // WakeLock: acquire saat play, release saat pause
                    if (MusicManager.getInstance().isPlaying()) {
                        acquireWake();
                    } else {
                        releaseWake();
                    }
                    break;
                case ACTION_NEXT:
                    MusicManager.getInstance().playNext();
                    acquireWake();
                    break;
                case ACTION_PREV:
                    MusicManager.getInstance().playPrev();
                    acquireWake();
                    break;
                case ACTION_STOP:
                    releaseWake();
                    stopSelf();
                    return START_NOT_STICKY;
            }
            updateNotification();
        }
        // Acquire wake saat service start (lagu sedang main)
        acquireWake();
        return START_STICKY; // Restart service otomatis kalau dimatikan sistem
    }

    private void acquireWake() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(60 * 60 * 1000L); // Max 1 jam, auto-release
        }
    }

    private void releaseWake() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public void updateNotification() {
        Song song = MusicManager.getInstance().getCurrentSong();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(song));
    }

    private Notification buildNotification(Song song) {
        // Tap notifikasi -> buka app
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Prev
        PendingIntent prevPending = buildServicePending(ACTION_PREV, 1);
        // Play/Pause
        PendingIntent playPending = buildServicePending(ACTION_PLAY_PAUSE, 2);
        // Next
        PendingIntent nextPending = buildServicePending(ACTION_NEXT, 3);

        boolean  isPlaying = MusicManager.getInstance().isPlaying();
        String   title     = song != null ? song.title  : "Drizzx Musik";
        String   artist    = song != null ? song.artist : "Pilih lagu untuk diputar";
        int      playIcon  = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_skip_prev,  "Sebelumnya", prevPending)
            .addAction(playIcon,                  isPlaying ? "Jeda" : "Putar", playPending)
            .addAction(R.drawable.ic_skip_next,  "Selanjutnya", nextPending)
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .setOngoing(isPlaying)           // Ongoing hanya saat playing
            .setAutoCancel(!isPlaying)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .build();
    }

    private PendingIntent buildServicePending(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Drizzx Musik", NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Kontrol pemutaran musik");
        channel.setShowBadge(false);
        channel.setSound(null, null);    // Tidak ada suara notifikasi
        channel.enableVibration(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Lagu tetap jalan walaupun app di-swipe keluar dari recents
        // Jangan stop service di sini!
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        releaseWake();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }
}
