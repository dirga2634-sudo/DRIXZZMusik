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
import com.drizzx.camera.camera.CameraCapabilities
import com.drizzx.camera.camera.CameraController
import com.drizzx.camera.config.CameraConfig
import com.drizzx.camera.config.ConfigRepository
import com.drizzx.camera.config.FilterPreset
import com.drizzx.camera.config.ProDefaults
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
    val message: String? = null,
    val filters: List<FilterPreset> = FilterPreset.builtIns(),
    val selectedFilter: FilterPreset = FilterPreset.ORIGINAL,
    val proModeEnabled: Boolean = false,
    val proSettings: ProDefaults = ProDefaults(),
    val supportsManualControls: Boolean = false,
    val isoRange: IntRange = 100..100,
    val exposureTimeRangeNs: LongRange = 1_000_000L..1_000_000L,
    val exposureCompensationRange: IntRange = 0..0,
    val minFocusDistanceDiopters: Float = 0f,
    val jpegQuality: Int = 95
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = CameraController(application.applicationContext)
    private val configRepository = ConfigRepository(application.applicationContext)

    private var activeConfig: CameraConfig = configRepository.load()

    private val _uiState = MutableStateFlow(
        CameraUiState(
            filters = activeConfig.filters,
            proSettings = activeConfig.pro,
            jpegQuality = activeConfig.imageProcessing.jpegQuality
        )
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var recordingTimerJob: Job? = null

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            val caps = controller.bindToCamera(lifecycleOwner, previewView)
            applyCapabilities(caps)
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (_uiState.value.isRecording) return
        viewModelScope.launch {
            val caps = controller.switchCamera(lifecycleOwner, previewView)
            val nowFront = controller.lensFacing == CameraSelector.LENS_FACING_FRONT
            applyCapabilities(caps)
            _uiState.update {
                it.copy(
                    isFrontCamera = nowFront,
                    flashMode = if (nowFront) FlashMode.OFF else it.flashMode
                )
            }
        }
    }

    private fun applyCapabilities(caps: CameraCapabilities) {
        _uiState.update {
            it.copy(
                hasFlashUnit = caps.hasFlash,
                minZoomRatio = caps.minZoomRatio,
                maxZoomRatio = caps.maxZoomRatio,
                zoomRatio = 1f,
                supportsManualControls = caps.supportsManualControls,
                isoRange = caps.isoRange,
                exposureTimeRangeNs = caps.exposureTimeRangeNs,
                exposureCompensationRange = caps.exposureCompensationRange,
                minFocusDistanceDiopters = caps.minFocusDistanceDiopters
            )
        }
        if (_uiState.value.proModeEnabled) {
            if (caps.supportsManualControls) {
                controller.applyManualControls(_uiState.value.proSettings)
            } else {
                // This camera doesn't support manual controls (e.g. a LIMITED
                // front camera) - fall back to auto instead of risking an
                // unsupported CaptureRequest breaking the session.
                controller.applyManualControls(ProDefaults())
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

    fun selectFilter(filter: FilterPreset) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    // --- Pro mode -------------------------------------------------------

    fun toggleProMode(enabled: Boolean) {
        _uiState.update { it.copy(proModeEnabled = enabled) }
        val settings = if (enabled) _uiState.value.proSettings else ProDefaults()
        controller.applyManualControls(settings)
    }

    /** Live update while dragging a control - pushes to the camera, no disk write. */
    fun updateProLive(transform: (ProDefaults) -> ProDefaults) {
        val updated = transform(_uiState.value.proSettings)
        _uiState.update { it.copy(proSettings = updated) }
        if (_uiState.value.proModeEnabled) {
            controller.applyManualControls(updated)
        }
    }

    /** Called once a drag/adjustment finishes - persists into the config file. */
    fun commitProSettings() {
        activeConfig = activeConfig.copy(pro = _uiState.value.proSettings)
        configRepository.save(activeConfig)
    }

    // --- Config import/export -------------------------------------------

    private fun currentConfigSnapshot(): CameraConfig {
        val state = _uiState.value
        return activeConfig.copy(
            pro = state.proSettings,
            filters = state.filters,
            imageProcessing = activeConfig.imageProcessing.copy(jpegQuality = state.jpegQuality)
        )
    }

    fun writeExportedConfig(uri: Uri): Boolean {
        activeConfig = currentConfigSnapshot()
        val xml = configRepository.exportXmlText(activeConfig)
        return try {
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Tidak bisa buka file tujuan")
            stream.use { out -> out.write(xml.toByteArray(Charsets.UTF_8)) }
            _uiState.update { it.copy(message = "Config berhasil di-export") }
            true
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Gagal export config") }
            false
        }
    }

    fun importConfigFrom(uri: Uri) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
                ?: throw IllegalStateException("File kosong")
            val imported = configRepository.parseImportedXml(text)
            activeConfig = imported
            configRepository.save(imported)
            _uiState.update {
                it.copy(
                    filters = imported.filters,
                    selectedFilter = FilterPreset.ORIGINAL,
                    proSettings = imported.pro,
                    jpegQuality = imported.imageProcessing.jpegQuality,
                    message = "Config berhasil di-import"
                )
            }
            if (_uiState.value.proModeEnabled) {
                controller.applyManualControls(imported.pro)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Config gak valid, gagal di-import") }
        }
    }

    // --- Capture ----------------------------------------------------------

    fun onShutterPressed(hasAudioPermission: Boolean) {
        when (_uiState.value.captureMode) {
            CaptureMode.PHOTO -> capturePhoto()
            CaptureMode.VIDEO -> toggleRecording(hasAudioPermission)
        }
    }

    private fun capturePhoto() {
        val state = _uiState.value
        val flashMode = when (state.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
        controller.capturePhoto(
            flashMode = flashMode,
            filter = state.selectedFilter,
            jpegQuality = state.jpegQuality,
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
