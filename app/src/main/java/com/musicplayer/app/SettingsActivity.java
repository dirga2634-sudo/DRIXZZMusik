package com.musicplayer.app;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;

import com.musicplayer.app.helper.PrefsManager;
import com.musicplayer.app.util.Constants;

/**
 * Layar pengaturan: default urutan sort, tampilkan ukuran file, hapus
 * seluruh favorit, dan informasi versi aplikasi.
 */
public class SettingsActivity extends BaseMusicActivity {

    private PrefsManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefsManager = new PrefsManager(this);

        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        applyTopInset(findViewById(R.id.settingsAppBar));

        setupSortOption();
        setupFileSizeToggle();
        setupClearFavorites();
        setupAboutSection();
        setupMiniPlayer(findViewById(R.id.settingsRoot));
    }

    private void setupSortOption() {
        RadioGroup sortGroup = findViewById(R.id.sortOrderRadioGroup);
        RadioButton nameRadio = findViewById(R.id.sortByNameRadio);
        RadioButton dateRadio = findViewById(R.id.sortByDateRadio);

        if (prefsManager.getSortOrder() == Constants.SORT_DATE) {
            dateRadio.setChecked(true);
        } else {
            nameRadio.setChecked(true);
        }

        sortGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int sortOrder = checkedId == R.id.sortByDateRadio ? Constants.SORT_DATE : Constants.SORT_NAME;
            prefsManager.setSortOrder(sortOrder);
        });
    }

    private void setupFileSizeToggle() {
        SwitchCompat showFileSizeSwitch = findViewById(R.id.showFileSizeSwitch);
        showFileSizeSwitch.setChecked(prefsManager.isShowFileSizeEnabled());
        showFileSizeSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefsManager.setShowFileSizeEnabled(isChecked));
    }

    private void setupClearFavorites() {
        MaterialButton clearFavoritesButton = findViewById(R.id.clearFavoritesButton);
        clearFavoritesButton.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.settings_reset_favorites)
                .setMessage(R.string.settings_reset_favorites_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    prefsManager.clearFavorites();
                    Toast.makeText(this, R.string.favorites_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void setupAboutSection() {
        TextView versionText = findViewById(R.id.aboutVersionText);
        String versionName = "1.0";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null) {
                versionName = info.versionName;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Tetap pakai versionName default bila gagal dibaca
        }
        versionText.setText(getString(R.string.settings_version) + " " + versionName);
    }
}
