package com.drizzx.camera.camera

import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.drizzx.camera.util.MediaStoreUtils
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Snapshot of what the currently bound camera can actually do. */
data class CameraCapabilities(
    val hasFlash: Boolean,
    val minZoomRatio: Float,
    val maxZoomRatio: Float
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
        return CameraCapabilities(
            hasFlash = camera.cameraInfo.hasFlashUnit(),
            minZoomRatio = zoomState?.minZoomRatio ?: 1f,
            maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
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

    fun capturePhoto(
        flashMode: Int,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("Kamera belum siap")
            return
        }
        capture.flashMode = flashMode

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
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
