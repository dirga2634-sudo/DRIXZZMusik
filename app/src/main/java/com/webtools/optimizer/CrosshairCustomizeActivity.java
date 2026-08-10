package com.webtools.optimizer;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.webtools.optimizer.databinding.ActivityCrosshairCustomizeBinding;
import com.webtools.optimizer.util.PrefsManager;
import com.webtools.optimizer.view.CrosshairView;

import java.util.Arrays;
import java.util.List;

/** Layar kustomisasi crosshair: preview langsung, pilih warna/bentuk/ukuran, simpan ke PrefsManager. */
public class CrosshairCustomizeActivity extends AppCompatActivity {

    private ActivityCrosshairCustomizeBinding binding;
    private List<View> colorSwatches;
    private int selectedColor;
    private int selectedShape;
    private float selectedSize;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCrosshairCustomizeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        selectedColor = PrefsManager.getCrosshairColor(this);
        selectedShape = PrefsManager.getCrosshairShape(this);
        selectedSize = PrefsManager.getCrosshairSize(this);

        colorSwatches = Arrays.asList(
                binding.colorWhite, binding.colorRed, binding.colorGreen,
                binding.colorCyan, binding.colorYellow, binding.colorMagenta);

        setupColorSwatch(binding.colorWhite, 0xFFFFFFFF);
        setupColorSwatch(binding.colorRed, 0xFFFF5C5C);
        setupColorSwatch(binding.colorGreen, 0xFF00E5A0);
        setupColorSwatch(binding.colorCyan, 0xFF4FC3F7);
        setupColorSwatch(binding.colorYellow, 0xFFFFD54F);
        setupColorSwatch(binding.colorMagenta, 0xFFFF4FD8);
        highlightSelectedColor();

        binding.shapeToggleGroup.check(shapeToButtonId(selectedShape));
        binding.shapeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            selectedShape = buttonIdToShape(checkedId);
            updatePreview();
        });

        binding.sizeSlider.setValue(selectedSize);
        binding.sizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            selectedSize = value;
            updatePreview();
        });

        binding.btnDone.setOnClickListener(v -> onDone());

        updatePreview();
    }

    private void setupColorSwatch(View swatch, int color) {
        swatch.setBackgroundTintList(ColorStateList.valueOf(color));
        swatch.setOnClickListener(v -> {
            selectedColor = color;
            highlightSelectedColor();
            updatePreview();
        });
    }

    private void highlightSelectedColor() {
        for (View swatch : colorSwatches) {
            boolean isSelected = swatchColor(swatch) == selectedColor;
            swatch.animate()
                    .scaleX(isSelected ? 1.3f : 1f)
                    .scaleY(isSelected ? 1.3f : 1f)
                    .setDuration(120)
                    .start();
        }
    }

    private int swatchColor(View swatch) {
        if (swatch == binding.colorWhite) return 0xFFFFFFFF;
        if (swatch == binding.colorRed) return 0xFFFF5C5C;
        if (swatch == binding.colorGreen) return 0xFF00E5A0;
        if (swatch == binding.colorCyan) return 0xFF4FC3F7;
        if (swatch == binding.colorYellow) return 0xFFFFD54F;
        if (swatch == binding.colorMagenta) return 0xFFFF4FD8;
        return 0xFFFFFFFF;
    }

    private int shapeToButtonId(int shape) {
        if (shape == CrosshairView.SHAPE_CROSS) return R.id.shape_cross;
        if (shape == CrosshairView.SHAPE_DOT) return R.id.shape_dot;
        if (shape == CrosshairView.SHAPE_CIRCLE) return R.id.shape_circle;
        return R.id.shape_cross_circle;
    }

    private int buttonIdToShape(int buttonId) {
        if (buttonId == R.id.shape_cross) return CrosshairView.SHAPE_CROSS;
        if (buttonId == R.id.shape_dot) return CrosshairView.SHAPE_DOT;
        if (buttonId == R.id.shape_circle) return CrosshairView.SHAPE_CIRCLE;
        return CrosshairView.SHAPE_CROSS_CIRCLE;
    }

    private void updatePreview() {
        binding.previewCrosshair.setStyle(selectedColor, selectedShape, selectedSize);
    }

    private void onDone() {
        PrefsManager.saveCrosshairStyle(this, selectedColor, selectedShape, selectedSize);
        // Kalau crosshair lagi aktif, restart cepat biar style baru langsung kepake.
        if (CrosshairService.isRunning) {
            stopService(new Intent(this, CrosshairService.class));
            ContextCompat.startForegroundService(this, new Intent(this, CrosshairService.class));
        }
        finish();
    }
}
