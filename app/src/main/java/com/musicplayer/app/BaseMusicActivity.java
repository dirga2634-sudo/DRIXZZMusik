package com.musicplayer.app;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.musicplayer.app.adapter.PlaylistAdapter;
import com.musicplayer.app.helper.AlbumArtLoader;
import com.musicplayer.app.helper.DatabaseHelper;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.model.PlaylistModel;
import com.musicplayer.app.util.AppExecutors;
import com.musicplayer.app.util.FormatUtils;

import java.util.List;

/**
 * Base class untuk seluruh Activity yang perlu terhubung ke MusicService
 * (semua Activity kecuali SplashActivity). Menangani:
 * - Bind/unbind ke MusicService mengikuti lifecycle onStart/onStop
 * - Sinkronisasi kondisi awal begitu service terhubung
 * - Wiring mini player standar (opsional, lewat setupMiniPlayer)
 * - Mengaktifkan mode edge-to-edge yang konsisten di seluruh layar
 */
public abstract class BaseMusicActivity extends AppCompatActivity implements MusicService.PlaybackListener {

    public interface OnPlaylistCreatedListener {
        void onPlaylistCreated(long playlistId);
    }

    protected MusicService musicService;
    protected boolean isServiceBound = false;
    protected DatabaseHelper databaseHelper;

    private View miniPlayerRoot;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isServiceBound = true;
            musicService.setPlaybackListener(BaseMusicActivity.this);
            onServiceReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        databaseHelper = new DatabaseHelper(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isServiceBound) {
            musicService.setPlaybackListener(null);
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    /**
     * Dipanggil begitu koneksi ke service berhasil. Implementasi dasar
     * menyinkronkan mini player (bila ada) dengan kondisi service saat ini,
     * supaya lagu yang sedang/terakhir diputar langsung terlihat tanpa
     * menunggu event berikutnya.
     */
    protected void onServiceReady() {
        MusicModel current = musicService.getCurrentSong();
        if (current != null) {
            onSongChanged(current);
            onPlaybackStateChanged(musicService.isPlaying());
            onProgressChanged(musicService.getCurrentPosition(), musicService.getDuration());
            onFavoriteChanged(musicService.isCurrentSongFavorite());
        }
        onShuffleRepeatChanged(musicService.isShuffleEnabled(), musicService.getRepeatMode());
    }

    /**
     * Dipanggil oleh subclass (biasanya di onCreate) untuk mengaktifkan
     * mini player standar pada root view yang diberikan. root harus berisi
     * hasil <include layout="@layout/layout_mini_player"/>.
     */
    protected void setupMiniPlayer(View root) {
        this.miniPlayerRoot = root.findViewById(R.id.miniPlayerContainer);
        if (miniPlayerRoot == null) {
            return;
        }
        ImageButton playPause = miniPlayerRoot.findViewById(R.id.miniPlayerPlayPause);
        ImageButton next = miniPlayerRoot.findViewById(R.id.miniPlayerNext);

        miniPlayerRoot.setOnClickListener(v -> startActivity(new Intent(this, NowPlayingActivity.class)));
        playPause.setOnClickListener(v -> {
            if (isServiceBound) {
                musicService.togglePlayPause();
            }
        });
        next.setOnClickListener(v -> {
            if (isServiceBound) {
                musicService.playNext();
            }
        });
    }

    protected void applyTopInset(View topBar) {
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /**
     * Menerapkan padding atas DAN bawah sekaligus, dipakai oleh layar penuh
     * seperti NowPlayingActivity yang tidak memiliki app bar/mini player
     * terpisah sebagai acuan inset.
     */
    protected void applyTopAndBottomInset(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return insets;
        });
    }

    @Override
    public void onSongChanged(MusicModel song) {
        if (miniPlayerRoot == null) return;
        TextView title = miniPlayerRoot.findViewById(R.id.miniPlayerTitle);
        TextView artist = miniPlayerRoot.findViewById(R.id.miniPlayerArtist);
        ImageView art = miniPlayerRoot.findViewById(R.id.miniPlayerArt);

        title.setText(song.getTitle());
        artist.setText(song.getArtist());
        AlbumArtLoader.getInstance(this).loadInto(art, song.getId(), song.getAlbumId());
        miniPlayerRoot.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (miniPlayerRoot == null) return;
        ImageButton playPause = miniPlayerRoot.findViewById(R.id.miniPlayerPlayPause);
        playPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onProgressChanged(int currentMs, int durationMs) {
        if (miniPlayerRoot == null) return;
        ProgressBar progress = miniPlayerRoot.findViewById(R.id.miniPlayerProgress);
        progress.setProgress(FormatUtils.calculateProgressPermille(currentMs, durationMs) / 10);
    }

    @Override
    public void onShuffleRepeatChanged(boolean shuffleEnabled, int repeatMode) {
        // Mini player standar tidak menampilkan status shuffle/repeat;
        // NowPlayingActivity meng-override method ini untuk memperbarui
        // ikon shuffle/repeat pada layar penuh.
    }

    @Override
    public void onFavoriteChanged(boolean isFavorite) {
        // Mini player standar tidak menampilkan status favorit;
        // NowPlayingActivity meng-override method ini untuk memperbarui
        // ikon hati pada layar penuh.
    }

    // ==================== DIALOG PLAYLIST (dipakai bersama beberapa Activity) ====================

    /**
     * Menampilkan dialog untuk membuat playlist baru. Callback dipanggil
     * dengan ID playlist yang baru dibuat setelah dialog otomatis tertutup.
     */
    protected void showCreatePlaylistDialog(@Nullable OnPlaylistCreatedListener callback) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_playlist, null);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.playlistNameInputLayout);
        TextInputEditText editText = dialogView.findViewById(R.id.playlistNameEditText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.create, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String name = editText.getText() != null ? editText.getText().toString().trim() : "";
                if (TextUtils.isEmpty(name)) {
                    inputLayout.setError(getString(R.string.playlist_name_empty_error));
                    return;
                }
                inputLayout.setError(null);
                AppExecutors.getInstance().diskIO(() -> {
                    long newId = databaseHelper.createPlaylist(name);
                    AppExecutors.getInstance().mainThread(() -> {
                        Toast.makeText(this, R.string.playlist_created, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        if (callback != null) {
                            callback.onPlaylistCreated(newId);
                        }
                    });
                });
            });
        });
        dialog.show();
    }

    /**
     * Menampilkan dialog pemilihan playlist untuk menambahkan satu lagu
     * (songId). Menyertakan opsi untuk langsung membuat playlist baru.
     */
    protected void showAddToPlaylistDialog(long songId) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_to_playlist, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.addToPlaylistRecyclerView);
        View createNewRow = dialogView.findViewById(R.id.createNewPlaylistRow);
        TextView emptyText = dialogView.findViewById(R.id.addToPlaylistEmptyText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.choose_playlist_title)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .create();

        PlaylistAdapter adapter = new PlaylistAdapter(this, false, new PlaylistAdapter.OnPlaylistActionListener() {
            @Override
            public void onPlaylistClick(PlaylistModel playlist) {
                AppExecutors.getInstance().diskIO(() -> {
                    boolean added = databaseHelper.addSongToPlaylist(playlist.getId(), songId);
                    AppExecutors.getInstance().mainThread(() -> {
                        Toast.makeText(BaseMusicActivity.this,
                                added ? R.string.added_to_playlist : R.string.already_in_playlist,
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
                });
            }

            @Override
            public void onPlaylistLongClick(PlaylistModel playlist) {
                // Mode pilih tidak mendukung aksi long-press
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        createNewRow.setOnClickListener(v -> {
            dialog.dismiss();
            showCreatePlaylistDialog(newId -> AppExecutors.getInstance().diskIO(() -> {
                databaseHelper.addSongToPlaylist(newId, songId);
                AppExecutors.getInstance().mainThread(() ->
                        Toast.makeText(this, R.string.added_to_playlist, Toast.LENGTH_SHORT).show());
            }));
        });

        AppExecutors.getInstance().diskIO(() -> {
            List<PlaylistModel> playlists = databaseHelper.getAllPlaylists();
            AppExecutors.getInstance().mainThread(() -> {
                adapter.submitList(playlists);
                emptyText.setVisibility(playlists.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(playlists.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });

        dialog.show();
    }
}
