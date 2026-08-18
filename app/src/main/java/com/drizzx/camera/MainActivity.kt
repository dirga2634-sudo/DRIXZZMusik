package com.drizzx.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drizzx.camera.ui.CameraScreen
import com.drizzx.camera.ui.ConfigScreen
import com.drizzx.camera.ui.theme.DrizzxCamTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrizzxCamTheme {
                PermissionGate()
            }
        }
    }
}

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    return permissions.toTypedArray()
}

@Composable
private fun PermissionGate() {
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }

    var grantedMap by remember {
        mutableStateOf(
            permissions.associateWith { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var requestedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        requestedOnce = true
        grantedMap = result
    }

    val cameraGranted = grantedMap[Manifest.permission.CAMERA] == true
    val audioGranted = grantedMap[Manifest.permission.RECORD_AUDIO] == true

    if (cameraGranted) {
        val cameraViewModel: CameraViewModel = viewModel()
        var showConfigScreen by remember { mutableStateOf(false) }

        if (showConfigScreen) {
            ConfigScreen(
                viewModel = cameraViewModel,
                onBack = { showConfigScreen = false }
            )
        } else {
            CameraScreen(
                hasAudioPermission = audioGranted,
                onOpenConfig = { showConfigScreen = true },
                viewModel = cameraViewModel
            )
        }
    } else {
        PermissionRationale(
            alreadyDenied = requestedOnce,
            onRequestPermissions = { launcher.launch(permissions) }
        )
    }
}

@Composable
private fun PermissionRationale(alreadyDenied: Boolean, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.permission_rationale_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (alreadyDenied) {
                    stringResource(R.string.permission_denied_body)
                } else {
                    stringResource(R.string.permission_rationale_body)
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (alreadyDenied) {
                Row {
                    OutlinedButton(onClick = onRequestPermissions) {
                        Text(stringResource(R.string.permission_try_again_button))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.permission_open_settings_button))
                    }
                }
            } else {
                Button(onClick = onRequestPermissions) {
                    Text(stringResource(R.string.permission_grant_button))
                }
            }
        }
    }
}
