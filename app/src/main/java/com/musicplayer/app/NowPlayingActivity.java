package com.musicplayer.app;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.imageview.ShapeableImageView;
import com.musicplayer.app.helper.AlbumArtLoader;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.Constants;
import com.musicplayer.app.util.FormatUtils;

/**
 * Layar pemutaran penuh: cover besar, judul/artis, SeekBar progres,
 * kontrol shuffle/previous/rewind/play-pause/forward/next/repeat, dan
 * tombol favorit.
 */
public class NowPlayingActivity extends BaseMusicActivity {

    private ShapeableImageView art;
    private TextView title;
    private TextView artistAlbum;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private ImageButton shuffleButton;
    private ImageButton repeatButton;
    private ImageButton playPauseButton;
    private ImageButton favoriteButton;

    private boolean userIsSeeking = false;
    private int pendingUserSeekMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);
        applyTopAndBottomInset(findViewById(R.id.nowPlayingRoot));

        art = findViewById(R.id.nowPlayingArt);
        title = findViewById(R.id.nowPlayingTitle);
        artistAlbum = findViewById(R.id.nowPlayingArtistAlbum);
        seekBar = findViewById(R.id.nowPlayingSeekBar);
        currentTimeText = findViewById(R.id.nowPlayingCurrentTime);
        totalTimeText = findViewById(R.id.nowPlayingTotalTime);
        shuffleButton = findViewById(R.id.nowPlayingShuffle);
        repeatButton = findViewById(R.id.nowPlayingRepeat);
        playPauseButton = findViewById(R.id.nowPlayingPlayPause);
        favoriteButton = findViewById(R.id.nowPlayingFavorite);
        ImageButton backButton = findViewById(R.id.nowPlayingBack);
        ImageButton overflowButton = findViewById(R.id.nowPlayingOverflow);
        ImageButton previousButton = findViewById(R.id.nowPlayingPrevious);
        ImageButton rewindButton = findViewById(R.id.nowPlayingRewind);
        ImageButton fastForwardButton = findViewById(R.id.nowPlayingFastForward);
        ImageButton nextButton = findViewById(R.id.nowPlayingNext);

        backButton.setOnClickListener(v -> finish());
        overflowButton.setOnClickListener(this::showOverflowMenu);
        favoriteButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.toggleFavoriteForCurrentSong();
        });
        playPauseButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.togglePlayPause();
        });
        previousButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.playPrevious();
        });
        nextButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.playNext();
        });
        rewindButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.rewind();
        });
        fastForwardButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.fastForward();
        });
        shuffleButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.setShuffleEnabled(!musicService.isShuffleEnabled());
        });
        repeatButton.setOnClickListener(v -> {
            if (isServiceBound) musicService.cycleRepeatMode();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isServiceBound) {
                    pendingUserSeekMs = FormatUtils.progressPermilleToMs(progress, musicService.getDuration());
                    currentTimeText.setText(FormatUtils.formatDuration(pendingUserSeekMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsSeeking = false;
                if (isServiceBound) {
                    musicService.seekTo(pendingUserSeekMs);
                }
            }
        });
    }

    private void showOverflowMenu(android.view.View anchor) {
        if (!isServiceBound || musicService.getCurrentSong() == null) return;
        MusicModel song = musicService.getCurrentSong();
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.inflate(R.menu.menu_now_playing);
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_add_to_playlist_now_playing) {
                showAddToPlaylistDialog(song.getId());
                return true;
            } else if (id == R.id.action_song_info_now_playing) {
                showSongInfo(song);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showSongInfo(MusicModel song) {
        String message = song.getTitle() + "\n"
                + song.getArtist() + " • " + song.getAlbum() + "\n"
                + FormatUtils.formatDuration(song.getDuration()) + " • "
                + FormatUtils.formatFileSize(song.getSize());
        new AlertDialog.Builder(this)
                .setTitle(R.string.song_info)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onSongChanged(MusicModel song) {
        title.setText(song.getTitle());
        artistAlbum.setText(song.getArtist() + " • " + song.getAlbum());
        totalTimeText.setText(FormatUtils.formatDuration(song.getDuration()));
        AlbumArtLoader.getInstance(this).loadInto(art, song.getId(), song.getAlbumId());
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        playPauseButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onProgressChanged(int currentMs, int durationMs) {
        if (userIsSeeking) return;
        seekBar.setProgress(FormatUtils.calculateProgressPermille(currentMs, durationMs));
        currentTimeText.setText(FormatUtils.formatDuration(currentMs));
        totalTimeText.setText(FormatUtils.formatDuration(durationMs));
    }

    @Override
    public void onShuffleRepeatChanged(boolean shuffleEnabled, int repeatMode) {
        shuffleButton.setImageTintList(android.content.res.ColorStateList.valueOf(
                getColorForToggle(shuffleEnabled)));

        if (repeatMode == Constants.REPEAT_ONE) {
            repeatButton.setImageResource(R.drawable.ic_repeat_one);
            repeatButton.setImageTintList(android.content.res.ColorStateList.valueOf(getColorForToggle(true)));
        } else {
            repeatButton.setImageResource(R.drawable.ic_repeat);
            repeatButton.setImageTintList(android.content.res.ColorStateList.valueOf(
                    getColorForToggle(repeatMode == Constants.REPEAT_ALL)));
        }
    }

    @Override
    public void onFavoriteChanged(boolean isFavorite) {
        favoriteButton.setImageResource(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        favoriteButton.setImageTintList(android.content.res.ColorStateList.valueOf(
                getColorForToggle(isFavorite)));
    }

    private int getColorForToggle(boolean active) {
        return androidx.core.content.ContextCompat.getColor(this,
                active ? R.color.blue_accent_light : R.color.text_primary);
    }
}
