package com.drizzx.camera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DrizzxColorScheme = darkColorScheme(
    primary = DrizzxAccent,
    onPrimary = CameraBackground,
    secondary = DrizzxAccentDim,
    background = CameraBackground,
    onBackground = OnCamera,
    surface = CameraSurface,
    onSurface = OnCamera,
    surfaceVariant = CameraSurfaceVariant,
    onSurfaceVariant = OnCameraMuted,
    error = RecordingRed,
    onError = OnCamera
)

/**
 * Drizzx Cam always renders in a dark scheme regardless of system theme -
 * a bright UI would wash out the viewfinder and blow out night shots.
 */
@Composable
fun DrizzxCamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DrizzxColorScheme,
        typography = DrizzxTypography,
        content = content
    )
}
