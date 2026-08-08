package com.webtools.optimizer;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.webtools.optimizer.databinding.ActivityMainBinding;
import com.webtools.optimizer.fragment.GamesFragment;
import com.webtools.optimizer.fragment.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final GamesFragment gamesFragment = new GamesFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, settingsFragment, "settings")
                    .hide(settingsFragment)
                    .add(R.id.fragment_container, gamesFragment, "games")
                    .commit();
        }
        activeFragment = gamesFragment;

        binding.bottomNav.setOnItemSelectedListener(this::onNavItemSelected);
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        Fragment target = item.getItemId() == R.id.nav_settings ? settingsFragment : gamesFragment;
        if (target == activeFragment) return true;

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
        return true;
    }
}
