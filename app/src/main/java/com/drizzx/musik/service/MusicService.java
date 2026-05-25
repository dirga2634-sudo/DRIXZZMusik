package com.drizzx.musik.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.core.app.NotificationCompat;

import com.drizzx.musik.MainActivity;
import com.drizzx.musik.MusicManager;
import com.drizzx.musik.R;
import com.drizzx.musik.model.Song;

public class MusicService extends Service {

    private static final String CHANNEL_ID = "drizzx_musik_channel";
    private static final int NOTIFICATION_ID = 101;
    private MediaSessionCompat mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaSession = new MediaSessionCompat(this, "DrizzxMusik");
        startForeground(NOTIFICATION_ID, buildNotification(null));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PLAY_PAUSE":
                    MusicManager.getInstance().playPause();
                    break;
                case "NEXT":
                    MusicManager.getInstance().playNext();
                    break;
                case "PREV":
                    MusicManager.getInstance().playPrev();
                    break;
                case "STOP":
                    stopSelf();
                    break;
            }
            updateNotification();
        }
        return START_STICKY;
    }

    public void updateNotification() {
        Song song = MusicManager.getInstance().getCurrentSong();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(song));
    }

    private Notification buildNotification(Song song) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Play/Pause action
        Intent playIntent = new Intent(this, MusicService.class);
        playIntent.setAction("PLAY_PAUSE");
        PendingIntent playPending = PendingIntent.getService(this, 1, playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Next action
        Intent nextIntent = new Intent(this, MusicService.class);
        nextIntent.setAction("NEXT");
        PendingIntent nextPending = PendingIntent.getService(this, 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Prev action
        Intent prevIntent = new Intent(this, MusicService.class);
        prevIntent.setAction("PREV");
        PendingIntent prevPending = PendingIntent.getService(this, 3, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean isPlaying = MusicManager.getInstance().isPlaying();
        String title = song != null ? song.title : "Drizzx Musik";
        String artist = song != null ? song.artist : "Pilih lagu untuk diputar";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_skip_prev, "Prev", prevPending)
            .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play, "Play", playPending)
            .addAction(R.drawable.ic_skip_next, "Next", nextPending)
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Drizzx Musik", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Kontrol pemutaran musik");
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) mediaSession.release();
    }
}
