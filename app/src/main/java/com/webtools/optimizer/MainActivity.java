package com.webtools.optimizer;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.webtools.optimizer.databinding.ActivityMainBinding;
import com.webtools.optimizer.fragment.OptimizerFragment;
import com.webtools.optimizer.fragment.WebFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final WebFragment webFragment = new WebFragment();
    private final OptimizerFragment optimizerFragment = new OptimizerFragment();
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, optimizerFragment, "optimizer")
                    .hide(optimizerFragment)
                    .add(R.id.fragment_container, webFragment, "web")
                    .commit();
        }
        activeFragment = webFragment;

        binding.bottomNav.setOnItemSelectedListener(this::onNavItemSelected);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (activeFragment == webFragment && webFragment.handleBackPressed()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        Fragment target = item.getItemId() == R.id.nav_optimizer ? optimizerFragment : webFragment;
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
