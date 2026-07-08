package com.musicplayer.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.musicplayer.app.adapter.PlaylistAdapter;
import com.musicplayer.app.model.PlaylistModel;
import com.musicplayer.app.util.AppExecutors;
import com.musicplayer.app.util.Constants;

import java.util.List;

/**
 * Menampilkan seluruh playlist buatan pengguna. Mendukung membuat,
 * mengganti nama, dan menghapus playlist lewat tekan-lama.
 */
public class PlaylistActivity extends BaseMusicActivity {

    private RecyclerView recyclerView;
    private View emptyState;
    private PlaylistAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        Toolbar toolbar = findViewById(R.id.playlistToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        applyTopInset(findViewById(R.id.playlistAppBar));

        recyclerView = findViewById(R.id.playlistsRecyclerView);
        emptyState = findViewById(R.id.playlistsEmptyState);
        FloatingActionButton fab = findViewById(R.id.fabCreatePlaylist);

        adapter = new PlaylistAdapter(this, true, new PlaylistAdapter.OnPlaylistActionListener() {
            @Override
            public void onPlaylistClick(PlaylistModel playlist) {
                Intent intent = new Intent(PlaylistActivity.this, PlaylistSongsActivity.class);
                intent.putExtra(Constants.EXTRA_PLAYLIST_ID, playlist.getId());
                intent.putExtra(Constants.EXTRA_PLAYLIST_NAME, playlist.getName());
                startActivity(intent);
            }

            @Override
            public void onPlaylistLongClick(PlaylistModel playlist) {
                showManagePlaylistMenu(playlist);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fab.setOnClickListener(v -> showCreatePlaylistDialog(newId -> loadPlaylists()));

        setupMiniPlayer(findViewById(R.id.playlistRoot));
        loadPlaylists();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaylists();
    }

    private void loadPlaylists() {
        AppExecutors.getInstance().diskIO(() -> {
            List<PlaylistModel> playlists = databaseHelper.getAllPlaylists();
            AppExecutors.getInstance().mainThread(() -> {
                adapter.submitList(playlists);
                emptyState.setVisibility(playlists.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showManagePlaylistMenu(PlaylistModel playlist) {
        View anchor = recyclerView.getChildAt(0) != null ? recyclerView.getChildAt(0) : recyclerView;
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.inflate(R.menu.menu_playlist_item);
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_rename_playlist) {
                showRenameDialog(playlist);
                return true;
            } else if (id == R.id.action_delete_playlist) {
                showDeleteConfirmation(playlist);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showRenameDialog(PlaylistModel playlist) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_playlist, null);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.playlistNameInputLayout);
        TextInputEditText editText = dialogView.findViewById(R.id.playlistNameEditText);
        editText.setText(playlist.getName());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.rename_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = editText.getText() != null ? editText.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                inputLayout.setError(getString(R.string.playlist_name_empty_error));
                return;
            }
            AppExecutors.getInstance().diskIO(() -> {
                databaseHelper.renamePlaylist(playlist.getId(), name);
                AppExecutors.getInstance().mainThread(() -> {
                    Toast.makeText(this, R.string.playlist_renamed, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadPlaylists();
                });
            });
        }));
        dialog.show();
    }

    private void showDeleteConfirmation(PlaylistModel playlist) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_playlist)
                .setMessage(R.string.delete_playlist_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> AppExecutors.getInstance().diskIO(() -> {
                    databaseHelper.deletePlaylist(playlist.getId());
                    AppExecutors.getInstance().mainThread(() -> {
                        Toast.makeText(this, R.string.playlist_deleted, Toast.LENGTH_SHORT).show();
                        loadPlaylists();
                    });
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
