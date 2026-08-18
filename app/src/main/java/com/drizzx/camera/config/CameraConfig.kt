package com.drizzx.camera.config

/**
 * Root of the shareable Drizzx Cam config file - this is the "plugin" Dirga
 * asked for: an XML file people can tune, export, and share, the same way
 * the GCam mod community shares config XMLs.
 */
data class CameraConfig(
    val version: Int = CURRENT_VERSION,
    val pro: ProDefaults = ProDefaults(),
    val imageProcessing: ImageProcessingSettings = ImageProcessingSettings(),
    val filters: List<FilterPreset> = FilterPreset.builtIns()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Starting values for Pro mode's manual controls. `xAuto = true` means "leave
 * this control on auto"; the paired value only applies once auto is turned
 * off for that control (in-app or by editing the config).
 *
 * ISO and shutter speed are tied together: Camera2 only allows manual sensor
 * exposure as a pair (turning `CONTROL_AE_MODE` off requires both), so
 * turning either one off puts exposure fully in manual.
 */
data class ProDefaults(
    val isoAuto: Boolean = true,
    val isoValue: Int = 100,
    val shutterAuto: Boolean = true,
    val shutterSpeedNs: Long = 16_666_666L, // ~1/60s
    // "auto" | "daylight" | "cloudy" | "incandescent" | "fluorescent" | "shade"
    val whiteBalancePreset: String = "auto",
    // Device compensation step index (not a raw EV value - the EV-per-step
    // varies by device, so the UI reads the live range from the camera).
    val exposureCompensationIndex: Int = 0,
    val focusAuto: Boolean = true,
    val focusDistanceDiopters: Float = 0f
)

data class ImageProcessingSettings(
    val jpegQuality: Int = 95
)

/**
 * A named color-grade preset. Saturation/contrast/warmth are plain
 * multipliers/offsets, deliberately simple so anyone can hand-edit the
 * exported XML and add their own without touching code.
 */
data class FilterPreset(
    val name: String,
    val saturation: Float, // 0f = grayscale, 1f = neutral, >1f = boosted
    val contrast: Float,   // 1f = neutral
    val warmth: Float      // -1f (cool/blue) .. 0f (neutral) .. 1f (warm/orange)
) {
    companion object {
        val ORIGINAL = FilterPreset("Original", saturation = 1f, contrast = 1f, warmth = 0f)

        fun builtIns(): List<FilterPreset> = listOf(
            FilterPreset("Vivid", saturation = 1.35f, contrast = 1.15f, warmth = 0.05f),
            FilterPreset("Mono", saturation = 0f, contrast = 1.1f, warmth = 0f),
            FilterPreset("Cool", saturation = 1.05f, contrast = 1.0f, warmth = -0.15f),
            FilterPreset("Warm", saturation = 1.1f, contrast = 1.0f, warmth = 0.18f),
            FilterPreset("Fade", saturation = 0.85f, contrast = 0.9f, warmth = 0.05f)
        )
    }
}
