package com.drizzx.musik;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.drizzx.musik.databinding.ActivityMainBinding;
import com.drizzx.musik.fragment.HomeFragment;
import com.drizzx.musik.fragment.LibraryFragment;
import com.drizzx.musik.fragment.PlayerFragment;
import com.drizzx.musik.fragment.SearchFragment;
import com.drizzx.musik.model.Song;
import com.drizzx.musik.service.MusicService;

public class MainActivity extends AppCompatActivity implements MusicManager.OnPlayerStateChanged {

    private ActivityMainBinding binding;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MusicManager.getInstance().setListener(this);

        setupNavigation();
        loadFragment(new HomeFragment(), 0);
        setupMiniPlayer();

        // Start music service
        startService(new Intent(this, MusicService.class));
    }

    private void setupNavigation() {
        binding.navHome.setOnClickListener(v -> loadFragment(new HomeFragment(), 0));
        binding.navSearch.setOnClickListener(v -> loadFragment(new SearchFragment(), 1));
        binding.navLibrary.setOnClickListener(v -> loadFragment(new LibraryFragment(), 2));
        selectNav(0);
    }

    private void loadFragment(Fragment f, int index) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, f)
            .commit();
        selectNav(index);
    }

    private void selectNav(int index) {
        int green = getColor(R.color.green);
        int dim = getColor(R.color.text_dim);
        binding.navIconHome.setColorFilter(index == 0 ? green : dim);
        binding.navTextHome.setTextColor(index == 0 ? green : dim);
        binding.navIconSearch.setColorFilter(index == 1 ? green : dim);
        binding.navTextSearch.setTextColor(index == 1 ? green : dim);
        binding.navIconLibrary.setColorFilter(index == 2 ? green : dim);
        binding.navTextLibrary.setTextColor(index == 2 ? green : dim);
    }

    private void setupMiniPlayer() {
        binding.miniPlayer.setVisibility(View.GONE);

        binding.miniPlayer.setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new PlayerFragment())
                .addToBackStack(null)
                .commit();
            binding.bottomNav.setVisibility(View.GONE);
            binding.miniPlayer.setVisibility(View.GONE);
        });

        binding.miniPlayPause.setOnClickListener(v -> {
            MusicManager.getInstance().playPause();
        });

        binding.miniNext.setOnClickListener(v -> {
            MusicManager.getInstance().playNext();
        });
    }

    private void startProgressUpdater() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                MusicManager mm = MusicManager.getInstance();
                if (mm.isPlaying()) {
                    long pos = mm.getPosition();
                    long dur = mm.getDuration();
                    if (dur > 0) {
                        binding.miniProgress.setProgress((int)((pos * 100) / dur));
                    }
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(progressRunnable);
    }

    @Override
    public void onSongChanged(Song song) {
        runOnUiThread(() -> {
            binding.miniPlayer.setVisibility(View.VISIBLE);
            binding.miniTitle.setText(song.title);
            binding.miniArtist.setText(song.artist);
            if (song.thumbnailUrl != null && !song.thumbnailUrl.isEmpty()) {
                Glide.with(this).load(song.thumbnailUrl)
                    .centerCrop().into(binding.miniThumbnail);
            }
            startProgressUpdater();
        });
    }

    @Override
    public void onPlayPause(boolean isPlaying) {
        runOnUiThread(() -> {
            binding.miniPlayPause.setImageResource(
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        });
    }

    @Override
    public void onProgress(long position, long duration) {}

    public void showPlayer() {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, new PlayerFragment())
            .addToBackStack(null)
            .commit();
        binding.bottomNav.setVisibility(View.GONE);
        binding.miniPlayer.setVisibility(View.GONE);
    }

    public void hidePlayer() {
        binding.bottomNav.setVisibility(View.VISIBLE);
        Song song = MusicManager.getInstance().getCurrentSong();
        if (song != null) binding.miniPlayer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            hidePlayer();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        MusicManager.getInstance().setListener(null);
    }
}
