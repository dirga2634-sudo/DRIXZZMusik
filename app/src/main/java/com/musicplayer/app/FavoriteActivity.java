package com.musicplayer.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicplayer.app.adapter.MusicAdapter;
import com.musicplayer.app.helper.MediaStoreHelper;
import com.musicplayer.app.helper.PrefsManager;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Menampilkan seluruh lagu yang ditandai favorit (disimpan lewat
 * SharedPreferences via PrefsManager).
 */
public class FavoriteActivity extends BaseMusicActivity implements MusicAdapter.OnSongActionListener {

    private RecyclerView recyclerView;
    private View emptyState;
    private MusicAdapter adapter;
    private PrefsManager prefsManager;
    private final List<MusicModel> favoriteSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_list);

        prefsManager = new PrefsManager(this);

        Toolbar toolbar = findViewById(R.id.songListToolbar);
        toolbar.setTitle(R.string.menu_favorites);
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        applyTopInset(findViewById(R.id.songListAppBar));

        recyclerView = findViewById(R.id.songListRecyclerView);
        emptyState = findViewById(R.id.songListEmptyState);
        TextView emptyText = findViewById(R.id.songListEmptyText);
        emptyText.setText(R.string.empty_favorites_message);

        adapter = new MusicAdapter(this, this, prefsManager.isShowFileSizeEnabled());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        setupMiniPlayer(findViewById(R.id.songListRoot));
        loadFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        MediaStoreHelper.loadAllSongsAsync(this, allSongs -> {
            favoriteSongs.clear();
            for (MusicModel song : allSongs) {
                if (prefsManager.isFavorite(song.getId())) {
                    favoriteSongs.add(song);
                }
            }
            adapter.submitList(favoriteSongs);
            if (isServiceBound && musicService.getCurrentSong() != null) {
                adapter.setPlayingSongId(musicService.getCurrentSong().getId());
            }
            emptyState.setVisibility(favoriteSongs.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onSongClick(MusicModel song, int position) {
        if (!isServiceBound) return;
        musicService.playQueue(favoriteSongs, position);
        adapter.setPlayingSongId(song.getId());
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    @Override
    public void onSongMenuClick(MusicModel song, View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_song_item);
        popupMenu.getMenu().findItem(R.id.action_toggle_favorite).setTitle(R.string.remove_from_favorites);

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_play_song) {
                onSongClick(song, favoriteSongs.indexOf(song));
                return true;
            } else if (id == R.id.action_add_to_playlist) {
                showAddToPlaylistDialog(song.getId());
                return true;
            } else if (id == R.id.action_toggle_favorite) {
                removeFavorite(song);
                return true;
            } else if (id == R.id.action_song_info) {
                showSongInfo(song);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    public void onFavoriteIconClick(MusicModel song) {
        removeFavorite(song);
    }

    private void removeFavorite(MusicModel song) {
        prefsManager.setFavorite(song.getId(), false);
        favoriteSongs.remove(song);
        adapter.submitList(favoriteSongs);
        emptyState.setVisibility(favoriteSongs.isEmpty() ? View.VISIBLE : View.GONE);
        Toast.makeText(this, R.string.removed_from_favorites_toast, Toast.LENGTH_SHORT).show();
    }

    private void showSongInfo(MusicModel song) {
        String message = song.getTitle() + "\n"
                + song.getArtist() + " • " + song.getAlbum() + "\n"
                + FormatUtils.formatDuration(song.getDuration()) + " • "
                + FormatUtils.formatFileSize(song.getSize());
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.song_info)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
