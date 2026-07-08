package com.musicplayer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

/**
 * Menerima broadcast ACTION_MEDIA_BUTTON dari sistem (tombol headset kabel
 * maupun tombol AVRCP dari perangkat Bluetooth) dan meneruskannya ke
 * MusicService, bahkan ketika aplikasi tidak sedang terbuka di layar.
 */
public class MediaButtonReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return;
        }
        KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (keyEvent == null) {
            return;
        }

        Intent serviceIntent = new Intent(context, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_MEDIA_BUTTON);
        serviceIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        ContextCompat.startForegroundService(context, serviceIntent);
    }
}
