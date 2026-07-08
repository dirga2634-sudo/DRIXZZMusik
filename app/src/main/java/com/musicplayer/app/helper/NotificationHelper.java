package com.musicplayer.app.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Build;

import com.musicplayer.app.MusicService;
import com.musicplayer.app.NowPlayingActivity;
import com.musicplayer.app.R;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.Constants;

/**
 * Membangun notifikasi MediaStyle untuk pemutaran musik: menampilkan
 * cover, judul, artis, serta tombol previous/play-pause/next yang
 * terhubung ke MediaSession supaya muncul juga di lock screen.
 */
public class NotificationHelper {

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.notification_channel_desc));
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Membangun notifikasi MediaStyle lengkap dengan tombol transport dan
     * cover album. albumArt boleh null (akan menampilkan ikon default).
     */
    public Notification buildNotification(MusicModel song, boolean isPlaying, Bitmap albumArt,
                                           MediaSession.Token sessionToken) {

        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0,
                new Intent(context, NowPlayingActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent previousIntent = buildServicePendingIntent(MusicService.ACTION_PREVIOUS, 1);
        PendingIntent playPauseIntent = buildServicePendingIntent(MusicService.ACTION_PLAY_PAUSE, 2);
        PendingIntent nextIntent = buildServicePendingIntent(MusicService.ACTION_NEXT, 3);
        PendingIntent stopIntent = buildServicePendingIntent(MusicService.ACTION_STOP, 4);

        Notification.Action previousAction = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_previous),
                context.getString(R.string.action_previous), previousIntent).build();

        Notification.Action playPauseAction = new Notification.Action.Builder(
                Icon.createWithResource(context, isPlaying ? R.drawable.ic_pause : R.drawable.ic_play),
                context.getString(isPlaying ? R.string.action_pause : R.string.action_play), playPauseIntent).build();

        Notification.Action nextAction = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_next),
                context.getString(R.string.action_next), nextIntent).build();

        Notification.MediaStyle mediaStyle = new Notification.MediaStyle();
        mediaStyle.setMediaSession(sessionToken);
        mediaStyle.setShowActionsInCompactView(0, 1, 2);

        Notification.Builder builder = new Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist())
                .setSubText(song.getAlbum())
                .setContentIntent(contentIntent)
                .setDeleteIntent(stopIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setShowWhen(false)
                .addAction(previousAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(mediaStyle);

        if (albumArt != null) {
            builder.setLargeIcon(albumArt);
        }

        return builder.build();
    }

    private PendingIntent buildServicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, requestCode, intent, flags);
        }
        return PendingIntent.getService(context, requestCode, intent, flags);
    }

    public void notify(Notification notification) {
        notificationManager.notify(Constants.NOTIFICATION_ID, notification);
    }

    public void cancel() {
        notificationManager.cancel(Constants.NOTIFICATION_ID);
    }
}
