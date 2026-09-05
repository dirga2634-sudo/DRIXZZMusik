package com.gomouse.pro.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.gomouse.pro.R;
import com.gomouse.pro.editor.EditorActivity;
import com.gomouse.pro.model.Profile;
import com.gomouse.pro.service.OverlayService;
import com.gomouse.pro.storage.ProfileRepository;
import com.gomouse.pro.util.PermissionUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ProfileAdapter.Listener {

    private ProfileRepository repository;
    private ProfileAdapter profileAdapter;
    private DeviceAdapter deviceAdapter;
    private InputManager inputManager;

    private TextView statusText;
    private TextView activeProfileText;
    private TextView noProfilesText;
    private SwitchMaterial overlaySwitch;
    private RecyclerView profileList;

    private boolean suppressSwitchCallback = false;

    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            refreshDevices();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            refreshDevices();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            refreshDevices();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = ProfileRepository.getInstance(this);
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        statusText = findViewById(R.id.text_overlay_status);
        activeProfileText = findViewById(R.id.text_active_profile);
        noProfilesText = findViewById(R.id.text_no_profiles);
        overlaySwitch = findViewById(R.id.switch_overlay);
        profileList = findViewById(R.id.list_recent_profiles);

        profileAdapter = new ProfileAdapter(this);
        profileList.setLayoutManager(new LinearLayoutManager(this));
        profileList.setAdapter(profileAdapter);

        deviceAdapter = new DeviceAdapter();
        RecyclerView deviceList = findViewById(R.id.list_devices);
        deviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceList.setAdapter(deviceAdapter);

        findViewById(R.id.btn_create_profile).setOnClickListener(v -> showCreateProfileDialog());
        findViewById(R.id.btn_open_editor).setOnClickListener(v -> openEditorForActiveProfile());

        overlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitchCallback) {
                return;
            }
            if (checked) {
                requestStartOverlay();
            } else {
                startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        inputManager.registerInputDeviceListener(deviceListener, null);
        refreshProfiles();
        refreshDevices();
        refreshOverlayStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inputManager.unregisterInputDeviceListener(deviceListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Profiles ---------------------------------------------------------

    private void refreshProfiles() {
        List<Profile> recents = repository.loadRecents();
        if (recents.isEmpty()) {
            recents = repository.loadAll();
        }
        profileAdapter.submitList(recents);
        noProfilesText.setVisibility(recents.isEmpty() ? View.VISIBLE : View.GONE);
        profileList.setVisibility(recents.isEmpty() ? View.GONE : View.VISIBLE);

        String activeId = repository.getActiveProfileId();
        Profile active = activeId != null ? repository.load(activeId) : null;
        activeProfileText.setText(active != null
                ? getString(R.string.active_profile_format, active.getName())
                : getString(R.string.no_active_profile));
    }

    private void showCreateProfileDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null);
        EditText input = view.findViewById(R.id.dialog_input);
        input.setHint(R.string.hint_profile_name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_create_profile)
                .setView(view)
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = getString(R.string.default_profile_name);
                    }
                    Profile profile = new Profile(name);
                    repository.save(profile);
                    repository.setActiveProfileId(profile.getId());
                    refreshProfiles();
                    openEditor(profile.getId());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void openEditorForActiveProfile() {
        String activeId = repository.getActiveProfileId();
        if (activeId == null) {
            Toast.makeText(this, R.string.error_no_active_profile, Toast.LENGTH_SHORT).show();
            return;
        }
        openEditor(activeId);
    }

    private void openEditor(String profileId) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROFILE_ID, profileId);
        startActivity(intent);
    }

    @Override
    public void onProfileClicked(Profile profile) {
        repository.setActiveProfileId(profile.getId());
        openEditor(profile.getId());
    }

    @Override
    public void onProfileMenuClicked(Profile profile, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_profile_item);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_set_active) {
                repository.setActiveProfileId(profile.getId());
                refreshProfiles();
                return true;
            } else if (id == R.id.action_duplicate) {
                Profile copy = profile.deepCopy(profile.getName() + " Copy");
                repository.save(copy);
                refreshProfiles();
                return true;
            } else if (id == R.id.action_export) {
                exportProfile(profile);
                return true;
            } else if (id == R.id.action_delete) {
                confirmDeleteProfile(profile);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void exportProfile(Profile profile) {
        try {
            android.net.Uri uri = repository.prepareShareUri(profile, "com.gomouse.pro.fileprovider");
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_export)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteProfile(Profile profile) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_delete_profile_format, profile.getName()))
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    repository.delete(profile.getId());
                    refreshProfiles();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // --- Devices ------------------------------------------------------------

    private void refreshDevices() {
        List<InputDevice> relevant = new ArrayList<>();
        for (int id : inputManager.getInputDeviceIds()) {
            InputDevice device = inputManager.getInputDevice(id);
            if (device == null || device.isVirtual()) {
                continue;
            }
            int sources = device.getSources();
            boolean relevantSource = (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                    || (sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                    || (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (relevantSource) {
                relevant.add(device);
            }
        }
        deviceAdapter.submitList(relevant);
    }

    // --- Overlay --------------------------------------------------------------

    private void refreshOverlayStatus() {
        boolean running = OverlayService.isRunning();
        statusText.setText(running ? R.string.overlay_status_on : R.string.overlay_status_off);
        suppressSwitchCallback = true;
        overlaySwitch.setChecked(running);
        suppressSwitchCallback = false;
    }

    private void requestStartOverlay() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.rationale_overlay_permission)
                    .setPositiveButton(R.string.action_continue, (d, w) ->
                            startActivity(PermissionUtils.overlayPermissionIntent(this)))
                    .setNegativeButton(R.string.action_cancel, (d, w) -> refreshOverlayStatus())
                    .show();
            return;
        }
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.rationale_accessibility_permission)
                    .setPositiveButton(R.string.action_continue, (d, w) ->
                            startActivity(PermissionUtils.accessibilitySettingsIntent()))
                    .setNegativeButton(R.string.action_cancel, (d, w) -> refreshOverlayStatus())
                    .show();
            return;
        }
        if (repository.getActiveProfileId() == null) {
            Toast.makeText(this, R.string.error_no_active_profile, Toast.LENGTH_SHORT).show();
            refreshOverlayStatus();
            return;
        }
        Intent intent = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_START);
        androidx.core.content.ContextCompat.startForegroundService(this, intent);
    }
}
