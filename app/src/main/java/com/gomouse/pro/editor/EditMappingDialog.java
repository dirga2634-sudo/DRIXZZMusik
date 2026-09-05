package com.gomouse.pro.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.gomouse.pro.model.ActionType;
import com.gomouse.pro.model.DirectionBinding;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.model.InputSourceType;
import com.gomouse.pro.util.InputCodeUtils;

/**
 * Configuration dialog for a single mapping: label, physical-input binding
 * (captured by literally pressing the key/button while the field is
 * focused), and any parameters specific to its {@link ActionType}.
 */
public final class EditMappingDialog {

    private EditMappingDialog() {
    }

    public interface OnDoneListener {
        void onDone();
    }

    public static void show(Context context, InputMapping mapping, EditorCanvasView canvas,
                             OnDoneListener onDone) {
        canvas.pushUndo();

        int pad = dp(context, 16);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        EditText labelField = new EditText(context);
        labelField.setHint("Label");
        labelField.setText(mapping.getLabel());
        root.addView(labelField);

        if (mapping.usesDirectionBindings()) {
            addSectionTitle(context, root, "Direction bindings");
            addDirectionRow(context, root, "Up", mapping.getUp());
            addDirectionRow(context, root, "Down", mapping.getDown());
            addDirectionRow(context, root, "Left", mapping.getLeft());
            addDirectionRow(context, root, "Right", mapping.getRight());

            if (mapping.getActionType() == ActionType.JOYSTICK) {
                CheckBox mouseLook = new CheckBox(context);
                mouseLook.setText("Also steer with mouse movement");
                mouseLook.setChecked(mapping.isMouseLookEnabled());
                mouseLook.setOnCheckedChangeListener((b, checked) -> mapping.setMouseLookEnabled(checked));
                root.addView(mouseLook);
            } else {
                CheckBox eightWay = new CheckBox(context);
                eightWay.setText("8-directional (diagonals)");
                eightWay.setChecked(mapping.isEightDirectional());
                eightWay.setOnCheckedChangeListener((b, checked) -> mapping.setEightDirectional(checked));
                root.addView(eightWay);
            }
        } else {
            addSectionTitle(context, root, "Input binding");
            addSimpleBindingRow(context, root, mapping);

            if (mapping.getActionType() == ActionType.SWIPE) {
                addSectionTitle(context, root, "Swipe");
                addSwipeControls(context, root, mapping);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Edit " + mapping.getActionType().name())
                .setView(scroll)
                .setPositiveButton("Save", (d, w) -> {
                    mapping.setLabel(labelField.getText().toString());
                    canvas.commitPropertyChange();
                    if (onDone != null) {
                        onDone.onDone();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> canvas.undo())
                .setOnCancelListener(d -> canvas.undo())
                .create();
        dialog.show();
    }

    private static void addSectionTitle(Context context, LinearLayout root, String text) {
        TextView title = new TextView(context);
        title.setText(text);
        title.setTextColor(Color.parseColor("#2979FF"));
        title.setPadding(0, dp(context, 14), 0, dp(context, 4));
        root.addView(title);
    }

    private static void addSimpleBindingRow(Context context, LinearLayout root, InputMapping mapping) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Spinner sourceSpinner = new Spinner(context);
        String[] sources = {"Keyboard", "Gamepad", "Mouse Button"};
        sourceSpinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, sources));
        int initialIndex = 0;
        if (mapping.getSourceType() == InputSourceType.GAMEPAD_BUTTON) initialIndex = 1;
        else if (mapping.getSourceType() == InputSourceType.MOUSE_BUTTON) initialIndex = 2;
        sourceSpinner.setSelection(initialIndex);
        row.addView(sourceSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button captureButton = new Button(context);
        captureButton.setText(describeBinding(mapping));
        row.addView(captureButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        Spinner mouseButtonSpinner = new Spinner(context);
        String[] mouseButtons = {"Left Click", "Right Click", "Middle Click", "Mouse Back", "Mouse Forward"};
        int[] mouseButtonValues = {
                InputCodeUtils.MOUSE_BUTTON_PRIMARY, InputCodeUtils.MOUSE_BUTTON_SECONDARY,
                InputCodeUtils.MOUSE_BUTTON_TERTIARY, InputCodeUtils.MOUSE_BUTTON_BACK,
                InputCodeUtils.MOUSE_BUTTON_FORWARD
        };
        mouseButtonSpinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, mouseButtons));
        mouseButtonSpinner.setVisibility(mapping.getSourceType() == InputSourceType.MOUSE_BUTTON ? View.VISIBLE : View.GONE);
        for (int i = 0; i < mouseButtonValues.length; i++) {
            if (mouseButtonValues[i] == mapping.getInputCode()) {
                mouseButtonSpinner.setSelection(i);
            }
        }
        root.addView(mouseButtonSpinner);

        captureButton.setVisibility(mapping.getSourceType() == InputSourceType.MOUSE_BUTTON ? View.GONE : View.VISIBLE);

        sourceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                if (position == 2) {
                    mapping.setSourceType(InputSourceType.MOUSE_BUTTON);
                    mapping.setInputCode(mouseButtonValues[Math.max(0, mouseButtonSpinner.getSelectedItemPosition())]);
                    mouseButtonSpinner.setVisibility(View.VISIBLE);
                    captureButton.setVisibility(View.GONE);
                } else {
                    mapping.setSourceType(position == 0 ? InputSourceType.KEYBOARD_KEY : InputSourceType.GAMEPAD_BUTTON);
                    mouseButtonSpinner.setVisibility(View.GONE);
                    captureButton.setVisibility(View.VISIBLE);
                    captureButton.setText(describeBinding(mapping));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        mouseButtonSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                mapping.setInputCode(mouseButtonValues[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        captureButton.setOnClickListener(v -> beginCapture(context, captureButton, (source, code) -> {
            mapping.setSourceType(source);
            mapping.setInputCode(code);
            captureButton.setText(describeBinding(mapping));
        }));
    }

    private static void addDirectionRow(Context context, LinearLayout root, String label, DirectionBinding binding) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 4), 0, dp(context, 4));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setWidth(dp(context, 60));
        row.addView(labelView);

        Button captureButton = new Button(context);
        captureButton.setText(binding.isBound()
                ? describe(binding.getSourceType(), binding.getInputCode())
                : "Unbound (touch only)");
        row.addView(captureButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clearButton = new Button(context);
        clearButton.setText("✕");
        clearButton.setOnClickListener(v -> {
            binding.clear();
            captureButton.setText("Unbound (touch only)");
        });
        row.addView(clearButton);

        captureButton.setOnClickListener(v -> beginCapture(context, captureButton, (source, code) -> {
            binding.setSourceType(source);
            binding.setInputCode(code);
            captureButton.setText(describe(source, code));
        }));

        root.addView(row);
    }

    private static void addSwipeControls(Context context, LinearLayout root, InputMapping mapping) {
        TextView durationLabel = new TextView(context);
        durationLabel.setText("Duration: " + mapping.getSwipeDurationMs() + " ms");
        root.addView(durationLabel);
        SeekBar durationSeek = new SeekBar(context);
        durationSeek.setMax(480);
        durationSeek.setProgress((int) Math.max(20, mapping.getSwipeDurationMs() - 20));
        durationSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long ms = progress + 20;
                mapping.setSwipeDurationMs(ms);
                durationLabel.setText("Duration: " + ms + " ms");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(durationSeek);

        TextView dirHint = new TextView(context);
        dirHint.setText("Direction & distance: drag the swipe arrow on the canvas after saving.");
        dirHint.setTextColor(Color.parseColor("#99E8ECFF"));
        dirHint.setPadding(0, dp(context, 6), 0, 0);
        root.addView(dirHint);
    }

    private interface CaptureCallback {
        void onCaptured(InputSourceType source, int code);
    }

    private static void beginCapture(Context context, Button anchor, CaptureCallback callback) {
        String originalText = anchor.getText().toString();
        anchor.setText("Press any key…");
        anchor.setFocusableInTouchMode(true);
        anchor.requestFocus();
        anchor.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP) {
                return true; // consume DOWN silently, act on UP so we get a clean single capture
            }
            if (!InputCodeUtils.isRemappable(keyCode)) {
                anchor.setText(originalText);
                anchor.setOnKeyListener(null);
                return true;
            }
            InputSourceType source = InputCodeUtils.isFromGamepad(event)
                    ? InputSourceType.GAMEPAD_BUTTON : InputSourceType.KEYBOARD_KEY;
            callback.onCaptured(source, keyCode);
            anchor.setOnKeyListener(null);
            return true;
        });
    }

    private static String describeBinding(InputMapping mapping) {
        if (mapping.getSourceType() == null) {
            return "Tap to bind…";
        }
        return describe(mapping.getSourceType(), mapping.getInputCode());
    }

    private static String describe(InputSourceType source, int code) {
        if (source == InputSourceType.MOUSE_BUTTON) {
            return InputCodeUtils.describeMouseButton(code);
        }
        String prefix = source == InputSourceType.GAMEPAD_BUTTON ? "[Pad] " : "";
        return prefix + InputCodeUtils.describeKeyCode(code);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
