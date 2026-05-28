package com.drizzx.musik.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.drizzx.musik.MainActivity;
import com.drizzx.musik.MusicApi;
import com.drizzx.musik.MusicManager;
import com.drizzx.musik.adapter.SongAdapter;
import com.drizzx.musik.databinding.FragmentSearchBinding;
import com.drizzx.musik.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private SongAdapter adapter;
    private List<Song> results = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);

        adapter = new SongAdapter(new SongAdapter.OnClick() {
            @Override
            public void onClick(Song song, int index) {
                playFromSearch(song, index);
            }
            @Override
            public void onMore(Song song) {
                MusicManager.getInstance().toggleFavorite(song);
                Toast.makeText(requireContext(),
                    MusicManager.getInstance().isFavorite(song.id) ?
                    "Ditambah ke Favorites" : "Dihapus dari Favorites",
                    Toast.LENGTH_SHORT).show();
            }
        });

        binding.rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvResults.setAdapter(adapter);

        binding.etSearch.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); return true; }
            return false;
        });

        binding.btnSearch.setOnClickListener(v -> doSearch());
        return binding.getRoot();
    }

    private void doSearch() {
        String query = binding.etSearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Ketik nama lagu dulu", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.rvResults.setVisibility(View.GONE);
        binding.tvEmpty.setVisibility(View.GONE);

        MusicApi.search(query, new MusicApi.ApiCallback() {
            @Override
            public void onSuccess(List<Song> songs) {
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (songs.isEmpty()) {
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        binding.tvEmpty.setText("Lagu tidak ditemukan");
                    } else {
                        results = songs;
                        adapter.setData(songs);
                        binding.rvResults.setVisibility(View.VISIBLE);
                        binding.tvResultCount.setText(songs.size() + " hasil ditemukan");
                    }
                });
            }
            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.tvEmpty.setText("Error: " + message);
                });
            }
        });
    }

    private void playFromSearch(Song song, int index) {
        binding.progressBar.setVisibility(View.VISIBLE);
        MusicApi.getStreamUrl(song.id, new MusicApi.SongCallback() {
            @Override
            public void onSuccess(Song s) {
                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    results.set(index, s);
                    MusicManager.getInstance().playQueue(results, index);
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
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
