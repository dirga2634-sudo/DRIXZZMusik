package com.drizzx.musik.fragment;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.drizzx.musik.MainActivity;
import com.drizzx.musik.MusicManager;
import com.drizzx.musik.R;
import com.drizzx.musik.databinding.FragmentPlayerBinding;
import com.drizzx.musik.model.Song;

public class PlayerFragment extends Fragment implements MusicManager.OnPlayerStateChanged {

    private FragmentPlayerBinding binding;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private boolean showLyrics = false;
    private boolean showEqualizer = false;
    private Equalizer equalizer;
    private BassBoost bassBoost;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPlayerBinding.inflate(inflater, container, false);

        MusicManager.getInstance().setListener(this);
        updateUI(MusicManager.getInstance().getCurrentSong());
        setupControls();
        startProgressUpdater();
        setupEqualizer();

        return binding.getRoot();
    }

    private void setupControls() {
        // Back button
        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).hidePlayer();
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        // Play/Pause
        binding.btnPlayPause.setOnClickListener(v -> MusicManager.getInstance().playPause());

        // Next
        binding.btnNext.setOnClickListener(v -> MusicManager.getInstance().playNext());

        // Prev
        binding.btnPrev.setOnClickListener(v -> MusicManager.getInstance().playPrev());

        // Favorite
        binding.btnFavorite.setOnClickListener(v -> {
            Song song = MusicManager.getInstance().getCurrentSong();
            if (song != null) {
                MusicManager.getInstance().toggleFavorite(song);
                updateFavoriteIcon(song.id);
                Toast.makeText(requireContext(),
                    MusicManager.getInstance().isFavorite(song.id) ?
                    "Ditambah ke Favorites" : "Dihapus dari Favorites",
                    Toast.LENGTH_SHORT).show();
            }
        });

        // Seekbar
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    long dur = MusicManager.getInstance().getDuration();
                    MusicManager.getInstance().seekTo((long)(progress / 100f * dur));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Lyrics toggle
        binding.btnLyrics.setOnClickListener(v -> {
            showLyrics = !showLyrics;
            binding.cardLyrics.setVisibility(showLyrics ? View.VISIBLE : View.GONE);
            binding.btnLyrics.setAlpha(showLyrics ? 1.0f : 0.5f);
        });

        // Equalizer toggle
        binding.btnEq.setOnClickListener(v -> {
            showEqualizer = !showEqualizer;
            binding.cardEqualizer.setVisibility(showEqualizer ? View.VISIBLE : View.GONE);
            binding.btnEq.setAlpha(showEqualizer ? 1.0f : 0.5f);
        });
    }

    private void setupEqualizer() {
        MusicManager mm = MusicManager.getInstance();
        if (mm.getPlayer() == null) return;

        try {
            int audioSessionId = mm.getPlayer().getAudioSessionId();

            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);

            bassBoost = new BassBoost(0, audioSessionId);
            bassBoost.setEnabled(true);

            // Setup EQ bands
            setupEqBands();
            setupBassBoost();

        } catch (Exception e) {
            binding.cardEqualizer.setVisibility(View.GONE);
            binding.btnEq.setVisibility(View.GONE);
        }
    }

    private void setupEqBands() {
        if (equalizer == null) return;

        short numBands = equalizer.getNumberOfBands();
        short[] minMax = equalizer.getBandLevelRange();
        int min = minMax[0];
        int max = minMax[1];
        int range = max - min;

        // Map seekbars to EQ bands
        SeekBar[] eqBars = {
            binding.eq60hz, binding.eq230hz, binding.eq910hz,
            binding.eq4khz, binding.eq14khz
        };
        String[] labels = {"60Hz", "230Hz", "910Hz", "4kHz", "14kHz"};

        for (int i = 0; i < Math.min(numBands, eqBars.length); i++) {
            final short band = (short) i;
            eqBars[i].setMax(100);
            int currentLevel = equalizer.getBandLevel(band);
            eqBars[i].setProgress((int)((currentLevel - min) * 100f / range));

            eqBars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && equalizer != null) {
                        int level = (int)(min + (progress / 100f * range));
                        equalizer.setBandLevel(band, (short) level);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        // Preset buttons
        binding.btnEqFlat.setOnClickListener(v -> applyPreset("flat"));
        binding.btnEqBass.setOnClickListener(v -> applyPreset("bass"));
        binding.btnEqPop.setOnClickListener(v -> applyPreset("pop"));
        binding.btnEqRock.setOnClickListener(v -> applyPreset("rock"));
    }

    private void applyPreset(String preset) {
        if (equalizer == null) return;
        short numBands = equalizer.getNumberOfBands();
        short[] minMax = equalizer.getBandLevelRange();
        int mid = (minMax[0] + minMax[1]) / 2;

        short[][] presets = {
            {0, 0, 0, 0, 0},           // flat
            {800, 400, 0, -200, -400}, // bass
            {-200, 100, 300, 200, -100}, // pop
            {400, 200, -100, 200, 300}  // rock
        };

        int pi = preset.equals("flat") ? 0 : preset.equals("bass") ? 1 :
                 preset.equals("pop") ? 2 : 3;

        for (short b = 0; b < Math.min(numBands, 5); b++) {
            equalizer.setBandLevel(b, presets[pi][b]);
        }

        // Update seekbars
        SeekBar[] eqBars = {binding.eq60hz, binding.eq230hz, binding.eq910hz,
            binding.eq4khz, binding.eq14khz};
        int range = minMax[1] - minMax[0];
        for (int i = 0; i < Math.min(numBands, eqBars.length); i++) {
            int level = equalizer.getBandLevel((short)i);
            eqBars[i].setProgress((int)((level - minMax[0]) * 100f / range));
        }
    }

    private void setupBassBoost() {
        if (bassBoost == null) return;
        binding.seekBass.setMax(1000);
        binding.seekBass.setProgress(0);
        binding.seekBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && bassBoost != null) {
                    bassBoost.setStrength((short) progress);
                    binding.tvBassValue.setText(progress / 10 + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void updateUI(Song song) {
        if (song == null || binding == null) return;
        binding.tvTitle.setText(song.title);
        binding.tvArtist.setText(song.artist);

        if (song.thumbnailUrl != null && !song.thumbnailUrl.isEmpty()) {
            Glide.with(this).load(song.thumbnailUrl)
                .centerCrop().into(binding.ivAlbumArt);
        } else {
            binding.ivAlbumArt.setImageResource(R.drawable.ic_music_placeholder);
        }

        // Lyrics
        if (song.lyrics != null && !song.lyrics.isEmpty()) {
            binding.tvLyrics.setText(song.lyrics);
        } else {
            binding.tvLyrics.setText("Lirik tidak tersedia untuk lagu ini");
        }

        updateFavoriteIcon(song.id);
        updatePlayPauseIcon();
    }

    private void updateFavoriteIcon(String songId) {
        if (binding == null) return;
        boolean fav = MusicManager.getInstance().isFavorite(songId);
        binding.btnFavorite.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        binding.btnFavorite.setColorFilter(fav ?
            requireContext().getColor(R.color.green) :
            requireContext().getColor(R.color.text_dim));
    }

    private void updatePlayPauseIcon() {
        if (binding == null) return;
        binding.btnPlayPause.setImageResource(
            MusicManager.getInstance().isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void startProgressUpdater() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding == null) return;
                MusicManager mm = MusicManager.getInstance();
                long pos = mm.getPosition();
                long dur = mm.getDuration();
                if (dur > 0) {
                    binding.seekBar.setProgress((int)(pos * 100 / dur));
                    binding.tvCurrent.setText(formatTime(pos));
                    binding.tvDuration.setText(formatTime(dur));
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(progressRunnable);
    }

    private String formatTime(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public void onSongChanged(Song song) {
        requireActivity().runOnUiThread(() -> updateUI(song));
    }

    @Override
    public void onPlayPause(boolean isPlaying) {
        requireActivity().runOnUiThread(this::updatePlayPauseIcon);
    }

    @Override public void onProgress(long position, long duration) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        if (equalizer != null) { equalizer.release(); equalizer = null; }
        if (bassBoost != null) { bassBoost.release(); bassBoost = null; }
        MusicManager.getInstance().setListener(null);
        binding = null;
    }
}
