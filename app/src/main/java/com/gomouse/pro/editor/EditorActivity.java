package com.gomouse.pro.editor;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.gomouse.pro.R;
import com.gomouse.pro.model.ActionType;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.model.Profile;
import com.gomouse.pro.service.GomouseAccessibilityService;
import com.gomouse.pro.storage.ProfileRepository;

public class EditorActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE_ID = "extra_profile_id";

    private ProfileRepository repository;
    private Profile profile;
    private EditorCanvasView canvas;

    private ImageButton btnUndo, btnRedo, btnLock, btnVisibility, btnDuplicate, btnDelete;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_editor);

        repository = ProfileRepository.getInstance(this);
        String profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        profile = profileId != null ? repository.load(profileId) : null;
        if (profile == null) {
            Toast.makeText(this, R.string.error_profile_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.editor_toolbar);
        toolbar.setTitle(profile.getName());
        toolbar.setNavigationOnClickListener(v -> saveAndExit());

        FrameLayout container = findViewById(R.id.canvas_container);
        canvas = new EditorCanvasView(this);
        canvas.setMappings(profile.getMappings());
        canvas.setGridSnap(profile.isGridSnapEnabled(), profile.getGridSize());
        canvas.setSelectionListener(new EditorCanvasView.SelectionListener() {
            @Override
            public void onSelectionChanged(InputMapping selected) {
                updateToolbarState(selected);
            }

            @Override
            public void onMappingsChanged() {
                updateUndoRedoButtons();
            }
        });
        container.addView(canvas);
        canvas.setOnClickListener(v -> {
            if (canvas.getSelected() != null) {
                EditMappingDialog.show(this, canvas.getSelected(), canvas, this::updateUndoRedoButtons);
            }
        });

        FloatingActionButton fabAdd = findViewById(R.id.fab_add);
        fabAdd.setOnClickListener(v -> showAddMenu());

        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnLock = findViewById(R.id.btn_lock);
        btnVisibility = findViewById(R.id.btn_visibility);
        btnDuplicate = findViewById(R.id.btn_duplicate);
        btnDelete = findViewById(R.id.btn_delete);

        btnUndo.setOnClickListener(v -> {
            canvas.undo();
            updateUndoRedoButtons();
        });
        btnRedo.setOnClickListener(v -> {
            canvas.redo();
            updateUndoRedoButtons();
        });
        btnLock.setOnClickListener(v -> {
            canvas.toggleSelectedLock();
            updateToolbarState(canvas.getSelected());
        });
        btnVisibility.setOnClickListener(v -> {
            canvas.toggleSelectedVisibility();
            updateToolbarState(canvas.getSelected());
        });
        btnDuplicate.setOnClickListener(v -> canvas.duplicateSelected());
        btnDelete.setOnClickListener(v -> confirmDelete());

        findViewById(R.id.btn_reset_layout).setOnClickListener(v -> confirmResetLayout());

        updateToolbarState(null);
        updateUndoRedoButtons();
    }

    private void showAddMenu() {
        String[] labels = {"Tap Button", "Hold Button", "Double Tap", "Swipe", "Joystick", "D-Pad"};
        ActionType[] types = {ActionType.TAP, ActionType.HOLD, ActionType.DOUBLE_TAP,
                ActionType.SWIPE, ActionType.JOYSTICK, ActionType.DPAD};
        new AlertDialog.Builder(this)
                .setTitle(R.string.editor_add_control)
                .setItems(labels, (dialog, which) -> canvas.addMapping(types[which]))
                .show();
    }

    private void confirmDelete() {
        if (canvas.getSelected() == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.editor_confirm_delete)
                .setPositiveButton(R.string.action_delete, (d, w) -> canvas.deleteSelected())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmResetLayout() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.editor_confirm_reset)
                .setPositiveButton(R.string.action_reset, (d, w) -> canvas.resetLayout())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void updateToolbarState(InputMapping selected) {
        boolean hasSelection = selected != null;
        btnLock.setEnabled(hasSelection);
        btnVisibility.setEnabled(hasSelection);
        btnDuplicate.setEnabled(hasSelection);
        btnDelete.setEnabled(hasSelection);
        btnLock.setAlpha(hasSelection ? 1f : 0.35f);
        btnVisibility.setAlpha(hasSelection ? 1f : 0.35f);
        btnDuplicate.setAlpha(hasSelection ? 1f : 0.35f);
        btnDelete.setAlpha(hasSelection ? 1f : 0.35f);
        if (hasSelection) {
            btnLock.setImageResource(selected.isLocked() ? R.drawable.ic_lock : R.drawable.ic_lock_open);
            btnVisibility.setImageResource(selected.isVisible() ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        }
    }

    private void updateUndoRedoButtons() {
        btnUndo.setEnabled(canvas.canUndo());
        btnRedo.setEnabled(canvas.canRedo());
        btnUndo.setAlpha(canvas.canUndo() ? 1f : 0.35f);
        btnRedo.setAlpha(canvas.canRedo() ? 1f : 0.35f);
    }

    private void saveAndExit() {
        profile.setMappings(canvas.getMappings());
        repository.save(profile);
        GomouseAccessibilityService service = GomouseAccessibilityService.getInstance();
        if (service != null && profile.getId().equals(repository.getActiveProfileId())) {
            service.reloadActiveProfile();
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        saveAndExit();
    }
}
