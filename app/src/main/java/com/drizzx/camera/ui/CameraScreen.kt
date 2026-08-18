package com.drizzx.camera.ui

import android.content.Intent
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drizzx.camera.CameraViewModel
import com.drizzx.camera.CaptureMode
import com.drizzx.camera.FlashMode
import com.drizzx.camera.R
import com.drizzx.camera.config.FilterPreset
import com.drizzx.camera.ui.theme.DrizzxAccent
import com.drizzx.camera.ui.theme.RecordingRed
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    hasAudioPermission: Boolean,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(2000)
            viewModel.consumeMessage()
        }
    }

    var focusRing by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(previewView) {
                    detectTapGestures { offset ->
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        viewModel.focusAt(point)
                        focusRing = offset
                    }
                }
                .pointerInput(previewView) {
                    detectTransformGestures { _, _, zoom, _ ->
                        viewModel.setZoom(viewModel.uiState.value.zoomRatio * zoom)
                    }
                }
        )

        focusRing?.let { offset ->
            FocusRing(offset = offset, onFinished = { focusRing = null })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlashButton(
                flashMode = uiState.flashMode,
                enabled = uiState.hasFlashUnit,
                onClick = viewModel::cycleFlash
            )

            AnimatedVisibility(visible = uiState.isRecording) {
                RecordingTimer(seconds = uiState.recordingSeconds)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.supportsManualControls) {
                    ProToggle(
                        active = uiState.proModeEnabled,
                        onToggle = { viewModel.toggleProMode(!uiState.proModeEnabled) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(
                    onClick = onOpenConfig,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Config & Plugin", tint = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.proModeEnabled && uiState.supportsManualControls) {
                ProControlsPanel(
                    pro = uiState.proSettings,
                    isoRange = uiState.isoRange,
                    exposureTimeRangeNs = uiState.exposureTimeRangeNs,
                    exposureCompensationRange = uiState.exposureCompensationRange,
                    minFocusDistanceDiopters = uiState.minFocusDistanceDiopters,
                    onIsoChange = { auto, value ->
                        viewModel.updateProLive { it.copy(isoAuto = auto, isoValue = value) }
                    },
                    onShutterChange = { auto, ns ->
                        viewModel.updateProLive { it.copy(shutterAuto = auto, shutterSpeedNs = ns) }
                    },
                    onWhiteBalanceChange = { preset ->
                        viewModel.updateProLive { it.copy(whiteBalancePreset = preset) }
                    },
                    onFocusChange = { auto, dist ->
                        viewModel.updateProLive { it.copy(focusAuto = auto, focusDistanceDiopters = dist) }
                    },
                    onExposureCompensationChange = { idx ->
                        viewModel.updateProLive { it.copy(exposureCompensationIndex = idx) }
                    },
                    onCommit = viewModel::commitProSettings,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.captureMode == CaptureMode.PHOTO) {
                FilterStrip(
                    filters = uiState.filters,
                    selected = uiState.selectedFilter,
                    onSelect = viewModel::selectFilter
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            ModeSwitcher(mode = uiState.captureMode, onModeChange = viewModel::setCaptureMode)

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LastCaptureButton(
                    uri = uiState.lastCaptureUri,
                    onClick = {
                        val uri = uiState.lastCaptureUri ?: return@LastCaptureButton
                        val mime = if (uiState.captureMode == CaptureMode.VIDEO) "video/*" else "image/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )

                ShutterButton(
                    isRecording = uiState.isRecording,
                    isVideoMode = uiState.captureMode == CaptureMode.VIDEO,
                    onClick = { viewModel.onShutterPressed(hasAudioPermission) }
                )

                IconButton(
                    onClick = { viewModel.switchCamera(lifecycleOwner, previewView) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.cd_switch_camera),
                        tint = Color.White
                    )
                }
            }
        }

        uiState.message?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun FocusRing(offset: Offset, onFinished: () -> Unit) {
    LaunchedEffect(offset) {
        delay(700)
        onFinished()
    }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { offset.x.toDp() - 32.dp },
                y = with(density) { offset.y.toDp() - 32.dp }
            )
            .size(64.dp)
            .border(1.5.dp, Color.White, CircleShape)
    )
}

@Composable
private fun FlashButton(flashMode: FlashMode, enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) {
        Spacer(modifier = Modifier.size(48.dp))
        return
    }
    val icon = when (flashMode) {
        FlashMode.OFF -> Icons.Default.FlashOff
        FlashMode.ON -> Icons.Default.FlashOn
        FlashMode.AUTO -> Icons.Default.FlashAuto
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = stringResource(R.string.cd_toggle_flash), tint = Color.White)
    }
}

@Composable
private fun RecordingTimer(seconds: Int) {
    val minutes = seconds / 60
    val secs = seconds % 60
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(RecordingRed, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "%d:%02d".format(minutes, secs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ModeSwitcher(mode: CaptureMode, onModeChange: (CaptureMode) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModePill(
            label = stringResource(R.string.mode_photo),
            selected = mode == CaptureMode.PHOTO,
            onClick = { onModeChange(CaptureMode.PHOTO) }
        )
        ModePill(
            label = stringResource(R.string.mode_video),
            selected = mode == CaptureMode.VIDEO,
            onClick = { onModeChange(CaptureMode.VIDEO) }
        )
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) DrizzxAccent else Color.Transparent
    val fg = if (selected) Color.Black else Color.White
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ShutterButton(isRecording: Boolean, isVideoMode: Boolean, onClick: () -> Unit) {
    val ringColor = if (isVideoMode) RecordingRed else Color.White
    val innerColor by animateColorAsState(
        targetValue = if (isRecording) RecordingRed else Color.White,
        label = "shutterInnerColor"
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 32.dp,
        label = "shutterInnerCorner"
    )
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 28.dp else 56.dp,
        label = "shutterInnerSize"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(3.dp, ringColor, CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(innerColor)
        )
    }
}

@Composable
private fun ProToggle(active: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) DrizzxAccent else Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(
            text = "PRO",
            color = if (active) Color.Black else Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun FilterStrip(
    filters: List<FilterPreset>,
    selected: FilterPreset,
    onSelect: (FilterPreset) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            FilterChip(
                name = FilterPreset.ORIGINAL.name,
                selected = selected.name == FilterPreset.ORIGINAL.name,
                onClick = { onSelect(FilterPreset.ORIGINAL) }
            )
        }
        items(filters) { filter ->
            FilterChip(
                name = filter.name,
                selected = selected.name == filter.name,
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun FilterChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) DrizzxAccent else Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            color = if (selected) Color.Black else Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun LastCaptureButton(uri: Uri?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (uri != null) 0.25f else 0.1f))
            .then(if (uri != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.cd_last_capture),
                tint = Color.White
            )
        }
    }
}
