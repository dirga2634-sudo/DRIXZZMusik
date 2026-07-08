package com.musicplayer.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.musicplayer.app.util.Constants;

/**
 * Layar splash yang tampil singkat saat aplikasi pertama dibuka, lalu
 * otomatis berpindah ke MainActivity. Tidak terhubung ke MusicService
 * karena tidak butuh kontrol pemutaran apapun.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::goToMain, Constants.SPLASH_DURATION_MS);
    }

    private void goToMain() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
