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
            @Override public void onClick(Song song, int index) { playSong(song, index); }
            @Override public void onMore(Song song) { showSongMenu(song); }
        });
        binding.rvTrending.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrending.setAdapter(adapter);
        binding.btnRefresh.setOnClickListener(v -> loadTrending());
        binding.btnRetry.setOnClickListener(v -> loadTrending());
        loadTrending();
        return binding.getRoot();
    }

    private void loadTrending() {
        if (binding == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.rvTrending.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.layoutTrendingHeader.setVisibility(View.GONE);

        MusicApi.getTrending(new MusicApi.ApiCallback() {
            @Override public void onSuccess(List<Song> songList) {
                if (getActivity() == null || binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.layoutError.setVisibility(View.GONE);
                    binding.layoutTrendingHeader.setVisibility(View.VISIBLE);
                    binding.rvTrending.setVisibility(View.VISIBLE);
                    songs = songList;
                    adapter.setData(songList);
                    binding.tvSongCount.setText(songList.size() + " lagu");
                });
            }
            @Override public void onError(String message) {
                if (getActivity() == null || binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.rvTrending.setVisibility(View.GONE);
                    binding.layoutTrendingHeader.setVisibility(View.GONE);
                    binding.layoutError.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void playSong(Song song, int index) {
        if (song.streamUrl == null || song.streamUrl.isEmpty()) {
            if (binding != null) binding.progressBar.setVisibility(View.VISIBLE);
            MusicApi.getStreamUrl(song.id, new MusicApi.SongCallback() {
                @Override public void onSuccess(Song s) {
                    if (getActivity() == null || binding == null) return;
                    requireActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        songs.set(index, s);
                        MusicManager.getInstance().playQueue(songs, index);
                        if (getActivity() instanceof MainActivity)
                            ((MainActivity) getActivity()).showPlayer();
                    });
                }
                @Override public void onError(String message) {
                    if (getActivity() == null || binding == null) return;
                    requireActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Gagal: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            MusicManager.getInstance().playQueue(songs, index);
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).showPlayer();
        }
    }

    private void showSongMenu(Song song) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(song.title)
            .setItems(new String[]{"Tambah ke Favorit", "Tambah ke Playlist"}, (d, w) -> {
                if (w == 0) {
                    MusicManager.getInstance().toggleFavorite(song);
                    boolean fav = MusicManager.getInstance().isFavorite(song.id);
                    Toast.makeText(requireContext(), fav ? "❤ Ditambah ke Favorit" : "Dihapus dari Favorit", Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
