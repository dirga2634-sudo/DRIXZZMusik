package com.drizzx.camera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.drizzx.camera.config.FilterPreset
import com.drizzx.camera.config.ProDefaults
import com.drizzx.camera.filter.PhotoFilterProcessor
import com.drizzx.camera.util.MediaStoreUtils
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Snapshot of what the currently bound camera can actually do. */
data class CameraCapabilities(
    val hasFlash: Boolean,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val supportsManualControls: Boolean = false,
    val isoRange: IntRange = 100..100,
    val exposureTimeRangeNs: LongRange = 1_000_000L..1_000_000L,
    val minFocusDistanceDiopters: Float = 0f,
    val exposureCompensationRange: IntRange = 0..0
)

/**
 * Owns every CameraX object directly so the ViewModel and UI layer never
 * import androidx.camera classes themselves - keeps camera-specific bugs
 * contained to one file when tuning per-device behaviour later.
 */
class CameraController(private val appContext: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    // Backs the temp-file -> filter -> MediaStore path used whenever a
    // non-Original filter is active. Cancelled in release().
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    private suspend fun obtainProvider(): ProcessCameraProvider {
        cameraProvider?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener(
                {
                    val provider = future.get()
                    cameraProvider = provider
                    continuation.resume(provider)
                },
                ContextCompat.getMainExecutor(appContext)
            )
        }
    }

    /** Binds preview + photo + video use-cases. Safe to call again (e.g. after switching camera). */
    suspend fun bindToCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): CameraCapabilities {
        val provider = obtainProvider()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
            )
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        provider.unbindAll()
        val camera = provider.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview,
            imageCapture,
            videoCapture
        )
        boundCamera = camera

        val zoomState = camera.cameraInfo.zoomState.value
        val exposureState = camera.cameraInfo.exposureState
        val manual = queryManualCapability(camera)

        return CameraCapabilities(
            hasFlash = camera.cameraInfo.hasFlashUnit(),
            minZoomRatio = zoomState?.minZoomRatio ?: 1f,
            maxZoomRatio = zoomState?.maxZoomRatio ?: 1f,
            supportsManualControls = manual.supported,
            isoRange = manual.isoRange,
            exposureTimeRangeNs = manual.exposureTimeRangeNs,
            minFocusDistanceDiopters = manual.minFocusDistanceDiopters,
            exposureCompensationRange = exposureState.exposureCompensationRange.lower..
                exposureState.exposureCompensationRange.upper
        )
    }

    suspend fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): CameraCapabilities {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        return bindToCamera(lifecycleOwner, previewView)
    }

    fun setTorch(enabled: Boolean) {
        boundCamera?.cameraControl?.enableTorch(enabled)
    }

    fun setZoomRatio(ratio: Float) {
        boundCamera?.cameraControl?.setZoomRatio(ratio)
    }

    fun focusOn(point: MeteringPoint) {
        val action = FocusMeteringAction.Builder(point).build()
        boundCamera?.cameraControl?.startFocusAndMetering(action)
    }

    /**
     * Pushes Pro mode's manual controls to the live camera. Safe to call with
     * every field on auto - that's how Pro mode gets turned back off.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun applyManualControls(pro: ProDefaults) {
        val camera = boundCamera ?: return
        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
        val builder = CaptureRequestOptions.Builder()

        val exposureManual = !pro.isoAuto || !pro.shutterAuto
        if (exposureManual) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, pro.isoValue)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, pro.shutterSpeedNs)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        if (!pro.focusAuto) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, pro.focusDistanceDiopters)
        } else {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        }

        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbModeFor(pro.whiteBalancePreset))

        camera2Control.setCaptureRequestOptions(builder.build())

        if (!exposureManual) {
            camera.cameraControl.setExposureCompensationIndex(pro.exposureCompensationIndex)
        }
    }

    private fun awbModeFor(preset: String): Int = when (preset.lowercase()) {
        "daylight" -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
        "cloudy" -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
        "incandescent" -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
        "fluorescent" -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
        "shade" -> CaptureRequest.CONTROL_AWB_MODE_SHADE
        else -> CaptureRequest.CONTROL_AWB_MODE_AUTO
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun queryManualCapability(camera: Camera): ManualCapability {
        return try {
            val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
            val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = manager.getCameraCharacteristics(cameraId)

            val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val supportsManual = hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

            val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

            ManualCapability(
                supported = supportsManual,
                isoRange = isoRange?.let { it.lower..it.upper } ?: (100..100),
                exposureTimeRangeNs = exposureRange?.let { it.lower..it.upper } ?: (1_000_000L..1_000_000L),
                minFocusDistanceDiopters = minFocus
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read Camera2 characteristics", t)
            ManualCapability()
        }
    }

    fun capturePhoto(
        flashMode: Int,
        filter: FilterPreset,
        jpegQuality: Int,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("Kamera belum siap")
            return
        }
        capture.flashMode = flashMode

        if (filter == FilterPreset.ORIGINAL) {
            // Fast path: CameraX writes straight to MediaStore, no re-encode.
            capture.takePicture(
                MediaStoreUtils.buildPhotoOutputOptions(appContext),
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val uri = output.savedUri
                        if (uri != null) onSaved(uri) else onError("Foto tersimpan tapi URI kosong")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed", exception)
                        onError(exception.message ?: "Gagal ambil foto")
                    }
                }
            )
            return
        }

        // Filtered path: capture to a private temp file first, then decode,
        // rotate upright, run the filter, and publish the result ourselves.
        val tempFile = File(appContext.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(tempFile).build(),
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processFilteredCapture(tempFile, filter, jpegQuality, onSaved, onError)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    tempFile.delete()
                    onError(exception.message ?: "Gagal ambil foto")
                }
            }
        )
    }

    private fun processFilteredCapture(
        tempFile: File,
        filter: FilterPreset,
        jpegQuality: Int,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        controllerScope.launch {
            val result = runCatching {
                val bitmap = decodeUprightBitmap(tempFile)
                val jpegBytes = PhotoFilterProcessor.apply(bitmap, filter, jpegQuality)
                bitmap.recycle()
                MediaStoreUtils.writePhotoBytes(appContext, jpegBytes) ?: error("Gagal nyimpen foto")
            }
            tempFile.delete()
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { uri -> onSaved(uri) },
                    onFailure = { t ->
                        Log.e(TAG, "Photo processing failed", t)
                        onError(t.message ?: "Gagal proses foto")
                    }
                )
            }
        }
    }

    private fun decodeUprightBitmap(file: File): Bitmap {
        val original = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Gagal decode hasil foto")
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotationDegrees == 0f) return original

        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        original.recycle()
        return rotated
    }

    fun startRecording(withAudio: Boolean, onEvent: (VideoRecordEvent) -> Unit) {
        val capture = videoCapture ?: return
        var pending = capture.output.prepareRecording(
            appContext,
            MediaStoreUtils.buildVideoOutputOptions(appContext)
        )
        if (withAudio) {
            pending = pending.withAudioEnabled()
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(appContext)) { event ->
            onEvent(event)
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun release() {
        cameraProvider?.unbindAll()
        activeRecording = null
        controllerScope.cancel()
    }

    private data class ManualCapability(
        val supported: Boolean = false,
        val isoRange: IntRange = 100..100,
        val exposureTimeRangeNs: LongRange = 1_000_000L..1_000_000L,
        val minFocusDistanceDiopters: Float = 0f
    )

    companion object {
        private const val TAG = "CameraController"
    }
}
