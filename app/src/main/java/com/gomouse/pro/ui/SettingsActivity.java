package com.gomouse.pro.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.gomouse.pro.BuildConfig;
import com.gomouse.pro.R;
import com.gomouse.pro.model.Profile;
import com.gomouse.pro.service.GomouseAccessibilityService;
import com.gomouse.pro.service.OverlayService;
import com.gomouse.pro.storage.ProfileRepository;
import com.gomouse.pro.util.PermissionUtils;

public class SettingsActivity extends AppCompatActivity {

    private ProfileRepository repository;
    private Profile activeProfile;

    private TextView activeProfileText;
    private SeekBar seekSensitivityX, seekSensitivityY, seekOpacity;
    private SwitchMaterial switchGridSnap;

    private View rowOverlay, rowAccessibility, rowBattery;

    private final ActivityResultLauncher<String> importLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImportUri);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        repository = ProfileRepository.getInstance(this);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        activeProfileText = findViewById(R.id.text_settings_active_profile);
        seekSensitivityX = findViewById(R.id.seek_sensitivity_x);
        seekSensitivityY = findViewById(R.id.seek_sensitivity_y);
        seekOpacity = findViewById(R.id.seek_opacity);
        switchGridSnap = findViewById(R.id.switch_grid_snap);

        rowOverlay = findViewById(R.id.row_overlay);
        rowAccessibility = findViewById(R.id.row_accessibility);
        rowBattery = findViewById(R.id.row_battery);

        setupPermissionRow(rowOverlay, R.string.permission_overlay_title,
                () -> PermissionUtils.canDrawOverlays(this),
                () -> startActivity(PermissionUtils.overlayPermissionIntent(this)));
        setupPermissionRow(rowAccessibility, R.string.permission_accessibility_title,
                () -> PermissionUtils.isAccessibilityServiceEnabled(this),
                () -> startActivity(PermissionUtils.accessibilitySettingsIntent()));
        setupPermissionRow(rowBattery, R.string.permission_battery_title,
                () -> true, // no reliable public API to query this state; action always offered
                () -> startActivity(PermissionUtils.ignoreBatteryOptimizationsSettingsIntent()));

        findViewById(R.id.btn_import_profile).setOnClickListener(v -> importLauncher.launch("application/json"));

        TextView versionText = findViewById(R.id.text_version);
        versionText.setText(getString(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        setupSensitivitySeekBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadActiveProfile();
        refreshPermissionRows();
    }

    private void loadActiveProfile() {
        String id = repository.getActiveProfileId();
        activeProfile = id != null ? repository.load(id) : null;
        boolean hasProfile = activeProfile != null;
        seekSensitivityX.setEnabled(hasProfile);
        seekSensitivityY.setEnabled(hasProfile);
        seekOpacity.setEnabled(hasProfile);
        switchGridSnap.setEnabled(hasProfile);

        if (hasProfile) {
            activeProfileText.setText(getString(R.string.active_profile_format, activeProfile.getName()));
            seekSensitivityX.setProgress(Math.round((activeProfile.getSensitivityX() - 0.2f) * 100));
            seekSensitivityY.setProgress(Math.round((activeProfile.getSensitivityY() - 0.2f) * 100));
            seekOpacity.setProgress(Math.round((activeProfile.getOpacity() - 0.2f) * 100));
            switchGridSnap.setChecked(activeProfile.isGridSnapEnabled());
        } else {
            activeProfileText.setText(R.string.no_active_profile);
        }
    }

    private void setupSensitivitySeekBars() {
        seekSensitivityX.setOnSeekBarChangeListener(seekListener(value -> {
            activeProfile.setSensitivityX(0.2f + value / 100f);
            saveAndReload();
        }));
        seekSensitivityY.setOnSeekBarChangeListener(seekListener(value -> {
            activeProfile.setSensitivityY(0.2f + value / 100f);
            saveAndReload();
        }));
        seekOpacity.setOnSeekBarChangeListener(seekListener(value -> {
            activeProfile.setOpacity(0.2f + value / 100f);
            saveAndReload();
        }));
        switchGridSnap.setOnCheckedChangeListener((button, checked) -> {
            if (activeProfile == null) {
                return;
            }
            activeProfile.setGridSnapEnabled(checked);
            saveAndReload();
        });
    }

    private interface SeekConsumer {
        void accept(int progress);
    }

    private SeekBar.OnSeekBarChangeListener seekListener(SeekConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && activeProfile != null) {
                    consumer.accept(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    private void saveAndReload() {
        repository.save(activeProfile);
        GomouseAccessibilityService service = GomouseAccessibilityService.getInstance();
        if (service != null) {
            service.reloadActiveProfile();
        }
        if (OverlayService.isRunning()) {
            startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_RELOAD_PROFILE));
        }
    }

    // --- Permission rows ---

    private interface StatusCheck {
        boolean isGranted();
    }

    private void setupPermissionRow(View row, int titleRes, StatusCheck check, Runnable action) {
        TextView title = row.findViewById(R.id.text_permission_title);
        title.setText(titleRes);
        MaterialButton button = row.findViewById(R.id.btn_permission_action);
        button.setOnClickListener(v -> action.run());
        row.setTag(check);
    }

    private void refreshPermissionRows() {
        refreshRow(rowOverlay, PermissionUtils.canDrawOverlays(this));
        refreshRow(rowAccessibility, PermissionUtils.isAccessibilityServiceEnabled(this));
        refreshRow(rowBattery, true);
    }

    private void refreshRow(View row, boolean granted) {
        TextView status = row.findViewById(R.id.text_permission_status);
        status.setText(granted ? R.string.permission_status_granted : R.string.permission_status_needed);
        status.setTextColor(ContextCompat.getColor(this,
                granted ? R.color.status_ok : R.color.status_warning));
    }

    // --- Import ---

    private void handleImportUri(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            Profile imported = repository.importFrom(in);
            if (imported == null) {
                Toast.makeText(this, R.string.error_import_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            repository.save(imported);
            Toast.makeText(this, getString(R.string.import_success_format, imported.getName()), Toast.LENGTH_SHORT).show();
            loadActiveProfile();
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_import_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
