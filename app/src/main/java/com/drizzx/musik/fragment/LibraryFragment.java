package com.drizzx.musik.fragment;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.drizzx.musik.MainActivity;
import com.drizzx.musik.MusicManager;
import com.drizzx.musik.R;
import com.drizzx.musik.adapter.SongAdapter;
import com.drizzx.musik.databinding.FragmentLibraryBinding;
import com.drizzx.musik.model.Playlist;
import com.drizzx.musik.model.Song;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class LibraryFragment extends Fragment {

    private FragmentLibraryBinding binding;
    private SongAdapter favAdapter;
    private int currentTab = 0; // 0=favorites, 1=playlists

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);

        favAdapter = new SongAdapter(new SongAdapter.OnClick() {
            @Override
            public void onClick(Song song, int index) {
                List<Song> favs = MusicManager.getInstance().getFavorites();
                MusicManager.getInstance().playQueue(favs, index);
                if (getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).showPlayer();
            }
            @Override
            public void onMore(Song song) {
                MusicManager.getInstance().toggleFavorite(song);
                Toast.makeText(requireContext(), "Dihapus dari Favorit", Toast.LENGTH_SHORT).show();
                loadFavorites();
            }
        });

        binding.rvContent.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvContent.setAdapter(favAdapter);

        binding.tabFavorites.setOnClickListener(v -> {
            currentTab = 0;
            updateTabs();
            loadFavorites();
        });

        binding.tabPlaylists.setOnClickListener(v -> {
            currentTab = 1;
            updateTabs();
            loadPlaylists();
        });

        binding.btnNewPlaylist.setOnClickListener(v -> showCreatePlaylistDialog());

        loadFavorites();
        updateTabs();
        return binding.getRoot();
    }

    private void loadFavorites() {
        List<Song> favs = MusicManager.getInstance().getFavorites();
        binding.btnNewPlaylist.setVisibility(View.GONE);
        if (favs.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.tvEmpty.setText("Belum ada lagu favorit\nTambahkan dengan menekan ikon hati");
            binding.rvContent.setVisibility(View.GONE);
            binding.tvCount.setText("0 lagu favorit");
        } else {
            binding.tvEmpty.setVisibility(View.GONE);
            binding.rvContent.setVisibility(View.VISIBLE);
            favAdapter.setData(favs);
            binding.tvCount.setText(favs.size() + " lagu favorit");
        }
    }

    private void loadPlaylists() {
        binding.btnNewPlaylist.setVisibility(View.VISIBLE);
        List<Playlist> playlists = MusicManager.getInstance().getPlaylists();
        if (playlists.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.tvEmpty.setText("Belum ada playlist\nBuat playlist baru");
            binding.rvContent.setVisibility(View.GONE);
            binding.tvCount.setText("0 playlist");
        } else {
            binding.tvEmpty.setVisibility(View.GONE);
            binding.rvContent.setVisibility(View.VISIBLE);
            binding.tvCount.setText(playlists.size() + " playlist");
            if (!playlists.isEmpty() && !playlists.get(0).songs.isEmpty())
                favAdapter.setData(playlists.get(0).songs);
        }
    }

    private void showCreatePlaylistDialog() {
        EditText et = new EditText(requireContext());
        et.setHint("Nama playlist");
        et.setPadding(48, 24, 48, 24);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Playlist Baru")
            .setView(et)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Buat", (d, w) -> {
                String name = et.getText().toString().trim();
                if (!name.isEmpty()) {
                    MusicManager.getInstance().createPlaylist(name);
                    Toast.makeText(requireContext(), "Playlist \"" + name + "\" dibuat", Toast.LENGTH_SHORT).show();
                    loadPlaylists();
                }
            }).show();
    }

    private void updateTabs() {
        // Ganti warna tab pakai drawable background bukan indicator view
        if (currentTab == 0) {
            // Favorit aktif
            binding.tabFavorites.setBackgroundResource(R.drawable.bg_btn_green);
            binding.tabFavorites.setTextColor(requireContext().getColor(R.color.bg));
            binding.tabPlaylists.setBackgroundResource(R.drawable.bg_btn_outline_green);
            binding.tabPlaylists.setTextColor(requireContext().getColor(R.color.text));
        } else {
            // Playlist aktif
            binding.tabPlaylists.setBackgroundResource(R.drawable.bg_btn_green);
            binding.tabPlaylists.setTextColor(requireContext().getColor(R.color.bg));
            binding.tabFavorites.setBackgroundResource(R.drawable.bg_btn_outline_green);
            binding.tabFavorites.setTextColor(requireContext().getColor(R.color.text));
        }
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
