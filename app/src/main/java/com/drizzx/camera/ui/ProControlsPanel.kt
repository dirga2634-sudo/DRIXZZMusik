package com.drizzx.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drizzx.camera.config.ProDefaults
import com.drizzx.camera.ui.theme.DrizzxAccent
import kotlin.math.roundToInt

private val STANDARD_SHUTTER_SPEEDS_NS = longArrayOf(
    125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L,
    16_666_666L, 33_333_333L, 66_666_666L, 125_000_000L, 250_000_000L, 500_000_000L,
    1_000_000_000L, 2_000_000_000L, 4_000_000_000L
)

private val WHITE_BALANCE_PRESETS = listOf(
    "auto" to "Auto",
    "daylight" to "Siang",
    "cloudy" to "Mendung",
    "incandescent" to "Lampu Pijar",
    "fluorescent" to "Neon",
    "shade" to "Teduh"
)

fun formatShutterSpeed(ns: Long): String {
    return if (ns >= 1_000_000_000L) {
        "${"%.1f".format(ns / 1_000_000_000.0)}s"
    } else {
        "1/${(1_000_000_000.0 / ns).roundToInt()}"
    }
}

@Composable
fun ProControlsPanel(
    pro: ProDefaults,
    isoRange: IntRange,
    exposureTimeRangeNs: LongRange,
    exposureCompensationRange: IntRange,
    minFocusDistanceDiopters: Float,
    onIsoChange: (auto: Boolean, value: Int) -> Unit,
    onShutterChange: (auto: Boolean, valueNs: Long) -> Unit,
    onWhiteBalanceChange: (preset: String) -> Unit,
    onFocusChange: (auto: Boolean, distance: Float) -> Unit,
    onExposureCompensationChange: (index: Int) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableShutterSpeeds = STANDARD_SHUTTER_SPEEDS_NS
        .filter { it in exposureTimeRangeNs.first..exposureTimeRangeNs.last }
        .ifEmpty { listOf(exposureTimeRangeNs.first) }
    val focusMax = minFocusDistanceDiopters.coerceAtLeast(0.01f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ManualControlRow(
            label = "ISO",
            auto = pro.isoAuto,
            displayValue = if (pro.isoAuto) "Auto" else pro.isoValue.toString(),
            onAutoToggle = { auto -> onIsoChange(auto, pro.isoValue) }
        ) {
            Slider(
                value = pro.isoValue.toFloat().coerceIn(isoRange.first.toFloat(), isoRange.last.toFloat()),
                onValueChange = { onIsoChange(false, it.roundToInt()) },
                onValueChangeFinished = onCommit,
                valueRange = isoRange.first.toFloat()..isoRange.last.toFloat(),
                colors = SliderDefaults.colors(thumbColor = DrizzxAccent, activeTrackColor = DrizzxAccent)
            )
        }

        val shutterIndex = availableShutterSpeeds.indexOfFirst { it >= pro.shutterSpeedNs }
            .let { if (it < 0) availableShutterSpeeds.lastIndex else it }
        ManualControlRow(
            label = "Shutter",
            auto = pro.shutterAuto,
            displayValue = if (pro.shutterAuto) "Auto" else formatShutterSpeed(pro.shutterSpeedNs),
            onAutoToggle = { auto -> onShutterChange(auto, pro.shutterSpeedNs) }
        ) {
            Slider(
                value = shutterIndex.toFloat(),
                onValueChange = { idx ->
                    val speed = availableShutterSpeeds.getOrElse(idx.roundToInt()) { pro.shutterSpeedNs }
                    onShutterChange(false, speed)
                },
                onValueChangeFinished = onCommit,
                valueRange = 0f..(availableShutterSpeeds.size - 1).coerceAtLeast(1).toFloat(),
                steps = (availableShutterSpeeds.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(thumbColor = DrizzxAccent, activeTrackColor = DrizzxAccent)
            )
        }

        ManualControlRow(
            label = "EV",
            auto = false,
            displayValue = if (pro.exposureCompensationIndex > 0) "+${pro.exposureCompensationIndex}" else "${pro.exposureCompensationIndex}",
            onAutoToggle = null
        ) {
            Slider(
                value = pro.exposureCompensationIndex.toFloat(),
                onValueChange = { onExposureCompensationChange(it.roundToInt()) },
                onValueChangeFinished = onCommit,
                valueRange = exposureCompensationRange.first.toFloat()..exposureCompensationRange.last.toFloat(),
                colors = SliderDefaults.colors(thumbColor = DrizzxAccent, activeTrackColor = DrizzxAccent)
            )
        }

        ManualControlRow(
            label = "Fokus",
            auto = pro.focusAuto,
            displayValue = if (pro.focusAuto) "Auto" else "%.1f".format(pro.focusDistanceDiopters),
            onAutoToggle = { auto -> onFocusChange(auto, pro.focusDistanceDiopters) }
        ) {
            Slider(
                value = pro.focusDistanceDiopters.coerceIn(0f, focusMax),
                onValueChange = { onFocusChange(false, it) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..focusMax,
                colors = SliderDefaults.colors(thumbColor = DrizzxAccent, activeTrackColor = DrizzxAccent)
            )
        }

        Text("White Balance", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WHITE_BALANCE_PRESETS) { (key, label) ->
                val selected = pro.whiteBalancePreset == key
                AssistChip(
                    onClick = {
                        onWhiteBalanceChange(key)
                        onCommit()
                    },
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) DrizzxAccent else Color.White.copy(alpha = 0.1f),
                        labelColor = if (selected) Color.Black else Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun ManualControlRow(
    label: String,
    auto: Boolean,
    displayValue: String,
    onAutoToggle: ((Boolean) -> Unit)?,
    slider: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayValue, color = DrizzxAccent, style = MaterialTheme.typography.bodyMedium)
                if (onAutoToggle != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = !auto,
                        onCheckedChange = { manual -> onAutoToggle(!manual) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DrizzxAccent)
                    )
                }
            }
        }
        if (onAutoToggle == null || !auto) {
            slider()
        }
    }
}
