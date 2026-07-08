package com.musicplayer.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicplayer.app.adapter.MusicAdapter;
import com.musicplayer.app.helper.MediaStoreHelper;
import com.musicplayer.app.helper.PermissionHelper;
import com.musicplayer.app.helper.PrefsManager;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.Constants;
import com.musicplayer.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Layar Home: menampilkan seluruh lagu hasil scan MediaStore dengan
 * pencarian realtime, sort, mini player, dan akses ke Favorites/Playlists/
 * Settings.
 */
public class MainActivity extends BaseMusicActivity implements MusicAdapter.OnSongActionListener {

    private RecyclerView songsRecyclerView;
    private ProgressBar scanProgressBar;
    private View emptyStateContainer;
    private TextView emptyStateTitle;
    private TextView emptyStateMessage;
    private EditText searchEditText;
    private ImageButton clearSearchButton;

    private MusicAdapter adapter;
    private PrefsManager prefsManager;

    private final List<MusicModel> allSongs = new ArrayList<>();
    private String currentQuery = "";
    private int currentSort = Constants.SORT_NAME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefsManager = new PrefsManager(this);
        currentSort = prefsManager.getSortOrder();

        Toolbar toolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);
        applyTopInset(findViewById(R.id.mainAppBar));

        songsRecyclerView = findViewById(R.id.songsRecyclerView);
        scanProgressBar = findViewById(R.id.scanProgressBar);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        emptyStateTitle = findViewById(R.id.emptyStateTitle);
        emptyStateMessage = findViewById(R.id.emptyStateMessage);
        searchEditText = findViewById(R.id.searchEditText);
        clearSearchButton = findViewById(R.id.clearSearchButton);

        adapter = new MusicAdapter(this, this, prefsManager.isShowFileSizeEnabled());
        songsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        songsRecyclerView.setAdapter(adapter);

        setupSearch();
        setupMiniPlayer(findViewById(R.id.mainRoot));

        if (PermissionHelper.hasStoragePermission(this)) {
            loadSongs();
        } else {
            requestStoragePermission();
        }
    }

    @Override
    protected void onServiceReady() {
        super.onServiceReady();
        MusicModel current = musicService.getCurrentSong();
        if (current != null) {
            adapter.setPlayingSongId(current.getId());
        }
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Tidak diperlukan: pencarian realtime hanya bereaksi terhadap
                // teks SETELAH berubah, lewat onTextChanged di bawah.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                clearSearchButton.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilterAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Tidak diperlukan, lihat penjelasan pada beforeTextChanged di atas.
            }
        });
        clearSearchButton.setOnClickListener(v -> searchEditText.setText(""));
    }

    private void requestStoragePermission() {
        if (PermissionHelper.shouldShowStorageRationale(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_rationale_title)
                    .setMessage(R.string.permission_rationale_message)
                    .setPositiveButton(R.string.grant_permission, (d, w) ->
                            PermissionHelper.requestPermissions(this, Constants.PERMISSION_REQUEST_CODE))
                    .setCancelable(false)
                    .show();
        } else {
            PermissionHelper.requestPermissions(this, Constants.PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != Constants.PERMISSION_REQUEST_CODE) {
            return;
        }
        if (PermissionHelper.hasStoragePermission(this)) {
            loadSongs();
        } else {
            scanProgressBar.setVisibility(View.GONE);
            showPermissionDeniedState();
        }
    }

    private void showPermissionDeniedState() {
        emptyStateContainer.setVisibility(View.VISIBLE);
        emptyStateTitle.setText(R.string.empty_songs_title);
        emptyStateMessage.setText(R.string.permission_denied_message);
        // Bila pengguna menolak permission, sentuhan pada area kosong akan
        // mengarahkan ke halaman pengaturan aplikasi supaya mudah diaktifkan
        // secara manual tanpa harus uninstall/install ulang.
        emptyStateContainer.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });
    }

    private void loadSongs() {
        scanProgressBar.setVisibility(View.VISIBLE);
        emptyStateContainer.setVisibility(View.GONE);
        emptyStateContainer.setOnClickListener(null);
        MediaStoreHelper.loadAllSongsAsync(this, songs -> {
            allSongs.clear();
            allSongs.addAll(songs);
            scanProgressBar.setVisibility(View.GONE);
            applyFilterAndSort();
        });
    }

    private void applyFilterAndSort() {
        List<MusicModel> filtered = currentAdapterSongs();
        adapter.submitList(filtered);
        if (isServiceBound && musicService.getCurrentSong() != null) {
            adapter.setPlayingSongId(musicService.getCurrentSong().getId());
        }
        updateEmptyState(filtered.isEmpty());
    }

    private void updateEmptyState(boolean isEmpty) {
        if (!isEmpty) {
            emptyStateContainer.setVisibility(View.GONE);
            return;
        }
        emptyStateContainer.setVisibility(View.VISIBLE);
        if (!currentQuery.isEmpty()) {
            emptyStateTitle.setText(R.string.empty_songs_title);
            emptyStateMessage.setText(R.string.empty_search_message);
        } else if (allSongs.isEmpty() && PermissionHelper.hasStoragePermission(this)) {
            emptyStateTitle.setText(R.string.empty_songs_title);
            emptyStateMessage.setText(R.string.empty_songs_message);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.action_favorites) {
            startActivity(new Intent(this, FavoriteActivity.class));
            return true;
        } else if (id == R.id.action_playlists) {
            startActivity(new Intent(this, PlaylistActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        String[] options = {
                getString(R.string.settings_sort_name_option),
                getString(R.string.settings_sort_date_option)
        };
        int checked = currentSort == Constants.SORT_NAME ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_sort_name)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    currentSort = which == 0 ? Constants.SORT_NAME : Constants.SORT_DATE;
                    prefsManager.setSortOrder(currentSort);
                    applyFilterAndSort();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== MusicAdapter.OnSongActionListener ====================

    @Override
    public void onSongClick(MusicModel song, int position) {
        playFromCurrentList(song);
    }

    @Override
    public void onSongMenuClick(MusicModel song, View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.inflate(R.menu.menu_song_item);
        boolean isFavorite = prefsManager.isFavorite(song.getId());
        popupMenu.getMenu().findItem(R.id.action_toggle_favorite)
                .setTitle(isFavorite ? R.string.remove_from_favorites : R.string.add_to_favorites);

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_play_song) {
                playFromCurrentList(song);
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

    private void playFromCurrentList(MusicModel song) {
        if (!isServiceBound) return;
        List<MusicModel> current = currentAdapterSongs();
        int index = current.indexOf(song);
        if (index < 0) index = 0;
        musicService.playQueue(current, index);
        adapter.setPlayingSongId(song.getId());
        startActivity(new Intent(this, NowPlayingActivity.class));
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

    private List<MusicModel> currentAdapterSongs() {
        List<MusicModel> filtered = new ArrayList<>();
        String query = currentQuery.toLowerCase(Locale.getDefault()).trim();
        for (MusicModel song : allSongs) {
            if (query.isEmpty()
                    || song.getTitle().toLowerCase(Locale.getDefault()).contains(query)
                    || song.getArtist().toLowerCase(Locale.getDefault()).contains(query)
                    || song.getAlbum().toLowerCase(Locale.getDefault()).contains(query)) {
                filtered.add(song);
            }
        }
        if (currentSort == Constants.SORT_NAME) {
            Collections.sort(filtered, (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        } else {
            Collections.sort(filtered, (a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));
        }
        return filtered;
    }
}
