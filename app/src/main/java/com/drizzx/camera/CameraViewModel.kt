package com.drizzx.camera

import android.app.Application
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.drizzx.camera.camera.CameraController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaptureMode { PHOTO, VIDEO }

enum class FlashMode { OFF, ON, AUTO }

data class CameraUiState(
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val flashMode: FlashMode = FlashMode.OFF,
    val hasFlashUnit: Boolean = false,
    val isFrontCamera: Boolean = false,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val lastCaptureUri: Uri? = null,
    val message: String? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = CameraController(application.applicationContext)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var recordingTimerJob: Job? = null

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            val caps = controller.bindToCamera(lifecycleOwner, previewView)
            _uiState.update {
                it.copy(
                    hasFlashUnit = caps.hasFlash,
                    minZoomRatio = caps.minZoomRatio,
                    maxZoomRatio = caps.maxZoomRatio,
                    zoomRatio = 1f
                )
            }
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (_uiState.value.isRecording) return
        viewModelScope.launch {
            val caps = controller.switchCamera(lifecycleOwner, previewView)
            val nowFront = controller.lensFacing == CameraSelector.LENS_FACING_FRONT
            _uiState.update {
                it.copy(
                    isFrontCamera = nowFront,
                    hasFlashUnit = caps.hasFlash,
                    minZoomRatio = caps.minZoomRatio,
                    maxZoomRatio = caps.maxZoomRatio,
                    zoomRatio = 1f,
                    flashMode = if (nowFront) FlashMode.OFF else it.flashMode
                )
            }
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(captureMode = mode) }
    }

    fun cycleFlash() {
        if (!_uiState.value.hasFlashUnit) return
        _uiState.update {
            val next = when (it.flashMode) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.ON -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            }
            it.copy(flashMode = next)
        }
    }

    fun setZoom(ratio: Float) {
        val state = _uiState.value
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        controller.setZoomRatio(clamped)
        _uiState.update { it.copy(zoomRatio = clamped) }
    }

    fun focusAt(point: MeteringPoint) {
        controller.focusOn(point)
    }

    fun onShutterPressed(hasAudioPermission: Boolean) {
        when (_uiState.value.captureMode) {
            CaptureMode.PHOTO -> capturePhoto()
            CaptureMode.VIDEO -> toggleRecording(hasAudioPermission)
        }
    }

    private fun capturePhoto() {
        val flashMode = when (_uiState.value.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
        controller.capturePhoto(
            flashMode = flashMode,
            onSaved = { uri -> _uiState.update { it.copy(lastCaptureUri = uri, message = "Foto tersimpan") } },
            onError = { error -> _uiState.update { it.copy(message = error) } }
        )
    }

    private fun toggleRecording(hasAudioPermission: Boolean) {
        if (_uiState.value.isRecording) {
            controller.stopRecording()
            return
        }
        controller.startRecording(withAudio = hasAudioPermission) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _uiState.update { it.copy(isRecording = true, recordingSeconds = 0) }
                    startRecordingTimer()
                }
                is VideoRecordEvent.Finalize -> {
                    recordingTimerJob?.cancel()
                    _uiState.update {
                        if (!event.hasError()) {
                            it.copy(
                                isRecording = false,
                                recordingSeconds = 0,
                                lastCaptureUri = event.outputResults.outputUri,
                                message = "Video tersimpan"
                            )
                        } else {
                            it.copy(isRecording = false, recordingSeconds = 0, message = "Gagal rekam video")
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        controller.release()
    }
}
