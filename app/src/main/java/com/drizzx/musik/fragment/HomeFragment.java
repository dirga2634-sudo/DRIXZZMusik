package com.drizzx.musik.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.drizzx.musik.MainActivity;
import com.drizzx.musik.MusicApi;
import com.drizzx.musik.MusicManager;
import com.drizzx.musik.adapter.SongAdapter;
import com.drizzx.musik.databinding.FragmentHomeBinding;
import com.drizzx.musik.model.Song;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private SongAdapter adapter;
    private List<Song> songs = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        adapter = new SongAdapter(new SongAdapter.OnClick() {
            @Override
            public void onClick(Song song, int index) {
                playSong(song, index);
            }
            @Override
            public void onMore(Song song) {
                showSongMenu(song);
            }
        });

        binding.rvTrending.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrending.setAdapter(adapter);
        loadTrending();
        return binding.getRoot();
    }

    private void loadTrending() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.rvTrending.setVisibility(View.GONE);

        MusicApi.getTrending(new MusicApi.ApiCallback() {
            @Override
            public void onSuccess(List<Song> songList) {
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.rvTrending.setVisibility(View.VISIBLE);
                    songs = songList;
                    adapter.setData(songList);
                    binding.tvSongCount.setText(songList.size() + " lagu trending");
                });
            }
            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvSongCount.setText("Gagal: " + message);
                    loadDemoSongs();
                });
            }
        });
    }

    private void loadDemoSongs() {
        songs = new ArrayList<>();
        songs.add(new Song("demo1", "Pastikan internet aktif", "Coba lagi", "", "0:00", "", ""));
        songs.add(new Song("demo2", "Gunakan tab Search", "Cari lagu favorit", "", "0:00", "", ""));
        adapter.setData(songs);
        binding.rvTrending.setVisibility(View.VISIBLE);
    }

    private void playSong(Song song, int index) {
        if (song.streamUrl == null || song.streamUrl.isEmpty()) {
            binding.progressBar.setVisibility(View.VISIBLE);
            MusicApi.getStreamUrl(song.id, new MusicApi.SongCallback() {
                @Override
                public void onSuccess(Song s) {
                    requireActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        songs.set(index, s);
                        MusicManager.getInstance().playQueue(songs, index);
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showPlayer();
                        }
                    });
                }
                @Override
                public void onError(String message) {
                    requireActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Gagal: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            MusicManager.getInstance().playQueue(songs, index);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showPlayer();
            }
        }
    }

    private void showSongMenu(Song song) {
        String[] options = {"Tambah ke Favorites", "Tambah ke Playlist"};
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(song.title)
            .setItems(options, (d, w) -> {
                if (w == 0) {
                    MusicManager.getInstance().toggleFavorite(song);
                    boolean fav = MusicManager.getInstance().isFavorite(song.id);
                    Toast.makeText(requireContext(),
                        fav ? "Ditambah ke Favorites" : "Dihapus dari Favorites",
                        Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
