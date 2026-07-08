package com.musicplayer.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicplayer.app.adapter.MusicAdapter;
import com.musicplayer.app.helper.MediaStoreHelper;
import com.musicplayer.app.helper.PrefsManager;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.AppExecutors;
import com.musicplayer.app.util.Constants;
import com.musicplayer.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Menampilkan seluruh lagu di dalam satu playlist tertentu, dengan opsi
 * tambahan untuk menghapus lagu dari playlist ini.
 */
public class PlaylistSongsActivity extends BaseMusicActivity implements MusicAdapter.OnSongActionListener {

    private RecyclerView recyclerView;
    private View emptyState;
    private MusicAdapter adapter;
    private PrefsManager prefsManager;
    private long playlistId;
    private final List<MusicModel> playlistSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_list);

        playlistId = getIntent().getLongExtra(Constants.EXTRA_PLAYLIST_ID, -1L);
        String playlistName = getIntent().getStringExtra(Constants.EXTRA_PLAYLIST_NAME);
        if (playlistId < 0) {
            finish();
            return;
        }

        prefsManager = new PrefsManager(this);

        Toolbar toolbar = findViewById(R.id.songListToolbar);
        toolbar.setTitle(playlistName != null ? playlistName : getString(R.string.menu_playlists));
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        applyTopInset(findViewById(R.id.songListAppBar));

        recyclerView = findViewById(R.id.songListRecyclerView);
        emptyState = findViewById(R.id.songListEmptyState);
        TextView emptyText = findViewById(R.id.songListEmptyText);
        emptyText.setText(R.string.empty_playlist_message);

        adapter = new MusicAdapter(this, this, prefsManager.isShowFileSizeEnabled());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        setupMiniPlayer(findViewById(R.id.songListRoot));
        loadPlaylistSongs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaylistSongs();
    }

    private void loadPlaylistSongs() {
        AppExecutors.getInstance().diskIO(() -> {
            List<Long> ids = databaseHelper.getSongIdsForPlaylist(playlistId);
            AppExecutors.getInstance().mainThread(() ->
                    MediaStoreHelper.loadSongsByIdsAsync(this, ids, songs -> {
                        playlistSongs.clear();
                        playlistSongs.addAll(songs);
                        adapter.submitList(playlistSongs);
                        if (isServiceBound && musicService.getCurrentSong() != null) {
                            adapter.setPlayingSongId(musicService.getCurrentSong().getId());
                        }
                        emptyState.setVisibility(playlistSongs.isEmpty() ? View.VISIBLE : View.GONE);
                    })
            );
        });
    }

    @Override
    public void onSongClick(MusicModel song, int position) {
        if (!isServiceBound) return;
        musicService.playQueue(playlistSongs, position);
        adapter.setPlayingSongId(song.getId());
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    @Override
    public void onSongMenuClick(MusicModel song, View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_song_item);
        popupMenu.getMenu().findItem(R.id.action_remove_from_playlist).setVisible(true);
        boolean isFavorite = prefsManager.isFavorite(song.getId());
        popupMenu.getMenu().findItem(R.id.action_toggle_favorite)
                .setTitle(isFavorite ? R.string.remove_from_favorites : R.string.add_to_favorites);

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_play_song) {
                onSongClick(song, playlistSongs.indexOf(song));
                return true;
            } else if (id == R.id.action_add_to_playlist) {
                showAddToPlaylistDialog(song.getId());
                return true;
            } else if (id == R.id.action_toggle_favorite) {
                toggleFavorite(song);
                return true;
            } else if (id == R.id.action_song_info) {
                showSongInfo(song);
                return true;
            } else if (id == R.id.action_remove_from_playlist) {
                removeFromPlaylist(song);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    public void onFavoriteIconClick(MusicModel song) {
        toggleFavorite(song);
    }

    private void toggleFavorite(MusicModel song) {
        boolean newState = !prefsManager.isFavorite(song.getId());
        prefsManager.setFavorite(song.getId(), newState);
        adapter.notifyFavoriteChanged(song.getId());
        Toast.makeText(this, newState ? R.string.added_to_favorites_toast : R.string.removed_from_favorites_toast,
                Toast.LENGTH_SHORT).show();
    }

    private void removeFromPlaylist(MusicModel song) {
        AppExecutors.getInstance().diskIO(() -> {
            databaseHelper.removeSongFromPlaylist(playlistId, song.getId());
            AppExecutors.getInstance().mainThread(() -> {
                playlistSongs.remove(song);
                adapter.submitList(playlistSongs);
                emptyState.setVisibility(playlistSongs.isEmpty() ? View.VISIBLE : View.GONE);
                Toast.makeText(this, R.string.removed_from_playlist_toast, Toast.LENGTH_SHORT).show();
            });
        });
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
}
